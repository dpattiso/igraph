package recogniser;
import java.awt.Toolkit;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javaff.JavaFF;
import javaff.data.Action;
import javaff.data.DomainRequirements;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.MutexSet;
import javaff.data.Parameter;
import javaff.data.Plan;
import javaff.data.Requirement;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.data.adl.Exists;
import javaff.data.adl.ForAll;
import javaff.data.adl.Imply;
import javaff.data.adl.Or;
import javaff.data.strips.And;
import javaff.data.strips.InstantAction;
import javaff.data.strips.Not;
import javaff.data.strips.Proposition;
import javaff.data.strips.SingleLiteral;
import javaff.data.temporal.DurativeAction;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.STRIPSTranslator;
import javaff.parser.SolutionParser;
import javaff.planning.STRIPSState;
import javaff.scheduling.SchedulingException;
import recogniser.hypothesis.AllFalseGoal;
import recogniser.hypothesis.BoundedGoalHypothesis;
import recogniser.hypothesis.ConjunctiveGoalHypothesis;
import recogniser.hypothesis.IGoalHypothesis;
import recogniser.hypothesis.MutexGoalSpace;
import recogniser.hypothesis.VariableGoalSpace;
import recogniser.learning.agent.NullAgent;
import recogniser.search.IHeuristic;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.IGRAPHPreferences;
import recogniser.util.RecognitionException;
import recogniser.util.StateHistoryTuple;
import recogniser.util.StripsRpg;
import recogniser.util.UnknownEstimateException;
import recogniser.util.VisualGoalSpace;
import sas.data.SASProblem;
import sas.parser.SASTranslator;
import sas.parser.SASplusParser;
import sas.util.NullOutputStream;
import sas.util.NullPrintStream;
import sas.util.SASException;
import javaff.search.UnreachableGoalException;
import sas.util.UnsolveableProblemException;
import threader.util.PlanScheduleState;
import threader.util.PlanThread;


public class IGRAPHTestHarness
{
	protected PriorityQueue<BoundedGoalHypothesis> queuedBoundHypotheses;
//	protected Map<IGoalHypothesis, Integer> targetMap = new HashMap<IGoalHypothesis, Integer>();
	protected UngroundProblem uproblem;
	protected GroundProblem gproblem;
	protected STRIPSState initialState;
	public GroundProblem gproblemWithGoal; //exposed for abandonment issues
	protected File pFile;
	protected File domain;
	protected SASProblem sasproblem;
	protected HybridSasPddlProblem hybridProblem;
	protected IGRAPH igraph;
	protected TotalOrderPlan completePlan;
	
	protected boolean computeBoundedHyps, visual;
	protected boolean debugOutput;

	
	private HashMap<MutexGoalSpace, HashMap<Fact, ArrayList<Double>>> pastEstimates;
	private HashMap<MutexGoalSpace, HashMap<Fact, ArrayList<Double>>> pastProbabilities;
	
	
	public IGRAPHTestHarness(String[] args) throws RecognitionException
	{
		this.initialisePreferences(args);
		
		try
		{
			this.setupRecogniser();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			
			throw new RecognitionException(e);
		}
	}
	
	public void initialisePreferences(String[] args) throws RecognitionException
	{
		this.computeBoundedHyps = true;
		
		if (args.length == 0)
		{
			IGRAPHTestHarness.printUsage();
			return;
		}

		String[] files = new String[args.length-1];
		for (int i = 1; i < args.length; i++)
			files[i-1] = args[i];
		
		Map<String, String> prefs = new HashMap<String, String>();
		
		prefs.put("domainFile", args[0]);
		prefs.put("problemFile", args[1]);
		prefs.put("solutionFile", args[2]);
		prefs.put("outputFile", args[3]);
		
		//rest are key-value pairs
		for (int i = 4; i < args.length; ) //no increment
		{
			String key = args[i];
			key = key.substring(1); // remove -
			//System.out.println("key is "+key);
			String value = args[i+1];
			
			//if partitions flag is specified, parse 8 probs
			// ST, UT, UA, T, W, B, U -- SA is ignored
			if (key.equals("partitions"))
			{
				prefs.put("partitionST", value);
				prefs.put("partitionUT", args[i+2]);
				prefs.put("partitionUA", args[i+3]);
				prefs.put("partitionT",  args[i+4]);
				prefs.put("partitionW",  args[i+5]);
				prefs.put("partitionB",  args[i+6]);
				prefs.put("partitionU",  args[i+7]);
				
				i+=8;
			}
			else if (key.equalsIgnoreCase("filter"))// && value.equalsIgnoreCase("stability"))
			{
				prefs.put(key, value);
				
				//need to also parse min stability/probability
//				if (value.equalsIgnoreCase("stability"))
					prefs.put("minStability", args[i+2]);
//				else if (value.equalsIgnoreCase("greedy"))
					prefs.put("minProbability", args[i+3]);
				
				i+=4;
			}
			else if (key.equalsIgnoreCase("bounded"))
			{
				if (value.equals("1"))
					this.computeBoundedHyps = true;
				else
					this.computeBoundedHyps = false;
				
				i+=2;
			}
			else
			{
				prefs.put(key, value);
				i+=2;
			}
		}
		
		try
		{
			IGRAPHPreferences.initialise(prefs);
			IGRAPHPreferences.printPreferences(System.out);
		}
		catch (NumberFormatException e)
		{
			printUsage(e);
			throw new RecognitionException(e);
		}
		catch (IllegalArgumentException e)
		{
			printUsage(e);
			throw new RecognitionException(e);
		}
		catch (NullPointerException e)
		{
			printUsage(e);
			throw new RecognitionException(e);
		}
	}
	
	/**
	 * Constructs and returns a DomainRequirements object which contains flags
	 * for the functionality currently available in IGRAPH.
	 * @return
	 */
	public static DomainRequirements GetRequirementsSupported()
	{
		DomainRequirements req = new DomainRequirements();
		req.addRequirement(Requirement.Typing);
		req.addRequirement(Requirement.Strips);
		req.addRequirement(Requirement.Equality);
		req.addRequirement(Requirement.ADL);
		req.addRequirement(Requirement.NegativePreconditions);
		req.addRequirement(Requirement.QuantifiedPreconditions);
		req.addRequirement(Requirement.ExistentialPreconditions);
		req.addRequirement(Requirement.UniversalPreconditions);

		return req;
	}

	public static void main(String[] args)
	{		
		
		try
		{
			IGRAPHTestHarness test = new IGRAPHTestHarness(args);
			test.doRecognition();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			System.exit(0);
		}
	}
	

