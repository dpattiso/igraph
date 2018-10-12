package recogniser.hypothesis;

import javaff.data.strips.Not;
import javaff.data.strips.NullFact;
import javaff.data.strips.PredicateSymbol;

/**
 * This is a goal which encapsulates the negation of multiple other goals. In theory 
 * it is a multivalued {@link Not}, but even Nots have attributes which can be modified.
 * 
 * @author David Pattison
 *
 */
public class AllFalseGoal extends NullFact
{

	/**
	 * Create an AllFalseGoal with the specified name. This is the only identifier associated
	 * with the object, so it should be unique.
	 * @param name
	 */
	public AllFalseGoal(String name)
	{
		super(new PredicateSymbol(name));
	}
	
	@Override
	public Object clone()
	{
		return new AllFalseGoal(super.getPredicateSymbol().getName());
	}
	
	@Override
	public boolean equals(Object obj)
	{
		return (obj instanceof AllFalseGoal) && super.equals(obj);
	}

	
	
	@Override
	public int hashCode()
	{
		return super.hashCode();
	}
	
	@Override
	public String toString()
	{
		return super.getPredicateSymbol().toString();
	}
	
	@Override
	public String toStringTyped()
	{
		return super.getPredicateSymbol().toStringTyped();
	}
}