package recogniser.learning;

import java.util.ArrayList;

import javaff.data.Fact;

/**
 * Represents the stability of a fact over N timesteps. Stability is classed as the number
 * of times a fact has had its value deleted and reachieved once being achieved for the first time.
 * 
 * @author David Pattison
 *
 */
public class FactStability
{
	protected Fact fact;
	protected boolean value;
	protected float changes;
	
	/**
	 * Creates a fact stability object with the specified fact and initial value.
	 * @param fact The fact.
	 * @param initValue Whether the initial value of the fact is true or false.
	 */
	public FactStability(Fact fact, boolean initValue)
	{
		this.fact = fact;
		
		this.changes = 0;
		this.value = initValue;
	}

	/**
	 * Determines whether the associated fact is currently true or false.
	 * @return True if the fact is currently set to true, false otherwise.
	 */
	public boolean isTrue()
	{
		return this.value;
	}


	/**
	 * Sets the fact to true for the next timestep.
	 * @param gc
	 */
	public void setTrue(boolean value)
	{
		if (value != this.value)
			this.changes++;
		
		this.value = value;
	}
	
	/**
	 * Returns whether the fact is actually static.
	 * @return True if the fact is static (cannot be negated) or false otherwise.
	 */
	public boolean isStatic()
	{
		return this.fact.isStatic();
	}
	
	/**
	 * Gets the stability associated with the fact, as a number between 0 and 1 (inclusive).
	 * @return
	 */
	public float getStability()
	{
		if (this.changes == 0)
			return 1;
		
		return 1f / this.changes;
	}
	
	/**
	 * Gets the fact associated with this object.
	 * @return The fact.
	 */
	public Fact getFact()
	{
		return fact;
	}

	/**
	 * Sets the fact associated with this object. This does not reset the stability of the fact.
	 * @param fact The fact.
	 */
	public void setFact(Fact fact)
	{
		this.fact = fact;
	}
	
	@Override
	public String toString()
	{
		return "Stability: "+ this.fact+" = " + this.getStability();
	}
}
