package recogniser.search;

import javaff.search.UnreachableGoalException;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.Plan;
import javaff.data.TotalOrderPlan;
import javaff.planning.PlanningGraph;
import javaff.planning.State;

public class GraphplanHeuristic extends AbstractHeuristic
{
	private PlanningGraph pg;
	private GroundProblem problem;
	
	public GraphplanHeuristic(GroundProblem gp)
	{		
		this.problem = gp;
		this.problem.getSTRIPSInitialState(); //force setup of current state
		this.pg = new PlanningGraph(this.problem);
	}
	
	public Object clone()
	{
		GroundProblem gp = (GroundProblem) this.problem.clone();
		gp.getSTRIPSInitialState(); //FIXME shouldnt do this here, should be in clone()
		GraphplanHeuristic clone = new GraphplanHeuristic(gp);
		return clone;
	}
	
	public void setGoal(GroundFact g)
	{
		this.problem.setGoal(g);
		this.pg.setGoal(g);
	}
	
	@Override
	public double getEstimate(Fact goal) throws UnreachableGoalException
	{		
		if (super.lookup.containsKey(goal))
			return super.lookup.get(goal);
		
		return this.computeEstimate(goal);
	}	
	
	@Override
	public double computeEstimate(Fact goal) throws UnreachableGoalException
	{		
//		this.pg = new PlanningGraph(this.problem);
		State s = this.problem.getState();
		s.goal = (GroundFact) goal;
		Plan plan = this.pg.getPlan(s);
//		System.out.println(gc + " = "+plan);
		
		if (plan != null)
		{
			return plan.getPlanLength();
		}
		else
			throw new UnreachableGoalException(goal, goal+" is unreachable");
	}


	public PlanningGraph getPlanGraph()
	{
		return pg;
	}


	public void setPlanGraph(PlanningGraph pg)
	{
		this.pg = pg;
	}


	public GroundProblem getProblem()
	{
		return problem;
	}


	public void setProblem(GroundProblem problem)
	{
		this.problem = problem;
		this.reset();
	}
	
	/**
	 * Creates a new PG and initialises it to be stable.
	 */
	@Override
	public void reset()
	{
		this.pg = new PlanningGraph(this.problem);
		this.problem.getSTRIPSInitialState(); //initialise state variable
		this.pg.constructStableGraph(this.problem.getState());
	}


}
