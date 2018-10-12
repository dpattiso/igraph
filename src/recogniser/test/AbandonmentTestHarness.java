package recogniser.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;

import recogniser.IGRAPHTestHarness;
import recogniser.hypothesis.IGoalHypothesis;
import recogniser.hypothesis.MutexGoalSpace;
import recogniser.util.RecognitionException;
import threader.util.PlanThreadGraph;

/**
 * Test harness for evaluating how IGRAPH performs when the agent abandons their goal and pursues another.
 * @author nau01136
 *
 */
public class AbandonmentTestHarness extends IGRAPHTestHarness
{
	private TreeMap<Integer, String> goalSwitches;
	private ArrayList<Fact> goalsInOrder;
	private File probabilitiesOutputFile;

	/**
	 * Arguments are the same as {@link IGRAPHTestHarness#IGRAPHTestHarness(String[])}, with the exception that parameter 0 is 
	 * a path to the file containing goal switches.
	 * @param args
	 * @throws RecognitionException
	 * @throws FileNotFoundException Thrown if the goal switch file cannot be found
	 * @see ContinuousPlanner Generates goal switch file
	 */
	public AbandonmentTestHarness(String[] args) throws RecognitionException, FileNotFoundException 
	{
		super(Arrays.copyOfRange(args, 2, args.length));
		
		this.goalSwitches = new TreeMap();
		this.goalsInOrder = new ArrayList<>();
		
		this.setupAbandonmentTester(args[0], args[1]);
	}
	
	/**
	 * Parse the goal switch file and store the results for later.
	 * @param goalSwitchFile
	 */
	protected void setupAbandonmentTester(String goalSwitchFile, String probOutputFile) throws FileNotFoundException
	{
		File abandonedFile = new File(goalSwitchFile);
		try
		{
			Scanner scan = new Scanner(abandonedFile);
			
			//file is in the format <change time> <goal>
			//we can't parse the 
			while (scan.hasNext())
			{
				int changeTime = scan.nextInt();
				String goal = scan.nextLine().trim();
				
				this.goalSwitches.put(changeTime, goal);
			}
			
			scan.close();
			
			
			this.probabilitiesOutputFile = new File(probOutputFile);
		}
		catch (FileNotFoundException e)
		{
			throw e;
		}
	}

	/**
	 * Takes the same arguments as {@link IGRAPHTestHarness}, with the exception of the first parameter now being a path to 
	 * a goal switch file of the form ([goal switch time] [goal string])+
	 * @param args
	 * @see IGRAPHTestHarness#main(String[])
	 */
	public static void main(String[] args)
	{		
		
		try
		{
			AbandonmentTestHarness test = new AbandonmentTestHarness(args);
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
	
	/**
	 * Need to catch the preprocess observation event and change IGRAPHs true goal to the new one
	 */
	@Override
	public void preprocessObservation(Action a) 
	{
		int currentTime = this.igraph.getGoalRecogniser().getObservedStepCount();
		if (this.goalSwitches.containsKey(currentTime) == false)
			return;
		
		String goalSwitch = this.goalSwitches.get(currentTime);
		if (goalSwitch == null)
			throw new NullPointerException("Cannot find goal switch at time "+currentTime);
		
		Fact newGoal = this.parseGoal(goalSwitch);
		if (newGoal == null)
			throw new NullPointerException("Could not find goal with signature: "+goalSwitch);
		
		this.switchGoal(newGoal);
	}		
	
	/**
	 * Switches the true goal used by the IGRAPH test harness.
	 * @param newGoal
	 */
	protected void switchGoal(Fact newGoal)
	{
		super.gproblemWithGoal.setGoal((GroundFact) newGoal);
		
		this.goalsInOrder.add(newGoal);
	}
	
	@Override
	public List<IGoalHypothesis> doRecognition() throws RecognitionException 
	{
		List<IGoalHypothesis> hyps = super.doRecognition();
		
		try
		{
			this.printAbandonedGoalProbabilities();
		}
		catch (IOException e)
		{
			throw new RecognitionException(e);
		}
		
		//uncomment if you want to see the thread graph generated		
//		PlanThreadGraph graph = this.igraph.getGoalRecogniser().getPlanThreader().getGraph();
//		graph.generateDotGraph(new File("/tmp/threadgraph.dot"));
		
		return hyps;
	}
	
	/**
	 * Outputs the probabilities of the abandoned and final goals over time.
	 */
	private void printAbandonedGoalProbabilities() throws IOException
	{
		if (this.probabilitiesOutputFile.exists() == false)
			this.probabilitiesOutputFile.createNewFile();
			
		PrintWriter writer = new PrintWriter(new FileWriter(this.probabilitiesOutputFile));
		
		for (Fact g: this.goalsInOrder)
		{
			writer.print(g.toString()+"\t");
		}
		writer.println();
		
		//get the probabilities of all switched goals over time
		for (int i=0; i <= this.igraph.getGoalRecogniser().getObservedStepCount(); i++)
		{
			for (Fact g : this.goalsInOrder)
			{
				Map<MutexGoalSpace, List<Double>> members = this.igraph.getGoalRecogniser().getHistoricalProbabilities(g);
				for (MutexGoalSpace mgs : members.keySet())
				{
					List<Double> probs = members.get(mgs);
					writer.print(probs.get(i));
				}
				writer.print("\t");
			}
			writer.println();
		}
		
		writer.close();
	}

	/**
	 * Converts a goal String to a Fact.
	 * @param goalString
	 * @return
	 */
	protected Fact parseGoal(String goalString)
	{
		int c = 0;
		for (Fact g : super.gproblem.getReachableFacts())
		{
			if (g.toString().equalsIgnoreCase(goalString))
				return g;
			
//			System.out.println(c++ + goalString + " = "+g.toString());
		}
		
		return null;
	}

}