	private void analyseAchievementTimes(
			List<IGoalHypothesis> intermediateHypotheses)
	{
		TotalOrderPlan plan = this.getPlan();
		
		HashMap<Fact, List<Integer>> goalAdditionTimes = new HashMap<Fact, List<Integer>>();
		HashMap<Fact, List<Integer>> hypothesisAdditionTimes = new HashMap<Fact, List<Integer>>();
		
		//record when each goal fact is added to the  state
		int planCount = 0;
		for (Fact g : this.gproblemWithGoal.getGoal().getFacts())
		{
			goalAdditionTimes.put(g, new ArrayList<Integer>());
			hypothesisAdditionTimes.put(g, new ArrayList<Integer>());
			
			if (gproblem.getInitial().contains(g) == true)
				goalAdditionTimes.get(g).add(0);
		}
		
		STRIPSState currState = this.gproblemWithGoal.getSTRIPSInitialState();
		for (Action a : plan.getActions())
		{
			for (Fact add : a.getAddPropositions())
			{
				if (this.gproblemWithGoal.getGoal().getFacts().contains(add) == false)
					continue;
				
				//if already true, ignore the addition
				if (currState.getFacts().contains(add))
					continue;
				
				goalAdditionTimes.get(add).add(planCount);
				
				currState = (STRIPSState) currState.apply(a);
			}
			
			
//			for (Not del : a.getDeletePropositions())
//			{
//				if (this.gproblemWithGoal.getGoal().getFacts().contains(del.literal) == false)
//					continue;
//				
//				//if already true, ignore the addition
//				if (currState.getFacts().contains(add))
//					continue;
//				
//				if (goalAdditionTimes.containsKey(add) == false)
//					goalAdditionTimes.get(add).add(planCount);
//			}
			
			++planCount;
		}
		
		Iterator<IGoalHypothesis> iter = intermediateHypotheses.iterator();
		IGoalHypothesis prevHyp = iter.next();
		//go over the first (initial) hypothesis before others
		for (Fact h : prevHyp.getGoals().getFacts())
		{
			if (this.gproblemWithGoal.getGoal().getFacts().contains(h) == false)
				continue;

			hypothesisAdditionTimes.get(h).add(0);
		}
		
		int hypCount = 0;
		while (iter.hasNext())
		{
			IGoalHypothesis hyp = iter.next();
			for (Fact h : hyp.getGoals().getFacts())
			{
				if (this.gproblemWithGoal.getGoal().getFacts().contains(h) == false)
					continue;
				
				//only interested if this goal wasn't in the previous hypothesis -- i.e. its been seen for the first time or added, deleted and re-added
				if (prevHyp.getGoals().getFacts().contains(h))
					continue;

				hypothesisAdditionTimes.get(h).add(hypCount);
			}
			
			prevHyp = hyp;
			
			++hypCount;
		}

		for (Fact g : this.gproblemWithGoal.getGoal().getFacts())
		{
			List<Integer> goalTimes = goalAdditionTimes.get(g);
			List<Integer> hypTimes = hypothesisAdditionTimes.get(g);
			
			for (Integer gTime : goalTimes)
			{
				//print "-1" to indicate goal not appearing in any hypothesis
				if (hypTimes.isEmpty())
					System.out.println("Hypothesis time accuracy: "+g.toString()+" Goal added="+gTime+", Hyp added=-1, Length="+this.getPlan().getPlanLength());
				
				for (Integer hTime : hypTimes)
				{
					
					System.out.println("Hypothesis time accuracy: "+g.toString()+" Goal added="+gTime+", Hyp added="+hTime+", Length="+this.getPlan().getPlanLength());
				}	
				
				
			}
		}

	}
	
	public STRIPSState computeFinalState(STRIPSState init, Plan plan, Set<Fact> unmovedGoals)
	{
		STRIPSState clone = (STRIPSState) init;//.clone();
		for (Object ao : ((TotalOrderPlan) plan).getActions())
		{
			Action a = (Action)ao;
//			System.out.println("Applying action "+a+" to "+clone.facts);
			clone = (STRIPSState) clone.apply(a);		
		}
		
		return clone;
	}
	
	/**
	 * Pre-observation setup. Problem instantiation, parsing, domain analysis etc.
	 * 
	 * @throws RecognitionException
	 * @throws IOException
	 * @throws ParseException
	 * @throws UnreachableGoalException Thrown if IGRAPH does not have all the true goal literals in it's goal-space.
	 */
	protected void setupRecogniser() throws RecognitionException, IOException, ParseException, UnreachableGoalException
	{
		this.setDebugOutput(false);
		
		long startTime = System.nanoTime();
		this.queuedBoundHypotheses = new PriorityQueue<BoundedGoalHypothesis>(1);
		
		this.domain = IGRAPHPreferences.DomainFile;
		this.pFile = IGRAPHPreferences.ProblemFile;
		
		this.uproblem = PDDL21parser.parseFiles(this.domain, this.pFile);
		UngroundProblem.RemoveStaticFacts = true; //needed for GR, not for planning
		boolean validDomain = this.checkRequirements(this.uproblem.requirements);
		if (validDomain == false)
			throw new IllegalArgumentException("Domain has unsupported requirements. IGRAPH currently supports the " +
					"following\n"+IGRAPHTestHarness.GetRequirementsSupported().toString());
		
		System.out.println("Grounding problem...");
		this.gproblem = this.uproblem.ground();
		System.out.println("Decompiling ADL...");
		int previousActionCount = this.gproblem.getActions().size();
		this.gproblem.decompileADL(); // remove any ADL by converting to STRIPS
		int adlActionCount = this.gproblem.getActions().size();
		System.out.println("Decompiling ADL complete");
		System.out.println(previousActionCount+" actions before ADL, "+adlActionCount+" after");
		
		System.out.println("Filtering irrelevant actions and facts");
		this.gproblemWithGoal = (GroundProblem) this.gproblem.clone();	
		
		//This block of code removes any goals which also appear in the initial state from consideration 
//		Set<Fact> goalFacts = new HashSet<Fact>(this.gproblemWithGoal.getGoal().getFacts());
//		Set<Fact> goalsInInitial = new HashSet<Fact>(goalFacts);
//		goalsInInitial.retainAll(this.gproblemWithGoal.getInitial());
//		goalFacts.removeAll(goalsInInitial);
//		this.gproblemWithGoal.setGoal(new And(goalFacts));
		
		
		/*
		 * this try-catch is really a bug-checker process. If we know the goal from the PDDL file, 
		 * then we might as well check to see if IGRAPH even has all of the literals in it's potential
		 * goal-space. clearly this is only possible via the test harness.
		 */
		try
		{
			gproblemWithGoal.filterReachableFacts(false);
		}
		catch (UnreachableGoalException e)
		{
			System.err.println("Goal specified in problem files cannot be reached by IGRAPH.");
			throw new UnreachableGoalException(this.gproblemWithGoal.getGoal(), "IGRAPH's reachable goal space does not contain all of the true goal literals");
		}
		
		
		//now filter out the real goal problem's reachable facts (which still has the goal)
		try
		{
			
			this.gproblem.filterReachableFacts(true); //True ignores the unreachable goal
		}
		catch (UnreachableGoalException e5)
		{
			//normally we don't care if the goal is unreachable, and the TRUE flag passed in 
			//should prevent this ever happening, but output an error if an exception is raised
			//Which should actually be impossible with the TRUE flag
			e5.printStackTrace();
		}
		
		this.initialState = (STRIPSState) this.gproblem.getSTRIPSInitialState().clone();
			
		this.gproblem.setGoal(new And()); //set goal to empty -- dont want the working gproblem to have the true goal!
		
		//generate a dummy goal for SAS+
		try
		{
			this.gproblem.setGoal(this.generateUsefulSASGoal());
			this.gproblem.recomputeSTRIPSInitialState();
			System.out.println("Generated dummy goal: "+this.gproblem.getGoal());
			
			//17/12/2012 -- This block of code is obsolete, but only if the correctly modified version of
			//the lama translator is used, as in the one which does not check for mutex goals and does not
			//trim out unnecessary variables
			
			//need to rewrite the pfile with all possible facts as the goal for SAS+ translation
//			String alteredPfilePath = this.pFile.getAbsolutePath();
//			alteredPfilePath = alteredPfilePath.substring(0, alteredPfilePath.lastIndexOf('/'));
	//		alteredPfilePath = alteredPfilePath + "/" + this.pFile.getName() + ".rec";
//			alteredPfilePath = alteredPfilePath + "/" + this.pFile.getName();
//			
//			File recFile = File.createTempFile(this.pFile.getName(), ".rec");
//			
//			File recPfile = STRIPSTranslator.translateToSTRIPSProblemFile(this.gproblem, recFile);

			
			//translate and initialise SAS+ problem
			if (IGRAPHPreferences.DoSASTranslation == true)
			{
				try
				{
					SASTranslator.translateToSAS(this.domain, this.pFile);
				}
				catch (Exception e)
				{
					System.out.println("Failed to translate into SAS+ problem");
					throw new RecognitionException(e);
				}
			}
		}
		catch (UnsolveableProblemException e4)
		{
			e4.printStackTrace();
			throw new RecognitionException(e4);
		}
		
		//parse in the SAS+ domain
		SASProblem sasOptimised = null;
		SASplusParser.reset();
		try
		{
			sasOptimised = SASplusParser.parse();
		}
		catch (FileNotFoundException e3)
		{
			e3.printStackTrace();
			throw new RecognitionException(e3);
		}
		catch (IOException e3)
		{
			e3.printStackTrace();
			throw new RecognitionException(e3);
		}
		catch (sas.parser.ParseException e3)
		{
			e3.printStackTrace();
			throw new RecognitionException(e3);
		}
		SASProblem sasAll = SASplusParser.sasProblemAll;
//		this.sasproblem.dtgs = sasOptimised.dtgs;
		sasAll.causalGraph = sasOptimised.causalGraph;
		this.sasproblem = sasAll;
		
//		this.sasproblem.causalGraph.generateDotGraph(new File("cg.dot"));
		
		
		
		this.visual = IGRAPHPreferences.Visual;
		
		long beforeAnalysis = System.nanoTime();
//		PDDL21parser.UP = new UngroundProblem(); //need to reset for re-parsing, or everything breaks
		
		System.out.println("Constructing hybrid problem representation");
		try
		{
			this.hybridProblem = new HybridSasPddlProblem(this.gproblem, this.sasproblem);
		}
		catch (UnreachableGoalException e3)
		{
			e3.printStackTrace();
			throw new RecognitionException(e3);
		}
		
		igraph = this.initialiseRecogniser();
		double analysisTime = (System.nanoTime() - beforeAnalysis) / 1000000000d;
		
		System.out.println("Analysis time is "+analysisTime);
		
//		TotalOrderPlan plan = null;
		System.out.println("Generating plan from file...");
		TotalOrderPlan plan = SolutionParser.parse(this.uproblem, IGRAPHPreferences.SolutionFile);
		this.setPlan(plan);
	
		long endTime = System.nanoTime();
		long initTime = (endTime - startTime) / 1000000000;
		System.out.println("Recogniser initialisation took "+initTime+" seconds");
		
	}
	
