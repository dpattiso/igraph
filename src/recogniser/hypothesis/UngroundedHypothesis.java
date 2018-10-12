package recogniser.hypothesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javaff.data.GroundFact;
import javaff.data.Parameter;
import javaff.data.strips.Predicate;
import javaff.data.strips.PredicateSymbol;
import javaff.data.strips.Proposition;
import recogniser.learning.agent.IAgent;

public class UngroundedHypothesis implements IGoalHypothesis
{
	private IAgent agent;
	private Predicate predicate;
	private List<Proposition> groundedPossibilities;
	private List<Set<Parameter>> parameters;
	
	public UngroundedHypothesis(Collection<Proposition> possibilities, IAgent agent)
	{
		verify(groundedPossibilities);
		
		this.agent = agent;
		this.groundedPossibilities = new ArrayList<Proposition>(possibilities);
		
		
		this.predicate = new Predicate(this.groundedPossibilities.get(0).getPredicateSymbol());
		this.parameters = new ArrayList<Set<Parameter>>();
		
		for (int i = 0; i < this.groundedPossibilities.get(0).getParameters().size(); i++)
		{
			HashSet<Parameter> paramsi = new HashSet<Parameter>();
			for (Proposition p : this.groundedPossibilities)
			{
				paramsi.add(p.getParameters().get(i));
			}
			
			this.parameters.add(paramsi);
		}
	}
	
	private void verify(Collection<Proposition> pos)
	{
		if (pos.size() < 1)
			throw new IllegalArgumentException("Empty list provided, must be >= 1");
			
		Proposition first = pos.iterator().next();
		PredicateSymbol sym = first.getPredicateSymbol();
		int paramCount = first.getParameters().size();
		for (Proposition gc : pos)
		{
			if (gc.getPredicateSymbol().equals(sym) == false)
				throw new IllegalArgumentException("All grounded propositions my have same predicate symbol");
			if (gc.getParameters().size() != paramCount)
				throw new IllegalArgumentException("All grounded propositions my have same number of parameters");
			
		}
		
	}
	
	@Override
	public String toString()
	{
		StringBuffer str = new StringBuffer();
		str.append("UngroundedHypothesis:\n");
		str.append(this.predicate);
		str.append(this.groundedPossibilities.toString());

		
		return str.toString();
	}

	@Override
	public IAgent getAgent()
	{
		return agent;
	}

	@Override
	public GroundFact getGoals()
	{
		return null;
	}

	@Override
	public double getProbability()
	{
		return 0;
	}

	@Override
	public void setProbability(double probability)
	{
		
	}
	
	@Override
	public Object clone()
	{
		return new UngroundedHypothesis(
				new ArrayList<Proposition>(
						this.groundedPossibilities), 
						agent);
	}

	@Override
	public void setGoals(GroundFact goals)
	{
		// TODO Auto-generated method stub
		
	}
}
