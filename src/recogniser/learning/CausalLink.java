package recogniser.learning;

import java.util.ArrayList;
import java.util.List;

import javaff.data.Action;
import javaff.data.strips.And;
import javaff.data.strips.Proposition;

/**
 * A series of causal links between actions. Actions are verified as being relevant when they are added
 * into the chain. A link between two adjacent actions is valid if any of the effects of action i 
 * achieves any of the preconditions of action i+1.
 * 
 * @author pattison
 */
public class CausalLink
{
	private List<Action> actions;
	private And effect; //TODO add in effect code
	
	/**
	 * Create an empty causal link.
	 */
	public CausalLink()
	{
		this.actions = new ArrayList<Action>();
	}
	
	/**
	 * Creates a causal link containing a list of ordered actions. This list is verified on creation.
	 * @param actions
	 */
	public CausalLink(List<Action> actions)
	{
		this.actions = new ArrayList<Action>();
		this.setActions(actions);
	}
	
	/**
	 * Adds an action to the end of the link. Links are verified before insertion.
	 * @param a
	 */
	public void addAction(Action a)
	{
		this.addAction(a, this.actions.size());
	}
	
	/**
	 * Adds an action to the chain at the specified position. Links are verified before insertion.
	 * @param a
	 * @param position
	 * @throws IndexOutOfBoundsException Thrown if position is illegal.
	 * @throws IllegalArgumentException Thrown if the action which is to be insered would result in an 
	 * illegal causal link.
	 */
	public void addAction(Action a, int position) throws IndexOutOfBoundsException, IllegalArgumentException
	{
		if (position < 0 || position > this.actions.size())
			throw new IndexOutOfBoundsException("Position is out of bounds");
		
		if (this.verifyActionInsertion(a, position) == false)
			throw new IllegalArgumentException("Causal Link is not valid");
		
		this.actions.add(position, a);
	}
	
	private boolean verifyActionInsertion(Action a, int position)
	{
		if (position == 0)
			return true;
		else if (position == actions.size())
		{
			Action prev = this.actions.get(position-1);
			return this.checkActionPair(prev, a);
		}
		else
		{
			Action prev = this.actions.get(position-1);
			Action next = this.actions.get(position);
			boolean foundPrev = this.checkActionPair(prev, a);
			boolean foundNext = this.checkActionPair(a, next);
			//System.out.println("prev and next AND == "+(foundPrev&foundNext));
			return foundPrev & foundNext;
		}
	}
	
	private boolean checkActionPair(Action first, Action second)
	{
//		System.out.println("checking "+first+" and "+second);
		for (Object pco : second.getPreconditions())
		{
			Proposition pc  = (Proposition)pco;
			if (first.getAddPropositions().contains(pc))// || first.getDeletePropositions().contains(pc))
			{
				return true;
			}
		}
//		System.out.println("found no link between "+first+" and "+second);
		
		return false;
	}
	
	public int size()
	{
		return this.actions.size();
	}

	public List<Action> getActions()
	{
		return actions;
	}

	public void setActions(List<Action> a)
	{
		if (a.size() > 1)
		{
			for (int i = 0; i < a.size() - 1; i++)
			{
				if (this.checkActionPair(a.get(i), a.get(i+1)) == false)
					throw new IllegalArgumentException("Chain of actions is illegal- no causal link present between " +
							a.get(i) + " and "+a.get(i+1));
			}
		}
		this.actions = new ArrayList<Action>(a);
	}
	
	public Action head()
	{
		if (this.actions.size() == 0)
			return null;
		else 
			return this.actions.get(0);
	}
	
	public Action tail()
	{
		if (this.actions.size() == 0)
			return null;
		else
			return this.actions.get(this.actions.size() - 1);
	}
	
//	public MacroAction toMacro()
//	{
//		return new MacroAction(this.actions);
//	}

	@Override
	public String toString()
	{
		return "CL: "+actions;
	}
}