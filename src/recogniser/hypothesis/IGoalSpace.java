package recogniser.hypothesis;

import java.util.Collection;

import javaff.data.Fact;
import javaff.data.MutexSpace;

/**
 * Represents an arrangement of goals (either individual or groups) which can be achieved.
 * @author David Pattison
 *
 */
public interface IGoalSpace
{
	/**
	 * Return all goals inside this goal-space.
	 * @return
	 */
	public Collection<? extends Fact> getGoals();

	/**
	 * Set the goals in this goal-space.
	 * @param goals
	 */
	public void setGoals(Collection<? extends Fact> goals);
	
	/**
	 * Get the current probability associated with a goal.
	 * @param gc The goal
	 * @return The probability of the goal
	 */
	public double getProbability(Fact gc);

	/**
	 * Set the probability of a goal.
	 * @param gc The goal.
	 * @param probability The probability of the goal.
	 */
	public void setProbability(Fact gc, double probability);
	
	/**
	 * Increment the probability of a goal by a fixed amount.
	 * @param gc The goal.
	 * @param increment The amount to increment the goals probability by.
	 */
	public void increment(Fact gc, double increment);

	/**
	 * Decrement the probability of a goal by a fixed amount.
	 * @param gc The goal.
	 * @param derement The amount to decrement the goals probability by.
	 */
	public void decrement(Fact gc, double decrement);
	
	/**
	 * Multiply the probability of a goal by a fixed amount.
	 * @param gc The goal.
	 * @param factor The amount to multiply the goals probability by.
	 */
	public void multiply(Fact gc, double factor);

	/**
	 * Create a deep-copy of this goal-space.
	 * @return A new instance of this goal-space which is identical.
	 */
	public Object clone();
	
	/**
	 * Set the mutex-space associated with this goal-space.
	 * @param ms The new mutex space.
	 */
	public void setMutexes(MutexSpace ms);
	
	/**
	 * Get the mutex-space associated with this goal-space.
	 * @return The mutex-space.
	 */
	public MutexSpace getMutexSpace();
	
	/**
	 * Remove the specified goal from this goal-space and all internal structures.
	 * @param g The goal to remove.
	 * @return True if this goal-space changed as a result of this operation, false otherwise.
	 */
	public boolean removeGoal(Fact g);
	
	/**
	 * Add the specified goal from this goal-space and all internal structures.
	 * @param g The goal to add.
	 * @return True if this goal-space changed as a result of this operation, false otherwise.
	 */
	public boolean addGoal(Fact g, double probability);
	
	/**
	 * Get the number of goals stored by this goal-space
	 * @return The size of the goal-space
	 */
	public int size();
	
	/**
	 * Sets all probabilities to zero. Does not remove goals.
	 */
	public void reset();
}
