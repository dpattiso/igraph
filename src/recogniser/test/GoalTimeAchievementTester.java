package recogniser.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javaff.JavaFF;
import javaff.data.*;
import javaff.data.strips.Not;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.planning.*;
import javaff.test.BatchTester;

public class GoalTimeAchievementTester
{
	private File domain, pfile, soln, outAdd, outDelete, outGoalCounts;
	private GroundProblem problem;
	private TotalOrderPlan plan;

	public GoalTimeAchievementTester(File domain, File pfile, File soln, File outAdd, File outDelete, File goalCounts)
	{
		this.domain = domain;
		this.pfile = pfile;
		this.soln = soln;
		this.outAdd = outAdd;
		this.outDelete = outDelete;
		this.outGoalCounts = goalCounts;
	}
	
	public void inspectPlan() throws IOException, ParseException
	{
		UngroundProblem uproblem = PDDL21parser.parseFiles(domain, pfile);
		this.problem = uproblem.ground();
		
		this.plan = SolutionParser.parse(uproblem, this.soln);
		
		
		BufferedWriter w = new BufferedWriter(new FileWriter(this.outGoalCounts, true));
		w.append(this.domain.getParent()+"\t"+this.problem.getGoal().getFacts().size()+"\n");
		w.close();
		
		

		HashMap<Fact, List<Integer>> achievementMap = new HashMap<Fact, List<Integer>>(); 
		HashMap<Fact, List<Integer>> deletionMap = new HashMap<Fact, List<Integer>>(); 
		
		int time = 0;
		for (Fact g : this.problem.getGoal().getFacts())
		{
			List<Integer> initialAchieved = new ArrayList<Integer>();
			achievementMap.put(g, initialAchieved);
			deletionMap.put(g, new ArrayList<Integer>());
			
			if (this.problem.getSTRIPSInitialState().getFacts().contains(g))
			{
				achievementMap.get(g).add(time);
			}
		}
		
		for (Action a : plan.getActions())
		{
			++time;
			
			for (Fact add : a.getAddPropositions())
			{
				if (this.problem.getGoal().getFacts().contains(add))
				{
					achievementMap.get(add).add(time);
				}
			}
			
			
			for (Not del : a.getDeletePropositions())
			{
				if (this.problem.getGoal().getFacts().contains(del.getLiteral()))
				{
					deletionMap.get(del.getLiteral()).add(time);
				}
			}
			
			
		}
		
		BufferedWriter addWriter = new BufferedWriter(new FileWriter(this.outAdd, true));
		addWriter.write(plan.getPlanLength() + "\t" + problem.getGoal().getFacts().size());
		for (Entry<Fact, List<Integer>> e : achievementMap.entrySet())
		{
//			System.out.println(e.getKey().toString()+": "+e.getValue().toString());
			
			for (Integer i : e.getValue())
			{
				addWriter.write("\t"+i);
			}
		}
		
		addWriter.write("\n");
		addWriter.close();
		
		
		
		BufferedWriter delWriter = new BufferedWriter(new FileWriter(this.outDelete, true));
		delWriter.write(plan.getPlanLength() + "\t" + problem.getGoal().getFacts().size());
		for (Entry<Fact, List<Integer>> e : deletionMap.entrySet())
		{
//			System.out.println(e.getKey().toString()+": "+e.getValue().toString());
			
			for (Integer i : e.getValue())
			{
				delWriter.write("\t"+i);
			}
		}
		
		delWriter.write("\n");
		delWriter.close();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		//make sure to delete old results files before running.
		//output format
		//<PLAN LENGTH> <GOAL COUNT> <GOAL ACHIEVEMENT TIMES>*
		//Goals are not currently ordered and may be achieved more than once during planning
		//only the Achievement of goals is checked, not the deletion of (and re-achievement)
		String path = "/tmp/ipc_achievements";
		File f = new File(path);
		if (f.exists() && f.isDirectory())
		{
			f.delete();
			f.mkdir();
		}
		
		GoalTimeAchievementTester.doIpc3(path);
		GoalTimeAchievementTester.doIpc5(path);

	}

	
	private static void doIpc3(String outputFile)
	{
		String basePath = "../Domains/ipc3";
		String[] domains = new String[]{"depots_ipc3", "driverlog_ipc3", "rovers_ipc3", "satellite", "zenotravel_ipc3"};

		File outGoalCount = new File(outputFile+"/goal_counts");
		
		for (String domain : domains)
		{
			String domainBasePath = basePath+"/"+domain;
			String domainPath = domainBasePath+"/domain.pddl";
			
			for (int i=1; i <= 15; i++)
			{
				File outFileAdd = new File(outputFile+"/"+domain+"_add");
				File outFileDel = new File(outputFile+"/"+domain+"_del");
				
				String iStr = (i < 10) ? "0"+i : i+"";
				
				String pfile = "pfile"+iStr;
				String pfilePath = domainBasePath+"/"+pfile;
				String solnPath = domainBasePath+"/solutions/pfile"+i+".soln";

				try
				{
					GoalTimeAchievementTester gt = new GoalTimeAchievementTester(new File(domainPath),
							new File(pfilePath), 
							new File(solnPath),
							outFileAdd,
							outFileDel,
							outGoalCount);
					
					gt.inspectPlan();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				catch (ParseException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
	

	private static void doIpc5(String outputFile)
	{
		String basePath = "../Domains/ipc5";
		String[] domains = new String[]{"openstacksPropositional", "storagePropositional", "trucksPropositional"};

		try
		{
			File outGoalCount = new File(outputFile+"/goal_counts");
			
			for (String domain : domains)
			{
				File outFileAdd = new File(outputFile+"/"+domain+"_add");
				outFileAdd.delete();
				outFileAdd.createNewFile(); //be sure to overwrite old file
				

				File outFileDel = new File(outputFile+"/"+domain+"_del");
				outFileDel.delete();
				outFileDel.createNewFile(); //be sure to overwrite old file
				
				
				String domainBasePath = basePath+"/"+domain;
				
				for (int i=1; i <= 15; i++) //Using 15 because tests are only using 1-15
				{
					String iStr = (i < 10) ? "0"+i : i+"";
	
					String domainPath = domainBasePath+"/domain_p"+iStr+".pddl";
					String pfile = "p"+iStr+".pddl";
					String pfilePath = domainBasePath+"/"+pfile;
					String solnPath = domainBasePath+"/solutions/pfile"+i+".soln";
	
					GoalTimeAchievementTester gt = new GoalTimeAchievementTester(new File(domainPath),
							new File(pfilePath), 
							new File(solnPath),
							outFileAdd,
							outFileDel,
							outGoalCount);
					
					gt.inspectPlan();
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (ParseException e)
		{
			e.printStackTrace();
		}
	}
}
