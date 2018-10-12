package recogniser.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javaff.data.DomainRequirements;
import javaff.data.Fact;
import javaff.data.GroundProblem;
import javaff.data.Requirement;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.planning.STRIPSState;
import recogniser.search.CEAHeuristic;
import recogniser.search.CGHeuristic;
import recogniser.search.JavaFFHeuristic;
import recogniser.search.MaxHeuristic;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.StripsRpg;
import sas.data.SASProblem;
import sas.data.SASState;
import sas.parser.SASTranslator;
import sas.parser.SASplusParser;
import sas.util.NullPrintStream;

public class BatchHeuristicTesterIpc5
{

	/**
	 * Get heuristic estimates for the firs N problems of the specified domain.
	 * @param args Args[0] = domain file, Args[1] = problem directory, Args[2] = solution directory, Args[3] = number of problems to compute for, 1...N, Args[4] = output file
	 * 		  
	 */
	public static void main(String[] args)
	{
		String domainPath = args[0];
		String problemPath = args[1];
		String solutionPath = args[2];
		int pcount = Integer.parseInt(args[3]);
		String output = args[4];

		BufferedWriter bufWriter = null;
		try
		{
			bufWriter = new BufferedWriter(new FileWriter(new File(output)));
			bufWriter.write("h_{max}\\th_{\\mathit{ff}}\\th_{cg}\\t|plan|\n");
			//System.out.println("Max\tFF\tCG\tGP");
			for (int i = 1; i <= pcount; i++)
			{
				String pfileNumber = (i < 10) ? "0"+i : ""+i;
				
				File domain = new File(domainPath+"/domain_p"+pfileNumber+".pddl");
				File problem = new File(problemPath+"/p"+pfileNumber+".pddl");
				File solution = new File(solutionPath+"/pfile"+i+".soln");
				getEstimates(domain, problem, solution, bufWriter);
				
				bufWriter.flush();
			}
			
			bufWriter.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (ParseException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void getEstimates(File domain, File problem, File solution, BufferedWriter out) throws Exception
	{
		
		UngroundProblem up = PDDL21parser.parseFiles(domain, problem);
		GroundProblem gp = up.ground();
		if (gp.getRequirements().contains(Requirement.ADL))
			gp.decompileADL();
					
		SASTranslator.translateToSAS(domain, problem, new NullPrintStream());
		SASProblem sp = SASplusParser.parse();
		sp.setupInitialState();
		HybridSasPddlProblem hybrid = new HybridSasPddlProblem(gp, sp);
		
		STRIPSState pddlCurrent = gp.getSTRIPSInitialState();
		SASState sasCurrent = sp.getCurrentState();
	
		TotalOrderPlan plan = SolutionParser.parse(up, solution);

		double hmax, hff, hcg, hgp, hcea, hplan = plan.getPlanLength();

		Fact goals = gp.getGoal();
		
		StripsRpg maxRpg = new StripsRpg(gp.getActions());
		maxRpg.constructFullRPG(pddlCurrent);
		MaxHeuristic max = new MaxHeuristic(maxRpg);
		hmax = max.getEstimate(goals);

		JavaFFHeuristic ff = new JavaFFHeuristic(gp);
		hff = ff.getEstimate(goals);
		
		CGHeuristic cg = new CGHeuristic(hybrid);
		hcg = cg.getEstimate(goals);
		
		CEAHeuristic cea = new CEAHeuristic(hybrid);
		hcea = cea.getEstimate(goals);
		
//		GraphplanHeuristic graphplan = new GraphplanHeuristic(gp);
//		hgp = graphplan.getEstimate(goals, pddlCurrent);
		
//		String output = hmax +"\t"+hff+"\t"+hcg+"\t"+hgp+"\t"+hplan+"\n";
		
		String output = hmax +"\t"+hff+"\t"+hcg+"\t"+hcea+"\t"+hplan+"\n";
		
		out.write(output);
		System.out.println(output);
	}
}
