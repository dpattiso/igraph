package recogniser.util;

import javaff.data.Action;

public class ProbabilisticAction
{
	private Action action;
	private float prob;

	public ProbabilisticAction(Action action, float prob)
	{
		super();
		this.action = action;
		this.prob = prob;
	}
	
	public ProbabilisticAction(Action action)
	{
		super();
		this.action = action;
		this.prob = 0f;
	}
	
	public Action getAction()
	{
		return action;
	}
	
	public void setAction(Action action)
	{
		this.action = action;
	}
	
	public float getProbability()
	{
		return prob;
	}
	
	public void setProbability(float prob)
	{
		this.prob = prob;
	}
}
