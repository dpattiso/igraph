package recogniser.hypothesis;

import recogniser.learning.agent.IAgent;
import javaff.data.GroundFact;

/**
 * Represents an agent's goal and associated probability.
 * 
 * @author pattison
 *
 */
public interface IGoalHypothesis extends Cloneable
{
	/**
	 * Gets the agent associated with this goal.
	 * @return
	 */
	public IAgent getAgent();	
	
	/**
	 * Gets the goal.
	 * @return
	 */
	public GroundFact getGoals();

	/**
	 * Gets the probability of this goal being true.
	 * @return
	 */
	public double getProbability();

	/**
	 * Sets the probability of this goal being true.
	 * @param probability
	 */
	public void setProbability(double probability);
	
	/**
	 * Sets the goals
	 * @return
	 */
	public void setGoals(GroundFact goals);
	
	/**
	 * Clone this hypothesis;
	 * @return
	 */
	public Object clone();
}