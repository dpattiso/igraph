package recogniser.search;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javaff.data.Fact;
import javaff.data.strips.Not;
import javaff.data.strips.Proposition;
import recogniser.search.IHeuristic;
import recogniser.util.HybridSasPddlProblem;
import sas.data.DomainTransitionGraph;
import sas.data.SASLiteral;
import sas.data.SASPlan;
import sas.data.SASProposition;
import sas.data.SASState;
import sas.search.CausalGraphHeuristic;
import javaff.search.UnreachableGoalException;

/**
 * An unfortunately named wrapper for the CausalGraphHeuristic in JavaSAS. Note that this class uses
 * a lookup table for any previously computed estimates. Thus, it must be reset after the current state
 * has been updated, by calling reset() or creating a new object.
 * @author David Pattison
 *
 */
public class CGHeuristic extends AbstractHeuristic
{
	private HybridSasPddlProblem problem;
	private HashMap<Fact, SASLiteral> factLookup;
	private CausalGraphHeuristic heuristic;
	
	
	/**
	 * Because CGH is just the sum of each individual literal's distance to the goal, a lookup can be used for any goals which have already had an h(G) value 
	 * computed. This must be reset (or the object destroyed) after the current/initial state changes!
	 */
	private HashMap<Fact, Double> previousDists;
	

	/**
	 * Internal constructor for initialising fields only. No processing performed.
	 */
	protected CGHeuristic()
	{
		this.problem = null;
		this.heuristic = null;
		this.previousDists = new HashMap<Fact, Double>();
		
		this.factLookup = new HashMap<Fact, SASLiteral>();
	}
	
	/**
	 * Creates a CG heuristic object. Note that this is an expensive operation and should only be done
	 * at problem initialisation, as a PDDL-to-SAS lookup is created. Use setProblem() and reset() for 
	 * updates.
	 * @param hybridProblem
	 */
	public CGHeuristic(HybridSasPddlProblem hybridProblem)
	{
		this(); //initialise fields
		
		this.problem = hybridProblem;
		this.heuristic = new CausalGraphHeuristic(this.problem.sasproblem, true);
		
		for (SASProposition l : this.problem.sasproblem.reachableFacts)
		{
			Proposition p = l.convertToPDDL(this.problem.sasproblem, this.problem);
			this.factLookup.put(p, l);
		}
	}
	
	@Override
	public Object clone()
	{
		CGHeuristic clone = new CGHeuristic();
		
		clone.factLookup = (HashMap<Fact, SASLiteral>) this.factLookup.clone();
		clone.heuristic = (CausalGraphHeuristic) this.heuristic.clone();
		clone.lookup = (HashMap<Fact, Double>) this.lookup.clone();
		clone.previousDists = (HashMap<Fact, Double>) this.previousDists.clone();
		clone.problem = (HybridSasPddlProblem) this.problem.clone();
		
		return clone;
	}
	
	/**
	 * Erase the lookup table.
	 */
	public void reset()
	{
		synchronized (this.previousDists)
		{
			this.previousDists.clear();
		}
		this.heuristic.resetCache();
		
		super.reset();
	}
	

	/**
	 * Get the distance to the goal from the current state, as dictated by getProblem().sasProblem.getCurrentState();
	 */
	@Override
	public double computeEstimate(Fact goal) throws UnreachableGoalException
	{
		double total = 0;
		for (Fact subFact : goal.getFacts())
		{
			double est = this.getEstimate(subFact, this.problem.sasproblem.getCurrentState());
			if (est == (double)CausalGraphHeuristic.Unreachable)
				return IHeuristic.Unreachable;
			
			total += est;
		}
		
		return total;
	}

