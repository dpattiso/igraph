package recogniser.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;


import javaff.JavaFF;
import javaff.data.Action;
import javaff.data.CompoundLiteral;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.Plan;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.data.strips.And;
import javaff.data.strips.Not;
import javaff.parser.PDDL21parser;
import javaff.parser.STRIPSTranslator;
import javaff.planning.STRIPSState;
import javaff.planning.State;
import javaff.search.UnreachableGoalException;

/**
 * A continuous planner based on JavaFF. Plans are generated using an initial problem file, but a new goal
 * and associated new plan can be requested at any time by calling switchGoal(). The alternative and expected
 * usage is to repeatedly call getNextObservation() which will update the current state given the next
 * action in the plan. Once a percentage threshold has been reached, the currently executing plan is
 * abandoned and a new goal randomly selected to be planned towards.  
 *  
 * @author David Pattison
 *
 */
public class ContinuousPlanner
{
	private JavaFF javaff;
	private File domain;
	private File pfile;
	private GroundProblem gproblem;
	
	private ArrayList<Fact> reachableGoals;
	private ArrayList<Fact> usedGoals;
	
	//As the plan is executed, this variable's value indicates 
	//the time at which the planner should switch to another goal.
	private float maxPlanPercentage, currentPlanPercentage;
	private Plan currentPlan;
	private int currentActionIndex;
	private Fact currentGoal;
	private State currentState;
	
	private boolean planning;
	private Random rand;
	private long maxPlanTimeoutSeconds;
	private int minimumPlanLength;
	private int maxSwitches;
	
	private HashSet<Fact> closedGoals;
	
	private ExecutorService threadPool;

	private String goalInputFile;
	private List<Fact> userGoals;
	private int userGoalCounter;

	/**
	 * 
	 * @param domain PDDL domain file
	 * @param pfile PDDL problem file -- the goal of this file is used as the first goal of the planner
	 * @param switchPercentage The ratio of a subplan at which it is abandoned [0:1]. For example, 0.4 means abandon the current plan/goal 
	 * 							after 40% of the plan's actions
	 * @param maxSwitches The maximum number of times a goal will be switched.
	 * @param minPlanLength The minimum length of a plan.
	 */
	public ContinuousPlanner(String domain, String pfile, float switchPercentage, int maxSwitches, int minPlanLength)
	{
		this(new File(domain), new File(pfile), switchPercentage, maxSwitches, minPlanLength, null);
	}

	/**
	 * 
	 * @param domain PDDL domain file
	 * @param pfile PDDL problem file -- the goal of this file is used as the first goal of the planner
	 * @param switchPercentage The ratio of a subplan at which it is abandoned [0:1]. For example, 0.4 means abandon the current plan/goal 
	 * 							after 40% of the plan's actions
	 * @param goalFile A file containing the goals which should be used during execution, rather than randomly selected
	 */
	public ContinuousPlanner(String domain, String pfile, float switchPercentage, String goalFile)
	{
		this(new File(domain), new File(pfile), switchPercentage, -1, -1, goalFile);
	}
	
	/**
	 * 
	 * @param domain PDDL domain file
	 * @param pfile PDDL problem file -- the goal of this file is used as the first goal of the planner
	 * @param switchPercentage The ratio of a subplan at which it is abandoned [0:1]. For example, 0.4 means abandon the current plan/goal 
	 * 							after 40% of the plan's actions
	 * @param maxSwitches The maximum number of times a goal will be switched, -1 if user-based goals are specified
	 * @param minPlanLength The minimum length of a plan, -1 if user-based goals are specified
	 * @param goalFile A file containing the goals which should be used instead of randomly switching on abandonment
	 */
	protected ContinuousPlanner(File domain, File pfile, float switchPercentage, int maxSwitches, int minPlanLength, String goalFile) 
	{
		this.domain = domain;
		this.pfile = pfile;
		this.goalInputFile = goalFile;
			
		
		this.maxPlanPercentage = switchPercentage;
		this.minimumPlanLength = minPlanLength;
		this.maxSwitches = maxSwitches;
		this.currentPlanPercentage = 0f;
		this.maxPlanTimeoutSeconds = 60;

		
		if (this.maxSwitches <= 0)
			this.maxSwitches = Integer.MAX_VALUE;
		
		this.closedGoals = new HashSet<Fact>();
		
		try
		{
			this.javaff = new JavaFF(domain, File.createTempFile("tmp_plan", "soln"));
//			this.javaff.setUseBFS(false);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new NullPointerException("Cannot create temporary plan file");
		}
		this.gproblem = null;
		this.reachableGoals = new ArrayList<Fact>();
		this.usedGoals = new ArrayList<Fact>();
		
		this.currentPlan = null;
		this.currentGoal = null;
		this.currentState = null;
		this.currentActionIndex = 0;
		
		this.rand = new Random(1234); //FIXME add seed parameter 

		//setup a thread pool executor for a single thread
		this.threadPool = Executors.newSingleThreadExecutor();
//		this.threadPool = Executors.newFixedThreadPool(1); //should be done in constructor, but prevents planner from working - don;t know why
		
	}
	
