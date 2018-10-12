package recogniser.hypothesis;

import java.util.Collection;
import java.util.HashSet;
import recogniser.learning.agent.IAgent;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.strips.And;

public class ConjunctiveGoalHypothesis extends AbstractGoalHypothesis //implements IGoalHypothesis
{
//	private IAgent agent;
//	private And goal;
//	private double probability;
	
	public ConjunctiveGoalHypothesis()
	{
	}

	public ConjunctiveGoalHypothesis(And goal, double probability)
	{
		this.goal = goal;
		this.probability = probability;
	}
	
	public ConjunctiveGoalHypothesis(Collection<? extends Fact> goals, double probability)
	{
		HashSet<Fact> lits = new HashSet<Fact>(goals);
//		for (SingleLiteral gc : goals)
//			lits.addAll(gc.getConditionalPropositions());
		
		this.goal = new And(lits);
		this.probability = probability;
	}
	
	@Override
	public String toString()
	{
		return "Hypothesis P("+this.probability+") = "+this.goal.toString();
	}
	
	@Override
	public And getGoals() 
	{
		return (And) super.getGoals();
	}
	
	@Override
	public void setGoals(GroundFact goal) 
	{	
		super.setGoals(new And(goal));
	}
	
	@Override
	public Object clone()
	{
		And andClone = (And) this.getGoals().clone();
		
		return new ConjunctiveGoalHypothesis(andClone, this.probability);
	}
}
