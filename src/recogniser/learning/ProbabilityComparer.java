package recogniser.learning;

import java.util.Comparator;

import javaff.data.Action;
import recogniser.util.ProbabilityMapping;

/**
 * Comparer which sorts probability mappings into a descending order.
 * @author David Pattison
 *
 */
public class ProbabilityComparer implements Comparator<ProbabilityMapping<Action>>
{
	@Override
	public int compare(ProbabilityMapping<Action> a,
			ProbabilityMapping<Action> b)
	{
		if (a.getProbability() > b.getProbability())
			return -1;
		else if (a.getProbability() < b.getProbability())
			return 1;
		else 
			return 0;					
	}
}