	/**
	 * Converts a goal String to a Fact.
	 * @param goalString
	 * @return
	 */
	protected Fact parseGoal(String goalString)
	{
		int c = 0;
		for (Fact g : this.gproblem.getReachableFacts())
		{
			if (g.toString().equalsIgnoreCase(goalString))
			{
				return g;
			}
//			System.out.println(c++ + goalString + " = "+g.toString());
		}
		
		return null;
	}

	/**
	 * Reads the goal file and converts the lines to Facts.
	 * @return
	 * @throws FileNotFoundException
	 * @throws UnreachableGoalException
	 */
	protected List<Fact> readGoalFile() throws FileNotFoundException, UnreachableGoalException
	{
		ArrayList<Fact> goals = new ArrayList<>();
		try
		{
			@SuppressWarnings("resource")
			Scanner scan = new Scanner(new File(this.goalInputFile));
			
			while (scan.hasNext())
			{
				String line = scan.nextLine();
				
				String copy = line;
				Fact goal = null;
				boolean conjunctive = false;
				if (copy.startsWith("(and "))
				{
					goal = new And();
					conjunctive = true;
					//cut off "(and "), last ")" and any trailing whitespace
					copy = copy.trim().substring(5);
					copy = copy.substring(0, copy.length()-1);
				}
				String[] tokens = copy.split("\\(|\\)|\\).*\\(");
				
				for (String tok : tokens)
				{
					//hack
					if (tok.isEmpty() || tok.matches("\\W+"))
						continue;
					
					tok = tok.trim();
					Fact literal = this.parseGoal(tok);
					
					if (literal == null)
						throw new UnreachableGoalException("Goal \""+tok+"\" in goal file does not exist in ground problem", new NullPointerException());
					
	
					if (conjunctive)
						((And)goal).add(literal);
				}
				goals.add(goal);
			}
			scan.close();
		}
		catch (FileNotFoundException e)
		{
			throw e;
		}
		
		return goals;
	}
	
	/**
	 * Start the continuous planning process.
	 * 
	 * @return
	 * @throws UnreachableGoalException Thrown if the goals in the file are not part of the domain or are unreachable.
	 * @throws FileNotFoundException Thrown if the goal file does not exist.
	 */
	public boolean startPlanning() throws FileNotFoundException, UnreachableGoalException
	{
		if (this.planning == true)
			return false;
		

		if (this.goalInputFile != null)
		{
			this.userGoals = this.readGoalFile();
			this.userGoalCounter = 0;
		}
		
		this.planning = true;
		this.currentPlanPercentage = 0f;
		this.currentActionIndex = 0;
		
		this.userGoalCounter = 0;
		
		//plan until we have a goal.
		switchGoal(); //randomly select and plan to goals until a valid goal and plan is found
		
		return true;
	}
	
