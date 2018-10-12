package recogniser.hypothesis;

import java.util.Collection;
import java.util.List;

import recogniser.search.IGoalHypothesisSelector;

public class GoalHypothesisSpace
{
	private IGoalHypothesisSelector hypothesisSelector;
	private Collection<IGoalHypothesis> hypotheses;
	
	private IGoalSpace goalSpace;
	
	public GoalHypothesisSpace(IGoalSpace goalSpace)
	{
		this.goalSpace = goalSpace;
	}
	
	public List<IGoalHypothesis> getBestHypotheses()
	{
		return hypothesisSelector.getHypotheses(this.hypotheses);
	}
}
