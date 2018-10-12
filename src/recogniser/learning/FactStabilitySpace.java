package recogniser.learning;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javaff.data.Fact;

/**
 * Represents the stability of a collection of facts over time. The stability of a fact is simply the number of
 * times it has transitioned from true to false or vice versa since observation started. For example, if a
 * fact is initialised to true (true in the initial state), then is negated at time 6 and reachieved at time 10,
 * it's stability will be 0.5, as it has transitioned twice from it's initial value.
 * 
 * @see FactStability
 * 
 * @author David Pattison
 *
 */
public class FactStabilitySpace
{
	private Map<Fact, FactStability> space;

	/**
	 * Creates an empty stability space.
	 */
	public FactStabilitySpace()
	{
		this.space = new HashMap<Fact, FactStability>();
	}

	/**
	 * Adds the specified fact to this space, with the specified initial value.
	 * @param factT The fact
	 * @param isTrue Whether the fact is true or false (negated)
	 * @return The previous {@link FactStability} object previously associated with the fact, or null if there was none.
	 */
	public FactStability addFact(Fact fact, boolean isTrue)
	{
		FactStability f = new FactStability(fact, isTrue);
		return this.space.put(fact, f);
	}
	
	/**
	 * Adds the specified fact to this space.
	 * @param f The fact
	 * @return The previous {@link FactStability} object previously associated with the fact, or null if there was none.
	 */
	public FactStability addFact(FactStability f)
	{
		return this.space.put(f.getFact(), f);
	}
	
	/**
	 * Updates a fact with a given value.
	 * @param gc The fact to update.
	 * @param isTrue The value of the fact.
	 * @return True if the fact is a member of this stability space, false otherwise
	 */
	public boolean update(Fact gc, boolean isTrue)
	{
		//TODO change this to throw an exception and return result of setting true.
		if (this.space.containsKey(gc))
		{
			this.space.get(gc).setTrue(isTrue);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Gets the stability of the specified fact.
	 * @param gc The fact
	 * @return The stability of the fact, in the range 0 to 1.
	 */
	public float getStability(Fact gc)
	{
		FactStability fs = this.space.get(gc);
		if (fs == null)
			return 0f;
		else
			return fs.getStability();
	}
	
	/**
	 * Gets the {@link FactStability} object associated with a fact.
	 * @param gc The fact.
	 * @return The {@link FactStability} of the given fact.
	 */
	public FactStability getFactStability(Fact gc)
	{
		return this.space.get(gc);
	}

	/**
	 * Clears this stability space of all members.
	 */
	public void clear()
	{
		this.space.clear();
	}

	/**
	 * Gets all facts which are in this stability space.
	 * @return A Collection of fact stabilities.
	 */
	public Collection<FactStability> getFacts()
	{
		return space.values();
	}
}
