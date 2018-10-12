package recogniser.util;

import java.util.Comparator;

import recogniser.hypothesis.IGoalHypothesis;

public class GoalHypothesisComparator implements Comparator<IGoalHypothesis>
{

	@Override
	public int compare(IGoalHypothesis a, IGoalHypothesis b)
	{
		if (a.getProbability() < b.getProbability())
			return +1;
		else if (a.getProbability() > b.getProbability())
			return -1;
		else
			return 0;
	}

}