	/**
	 * Update the current state with the next action in the current plan. If this action exceeds
	 * the plan-switch threshold, a new goal and plan is computed prior to the above behaviour being executed.
	 * @return The next action from the current plan.
	 */
	public Action getNextObservation()
	{
		boolean hasGoal = true;
		if (this.currentPlanPercentage >= this.maxPlanPercentage && this.usedGoals.size() < this.maxSwitches)
		{
			hasGoal = this.switchGoal();
			System.out.println("Switching goal at step "+this.currentActionIndex+" to "+this.currentGoal);
		}
		
		if (hasGoal == false || this.usedGoals.size() > this.maxSwitches || (this.usedGoals.size() == this.maxSwitches && this.currentPlanPercentage == 1f))
		{
			return null;
		}
		
		//FIXME zero length plans fail here (as in if the goal is already achieved and the plan has zero length)
		
		Action nextAction = this.currentPlan.getActions().get(this.currentActionIndex++); 
		//update current state
		State nextState = this.currentState.apply(nextAction);
		this.currentState = nextState;
		
		this.currentPlanPercentage = ((float)this.currentActionIndex / (float)this.currentPlan.getActionCount());
		
		return nextAction;
	}
	
	/**
	 * Randomly select a new goal (different to the current one), compute a plan to it
	 * and then switch execution to this new plan.
	 */
	public boolean switchGoal()
	{
		Fact newGoal = null;
		
		//if the input file is null, switch to a random goal
		boolean foundPlan = false;
		while (foundPlan == false)
		{
			if (this.goalInputFile == null)
			{
				newGoal = this.getRandomGoal(this.currentGoal);
				System.out.println("Selected random goal "+newGoal);
			}
			else
			{
				if (this.userGoalCounter >= this.userGoals.size())
				{
					System.out.println("All user-defined goals processed");
					newGoal = null;
					break;
				}
				
				Fact nextUserGoal = this.userGoals.get(this.userGoalCounter++);
				newGoal = nextUserGoal;
			}
				
			foundPlan = this.switchGoal(newGoal);
			if (foundPlan == true && this.currentPlan.getActions().size() < this.minimumPlanLength)
			{
				System.out.println("Plan too short, only "+this.currentPlan.getActions().size()+" steps. Minimum "+this.minimumPlanLength+" needed");
				foundPlan = false;
//				closedGoals.add(newGoal);
			}

			if (foundPlan)
				this.usedGoals.add(newGoal); //only add if associated plan is valid
		}
	
		//else switch to the next user-specified goal

		
		this.closedGoals.clear();
		this.currentGoal = newGoal;
		
		return newGoal != null;
	}
	
	/**
	 * Compute a plan to the new goal specified
	 * and then switch execution to this new plan.
	 */
	public boolean switchGoal(Fact newGoal)
	{
		Plan newPlan = this.getPlan(newGoal);
		if (newPlan == null)
			return false;
		
		this.setCurrentPlan(newPlan);
		
		return true;
	}
	
	/**
	 * Sets the currently executing plan;
	 * @param newPlan
	 */
	protected void setCurrentPlan(Plan newPlan)
	{
		this.currentPlan = newPlan;
		this.currentActionIndex = 0;
		this.currentPlanPercentage = 0f;
	}

