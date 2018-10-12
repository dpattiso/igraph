package recogniser.test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import recogniser.search.CEAHeuristic;
import recogniser.search.CGHeuristic;
import recogniser.search.JavaFFHeuristic;
import recogniser.search.MaxHeuristic;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.StripsRpg;
import sas.data.SASAction;
import sas.data.SASProblem;
import sas.data.SASState;
import sas.parser.SASTranslator;
import sas.parser.SASplusParser;
import javaff.data.Action;
import javaff.data.GroundProblem;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.planning.STRIPSState;

public class HeuristicTester
{

	
	
	public static void main(String[] args)
	{
		File domain = new File(args[0]);
		File problem = new File(args[1]);
		File solution = new File(args[2]);
		
		try
		{
			UngroundProblem up = PDDL21parser.parseFiles(domain, problem);
			GroundProblem gp = up.ground();
						
			SASTranslator.translateToSAS(domain, problem);
			SASProblem sp = SASplusParser.parse();
			sp.setupInitialState();
			HybridSasPddlProblem hybrid = new HybridSasPddlProblem(gp, sp);
			
			TotalOrderPlan plan = SolutionParser.parse(up, solution);
			
			Queue<Action> actions = new LinkedList<Action>(plan.getActions());
			STRIPSState pddlCurrent = gp.getSTRIPSInitialState();
			SASState sasCurrent = sp.getCurrentState();
			
			System.out.println("Max\tFF\tCG\tCEA");
			do
			{
				double hmax, hff, hcg, hcea, hgp;
				
				StripsRpg maxRpg = new StripsRpg(gp.getActions());
				maxRpg.constructFullRPG(pddlCurrent);
				MaxHeuristic max = new MaxHeuristic(maxRpg);
				hmax = max.getEstimate(gp.getGoal());
				
				JavaFFHeuristic ff = new JavaFFHeuristic(gp);
				hff = ff.getEstimate(gp.getGoal());
				
				CGHeuristic cg = new CGHeuristic(hybrid);
				hcg = cg.getEstimate(gp.getGoal());
				
				CEAHeuristic cea = new CEAHeuristic(hybrid);
				hcea = cea.getEstimate(gp.getGoal());
				
//				GraphplanHeuristic graphplan = new GraphplanHeuristic(gp);
//				hgp = graphplan.getEstimate(gp.goal, pddlCurrent);
				
				System.out.println(hmax +"\t"+hff+"\t"+hcg+"\t"+hcea);
				
				Action pddlAction = actions.remove();
				SASAction sasAction = null;
				for (SASAction sasa : hybrid.sasproblem.actions.values())
				{
					if (sasa.toString().equals(pddlAction.toString()))
					{
						sasAction = sasa;
					}
				}
				if (sasAction == null)
					throw new NullPointerException("Could not find equivalent SAS+ action for "+pddlAction);
				
				if (pddlAction.isApplicable(pddlCurrent) == false)
					throw new NullPointerException("Action not applicable");
				
				pddlCurrent = (STRIPSState) pddlCurrent.apply(pddlAction);
				sasCurrent = sasCurrent.apply(sasAction);
				
				gp.setState(pddlCurrent);
				hybrid.setState(pddlCurrent);
				hybrid.sasproblem.state = sasCurrent;
			}
			while (actions.isEmpty() == false);
			

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
}
