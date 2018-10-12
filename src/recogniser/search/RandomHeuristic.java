package recogniser.search;

import java.util.Random;

import javaff.data.Fact;
import javaff.search.UnreachableGoalException;

/**
 * Heuristic which acts as a positive random number generator.
 * @author David Pattison
 *
 */
public class RandomHeuristic extends AbstractHeuristic
{
	private Random rand;
	private double maxValue;
	
	public RandomHeuristic() 
	{
		this.rand = new Random();
		this.maxValue = Integer.MAX_VALUE;
	}

	public RandomHeuristic(int seed)
	{
		this();
		
		this.rand.setSeed(seed);
	}
	
	public RandomHeuristic(int seed, double maxValue)
	{
		this(seed);
		
		this.maxValue = maxValue;
	}
	
	/**
	 * Shallow clone.
	 */
	@Override
	public Object clone() 
	{
		return this;
	}

	/**
	 * Returns a random number between 0 and {@link #getMaxValue()}
	 */
	@Override
	protected synchronized double computeEstimate(Fact goal) throws UnreachableGoalException 
	{
		
//		return this.rand.nextDouble() * this.getMaxValue();
		return this.rand.nextInt((int) this.getMaxValue());
	}

	public double getMaxValue()
	{
		return maxValue;
	}

	public void setMaxValue(double maxValue)
	{
		this.maxValue = maxValue;
	}

	public Random getRand()
	{
		return rand;
	}

	public void setRand(Random rand)
	{
		this.rand = rand;
	}
	

}