	/**
	 * Convenience method for collecting the heuristic estimates to goals based on sub-goal-spaces
	 * after each observation.
	 */
	private void updateGoalEstimates()
	{
		for (Entry<MutexGoalSpace, HashMap<Fact, ArrayList<Double>>> mgs : this.pastEstimates.entrySet())
		{
			
			for (Entry<Fact, ArrayList<Double>> e : mgs.getValue().entrySet())
			{
				Fact g = e.getKey();
				if (g instanceof AllFalseGoal)
					continue;
				
				double h = this.igraph.getGoalRecogniser().getCurrentPropDist(g); //don't know why this protected method is visible!
				
				e.getValue().add(h);
			}
		}
		
	}
	
	private void initialiseGoalEstimates()
	{
		this.pastEstimates = new HashMap<MutexGoalSpace, HashMap<Fact,ArrayList<Double>>>();
		for (MutexGoalSpace mgs : ((VariableGoalSpace) this.igraph.getGoalRecogniser().getGoalSpace()).getVariableGoalSpaces())
		{
			this.pastEstimates.put(mgs, new HashMap<Fact, ArrayList<Double>>());
			
			for (Fact g : mgs.getGoals())
			{
				if (g instanceof AllFalseGoal)
					continue;
				
				this.pastEstimates.get(mgs).put(g, new ArrayList<Double>());
				
			}
		}
		
	}
	
	/**
	 * Prints the current estimate to each fact in the true goal. Useful for debugging.
	 */
	public void printGoalEstimates()
	{
//		for (Fact g : this.gproblemWithGoal.getGoal().getFacts())
//		{
//			double h = this.igraph.getGoalRecogniser().getCurrentPropDist(g); //don't know why this protected method is visible!
//			System.out.println("Goal estimate for - " + g+" - at - "+this.igraph.getGoalRecogniser().getObservedStepCount()+" - "+h);
//		}
		
		for (MutexGoalSpace mgs : ((VariableGoalSpace) this.igraph.getGoalRecogniser().getGoalSpace()).getVariableGoalSpaces())
		{
			for (Fact g : mgs.getGoals())
			{
				if (g instanceof AllFalseGoal)
					continue;
				
				double h = this.igraph.getGoalRecogniser().getCurrentPropDist(g); //don't know why this protected method is visible!
				System.out.println("Goal estimate for , " + g+" , "+mgs.hashCode()+" , "+this.igraph.getGoalRecogniser().getObservedStepCount()+" , "+h);
			}
		}
	}
	

	/**
	 * Convenience method for collecting the probabilities
	 * after each observation.# on a per-sub-goal-space basis
	 */
	private void updateGoalProbabilities()
	{
		for (Entry<MutexGoalSpace, HashMap<Fact, ArrayList<Double>>> mgs : this.pastProbabilities.entrySet())
		{
			
			for (Entry<Fact, ArrayList<Double>> e : mgs.getValue().entrySet())
			{
				Fact g = e.getKey();
//				if (g instanceof AllFalseGoal)
//					continue;
				
				double p = mgs.getKey().getProbability(g); //don't know why this protected method is visible!
				
				e.getValue().add(p);
			}
		}
		
	}

	/**
	 * Prints the probability of every fact in each goal space being the goal from time t=0 to present. Useful for debugging.
	 */	
	private void initialiseGoalProbabilities()
	{
		this.pastProbabilities = new HashMap<MutexGoalSpace, HashMap<Fact,ArrayList<Double>>>();
		for (MutexGoalSpace mgs : ((VariableGoalSpace) this.igraph.getGoalRecogniser().getGoalSpace()).getVariableGoalSpaces())
		{
			this.pastProbabilities.put(mgs, new HashMap<Fact, ArrayList<Double>>());
			
			for (Fact g : mgs.getGoals())
			{
//				if (g instanceof AllFalseGoal)
//					continue;
				
				this.pastProbabilities.get(mgs).put(g, new ArrayList<Double>());
				
			}
		}
		
	}
	
	/**
	 * Prints the current estimate to each fact in the true goal. Useful for debugging.
	 */
	public void printGoalProbabilities()
	{		
		for (MutexGoalSpace mgs : ((VariableGoalSpace) this.igraph.getGoalRecogniser().getGoalSpace()).getVariableGoalSpaces())
		{
			for (Fact g : mgs.getGoals())
			{
				if (g instanceof AllFalseGoal)
					continue;
				
				double p = mgs.getProbability(g); //don't know why this protected method is visible!
				System.out.println("Goal probability for , " + g+" , "+mgs.hashCode()+" , "+this.igraph.getGoalRecogniser().getObservedStepCount()+" , "+p);
			}
		}
	}
	

	
	private void printProbabilities()
	{
		for (Entry<MutexGoalSpace, HashMap<Fact, ArrayList<Double>>> maps : this.pastProbabilities.entrySet())
		{
			ArrayList<Fact> orderedFacts = new ArrayList<Fact>(maps.getValue().keySet());

			System.out.println("Estimates for MGS "+maps.getKey().hashCode());
			for (Fact f : orderedFacts)
			{
				System.out.print(f);
				if (this.gproblemWithGoal.getGoal().getFacts().contains(f))
					System.out.print("*");
				
				System.out.print("\t");
			}
			System.out.println();
			
			for (int i = 0; i < this.igraph.getGoalRecogniser().getObservedStepCount()+1; i++)
			{
				for (Fact f : orderedFacts)
				{
					double pAtT = maps.getValue().get(f).get(i); 
	
					System.out.print(pAtT+"\t");
				}
				System.out.println();
			}
		}
	}


