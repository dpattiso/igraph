package recogniser.search;

import javaff.search.UnreachableGoalException;
import javaff.data.Fact;

/**
 * Interface for a heuristic.
 * @author pattison
 *
 */
public interface IHeuristic
{
	/**
	 * The value of an unreachable goal. Defaults to Float.MAX_VALUE.
	 */
	public double Unreachable = Double.MAX_VALUE;

	/**
	 * Gets the heuristic estimate to the goal specified. This need not be an integer.
	 * @param goal The goal.
	 * @return An estimate of work remaining to achieve the goal.
	 * @throws UnreachableGoalException Thrown if the goal is unreachable.
	 */
	public double getEstimate(Fact goal) throws UnreachableGoalException;

	public void reset();
	
	
	public Object clone();
}
