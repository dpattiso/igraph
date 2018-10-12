
package recogniser.hypothesis;

import recogniser.learning.agent.IAgent;
import recogniser.learning.agent.NullAgent;
import javaff.data.GroundFact;
import javaff.data.strips.Proposition;

public class SingleGoalHypothesis extends AbstractGoalHypothesis //implements IGoalHypothesis
{	
	public SingleGoalHypothesis()
	{
		super();
	}
	
	public SingleGoalHypothesis(Proposition goal, IAgent a, double probability)
	{
		super(a, goal, probability);
	}
	
	@Override
	public boolean equals(Object obj) 
	{
		SingleGoalHypothesis other = (SingleGoalHypothesis) obj;
		
	
		return super.equals(other);
	}

	@Override
	public Object clone()
	{
		return new SingleGoalHypothesis((Proposition) this.goal.clone(), this.agent, this.probability);
	}
}
