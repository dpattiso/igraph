package recogniser.hypothesis;

import javaff.data.GroundFact;
import recogniser.learning.agent.IAgent;

/**
 * A hypothesis which is expected to be true in n steps.
 * @author David Pattison
 *
 */
public class BoundedGoalHypothesis implements IGoalHypothesis, Comparable<BoundedGoalHypothesis>
{
	private IGoalHypothesis hyp;
	private double creationTime, boundTime;

	public BoundedGoalHypothesis(IGoalHypothesis hyp, double creationTime, double bound)
	{
		this.hyp = hyp;
		this.boundTime = bound;
		this.creationTime = creationTime;
	}
	
	public double getCreationTime() {
		return creationTime;
	}

	public void setCreationTime(int creationTime) {
		this.creationTime = creationTime;
	}

	public double getBoundTime() {
		return boundTime;
	}

	public void setBoundTime(int boundTime) {
		this.boundTime = boundTime;
	}

	public IAgent getAgent() {
		return hyp.getAgent();
	}

	public GroundFact getGoals() {
		return hyp.getGoals();
	}

	public double getProbability() {
		return hyp.getProbability();
	}

	public void setProbability(double probability) {
		hyp.setProbability(probability);
	}

	public void setGoals(GroundFact goals) {
		hyp.setGoals(goals);
	}

	public Object clone() 
	{
		return new BoundedGoalHypothesis((IGoalHypothesis) this.hyp.clone(), this.creationTime, this.boundTime);
	}
	
	public double getTargetTime()
	{
		return this.creationTime + this.boundTime;
	}
	
	@Override
	public String toString()
	{
		return "BHyp - C="+this.creationTime+", B"+this.boundTime+", T"+this.getTargetTime()+", "+hyp.toString();
	}

	@Override
	public int compareTo(BoundedGoalHypothesis other)
	{
		if (this.creationTime < other.creationTime)
			return -1;
		else if (this.creationTime > other.creationTime)
			return 1;
		else
		{
			if (this.boundTime < other.boundTime)
				return -1;
			else if (this.boundTime > other.boundTime)
				return 1;
			else
				return 0;
		}
			
	}
}
