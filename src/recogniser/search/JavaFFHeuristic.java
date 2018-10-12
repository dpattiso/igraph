package recogniser.search;

import java.util.HashMap;
import java.util.Map;

import javaff.data.Fact;
import javaff.data.GroundProblem;
import javaff.data.Plan;
import javaff.data.RelaxedPlan;
import javaff.data.TotalOrderPlan;
import javaff.data.strips.NullPlan;
import javaff.planning.RelaxedPlanningGraph;
import javaff.planning.STRIPSState;
import javaff.search.UnreachableGoalException;

public class JavaFFHeuristic extends AbstractHeuristic
{
	private HashMap<Fact, Plan> planLookup;
	
	private RelaxedPlanningGraph rpg;
//	private STRIPSState state;
	
	protected JavaFFHeuristic()
	{
		this.planLookup = new HashMap<Fact, Plan>();
	}
	
	/**
	 * Constructs a wrapper for the FF heuristic. This constructor creates a stable RPG
	 * from which multiple relaxed plans can be extracted.
	 * @param gp
	 */
	public JavaFFHeuristic(GroundProblem gp)
	{	
		this();
		
		this.rebuildRPG(gp);		
	}	
	
	/**
	 * Constructs a wrapper for the FF heuristic. This constructor only construct the RPG until the
	 * specified goal is achieved. It will be of no use for extracting goals achieved at layers above this!
	 * @param gp
	 * @param goal The goal of the RPG.
	 */
	public JavaFFHeuristic(GroundProblem gp, Fact goal)
	{		
		this();
		
		this.rpg = new RelaxedPlanningGraph(gp);
		this.rpg.getPlan(gp.getState());
	}
	
	public JavaFFHeuristic(RelaxedPlanningGraph rpg)
	{
		this();
		
		this.rpg = rpg;
	}
	
	public Object clone()
	{
		JavaFFHeuristic clone = new JavaFFHeuristic();
		clone.rpg = (RelaxedPlanningGraph) this.rpg.clone(); //FIXME this is a massive IGRAPH bottleneck - 50% of CPU time!
		
		return clone;
	}
	
	public void setGoal(Fact g)
	{
		this.rpg.setGoal(g);
	}

	public RelaxedPlanningGraph getRpg()
	{
		return rpg;
	}

	public void setRpg(RelaxedPlanningGraph rpg)
	{
		this.rpg = rpg;
	}


	
	public void rebuildRPG(GroundProblem gp)
	{		
		this.planLookup.clear();

		this.rpg = new RelaxedPlanningGraph(gp);
		this.rpg.constructStableGraph(gp.getState());
	}
	
	
	@Override
	public double computeEstimate(Fact gc) throws UnreachableGoalException
	{
		RelaxedPlan plan = this.rpg.getPlanFromExistingGraph(gc);
//		System.out.println(gc + " = "+plan);
		

		if (plan != null)
		{
			//update the cache before returning -- only if non-null
			this.planLookup.put(gc, plan);
			
			return plan.getPlanLength();
		}
		else
		{
			this.planLookup.put(gc, new NullPlan(gc));
			
			throw new UnreachableGoalException(gc, "No relaxed plan to "+gc);
		}
	}
	
	public Map<Fact, Plan> getCachedPlans()
	{
		return this.planLookup;
	}
}



