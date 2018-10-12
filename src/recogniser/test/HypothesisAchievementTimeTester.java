package recogniser.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.TotalOrderPlan;
import javaff.parser.ParseException;
import recogniser.IGRAPHTestHarness;
import recogniser.hypothesis.IGoalHypothesis;
import recogniser.util.RecognitionException;

public class HypothesisAchievementTimeTester extends IGRAPHTestHarness
{

	
	
	public HypothesisAchievementTimeTester(String[] args)
			throws RecognitionException, IOException, ParseException
	{
		super(args);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public List<IGoalHypothesis> doRecognition() throws RecognitionException
	{
		List<IGoalHypothesis> intermediateHypotheses = super.doRecognition();
		
		this.analyseAchievementTimes(intermediateHypotheses);
		
		return intermediateHypotheses;
	}

	private void analyseAchievementTimes(
			List<IGoalHypothesis> intermediateHypotheses)
	{
		TotalOrderPlan plan = super.getPlan();
		
		HashMap<Fact, Integer> goalAdditionTimes = new HashMap<Fact, Integer>();
		
		//record when each goal fact is added to the  state
		int planCount = 0;
		for (Action a : plan.getActions())
		{
			for (Fact add : a.getAddPropositions())
			{
				if (super.gproblemWithGoal.getGoal().getFacts().contains(add) == false)
					continue;
				
				if (goalAdditionTimes.containsKey(add) == false)
					goalAdditionTimes.put(add, planCount);
				
			}
			
			++planCount;
		}
		
		int hypCount = 0;
		HashMap<Fact, Integer> hypothesisAdditionTimes = new HashMap<Fact, Integer>();
		for (IGoalHypothesis hyp : intermediateHypotheses)
		{
			for (Fact h : hyp.getGoals().getFacts())
			{
				if (super.gproblemWithGoal.getGoal().getFacts().contains(h) == false)
						continue;

				if (hypothesisAdditionTimes.containsKey(h) == false)
					hypothesisAdditionTimes.put(h, hypCount);
				
			}
			
			++hypCount;
		}
		
		BufferedWriter writer;
		try
		{
			writer = new BufferedWriter(new FileWriter(new File("/tmp/nums")));
			writer.write("Goal\tHyp\n");
			
			for (Fact g : super.gproblemWithGoal.getGoal().getFacts())
			{
				int goalTime = goalAdditionTimes.get(g);
				
				int hypTime = -1;
				if (hypothesisAdditionTimes.containsKey(g))
				{
					hypTime = hypothesisAdditionTimes.get(g);
				}
				
				writer.write(goalTime+"\t"+hypTime+"\n");
			}
			writer.close();
			
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
		}

		
	}

	public static void main(String[] args)
	{
		try
		{
			HypothesisAchievementTimeTester tst = new HypothesisAchievementTimeTester(args);
			tst.doRecognition();
			
			System.exit(0);
		}
		catch (RecognitionException | IOException | ParseException e)
		{
			e.printStackTrace();
		}
		 
		 
		
	}

}