	/**
	 * @param files
	 */
	public List<IGoalHypothesis> doRecognition() throws RecognitionException
	{	
		HashSet<Fact> unusedGoals = new HashSet<Fact>();
		STRIPSState finalState = this.computeFinalState(this.gproblemWithGoal.getSTRIPSInitialState(), this.getPlan(), unusedGoals);
		
		this.pastEstimates = new HashMap<MutexGoalSpace, HashMap<Fact,ArrayList<Double>>>();
		
		long startTime = System.nanoTime();
		System.out.println("True init is "+igraph.getGoalRecogniser().getInitialHypothesis());
		System.out.println("Plan to parse is "+this.getPlan().getActions().size()+" steps:");
		this.getPlan().print(System.out);
		int observationCount = 1;
//		GroundProblem initGP = (GroundProblem) stub.clone();

		IGoalHypothesis intermediateHyp = null;
		double totalProcessTime = 0;
		
		ArrayList<IGoalHypothesis> allIntermediateHypotheses = new ArrayList<IGoalHypothesis>();
		
		VisualGoalSpace vgs = null;
		if (visual)
		{
			vgs = new VisualGoalSpace(this.igraph.getGoalRecogniser(),
					this.gproblemWithGoal.getGoal(), 
					this.getPlan());
			vgs.setSize(Toolkit.getDefaultToolkit().getScreenSize());
			vgs.setVisible(true);

			try	
			{
				if (vgs.pauseForUpdate())
				{
					while(vgs.paused())
					{
						//wait
						Thread.sleep(10);
					}
				}
				else
				{
					Thread.sleep(3000);
				}
			}
			catch (InterruptedException e1)
			{
				e1.printStackTrace();
			}
		}
		
		
		//get initial hypothesis
		intermediateHyp = igraph.getGoalRecogniser().getInitialHypothesis();
		allIntermediateHypotheses.add(intermediateHyp);
		
		//algorithm needs the queue to be non-empty
		double planEst, prevPlanEst = 1;
		try {
			planEst = igraph.getGoalRecogniser().getEstimatedStepsRemaining();
		} catch (UnknownEstimateException e1) {
			planEst = 1;
		}
		
		//may have unreachable hypotheses
		if (intermediateHyp != null)
			this.queuedBoundHypotheses.add(new BoundedGoalHypothesis(intermediateHyp, 0, planEst));
		
		try
		{
			//sanity check -- see if the true goal is even reachable according to IGRAPH
			if (igraph.getGoalRecogniser().getGoalSpace().getGoals().containsAll(this.gproblemWithGoal.getGoal().getFacts()) == false)
				throw new RecognitionException("IGRAPH's goal-space does not contain the true goal");
			
			double totalF1Score = 0, totalF1StateScore = 0;
			double totalRecallScore = 0, totalPrecisionScore = 0;
			double totalRecallStateScore = 0, totalPrecisionStateScore = 0;
			
			//output the distances to the goal after each observation
//			printGoalEstimates();
			this.initialiseGoalEstimates();
			this.initialiseGoalProbabilities();
			
			this.updateGoalEstimates();
			this.updateGoalProbabilities();
			
//			this.getPlan().clear(); //ONLY UNCOMMENT FOR INITIAL HYP TESTING. DELETES THE PLAN TO OBSERVE
			System.out.println("Plan has length "+this.getPlan().getPlanLength());
			for (Action a : this.getPlan().getActions())
			{
				//predict the next action before we actually "see" it.
				
				
				Map<PlanScheduleState, Action> perThreadActions = igraph.getGoalRecogniser().getPredictedNextActions();
				
				for (Entry<PlanScheduleState, Action> e : perThreadActions.entrySet())
				{
					System.out.println("Predicted next action from state "+e.getKey().stateId+" is "+ e.getValue());
					
					double nextActionScore = this.getNextActionScore(e.getValue(), a);
					System.out.println("Next action prediction score: "+nextActionScore);
				}

				Action predictedNextAction = this.igraph.getGoalRecogniser().getPredictedNextAction();
				System.out.println("Predicted next action is "+ perThreadActions);
				double nextActionScore = this.getNextActionScore(predictedNextAction, a);
				System.out.println("Next action prediction score: "+nextActionScore);
				
//				Collection<Action> predictedNextActions = igraph.getGoalRecogniser().getPredictedNextActions(1);
//				System.out.println("Predicted next actions are "+ predictedNextActions);
//				if (predictedNextActions.contains(a))
//					System.out.println("Next action was in hypothesis set");
//				else
//					System.out.println("Failed to predict next action");
				
				System.out.println("Observed action "+observationCount+": "+a);
//				prWriter.write(""+observationCount+"\t"+a.toString());
				
				long lastTime = System.nanoTime();
				
				this.preprocessObservation(a);
				igraph.actionObserved(a, NullAgent.getInstance());
				double processTime = (System.nanoTime() - lastTime) / 1000000000d;
	//			System.out.println("Time to process step "+count+" = "+processTime);
				totalProcessTime += processTime;
				
				//print out the new estimates
//				printGoalEstimates();
				this.updateGoalEstimates();
				this.updateGoalProbabilities();
				
				Set<Fact> currentStateLiterals = new HashSet<Fact>(igraph.getGoalRecogniser().getCurrentState().getTrueFacts());
				this.checkQueuedBoundHypotheses(currentStateLiterals, observationCount);				

				
				int stepsRemaining = this.getPlan().getPlanLength() - observationCount;
				System.out.println("Estimated steps remaining: "+planEst+". True remaining is "+stepsRemaining);
				
//				List<BoundedGoalHypothesis> boundHyps = this.igraph.getGoalRecogniser().getBoundedPlanHypothesis((int)est);
//				this.queuedBoundHypotheses.addAll(boundHyps);
				
				//this is a test harness optimisation. The estimated steps remaining can be in the thousands for certain problems, so
				//the majority of these hypotheses will never even be evaluated (i.e. t will never equal est)
				//so just trim this to be equal to the number of remaining observations.
				if (planEst > stepsRemaining)
				{
					planEst = stepsRemaining;
				}
				if (planEst <= 0)
				{
					planEst = 1;
				}
					
				//only compute bounded hypotheses if necessary
				if (this.computeBoundedHyps)
				{
					long now = System.nanoTime();
					
					List<BoundedGoalHypothesis> boundedHyp = igraph.getGoalRecogniser().getBoundedHypotheses((int) planEst);
					this.queuedBoundHypotheses.addAll(boundedHyp);
					
					
					long now2 = System.nanoTime();
					System.out.println("Generating bounded hypotheses took "+ ((now2-now)/1000000)+" milliseconds");
				}
				
				System.out.println("Computing intermediate hypothesis "+observationCount);
				intermediateHyp = igraph.getGoalRecogniser().getImmediateGoalHypothesis();
				allIntermediateHypotheses.add(intermediateHyp);
				
				System.out.println("True goal is "+this.gproblemWithGoal.getGoal());
				System.out.println("Intermediate hypothesis "+ observationCount +" is "+intermediateHyp);		
				double interPrecision = this.getPrecision(intermediateHyp.getGoals().getFacts(), this.gproblemWithGoal.getGoal().getFacts());
				double interRecall = this.getRecall(intermediateHyp.getGoals().getFacts(), this.gproblemWithGoal.getGoal().getFacts());
				double interF1Score = this.getF1Score(interPrecision, interRecall);
				
				totalF1Score += interF1Score; //update total goal F1 score
				totalRecallScore += interRecall;
				totalPrecisionScore += interPrecision;
				
				System.out.println("Intermediate P/R score is ("+interPrecision+", "+interRecall+") = "+interF1Score);
				
				//swap around
				double lastInterFinalStateRecall = getRecall(intermediateHyp.getGoals().getFacts(), finalState.getTrueFacts());
				double lastInterFinalStatePrecision = getPrecision(intermediateHyp.getGoals().getFacts(), finalState.getTrueFacts());
				double lastInterFinalStateF1 = getF1Score(lastInterFinalStatePrecision, lastInterFinalStateRecall);

				System.out.println("Intermediate State Precision score is "+lastInterFinalStatePrecision);
				System.out.println("Intermediate State Recall score is "+lastInterFinalStateRecall);
				System.out.println("Intermediate State F1 score is "+lastInterFinalStateF1);

				totalF1StateScore += lastInterFinalStateF1; //update total state F1 score
				totalRecallStateScore += lastInterFinalStateRecall;
				totalPrecisionStateScore += lastInterFinalStatePrecision;
				

				if (visual)
				{
					vgs.actionObserved(observationCount);
					vgs.repaint();
					
					try
					{
						if (vgs.pauseForUpdate())
						{
							while(vgs.paused())
							{
								//wait
								Thread.sleep(10);
							}
						}
						else
						{
							Thread.sleep(1000000000);
						}
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}

				observationCount++;
				prevPlanEst = planEst;
			}
			
			this.igraph.terminate();
			
			
			this.analyseAchievementTimes(allIntermediateHypotheses);
			
		
			System.out.println("True goal was "+this.gproblemWithGoal.getGoal());
			IGoalHypothesis finalHyp = igraph.getGoalRecogniser().computeFinalGoalHypotheses(NullAgent.getInstance(), 1).get(0);
//			IGoalHypothesis initHypCurr = igraph.getGoalRecogniser().computeInitialHypothesisFromCurrent();
			IGoalHypothesis initHyp = igraph.getGoalRecogniser().getInitialHypothesis();
			System.out.println("Final hypothesis is "+finalHyp);
//			System.out.println("Initial current hypothesis is "+initHypCurr);
			System.out.println("True Initial hypothesis is "+initHyp);
		
			long endTime = System.nanoTime();
			
			System.out.println("Average step process time was "+(totalProcessTime / this.getPlan().getActions().size())+" seconds");
	
			double finalPrecision = this.getPrecision(finalHyp.getGoals().getFacts(), this.gproblemWithGoal.getGoal().getFacts());
			double finalRecall = this.getRecall(finalHyp.getGoals().getFacts(), this.gproblemWithGoal.getGoal().getFacts());
			double finalF1Score = this.getF1Score(finalPrecision, finalRecall);

			//P/R/F1 of final state
			System.out.println("Final precision score is "+finalPrecision);
			System.out.println("Final recall score is "+finalRecall);
			System.out.println("Final F1 score is "+finalF1Score);
			
			double linearTotal = 0;
			for (double i = this.getPlan().getPlanLength() ; i > 0; i--)
				linearTotal += 1/i;

			System.out.println("Total F1 score is "+totalF1Score+" of "+this.getPlan().getPlanLength()+" ("+((totalF1Score/(double)this.getPlan().getPlanLength())*100)+"%)");
			System.out.println("Total F1 score is "+totalF1Score+", total linear F1 score would be "+linearTotal);
			System.out.println("Total F1 score is "+totalF1Score+" ("+totalPrecisionScore+","+totalRecallScore+")");
			
			
			System.out.println("Total F1 State score is "+totalF1StateScore+" of "+this.getPlan().getPlanLength()+" ("+((totalF1StateScore/(double)this.getPlan().getPlanLength())*100)+"%)");
			System.out.println("Total F1 State score is "+totalF1StateScore+", total linear F1 score would be "+linearTotal);
			System.out.println("Total goal score is "+totalF1StateScore+" "+totalPrecisionStateScore+" "+totalRecallStateScore+"");
			
//			double initialCurrentPrecision = this.getPrecision(initHypCurr.getGoals().getFacts(), this.gproblemWithGoal.goal.getFacts());
//			double initialCurrentRecall = this.getRecall(initHypCurr.getGoals().getFacts(), this.gproblemWithGoal.goal.getFacts());
//			double initialCurrentF1Score = this.getF1Score(initialCurrentPrecision, initialCurrentRecall);
//
//			//P/R/F1 of initial hyp
//			System.out.println("Initial Current precision score is "+initialCurrentPrecision);
//			System.out.println("Initial Current recall score is "+initialCurrentRecall);
//			System.out.println("Initial Current F1 score is "+initialCurrentF1Score);
	
			//P/R/F1 of inital hyp
			double initialPrecision = 0;
			double initialRecall = 0;
			double initialF1Score = 0;
			if (initHyp != null)
			{
				initialPrecision = this.getPrecision(initHyp.getGoals().getFacts(), this.gproblemWithGoal.getGoal().getFacts());
				initialRecall = this.getRecall(initHyp.getGoals().getFacts(), this.gproblemWithGoal.getGoal().getFacts());
				initialF1Score = this.getF1Score(initialPrecision, initialRecall);
			}
			
			System.out.println("Initial precision score is "+initialPrecision);
			System.out.println("Initial recall score is "+initialRecall);
			System.out.println("Initial F1 score is "+initialF1Score);
			
			double finalStatePrecision = this.getPrecision(finalHyp.getGoals().getFacts(), igraph.getGoalRecogniser().getCurrentState().getTrueFacts());
			double finalStateRecall = this.getRecall(finalHyp.getGoals().getFacts(), igraph.getGoalRecogniser().getCurrentState().getTrueFacts());
			double finalStateF1Score = this.getF1Score(finalStatePrecision, finalStateRecall);
			
			System.out.println("Final state precision score is "+finalStatePrecision);
			System.out.println("Final state recall score is "+finalStateRecall);
			System.out.println("Final state F1 score is "+finalStateF1Score);
	//		System.out.println("Last intermediate hyp is "+intermediateHyp.getGoals());
	//		System.out.println("Final state is "+finalState.facts);
			
			
	//		double lastInterStatePrecision = getPrecision(finalHyp.getGoals().getFacts(), finalState.facts);
	
			
			System.out.println("Final hypothesis represents "+(finalStatePrecision*100)+"% of the final state, " +
					(finalStateRecall*100)+"% needed");
			
			System.out.println("Final State is "+igraph.getGoalRecogniser().getCurrentState().getTrueFacts());
			System.out.println("Goal was "+this.gproblemWithGoal.getGoal());
			System.out.println("Hypothesis is is "+finalHyp.getGoals());
			
			System.out.println("Time to solve is "+((endTime - startTime)/1000000000d));
	
//			int step = 1;
//			System.out.println("All intermediate hypotheses were: ");
	//		for (IGoalHypothesis h : hypHistory)
	//		{
	//			System.out.println(step++ +": "+h.toString());
	//		}
			
//			igraph.getGoalRecogniser().getPlanThreader().getGraph().generateDotGraph(new File("/tmp/final_threads.dot"));
			
			
//			prWriter.close();
			
			if (this.showDebugOutput())
			{
				this.printHeuristicEstimates();
				this.printProbabilities();
			}
			
		}
		catch (SchedulingException e)
		{
			System.err.println("There was a problem in scheduling the previous observed action");
			e.printStackTrace();
		}
		catch (UnreachableGoalException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (ExecutionException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			igraph.getGoalRecogniser().terminate(); //kill the thread pool
		}
		
		
		return allIntermediateHypotheses;
	}
	
	/**
	 * Hostspot method which is fired prior to an observation being processed by the test harness.
	 * @param a
	 */
	public void preprocessObservation(Action a) 
	{
		
	}

	private void printHeuristicEstimates()
	{
		for (Entry<MutexGoalSpace, HashMap<Fact, ArrayList<Double>>> maps : this.pastEstimates.entrySet())
		{
			ArrayList<Fact> orderedFacts = new ArrayList<Fact>(maps.getValue().keySet());

			System.out.println("Estimates for MGS "+maps.getKey().hashCode());
			for (Fact f : orderedFacts)
			{
				System.out.print(f);
				if (this.gproblemWithGoal.getGoal().getFacts().contains(f))
					System.out.print("*");
				
				System.out.print("\t");
			}
			System.out.println();
			
			for (int i = 0; i < this.igraph.getGoalRecogniser().getObservedStepCount()+1; i++)
			{
				for (Fact f : orderedFacts)
				{
					double hAtT = maps.getValue().get(f).get(i); 
	
					System.out.print(hAtT+"\t");
				}
				System.out.println();
			}
		}
	}
	
	protected void setPlan(TotalOrderPlan plan)
	{
		this.completePlan = plan;
	}

	protected TotalOrderPlan getPlan()
	{
		return this.completePlan;
	}

	protected IGRAPH initialiseRecogniser() throws RecognitionException
	{
		return new IGRAPH(this.hybridProblem, IGRAPHPreferences.DomainFile);
	}

	/**
	 * Test whether the specified set of domain requirements is supported by IGRAPH.
	 * @param problemRequirements
	 * @return
	 */
	protected boolean checkRequirements(DomainRequirements problemRequirements)
	{
		return IGRAPHTestHarness.GetRequirementsSupported().subsumes(problemRequirements);
	}
	/**
	 * Compares two actions and scores them based on how similar the predicted next action and actual next action
	 * are. The score is determined as follows
	 * If predicted.name != actual.name, return 0
	 * Else 
	 * 	foreach param in actual
	 * 		if param == predicted.param
	 * 			score++
	 * 
	 * return score/total
	 * 
	 * Where "total" is (1 + |parameters|)
	 * @param predictedNextAction
	 * @param trueNextAction
	 * @return
	 */
	protected double getNextActionScore(Action predictedNextAction, Action trueNextAction)
	{
		double score = 1; //initialise to 1, because if the action name is wrong the method just returns 0
		double total = 1 + trueNextAction.getParameters().size(); //1 for action name, 1 for each parameter
		if (predictedNextAction.getName().equals(trueNextAction.getName()) == false)
			return 0;
		
		for (int i = 0; i < trueNextAction.getParameters().size(); i++)
		{
			if (trueNextAction.getParameters().get(i).equals(predictedNextAction.getParameters().get(i)))
				++score;
		}
		
		double res = score/total;
		return res;
	}

//	/**
//	 * The actual goal is used by SAS+ hen generting DTGs and the CG. But, we shouldn't have the goal
//	 * in order to do GR, therefore a "useful" goal is generated which attempts to pick out on
//	 * reachable fact for each object in the domain. However, this is not perfect, as unreachable goals
//	 * are common. 
//	 * @param gp
//	 * @param reachable
//	 * @return
//	 */
//	protected GroundFact generateUsefulSASGoal(GroundProblem gp, Set<Proposition> reachable)
//	{
//		HashMap<Parameter, Proposition> goalMap = new HashMap<Parameter, Proposition>();
//		HashSet<Parameter> blockedParams = new HashSet<Parameter>();
//		HashSet<Proposition> blockedFacts = new HashSet<Proposition>();
//		
//		LinkedList<Parameter> unassigned = new LinkedList<Parameter>(gp.objects);
//		
//		
//		out : while (unassigned.isEmpty() == false)// for (Parameter o : gp.objects)
//		{
//			Parameter o = unassigned.pop();
//			
////			if (blockedParams.contains(o))
////				continue out;
//			
//			in : for (Proposition p : reachable)
//			{
//				if (blockedFacts.contains(p) == false &&
//						gp.initial.contains(p) == false && 
//						p.isStatic() == false &&
//						p.getParameters().contains(o) == true)// &&
//						//goalMap.containsValue(p) == false)
//				{
//					//still need to check if any of p's parameters are in blocked set
////					for (Object ppo : p.getParameters())
////					{
////						if (blockedParams.contains(ppo))
////							continue in;
////					}
//					
//					//propagate this selected goal through all its parameters
//					LinkedList<Parameter> toUpdate = new LinkedList<Parameter>(p.getParameters());
//					//while (toUpdate.isEmpty() == false)
//					for (Parameter ppo : toUpdate)
//					{
////						Parameter ppo = toUpdate.pop();
//						
//						//if object is unassigned
//						if (goalMap.get(ppo) == null)
//						{
//							unassigned.remove(ppo);
//							goalMap.put(ppo, p);
//						}
//						else
//						{
//							Proposition existingMapping = goalMap.get(ppo);
//							blockedFacts.add(existingMapping);
//							
//							for (Parameter otherParam : existingMapping.getParameters())
//							{
//								if (toUpdate.contains(otherParam) == false)
//								{
//									goalMap.put(otherParam, null);
//									unassigned.add(otherParam);
//								}
//							}
//
//							unassigned.remove(ppo);
//							goalMap.put(ppo, p);
//						}
//					}
//					blockedFacts.add(p);
////					blockedParams.addAll(p.getParameters());
//					continue out;
//				}
//			}
//		}
//
//		HashSet<Parameter> assignedObjects = new HashSet<Parameter>();
//		Collection<Fact> finalGoal = new HashSet<Fact>();
//		for (Proposition p : goalMap.values())
//		{
//			if (p != null)
//			{
//				assignedObjects.addAll(p.getParameters());
//				finalGoal.add(p);
//			}
//		}
//		
//		boolean success = gp.objects.equals(assignedObjects);
//		if (success)
//			System.out.println("Successfully constructed complete dummy goal");
//		else
//			System.out.println("WARNING: Failed to generate complete dummy goal");
//		
//		And and = new And(finalGoal);
//		return and;
//	}
	

	/** 
	 * The actual goal is used by SAS+ when generating DTGs and the CG. But, we shouldn't have the goal
	 * in order to do GR, therefore a "useful" goal is generated which attempts to pick out on
	 * reachable fact for each object type (and therefore each object) in the domain. 
	 * @param gp
	 * @param reachable
	 * @return
	 * @throws UnsolveableProblemException 
	 */
	protected And generateUsefulSASGoal() throws UnsolveableProblemException
	{		
		//THIS VERSION PERFORMS TRANSLATION IN ONE PASS, WITH A PROBABLY UNREACHABLE AND MUTEX GOAL -- APPROPRIATE VERSION OF IGRAPH-LAMA TRANSLATOR MUST BE USED.
		And pddlGoal = new And();
		HashSet<Parameter> goalFor = new HashSet<Parameter>(); 
		
		for (Entry<Parameter, Set<Proposition>> e : this.gproblem.getObjectPropositionMap().entrySet())
		{
			if (e.getValue().isEmpty())
				System.err.println("No facts exist for object "+e.getValue()+". No goal generated.");
			else if (goalFor.contains(e.getKey()))
				System.out.println("Already have goal for "+e.getKey()+"... skipping");
				
			Iterator<Proposition> it = e.getValue().iterator();
			boolean found = false;
			while (it.hasNext())
			{
				Proposition first = it.next();
				if (first.isStatic())
					continue;
				
				pddlGoal.add(first);
				goalFor.addAll(first.getParameters());
				
				found = true;
			}
			
			if (found == false)
			{
				System.err.println("Failed to produce a goal for object "+e.getKey());
			}
		}
		

//		System.out.println("Generating goal using "+pddlGoal);
//		SASProblem sprob;
//		try
//		{
//			sprob = this.generateSASProblem(pddlGoal);
//		}
//		catch (UnsolveableProblemException e)
//		{
//			throw new UnsolveableProblemException("Failed to produce a goal for translation.", e);
//		}
		
		return pddlGoal;
	}
	
	
	
//
//	/** 
//	 * The actual goal is used by SAS+ when generating DTGs and the CG. But, we shouldn't have the goal
//	 * in order to do GR, therefore a "useful" goal is generated which attempts to pick out on
//	 * reachable fact for each object type (and therefore each object) in the domain. 
//	 * @param gp
//	 * @param reachable
//	 * @return
//	 */
//	protected And generateUsefulSASGoal()
//	{		
//		//some grounded facts may be unreachable and rejected by SAS+ translation -- might as well keep a 
//		//note of what they are so these can be removed from the PDDL version
//		Set<Proposition> unreachable = new HashSet<Proposition>();
//		And pddlGoal = new And();
//		
//		//generate a SASProblem for each object in the problem 
//		Map<Parameter, SASProblem> sasproblems = new HashMap<Parameter, SASProblem>();
//		HashSet<Parameter> haveGoalFor = new HashSet<Parameter>();
//		out: for (Parameter param : gproblem.objects)
//		{
//			if (haveGoalFor.contains(param))
//				continue out;
//			
//			boolean foundOne = false;
//			LinkedList<Proposition> validProps = new LinkedList<Proposition>(this.gproblem.objectPropositionMap.get(param));
//			validProps.removeAll(unreachable); //eliminate any known unreachable facts
//			
//			in: while (validProps.isEmpty() == false)
//			{
//				Proposition prop = validProps.remove();
//				
//				if (this.gproblem.initial.contains(prop))// && prop.isStatic() == false)
//				{
//					validProps.remove(prop);
//					continue in;
//				}
//
//				System.out.println("Generating goal for "+param+", using "+prop);
//				SASProblem sprob;
//				try
//				{
//					sprob = this.generateSASProblem(prop);
//					pddlGoal.add(prop);
//					haveGoalFor.addAll(prop.getParameters());
//				}
//				catch (UnsolveableProblemException e)
//				{
//					System.out.println("Goal "+prop+" seems to be unreachable. Retrying with different goal...");
//					//e.printStackTrace();
//					
//					unreachable.add(prop);
//					
//					continue in;
//				}
//				finally
//				{
//					validProps.remove(prop);	
//				}
//
//				System.out.println("Finished SAS+");
//				
//				sasproblems.put(param, sprob);
//				foundOne = true;
//				continue out;
//			}
//			
//			//no goal was generated for this type!
//			if (foundOne == false)
//			{
//				System.err.println("No goal generated for "+param);
//			}
//		}
//
//		
//		this.gproblem.reachableFacts.removeAll(unreachable);
//
//		
//		return pddlGoal;
//	}
//	

//  @Deprecated -- doesnt work anyway!	
//	protected SASProblem extractUnifiedSASProblem(Map<Parameter, SASProblem> sasproblems)
//	{
//		int sasIdCounter = 0;
//		
//		//this represents the final, unified sas problem
//		SASProblem finalProblem = new SASProblem();
//		
//		//setup the easy stuff. Just assume always optimal plan length.
//		finalProblem.name = this.gproblem.name;
//		finalProblem.optimisePlanLength = true;
//
//		//the final causal graph.
//		CausalGraph finalCG = new CausalGraph();
//
//		Map<Integer, DomainTransitionGraph> newDTGsMap = new HashMap<Integer, DomainTransitionGraph>(); //newID --> newDTG
//		Map<Integer, Integer> idMap = new HashMap<Integer, Integer>(); //oldID --> newID
//		Map<Parameter, Integer> objectIds = new HashMap<Parameter, Integer>(); //object --> new ID
//		
//		for (Entry<Parameter, SASProblem> e : sasproblems.entrySet())
//		{
//			SASProblem sprob = e.getValue();
//			
//			
//			//foreach DTG in this problem, assign it a new unique ID if it has never been seen before, 
//			//or use the existing known ID
//			for (DomainTransitionGraph dtg : sprob.causalGraph.getDTGs())
//			{
//				Parameter pddlVersion = null;
//				try
//				{
//					pddlVersion = dtg.sasVariable.getObject().convertToPDDL(sprob, this.gproblem);
//				}
//				catch (NullPointerException ex)
//				{
//					pddlVersion = new PDDLObject(dtg.sasVariable.getObject().getName());
//				}
//				
//				//if we have never seen this object before, add it and generate a new unique ID for it.
//				if (objectIds.containsKey(pddlVersion) == false)
//				{
//					int newId = ++sasIdCounter;
//					objectIds.put(pddlVersion, newId);
//					idMap.put(dtg.sasVariable.getId(), newId);
//					
//					DomainTransitionGraph newDTG = (DomainTransitionGraph) dtg.clone();
//					newDTG.sasVariable.setId(newId);
//					
//					newDTGsMap.put(newId, newDTG);
//				}
//			}
//			
//			
//			//now deal with the causal graph. Must merge together ALL causal graphs from all SASProblems
//			//Made further complicated by the new ID mappings!
//			CausalGraph old = sprob.causalGraph;
//			for (DomainTransitionGraph oldDTG : old.vertexSet())
//			{
//				//the new DTG which the old has been mapped to
//				int newInID = idMap.get(oldDTG.sasVariable.getId());
//				DomainTransitionGraph newInDTG = newDTGsMap.get(newInID);
//				
//				Collection<DomainTransitionGraph> connOut = old.getOutgoingVertices(oldDTG);
//				for (DomainTransitionGraph outDTG : connOut)
//				{
//					//the stateId of the new out-edge-connected DTG
//					int newOutID = idMap.get(outDTG.sasVariable.getId());
//					//the DTG itself
//					DomainTransitionGraph newOutDTG = newDTGsMap.get(newOutID);
//					
//					//recreate edge in unioned DTG
//					if (finalCG.containsEdge(newInDTG, newOutDTG) == false)
//						finalCG.addEdge(newInDTG, newOutDTG);
//				}
//			}
//		}
//
//		//set the new CG
//		finalProblem.causalGraph = finalCG;
//		
//		//loop through all sas problems again, because all actions need setup again to reflect new
//		//variable IDs. Same for goal and initial states.
//		int actionId = 0;
//		for (Entry<Parameter, SASProblem> e : sasproblems.entrySet())
//		{
//			SASProblem subproblem = e.getValue();
//					
//		}
//		
//		return finalProblem;
//	}


//	protected SASProblem mergeSASProblem(Map<Type, SASProblem> sasproblems)
//	{
//		SASProblem sas = new SASProblem();
//		
//		//simply merge everything but the DTG and CG
//		for (Entry<Type, SASProblem> e : sasproblems.entrySet())
//		{
//			Type type = e.getKey();
//			SASProblem sp = e.getValue();
//			
//			sas.actions.putAll(sp.actions);
//			sas.axioms.putAll(sp.axioms);
//			sas.derivedPredicates.putAll(sp.derivedPredicates);
//			sas.goal.putAll(sp.goal);
//			sas.initial.putAll(sp.initial);
//			sas.mutexes.putAll(sp.mutexes);
//			
//			sas.name = sp.name;
//			sas.reachableFacts.addAll(sp.reachableFacts);
//			
//			sas.optimisePlanLength = sp.optimisePlanLength;
//			sas.state.vars.putAll(sp.state.vars);
//			sas.variables.putAll(sp.variables);
//			
//			
//			Set<PDDLObject> typeObjects = this.uproblem.typeSets.get(type);
//			for (PDDLObject p : typeObjects)
//			{
//				sp.get
//				
//			}
//		}
//		
//		
//		
//	}
	
//	protected DomainTransitionGraph replaceDTGVariable(DomainTransitionGraph dtg, SASVariable newVar)
//	{
//		DomainTransitionGraph newDTG = (DomainTransitionGraph) dtg.clone();
//		
//		newDTG.sasVariable = newVar;
//		
//		return newDTG;
//	}

	/**
	 * Generates a SASProblem for the specified single-literal goal -- guaranteeing (I think), that a DTG will
	 * be created for all parameters of the goal.
	 * @param goal
	 * @return
	 * @throws UnsolveableProblemException
	 * @throws IOException 
	 * @throws SASException 
	 * @deprecated No longer required.
	 */
	protected SASProblem generateSASProblem(GroundFact goal) throws UnsolveableProblemException, IOException, SASException 
	{
		GroundProblem gpForSAS = (GroundProblem) this.gproblem.clone(); 
		gpForSAS.setGoal(goal);
		
		//need to rewrite the pfile with all possible facts as the goal for SAS+ translation
		String alteredPfilePath = this.pFile.getAbsolutePath();
		alteredPfilePath = alteredPfilePath.substring(0, alteredPfilePath.lastIndexOf('/'));
//		alteredPfilePath = alteredPfilePath + "/" + this.pFile.getName() + ".rec";
		alteredPfilePath = alteredPfilePath + "/" + this.pFile.getName();
		File recFile = File.createTempFile(this.pFile.getName(), ".rec", new File(alteredPfilePath));
		
		File recPfile = STRIPSTranslator.translateToSTRIPSProblemFile(gpForSAS, recFile);
		recPfile.deleteOnExit();
		
		//translate and initialise SAS+ problem
		try
		{
//			SASTranslator.translateToSAS(this.domain, recPfile, new NullPrintStream()); //ignore output from translation
			SASTranslator.translateToSAS(this.domain, recPfile, System.out); //send output from translation to System.out
		}
		catch (UnsolveableProblemException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new SASException(e);
		}
		
		//parse in the SAS+ domain
		SASProblem sasOptimised = null;
		try
		{
			sasOptimised = SASplusParser.parse();
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			throw new SASException(e);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new SASException(e);
		}
		catch (sas.parser.ParseException e)
		{
			e.printStackTrace();
			throw new SASException(e);
		}
		SASProblem sasAll = SASplusParser.sasProblemAll;
//		this.sasproblem.dtgs = sasOptimised.dtgs;
		sasAll.causalGraph = sasOptimised.causalGraph;
		
		return sasAll;	
	}
	
	/**
	 * This method checks all bounded hypotheses which are due to become true at this timestep. The score of the
	 * hypothesis is a P/R-F1 score based upon the DIFFERENCE between the current state and the queued
	 * hypothesis. Therefore, the hypothesis should not score 90% just because 90% of the facts in the 
	 * hypothesis are still true in the current state.
	 * @param state
	 * @param currentTime
	 */
	protected void checkQueuedBoundHypotheses(Set<Fact> state, int currentTime)
	{
		HashSet<Fact> currentState = new HashSet<Fact>(state);
		currentState.removeAll(this.gproblem.getStaticFacts());
		
		
		TreeSet<BoundedGoalHypothesis> toRemove = new TreeSet<BoundedGoalHypothesis>();
		BoundedGoalHypothesis[] arr = this.queuedBoundHypotheses.toArray(new BoundedGoalHypothesis[this.queuedBoundHypotheses.size()]);
		Arrays.sort(arr);
		for (BoundedGoalHypothesis bhyp : arr)
		{
			if (bhyp.getTargetTime() == currentTime)
			{
				toRemove.add(bhyp);
				
				//find out the difference between the state at creation time and the state as it is now
				//so that we can correlate the P/R score with this.
				StateHistoryTuple creationStateTuple = this.igraph.getGoalRecogniser().getStateAt((int)bhyp.getCreationTime());
				//no need to get the c+b state, as it is the current one (if this method is being used correctly)
				
				Collection<Fact> unchanged = new HashSet<Fact>(creationStateTuple.getState().getTrueFacts());
				unchanged.removeAll(this.gproblem.getStaticFacts());
				unchanged.retainAll(currentState);
				HashSet<Fact> intermediateGoal = new HashSet<Fact>(currentState); //this is the real intermediate goal of the agent/the state
				intermediateGoal.removeAll(unchanged);
				
				//this strips out any members of the bounded hyp which were true in the creation state and still
				//are now, as these are irrelevant
				And strippedGoal = new And(bhyp.getGoals().getFacts());
				strippedGoal.removeAll(unchanged);
				ConjunctiveGoalHypothesis chyp = new ConjunctiveGoalHypothesis(strippedGoal, bhyp.getProbability());
				BoundedGoalHypothesis hyp2 = new BoundedGoalHypothesis(chyp, bhyp.getCreationTime(), bhyp.getBoundTime());
				
				
				double diff = intermediateGoal.size() / (double)currentState.size();
				
				double precGoal = this.getPrecision(hyp2.getGoals().getFacts(), intermediateGoal);
				double recGoal = this.getRecall(hyp2.getGoals().getFacts(), intermediateGoal);
				double f1Goal = this.getF1Score(precGoal, recGoal);
				
				double precState = this.getPrecision(hyp2.getGoals().getFacts(), currentState);
				double recState = this.getRecall(hyp2.getGoals().getFacts(), currentState);
				double f1State = this.getF1Score(precState, recState);
				
	//			int targetTime = this.targetMap.get(h);
				double targetTime = bhyp.getBoundTime();
				double creationTime = bhyp.getCreationTime();
				
				System.out.println("Bounded Hypothesis C="+creationTime+", B="+(targetTime)+
						", Goal P/R "+precGoal+", "+recGoal+" = "+f1Goal+
						", State P/R "+precState+", "+recState+" = "+f1State+
						", Diff="+diff); 
//						", Diff="+diff +", BHyp="+hyp2.getGoals().toString()); //printing out hypothesis itself causes massive log files
			}
//			else if (bhyp.getTargetTime() > currentTime)
//				break;
			
		}
		
		this.queuedBoundHypotheses.removeAll(toRemove);
	}

	public double getPlanSimilarity(List actual, List imposter)
	{
		int inPosMatchCount = 0, orderMatchCount = 0, anyMatchCount = 0;
		boolean prevMatch = true;
		
		List longer, shorter;
		if (actual.size() > imposter.size())
		{
			longer = actual;
			shorter = imposter;
		}
		else
		{
			longer = imposter;
			shorter = actual;
		}
		
		
		
		for (int i = 0; i < shorter.size(); i++)
		{
			if (longer.get(i).equals(shorter.get(i)))
			{
				inPosMatchCount++;
				anyMatchCount++;
				if (prevMatch == true)
				{
					orderMatchCount++;
					prevMatch = true;
				}
			}
			else
			{
				if (longer.contains(shorter.get(i)))
					anyMatchCount++;
				
				prevMatch = false;
			}
		}
		
		return (double)anyMatchCount/(double)longer.size();
	}

	protected static void printUsage(Exception e)
	{
		e.printStackTrace();
		
		IGRAPHTestHarness.printUsage();
	}
	
	protected static void printUsage()
	{		
		System.out.println("Illegal arguments. Usage:\n");
		System.out.println("\t<domain file path> <problem file path> <solution file path> <output path prefix>");
		System.out.println("\t[-heuristic {Max,FF,CG,CEA,JavaFF,Random}] [-likelihood {ML,MLThreaded,SA}] [-filter {Greedy,Stability} [<min stability [0:1]>]");
		System.out.println("\t[-bayesLambda <0-1>] [-bayesLaplace <0:N>] [-goalSpace {Map}]");
		System.out.println("\t[-visual {1,0}]");
		System.out.println("\t[-bounded {1,0} -partial {1,0} -multithreaded {1,0}");
	}

	public GroundProblem getOriginalGroundProblem()
	{
		return this.gproblemWithGoal;
	}
	
	public GroundFact getTrueGoal()
	{
		return this.gproblemWithGoal.getGoal();
	}
	
	/**
	 * Gets the precision of the hypothesis vs the true goal. Note that any static facts
	 * in either of these are ignored! This is purely because this is a convenient place to do this.
	 * Statics should really just never be added to either set.
	 * @param hypothesis
	 * @param trueGoal
	 * @return
	 */
	public double getPrecision(Set hypothesis, Set trueGoal)
	{
		Set trimmedHypothesis = new HashSet(hypothesis);
		trimmedHypothesis.removeAll(this.gproblem.getStaticFacts());
		if (trimmedHypothesis.isEmpty())
			return 0;
		
		Set inBoth = new HashSet(hypothesis);
		inBoth.removeAll(this.gproblem.getStaticFacts());
		inBoth.retainAll(trueGoal);
		
		return (inBoth.size() / (double) trimmedHypothesis.size());
	}

	/**
	 * Gets the recall of the hypothesis vs the true goal. Note that any static facts
	 * in either of these are ignored! This is purely because this is a convenient place to do this.
	 * Statics should really just never be added to either set.
	 * @param hypothesis
	 * @param trueGoal
	 * @return
	 */
	public double getRecall(Set hypothesis, Set trueGoal)
	{
		Set trimmedTrueGoal = new HashSet(trueGoal);
		trimmedTrueGoal.removeAll(this.gproblem.getStaticFacts());
		
		Set inBoth = new HashSet(hypothesis);
		inBoth.removeAll(this.gproblem.getStaticFacts());
		inBoth.retainAll(trueGoal);
		
		return (inBoth.size() / (double) trimmedTrueGoal.size());
	}

	/**
	 * Gets the F1 score given the specified precision and recall scores. 
	 * Computed as (2 * precision * recall) / (precision + recall)
	 * @return
	 */
	public double getF1Score(double precision, double recall)
	{
		if (precision + recall == 0)
			return 0f;
		
		double f1 = 2 * precision * recall;

		f1 = f1/(precision + recall);
		
		return f1;
	}
	
	@Override
	protected void finalize() throws Throwable
	{
		this.igraph.getGoalRecogniser().terminate();
		
		super.finalize();
	}

	public IGRAPH getRecogniser()
	{
		return igraph;
	}

	public boolean showDebugOutput()
	{
		return debugOutput;
	}

	public void setDebugOutput(boolean debugOutput)
	{
		this.debugOutput = debugOutput;
	}

}
