package recogniser.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import javaff.data.GroundProblem;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.planning.STRIPSState;
import recogniser.search.CGHeuristic;
import recogniser.search.GraphplanHeuristic;
import recogniser.search.JavaFFHeuristic;
import recogniser.search.MaxHeuristic;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.StripsRpg;
import sas.data.SASProblem;
import sas.data.SASState;
import sas.parser.SASTranslator;
import sas.parser.SASplusParser;
import sas.util.NullPrintStream;
import sas.util.UnsolveableProblemException;

public class GroundingTester
{
	
	/**
	 * Get the goal and action space sizes for the firs N problems of the specified domain.
	 * @param args Args[0] = domain file, Args[1] = problem directory, Args[2] = number of problems to compute for, 1...N, Args[3] = output file
	 * 		  
	 */
	public static void main(String[] args)
	{
		String domainPath = args[0];
		String problemPath = args[1];
		int pcount = Integer.parseInt(args[2]);
		String output = args[3];

		BufferedWriter bufWriter = null;
		try
		{
			bufWriter = new BufferedWriter(new FileWriter(new File(output)));

			for (int i = 1; i <= pcount; i++)
			{
				String suffix = (i < 10) ? "0"+i : ""+i;
				suffix = suffix+".pddl"; //for non IPC3 problems
				
				File domain = new File(domainPath+((i < 10) ? "0"+i : ""+i)+".pddl");
				File problem = new File(problemPath+suffix);

				
				try
				{
					printSizes(domain, problem, bufWriter,i);
				}
				catch (Exception e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				bufWriter.flush();
			}
			
			bufWriter.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

//	/**
//	 * Get the goal and action space sizes for the firs N problems of the specified domain.
//	 * @param args Args[0] = domain file, Args[1] = problem directory, Args[2] = number of problems to compute for, 1...N, Args[3] = output file
//	 * 		  
//	 */
//	public static void main(String[] args)
//	{
//		File domain = new File(args[0]);
//		String problemPath = args[1];
//		int pcount = Integer.parseInt(args[2]);
//		String output = args[3];
//
//		BufferedWriter bufWriter = null;
//		try
//		{
//			bufWriter = new BufferedWriter(new FileWriter(new File(output)));
//
//			for (int i = 1; i <= pcount; i++)
//			{
//				String suffix = (i < 10) ? "0"+i : ""+i;
//				suffix = suffix+".pddl"; //for non IPC3 problems
//				File problem = new File(problemPath+suffix);
//
//				
//				printSizes(domain, problem, bufWriter,i);
//				
//				bufWriter.flush();
//			}
//			
//			bufWriter.close();
//		}
//		catch (IOException e)
//		{
//			e.printStackTrace();
//		}
//		catch (Exception e)
//		{
//			e.printStackTrace();
//		}
//	}
	
	private static void printSizes(File domain, File problem, BufferedWriter bufWriter, int number) throws UnsolveableProblemException, IndexOutOfBoundsException, Exception
	{
		
		UngroundProblem up = PDDL21parser.parseFiles(domain, problem);
		GroundProblem gp = up.ground();
					
		SASTranslator.translateToSAS(domain, problem, new NullPrintStream());
		SASProblem sp = SASplusParser.parse();
		sp.setupInitialState();
		int originalGoalSpaceSize = gp.getReachableFacts().size();
		int originalActionSpaceSize = gp.getActions().size();
		
		gp.filterReachableFacts();
		
		HybridSasPddlProblem hybrid = new HybridSasPddlProblem(gp, sp);
		
//		HybridSasPddlProblem filter = (HybridSasPddlProblem) hybrid.clone();
//		filter.filterReachableFacts();
	
		
		int filteredGoalSpaceSize = hybrid.getReachableFacts().size();
		int filteredActionSpaceSize = hybrid.getActions().size();
		
		bufWriter.write(number+"\t"+originalGoalSpaceSize+"\t"+filteredGoalSpaceSize+"\t"+originalActionSpaceSize+"\t"+filteredActionSpaceSize+"\n");
	}

}
