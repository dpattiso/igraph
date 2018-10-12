package recogniser.search;

import java.util.HashMap;

import javaff.data.Fact;
import javaff.search.UnreachableGoalException;

/**
 * Implementaiton of {@link IHeuristic} which caches estimates to allow for faster processing. Must be reset through a call
 * to {@link #reset()} once estimates need updated.
 * @author David Pattison
 *
 */
public abstract class AbstractHeuristic implements IHeuristic
{
	
	protected HashMap<Fact, Double> lookup;

	public AbstractHeuristic()
	{
		this.lookup = new HashMap<Fact, Double>();
	}
	
	public abstract Object clone();

	/**
	 * Gets the estimate to the specified goal. Returns a cached value if it has already been computed since
	 * the last call to {@link #reset()}.
	 */
	public double getEstimate(Fact goal) throws UnreachableGoalException
	{
		double h;
		if (this.lookup.containsKey(goal))
		{
			h = this.lookup.get(goal);
			this.lookup.put(goal, h);
		}
		else
		{
			h = this.computeEstimate(goal);
		}
		
		return h;
	}
	
	/**
	 * Delegate method for subclasses of {@link AbstractHeuristic}. Performs the actual process of 
	 * computing an estimate to the specified goal. @link{#getEstimate} only forwards to this method
	 * if there is no previously-cached estimate.
	 * @param goal
	 * @return
	 * @throws UnreachableGoalException
	 */
	protected abstract double computeEstimate(Fact goal) throws UnreachableGoalException;
	
	/**
	 * Reset all cached estimates -- forces the heuristic to recompute the distance to a goal, given that
	 * the called uses {@link #getEstimate(Fact)}.
	 */
	public void reset()
	{
		this.lookup.clear();
	}
}
