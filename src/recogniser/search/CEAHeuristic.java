package recogniser.search;

import java.util.HashMap;
import java.util.TreeSet;

import recogniser.util.HybridSasPddlProblem;
import sas.data.CausalGraph;
import sas.data.SASLiteral;
import sas.data.SASProposition;
import sas.data.SASState;
import sas.search.CausalGraphHeuristic;
import sas.search.CeaHeuristic;
import javaff.data.Fact;
import javaff.data.strips.Not;
import javaff.data.strips.Proposition;
import javaff.search.UnreachableGoalException;

/**
 * IGRAPH wrapper for the context enhanced additive heuristic
 * @author David Pattison
 * @see CeaHeuristic
 *
 */
public class CEAHeuristic extends AbstractHeuristic
{
	private HybridSasPddlProblem problem;
	private HashMap<Fact, SASLiteral> factLookup;
	
	private CeaHeuristic heuristic;

	/**
	 * Internal method for creating object without initialising fields. Primarily for 
	 * {@link #clone()}.
	 */
	protected CEAHeuristic()
	{
		super();
		
		this.problem = null;	
	}
	
	/**
	 * Create an IGRAPH wrapper for the JavaSAS {@link CeaHeuristic} heuristic. 
	 * @param problem
	 */
	public CEAHeuristic(HybridSasPddlProblem problem)
	{
		this();
		
		this.problem = problem;
		
		this.initialise();
	}
	
	
	protected void initialise()
	{
		this.factLookup = new HashMap<Fact, SASLiteral>();
		
		for (SASProposition l : this.problem.sasproblem.reachableFacts)
		{
			Proposition p = l.convertToPDDL(this.problem.sasproblem, this.problem);
			this.factLookup.put(p, l);
		}

		this.heuristic = new CeaHeuristic(this.problem.getCausalGraph());
	}
	
	/**
	 * Performing a clone() is an expensive process. This method performs a partial clone by returning
	 * a new instance, but with all pre-processed information already set up. That is, this method returns
	 * a new {@link CEAHeuristic} instance and associated {@link CeaHeuristic}, but all pre-processing
	 * which is required with a normal new instance is bypassed, as this will always produce the same output
	 * when the same causal graph is used.
	 * @return
	 */
	public CEAHeuristic branch()
	{
		CEAHeuristic clone = new CEAHeuristic();
		
		clone.factLookup = this.factLookup;
		clone.heuristic = this.heuristic.branch();
		clone.lookup = this.lookup;
		clone.problem = this.problem;
		
		return clone;		
	}

	/**
	 * Forwards to {@link #getEstimate(Fact)}
	 */
	@Override
	public double computeEstimate(Fact goals) throws UnreachableGoalException
	{
		double est = this.getEstimate(goals, this.problem.sasproblem.getCurrentState());
		if (est == CEAHeuristic.Unreachable)
			return IHeuristic.Unreachable;
			
		return est;
	}
	
	/**
	 * Forwards to {@link #getEstimate(Fact, SASState)} with the initial state as the second parameter.
	 */
	@Override
	public double getEstimate(Fact goal) throws UnreachableGoalException
	{
		return this.getEstimate(goal, this.problem.sasproblem.getCurrentState());
	}
	
	
	/**
	 * Get the context enhanced additive heuristic's estimate to the goal specified. This is the sum of the
	 * individual goals estimates.
	 * @param goal
	 * @param initial
	 * @return
	 * @throws UnreachableGoalException
	 */
	public double getEstimate(Fact goal, SASState initial)
			throws UnreachableGoalException
	{
		try
		{
			//normally, could just send the conjunctive goal to the CGH, but we want faster lookups so send each one individually and remember the
			//estimate for it, in case it is seen in the future. Thus, only 1 plan needs to ever be computed per fact.
			TreeSet<SASLiteral> sasGoals = new TreeSet<>();
			for (Fact g : goal.getFacts())
			{
				boolean positive = true;
				SASLiteral sasGoal = null;
				if (g instanceof Not)
				{
					sasGoal = this.factLookup.get(((Not)g).getLiteral());
					positive = false;
				}
				else
					sasGoal = this.factLookup.get(g);
				
				sasGoals.add(sasGoal);
			}

			SASState s = (SASState) initial.clone();
			double plan = this.heuristic.getEstimate(s, sasGoals);
			
			return plan;
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
	
	
	
	/**
	 * Clones this heuristic wrapper. The wrapped {@link CeaHeuristic} itself is deep copied, while
	 * all other internal fields are shallow copies.
	 */
	@Override
	public Object clone()
	{
		CEAHeuristic clone = new CEAHeuristic();
		
		clone.factLookup = (HashMap<Fact, SASLiteral>) this.factLookup.clone();
		clone.heuristic = (CeaHeuristic) this.heuristic.clone();
		clone.lookup = (HashMap<Fact, Double>) this.lookup.clone();
		clone.problem = (HybridSasPddlProblem) this.problem.clone();
		
		return clone;
	}

	@Override
	public void reset()
	{
		super.reset();
		
		
	}
}