	/**
	 * Get the distance to the goal from the specified initial state.
	 */
	protected double getEstimate(Fact goal, SASState initial) throws UnreachableGoalException
	{	
		try
		{
			double totalPlan = 0;
			
			//normally, could just send the conjunctive goal to the CGH, but we want faster lookups so send each one individually and remember the
			//estimate for it, in case it is seen in the future. Thus, only 1 plan needs to ever be computed per fact.
			for (Fact g : goal.getFacts())
			{
				//if the plan for this has already been computed, use it instead of searching again
				synchronized (this.previousDists)
				{
					if (this.previousDists.containsKey(g))
					{
						double existing = this.previousDists.get(g);
						totalPlan += existing;
						
						continue;
					}
				}
				
				SASLiteral sasGoal = null;
				synchronized (this.factLookup)
				{
					if (g instanceof Not)
					{
						sasGoal = this.factLookup.get(((Not)g).getLiteral());
					}
					else
					{
						sasGoal = this.factLookup.get(g);
					}
					
				}
//				if (sas == null)
//					throw new NullPointerException("Cannot find "+g);
				
				HashMap<Integer, Integer> singleGoalSet = new HashMap<Integer, Integer>(); //need a Map for goals
				singleGoalSet.put(sasGoal.getVariableId(), sasGoal.getValueId()); //which contains only a single var-val mapping
				
				//bit of a hack to get multi-threading working- could be done inside the heuristic itself, but that's really shoving AUTOGRAPH work into 
				//non-AUTOGRAPH projects
//				CausalGraphHeuristic newHeuristic = (CausalGraphHeuristic) this.heuristic.clone();
				CausalGraphHeuristic newHeuristic = new CausalGraphHeuristic(this.problem.sasproblem, false);
				double plan = newHeuristic.getEstimate(initial, sasGoal);
				
				//append single goal plan
				totalPlan += plan;
				
				synchronized (this.previousDists)
				{
					this.previousDists.put(g, plan); //remember that g can be a Not
				}
			}
			
			return (double) totalPlan;
		}
		catch (UnreachableGoalException e)
		{
			throw e;
		}
		catch (IllegalArgumentException e)
		{
			System.err.println("Goal "+goal+", at least partially, does not exist in SAS+. Perhaps it is unreachable?");
		}
		
		return IHeuristic.Unreachable;
	}

//	/**
//	 * Convert the conjunctive PDDL goal into a SAS+ one.
//	 * @param gc
//	 * @return
//	 * @deprecated No longer used by IGRAPH, but could be reintroduced -- allows negative goals to be included
//	 */
//	protected void convertGoal(Fact gc, Map<Integer, Integer> positive, Map<Integer, Integer> negative) throws IllegalArgumentException
//	{
//		positive.clear();
//		negative.clear();
//		
//		try
//		{
//			for (Fact f : gc.getFacts())
//			{
//				if (f.isStatic())
//					continue;
//				
//				SASLiteral sas = null;
//				if (f instanceof Not)
//				{
//					sas = this.getFactLookup().get(((Not)f).getLiteral());
//					if (sas == null)
//						throw new IllegalArgumentException("PDDL goal "+((Not)f).getLiteral().toString()+" has no SAS+ equivalent");
//					
//					negative.put(sas.getVariableId(), sas.getValueId());
//				}
//				else
//				{
//					sas = this.getFactLookup().get(f);
//					if (sas == null)
//						throw new IllegalArgumentException("PDDL goal "+f.toString()+" has no SAS+ equivalent");
//					
//					positive.put(sas.getVariableId(), sas.getValueId());
//				}
//				
//			}
//		}
//		catch (IllegalArgumentException e)
//		{
//////			throw new IllegalArgumentException("PDDL goal "+f.toString()+" has no SAS+ equivalent");
////			e.printStackTrace();
//			System.err.println(e.getMessage());
//			throw e;
//		}
//	}
	

	

	public HybridSasPddlProblem getProblem()
	{
		return problem;
	}

	public void setProblem(HybridSasPddlProblem problem)
	{
		this.problem = problem;
		this.heuristic = new CausalGraphHeuristic(this.problem.sasproblem, true);
	}
}
