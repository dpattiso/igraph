package recogniser.search;

import java.util.Collection;
import java.util.List;

import recogniser.hypothesis.IGoalHypothesis;

/**
 * Interface for objects which select hypotheses from amongst a goal space.
 * @author pattison
 *
 */
public interface IGoalHypothesisSelector
{
	/**
	 * Gets the single best hypothesis from the given list.
	 * @param hypotheses
	 * @return
	 */
	public IGoalHypothesis getBestHypothesis(Collection<IGoalHypothesis> hypotheses);
	
	/**
	 * Gets an ordered list of the x best hypotheses in the given list.
	 * @param hypotheses
	 * @return An ordered lists which has the best hypothesis at its head.
	 */
	public List<IGoalHypothesis> getHypotheses(Collection<IGoalHypothesis> hypotheses);
}
