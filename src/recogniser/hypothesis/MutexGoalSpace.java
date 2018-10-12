package recogniser.hypothesis;

import java.util.*;
import java.util.Map.Entry;

import javaff.data.Fact;
import javaff.data.MutexSpace;
import javaff.graph.FactMutex;

/**
 * A goal space which recognises completeMutexSpace between goals. Note that no action is taken with this information
 * within the class- Mutex info exists for other classes to make use of.
 * 
 * @author pattison
 *
 */
public class MutexGoalSpace extends IndependentGoalSpace
{
	private MutexSpace mutexMap;

	public MutexGoalSpace()
	{
		super();
	}
	
	public MutexGoalSpace(Collection<Fact> goals, MutexSpace mutexes)
	{
		super(goals);
		
		this.validateGoals(goals, mutexes);
		
		this.mutexMap = mutexes;
	}
	
	/**
	 * Constructs a goal space from the keys of the specified mutex space. The specified mutexspace
	 * then becomes this goal space's mutex space.
	 * @param completeMutexSpace
	 */
	public MutexGoalSpace(MutexSpace mutexes)
	{
		super(mutexes.getKeys());

		this.mutexMap = mutexes;
		
		this.setMutexes(mutexes);
	}
	
//	public MutexGoalSpace(Collection<Fact> groundedProps)
//	{
//		super(groundedProps);
//		
//		this.constructMutexes();
//	}
	
	@Override
	public String toString()
	{
		return "Mutex goal space: "+this.getGoals().toString();
	}
	
	@Override
	public boolean removeGoal(Fact g)
	{
		super.removeGoal(g);
		
		return this.mutexMap.removeMutexes(g);
	}
	
	protected void validateGoals(Collection<Fact> goals, MutexSpace mutexes)
	{
		if (mutexes.getKeys().containsAll(goals) == false)
			throw new NullPointerException("Mutex set does not contain keys for all goals");
	}
	
	/**
	 * Gets all the completeMutexSpace in this goal space.
	 * @return
	 */
	public Map<Fact, FactMutex> getAllMutexes()
	{
		return mutexMap.getMutexMap();
	}
	
	@Override
	public Object clone()
	{
		IndependentGoalSpace igs = (IndependentGoalSpace) super.clone();
		MutexGoalSpace mgs = new MutexGoalSpace();
		mgs.goals = igs.goals;
//		mgs.constructMutexes();
		mgs.mutexMap = (MutexSpace) this.mutexMap.clone();
//		mgs.normalizedProbabilities = (HashMap<GroundCondition, Double>) this.normalizedProbabilities.clone();
		return mgs;
	}	

	/**
	 * Returns the probability of a goal. Note that this will have been normalized with anything the goal is mutex with.
	 */
	@Override
	public double getProbability(Fact gc)
	{
		return super.getProbability(gc);
	}

//	/**
//	 * Sets the facts which are classed as goals. Constructs completeMutexSpace from 
//	 */
	@Override
	public void setGoals(Collection<? extends Fact> goals)
	{
		super.setGoals(goals);
		
//		this.constructMutexes();
	}
	
	/**
	 * Computes the magnitude of a fact mutex's members for use in normalisation.
	 * @param mutex
	 * @return
	 */
	protected double getMagnitude(FactMutex mutex)
	{
		double mag = super.getProbability(mutex.getOwner());
		mag *= mag;
		for (Fact gc : mutex.getOthers())
		{
			Double p = super.getProbability(gc);
			if (p != null)
				mag += p*p;
		}
		
		return (double)Math.sqrt(mag);
	}
	
	public FactMutex getMutexes(Fact gc)
	{
		return this.mutexMap.getMutexes(gc);
	}
	
	/**
	 * Determines whether 2 proposition are mutex.
	 * @param a
	 * @param b
	 * @return
	 * @throws NullPointerException Thrown if the first parameter does not exist in this goal space.
	 */
	public boolean isMutex(Fact a, Fact b)
	{
		return this.mutexMap.getMutexes(a).isMutexWith(b);
	}
	
	@Override
	public MutexSpace getMutexSpace()
	{
		return this.mutexMap;
	}
	
	@Override
	public void setMutexes(MutexSpace ms)
	{
		this.mutexMap = ms;
	}
}
