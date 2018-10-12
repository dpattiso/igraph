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

public class SolutionLengthTester
{
	
	/**
	 * Get the goal and action space sizes for the firs N problems of the specified domain.
	 * @param args Args[0] = domain file, Args[1] = problem directory, Args[2] = number of problems to compute for, 1...N, Args[3] = output file
	 * 		  
	 */
	public static void main(String[] args)
	{	
		try
		{
			doIpc3(args[0], args[1]);
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void printLength(File domain, File problem, File solution, BufferedWriter bufWriter, int number) throws UnsolveableProblemException, IndexOutOfBoundsException, Exception
	{
		UngroundProblem up = PDDL21parser.parseFiles(domain, problem);
		TotalOrderPlan plan = SolutionParser.parse(up, solution);
		
		int length = plan.getPlanLength();
		bufWriter.write(number+"\t"+length+"\n");
	}

	private static void doIpc3(String outputFile, String planner) throws IndexOutOfBoundsException, UnsolveableProblemException, Exception
	{
		String basePath = "../Domains/ipc3";
		String solnBasePath = "../Domains/AUTOGRAPH/special/ipc3/"+planner;
		String[] domains = new String[]{"depots_ipc3", "driverlog_ipc3", "rovers_ipc3", "satellite", "zenotravel_ipc3"};


		BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFile)));
		
		for (String domain : domains)
		{
			String domainBasePath = basePath+"/"+domain;
			String domainPath = domainBasePath+"/domain.pddl";
			
			for (int i=1; i <= 15; i++)
			{
				String iStr = (i < 10) ? "0"+i : i+"";
				
				String pfile = "pfile"+iStr;
				String pfilePath = domainBasePath+"/"+pfile;
				String solnPath = solnBasePath+"/"+domain+"/pfile"+i+".soln";

				try
				{
					SolutionLengthTester.printLength(new File(domainPath), new File(pfilePath), new File(solnPath), writer, i);
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
		writer.close();
	}
	


}