	/**
	 * Compute a plan to the specified goal, from the current state. The current state is simply the state
	 * which the previous plan finished in.
	 * 
	 * @param newGoal
	 * @return
	 */
	protected Plan getPlan(Fact newGoal)
	{		
		((STRIPSState)this.currentState).getRPG().constructStableGraph(this.currentState);
		
		GroundProblem newProblem = (GroundProblem) this.gproblem.clone();
		newProblem.setState((STRIPSState) this.currentState.clone());
		newProblem.setInitial(((STRIPSState)this.currentState).getTrueFacts());
		newProblem.setGoal((GroundFact) newGoal);
		newProblem.recomputeSTRIPSInitialState();

		JavaFFThread javaffThread = new JavaFFThread(newProblem);
		long startTime = System.nanoTime() / 1000000000;
		long now = startTime;
		javaffThread.start();
		do
		{
			now = System.nanoTime() / 1000000000;
			
			if (javaffThread.isPlanningFinished())
			{
				if (javaffThread.plan != null)
					return javaffThread.plan;
				else 
					break;
			}
			
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		while ((now - startTime) < this.maxPlanTimeoutSeconds);
		
		try
		{
			javaffThread.stop(); //sweet jesus this is bad practice, but calling interrupt() rarely works, and I just want it to... well, work
		}
		catch (Exception e)
		{
			System.err.println("Error whilst interrupting planning thread:");
			e.printStackTrace();
		}
		
		return null;
	}

	/**
	 * Sets up the planner by grounding the problem using the specified domain and initial problem file.
	 * This can be avoided if the problem has already been grounded.
	 * @return
	 */
	protected void setupPlanner()
	{

		// ********************************
		// Parse and Ground the Problem
		// ********************************
		UngroundProblem unground = PDDL21parser.parseFiles(this.domain, this.pfile);
		
		if (unground == null)
		{
			System.err.println("Parsing error - see console for details");
			throw new NullPointerException("Unable to parse domain");
		}
		
		System.out.println("Grounding...");
		GroundProblem gp = unground.ground();
		System.out.println("Grounding complete");
		System.out.println("Decompiling ADL...");
		int previousActionCount = gp.getActions().size();
		gp.decompileADL();
		int adlActionCount = gp.getActions().size();
		System.out.println("Decompiling ADL complete");
		System.out.println(previousActionCount+" actions before ADL, "+adlActionCount+" after");
		
		try
		{
			gp.filterReachableFacts();
		}
		catch (UnreachableGoalException e)
		{
			e.printStackTrace();
		}
		
		this.gproblem = gp;
		this.currentState = (State) this.gproblem.getSTRIPSInitialState().clone();
		this.reachableGoals = new ArrayList<Fact>(this.gproblem.getReachableFacts());
		
		this.gproblem.setGoal((GroundFact) this.getRandomGoal(null));
	}
	
	private class HPair implements Comparable<HPair>
	{
		public Fact fact;
		public int hValue;
		
		public HPair(Fact f, int hValue)
		{
			this.fact = f;
			this.hValue = hValue;
		}

		@Override
		public int compareTo(HPair o)
		{
			int res = Integer.compare( o.hValue, this.hValue); //swap, want higher numbers first;
			if (res != 0)
				return res;
			else
				return o.fact.toString().compareTo(this.fact.toString());
		}
	}
	
	
	
	/**
	 * Randomly select a new goal which is different to the specified goal from the set of known goals. 
	 * 
	 * @param currentGoal
	 * @return
	 */
	protected Fact getRandomGoal(Fact currentGoal)
	{
		Fact newGoal = null;
		((STRIPSState)this.currentState).getRPG().constructStableGraph(this.currentState);
		int rpgLayers = ((STRIPSState)this.currentState).getRPG().size();
//		LinkedList<Fact> reachable = new LinkedList<Fact>(((STRIPSState)this.currentState).getRPG().getFactsAtLayer(rpgLayers));
		LinkedList<Fact> reachable = new LinkedList<Fact>(this.gproblem.getReachableFacts());
		//Collections.shuffle(reachable); //most of the time things will come back in the same order, which leads to short, cyclic plans
		//TODO consider putting in an option for a random seed here
		Collections.sort(reachable);
		
		int h = -1;
		do
		{
			if (reachable.isEmpty())
				throw new NullPointerException("No more goals to check.");
			
			while ((newGoal = reachable.get(this.rand.nextInt(reachable.size()))) instanceof Not)
			{}
			
			//use shuffled version
//			while ((newGoal = reachable.poll()) instanceof Not)
//			{}
			
			if (newGoal == null)
				throw new NullPointerException("No valid goals could be found");
			
			h = ((STRIPSState)this.currentState).getRPG().getPlanFromExistingGraph(newGoal).getPlanLength();


//			System.out.println("H is "+h+", "+newGoal);
			
//			HPair pair = new HPair(first, h);
//			sortedFacts.add(pair);
			
		}
		while (this.closedGoals.contains(newGoal) || 
				newGoal.equals(currentGoal) ||
				h < this.minimumPlanLength ||
				newGoal instanceof Not ||
				((STRIPSState)this.currentState).getFacts().contains(newGoal) == true ||
				newGoal.isStatic() == true);
		
//		this.closedGoals.add(newGoal);
		return newGoal;
	}
	
	public void terminateThreadPool()
	{
		this.threadPool.shutdownNow();
	}
	
	public static void main(String[] args)
	{
		
		String domain = args[0];
		String pfile = args[1];
		File soln = new File(args[2]);
		File switchFileOutput = new File(args[3]);
		float switchPercentage = Float.parseFloat(args[4]);
		int maxSteps = Integer.parseInt(args[5]);
		
		ContinuousPlanner cplan = null;
		
		String arg = args[6];

		if (Pattern.matches("[0-9]+", arg))
		{
			int maxSwitches = Integer.parseInt(arg);
			int minPlanLength = Integer.parseInt(args[7]);
			
			cplan = new ContinuousPlanner(domain, pfile, switchPercentage, maxSwitches, minPlanLength);
		}
		else
		{
			cplan = new ContinuousPlanner(domain, pfile, switchPercentage, arg);
		}
		cplan.setupPlanner();
		
		
		try
		{
			cplan.startPlanning();
		}
		catch (FileNotFoundException | UnreachableGoalException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		TotalOrderPlan plan = new TotalOrderPlan(null);


		BufferedWriter goalSwitchWriter = null;
		Fact prevGoal = (Fact) cplan.currentGoal.clone();
		//log this goal switch, and when it occurred
		try
		{
			goalSwitchWriter = new BufferedWriter(new FileWriter(switchFileOutput));
			
			goalSwitchWriter.append(plan.getPlanLength()+" "+prevGoal.toString()+"\n");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		for (int i = 0; i < maxSteps; i++)
		{
			System.out.println("Step "+i+": "+cplan.currentPlanPercentage*100 + "% through plan (max "+cplan.maxPlanPercentage*100+"%)");
			
			Action nextAction = cplan.getNextObservation();
			if (nextAction == null)
				break;
			
			Fact currGoal = (Fact) cplan.currentGoal.clone();
			if (currGoal.equals(prevGoal) == false)
			{
				prevGoal = (Fact) currGoal.clone();
				
				//log this goal switch, and when it occurred
				try
				{
					goalSwitchWriter.append(plan.getPlanLength()+" "+currGoal.toString()+"\n");
				}
				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			plan.addAction(nextAction);
			
			System.out.println("Total plan length: "+plan.getActionCount());
		}
		
		if (cplan.currentPlanPercentage < 1f )
			plan.getActions().addAll(cplan.currentPlan.getActions().subList(cplan.currentActionIndex, cplan.currentPlan.getActions().size()));

		try
		{
			System.out.println("Created plan of length "+plan.getActionCount()+" (max "+maxSteps+" with cutoff of "+switchPercentage*100+"%. Plan contains "+cplan.usedGoals.size()+" goal changes");
			System.out.println("Writing plan file to "+soln.getAbsolutePath());
			
//			System.out.println("plan is "+plan+", file is "+fileOut);
			soln.delete();
			soln.createNewFile();
			
			FileOutputStream outputStream = new FileOutputStream(soln);
			PrintWriter printWriter = new PrintWriter(outputStream);
			plan.print(printWriter);
			printWriter.close();
			
			if (goalSwitchWriter != null)
				goalSwitchWriter.close(); //close goal switch logger
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		//kill the thread pool manually or execution will not stop.
		cplan.terminateThreadPool();
	}
	
	
	public class JavaFFCallable implements Callable<TotalOrderPlan>
	{
		private GroundProblem gp;
		Thread r;
		
		public JavaFFCallable(GroundProblem gp)
		{
			super();
			this.gp = gp;
		}

		@Override
		public TotalOrderPlan call() throws Exception
		{
			try
			{
				return (TotalOrderPlan) javaff.plan(this.gp);
			}
			catch (Exception e)
			{
				return null;
			}
		}
	}
	
	public class JavaFFThread extends Thread
	{
		private GroundProblem gp;
		private volatile TotalOrderPlan plan;
		private volatile boolean planningFinished;
		
		public JavaFFThread(GroundProblem gp)
		{
			super();
			this.gp = gp;
			this.planningFinished = false;
		}

		@Override
		public void run()
		{
			try
			{
				this.plan = (TotalOrderPlan) javaff.plan(this.gp);
				this.planningFinished = true;
			}
			catch (Exception e)
			{
				System.out.println("Planning interrupted");
			}
				
		}
		
		public TotalOrderPlan getPlan()
		{
			return this.plan;
		}
		
		public boolean isPlanningFinished()
		{
			return this.planningFinished;
		}
	}


	public long getMaxPlanTimeoutMillis()
	{
		return maxPlanTimeoutSeconds;
	}

	public void setMaxPlanTimeoutMillis(long maxPlanTimeoutMillis)
	{
		this.maxPlanTimeoutSeconds = maxPlanTimeoutMillis;
	}
}
