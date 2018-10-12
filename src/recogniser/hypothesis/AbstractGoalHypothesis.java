package recogniser.hypothesis;

import javaff.data.GroundFact;
import javaff.data.strips.And;
import recogniser.learning.agent.IAgent;

public abstract class AbstractGoalHypothesis implements IGoalHypothesis
{
//	protected int creationTime, targetTime;
	protected IAgent agent;
	protected GroundFact goal;
	protected double probability;

	public AbstractGoalHypothesis()
	{
		this.agent = null;
		this.goal = new And();
		this.probability = 0;
	}
	
	public AbstractGoalHypothesis(IAgent agent, GroundFact goal, double probability)
	{
		this();
//		this.creationTime = creationTime;
//		this.targetTime = targetTime;
		this.agent = agent;
		this.goal = goal;
		this.probability = probability;
	}
	
	@Override
	public GroundFact getGoals()
	{
		return goal;
	}

	@Override
	public void setGoals(GroundFact goal)
	{
		this.goal = goal;
	}

	@Override
	public double getProbability()
	{
		return probability;
	}

	@Override
	public void setProbability(double probability)
	{
		this.probability = probability;
	}

	@Override
	public IAgent getAgent()
	{
		return agent;
	}

	public void setAgent(IAgent agent)
	{
		this.agent = agent;
	}

//	@Override
//	public int getCreationTime()
//	{
//		return creationTime;
//	}
//
//	@Override
//	public int getTargetTime()
//	{
//		return targetTime;
//	}

	@Override
	public abstract Object clone();
}