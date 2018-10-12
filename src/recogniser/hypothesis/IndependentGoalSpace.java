package recogniser.hypothesis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javaff.data.Fact;
import javaff.data.MutexSpace;
import javaff.data.strips.Proposition;

/**
 * Represents a goal space in which propositions are assumed to be independent of one another
 * and have probabilities associated with them. This goal space has NO completeMutexSpace. Accessing the appropriate
 * methods will return null;
 * @author David Pattison
 *
 */
public class IndependentGoalSpace implements IGoalSpace
{
	
	private static final MutexSpace EMPTY_MUTEX_SET = new MutexSpace();
//	private IGoalHeuristic goalHeuristic;
	protected Map<Fact, Double> goals;
	
	public IndependentGoalSpace()
	{
		this.goals = new HashMap<Fact, Double>();
//		this.goalHeuristic = new 
	}
	
	/**
	 * Creates a goal space from the specified propositions.
	 */
	public IndependentGoalSpace(Collection<Fact> goals)
	{
		this();
		
//		if (this.checkGoalsForAtomicity(groundedProps) == false)
//			throw new IllegalArgumentException("Goals are not all individual");
		
//		double prob = 1f/goals.size();
		for (Fact g : goals)
			this.goals.put(g, 0d);
	}
	
	@Override
	public Object clone()
	{
		IndependentGoalSpace clone = new IndependentGoalSpace();
		clone.goals = new HashMap<Fact, Double>(this.goals);
		return clone;
	}
	
	@Override
	public int size()
	{
		return this.goals.size();
	}
	
	/**
	 * Sets all probabilities to zero. Does not remove goals.
	 */
	@Override
	public void reset()
	{
		for (Entry<Fact, Double> entry : this.goals.entrySet())
		{
			this.goals.put(entry.getKey(), 0d);
		}
	}

	@Override
	public Set<? extends Fact> getGoals()
	{
		return goals.keySet();
	}

//	/**
//	 * Constructs the goal space with each goal having 1/collection_size as its probability.
//	 */
	@Override
	public void setGoals(Collection<? extends Fact> goals)
	{
//		if (this.checkGoalsForAtomicity(goals) == false)
//			throw new IllegalArgumentException("Goals are not all individual");

		this.goals.clear();
		
//		double prob = 1f/goals.size();
		for (Fact g : goals)
			this.goals.put(g, 0d);
	}
	
	/**
	 * Returns the probability associated with a goal.
	 * @param gc
	 * @return The probability of the goal.
	 * @throws NullPointerException If the goal does not exist in this goal-space.
	 */
	@Override
	public double getProbability(Fact gc)
	{
		Double f = this.goals.get(gc);
		if (f == null)
//			return -1;
			throw new NullPointerException("Cannot find "+gc+" in goal space");
		
		return f;
	}
		
	/**
	 * @throws NullPointerException If the goal does not exist in this goal-space.
	 * @throws IllegalArgumentException If the probability specified is outwith the range [0:1]
	 */
	@Override
	public void setProbability(Fact gc, double probability)
	{
		this.validateUpdate(gc, probability);
		
		this.goals.put(gc, probability);
		
	}
	
	protected void validateUpdate(Fact gc, double probability)
	{
		if (this.goals.containsKey(gc) == false)
			throw new NullPointerException("Goal "+gc+" is not in goal space");
		if (probability < 0 || probability > 1)
			throw new IllegalArgumentException("Probability is not in range [0:1] - "+probability);
	}

	/**
	 * Sets the probability of a collection of individual goals.
	 * @param goals
	 * @param probability
	 */
	public void setProbability(Collection<Fact> goals, double probability)
	{
		for (Fact gc : goals)
			this.setProbability(gc, probability);
	}
	
	private boolean verifyTotal()
	{
		double total = 0;
		for (Double f : this.goals.values())
			total += f;
		
		return total == this.goals.size();
	}
	
	private void setAllProbabilities(double p)
	{
		for (Fact g : this.goals.keySet())
			this.goals.put(g, p);
	}
	
	
//	public void addGoal(Proposition p)
//	{
//		if (this.goals.containsKey(p) == false)
//			this.goals.add(p);
//	}
//	
//	public void addGoal(Proposition p, double probability)
//	{
//		if (this.goals.containsKey(p) == false)
//			this.goals.put(p, probability);
//	}
	
	public boolean removeGoal(Proposition p)
	{
		Double rem = this.goals.remove(p);
		if (rem != null)
		{	
			double inc = rem/(this.goals.size()+1);
			for (Fact g : this.goals.keySet())
				this.goals.put(g, this.goals.get(g) + inc);
		}
		
		return rem == null;
	}

	/**
	 * Increments the specified GroundCondition by the specified amount.
	 * @param gc
	 * @param increment
	 */
	@Override
	public void increment(Fact gc, double increment)
	{
		double num = this.goals.get(gc) + increment;
		this.setProbability(gc, num);
	}

	/**
	 * Decrements the specified GroundCondition by the specified amount.
	 * @param gc
	 * @param increment
	 */
	@Override
	public void decrement(Fact gc, double decrement)
	{
		double num = this.goals.get(gc) - decrement;
		this.setProbability(gc, num);
	}

	/**
	 * Multiplies the specified GroundCondition by the specified amount.
	 * @param gc
	 * @param factor
	 */
	@Override
	public void multiply(Fact gc, double factor)
	{
		this.setProbability(gc, this.goals.get(gc) * factor);
	}
	
	private boolean checkGoalsForAtomicity(Collection<Fact> goals)
	{
		for (Fact gc : goals)
			if (gc instanceof Proposition == false)
				return false;
		
		return true;
	}
	
	@Override
	public String toString()
	{
		StringBuffer strBuf = new StringBuffer();
		strBuf.append("Independent goal space: \n");
		for (Entry<Fact, Double> gp : goals.entrySet())
		{
			strBuf.append(gp.getKey()+" : "+gp.getValue()+"\n");
		}
		
		return strBuf.toString();
	}

	@Override
	public MutexSpace getMutexSpace()
	{
		return EMPTY_MUTEX_SET;
	}

	/**
	 * Stub method. Mutexes are not used in an IndependentGoalSpace.
	 */
	@Override
	public void setMutexes(MutexSpace ms)
	{
		
	}

	@Override
	public boolean removeGoal(Fact g)
	{
		return this.goals.remove(g) != null;
	}

	@Override
	public boolean addGoal(Fact g, double prob)
	{
		this.validateUpdate(g, prob);
		
		return this.goals.put(g, prob) == null;
	}
}
