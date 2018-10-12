package recogniser.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import javaff.data.Action;
import javaff.data.CompoundLiteral;
import javaff.data.Fact;
import javaff.data.strips.Proposition;
import javaff.data.strips.SingleLiteral;
import javaff.planning.STRIPSState;
import javaff.planning.State;

/**
 * Represents the a state space over time. Histories are stored in a TreeSet which is sorted based on 
 * each tuple's getTime() attribute.
 * 
 * @author David Pattison
 *
 */
public class StateHistory implements Iterable<StateHistoryTuple>
{
	private TreeSet<StateHistoryTuple> history;
	private STRIPSState unionState;
	
	/**
	 * Creates an empty history.
	 */
	public StateHistory()
	{
		this.history = new TreeSet<StateHistoryTuple>(new HistoryComparator());
		this.unionState = new STRIPSState(null, new HashSet(), null);
	}
	
	@Override
	public Iterator<StateHistoryTuple> iterator()
	{
		return this.history.iterator();
	}
	
	public StateHistoryTuple first()
	{
		return this.states().first();
	}
	
	public StateHistoryTuple last()
	{
		return this.states().last();
	}

	/**
	 * Determines whether a set of facts has been true at any point during execution.
	 * @param p
	 * @return
	 */
	public boolean haveFactsBeenTrue(Fact p)
	{
		if (p instanceof Proposition)
			return this.unionState.getTrueFacts().contains(p); 
		
		for (StateHistoryTuple tup : history)
		{
			if (p instanceof SingleLiteral && tup.state.getTrueFacts().contains(p))
				return true;
			else if (p instanceof CompoundLiteral && tup.state.getTrueFacts().containsAll(((CompoundLiteral)p).getFacts()))
				return true;
		}
		
		return false;
	}
	
	/**
	 * Determines whether a set of facts have become true, then false at any point during execution.
	 * @param p
	 * @return
	 */
	public boolean haveFactsBeenFalse(Fact p)
	{
		boolean seenAsTrue = false;
		
		for (StateHistoryTuple tup : history)
		{
			if (p instanceof SingleLiteral && tup.state.getTrueFacts().contains(p))
				seenAsTrue = true;
			else if (p instanceof CompoundLiteral && tup.state.getTrueFacts().containsAll(((CompoundLiteral)p).getFacts()))
				seenAsTrue = true;
			
			if (seenAsTrue)
			{
				if (p instanceof SingleLiteral && tup.state.getTrueFacts().contains(p) == false)
					return true;
				else if (p instanceof CompoundLiteral && tup.state.getTrueFacts().containsAll(((CompoundLiteral)p).getFacts()) == false)
					return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Determines whether a set of facts have been true throughout all past states.
	 * @param p
	 * @return
	 */
	public boolean haveFactsChanged(Fact p)
	{
		for (StateHistoryTuple tup : history)
		{
			if (tup.state.getTrueFacts().containsAll(p.getFacts()) == false)
				return true;
		}
		
		return false;
	}
	
	/**
	 * Determines whether a set of facts have been true throughout all states beginning at the specified state index.
	 * @param p
	 * @param sinceStepNumber The step number to begin at (zero-indexed).
	 * @return
	 */
	public boolean haveFactsChanged(Fact p, int sinceStepNumber)
	{
		int count = 0;
		for (StateHistoryTuple tup : history)
		{
			if (count >= sinceStepNumber)
			{
				if (tup.state.getTrueFacts().containsAll(p.getFacts()) == false)
					return true;
			}

			count++;
		}
		
		return false;
	}

	public void add(STRIPSState s, Action a, long time, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.history.add(new StateHistoryTuple(s, time, a, factsNearer, factsFurther, factsUnmoved));
		
		if (s instanceof STRIPSState)
			this.unionState.getTrueFacts().addAll(s.getTrueFacts());
	}
	
	public void add(STRIPSState s, long time, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.history.add(new StateHistoryTuple(s, time, factsNearer, factsFurther, factsUnmoved));

		if (s instanceof STRIPSState)
			this.unionState.getTrueFacts().addAll(s.getTrueFacts());
	}
	
	public void add(STRIPSState s, Action a, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.history.add(new StateHistoryTuple(s, a, factsNearer, factsFurther, factsUnmoved));

		if (s instanceof STRIPSState)
			this.unionState.getTrueFacts().addAll(s.getTrueFacts());
	}	
	
	public StateHistoryTuple get(final int index)
	{
		Iterator<StateHistoryTuple> iter = this.history.iterator();
		int count = 0;
		StateHistoryTuple toRemove = null;
		while (iter.hasNext())
		{
			StateHistoryTuple t = iter.next();
			if (count == index)
			{
				toRemove = t;
				break;
			}
			count++;
		}	
		
		return toRemove;
	}
	
	public boolean remove(int index)
	{
		Iterator<StateHistoryTuple> iter = this.history.iterator();
		int count = 0;
		StateHistoryTuple toRemove = null;
		while (iter.hasNext())
		{
			StateHistoryTuple t = iter.next();
			if (count == index)
			{
				toRemove = t;
				break;
			}
			count++;
		}	
		
		if (toRemove == null)
			return false;
		else
			return this.history.remove(toRemove);
	}
	
	public boolean remove(State s)
	{
		Iterator<StateHistoryTuple> iter = this.history.iterator();
		int count = 0;
		StateHistoryTuple toRemove = null;
		while (iter.hasNext())
		{
			StateHistoryTuple t = iter.next();
			if (t.state.equals(s))
			{
				toRemove = t;
				break;
			}
		}	
		
		if (toRemove == null)
			return false;
		else
			return this.history.remove(toRemove);
	}	
	
	public boolean remove(StateHistoryTuple t)
	{
		return this.history.remove(t);
	}
	
	
	/**
	 * Simple comparator which sorts StateHistoryTuples ascendingly based on the time they were true. 
	 * @author David Pattison
	 *
	 */
	public class HistoryComparator implements Comparator<StateHistoryTuple>
	{
		@Override
		public int compare(StateHistoryTuple a, StateHistoryTuple b)
		{
			if (a.getTime() < b.getTime())
				return -1;
			else if (a.getTime() > b.getTime())
				return +1;
			else
				return 0;
		}
	}
	
	/**
	 * Returns all the states in this history, ordered by observation time.
	 * @return
	 */
	public TreeSet<StateHistoryTuple> states()
	{
		return history;
	}

	/**
	 * Returns this state tuple which is closest to the specified time. Note that this method will always "round down"
	 * to the nearest known state, ie, if the parameter's time lies between state i and i+1, state i will always be 
	 * returned.
	 * @param time
	 * @return The tuple associated with time t
	 */
	public StateHistoryTuple getStateAt(long time)
	{
		if (this.states().size() < 2)
		{
			if (this.states().first().getTime() == time)
				return this.states().first();
			else
				return null;
		}
		
		Iterator<StateHistoryTuple> it = this.states().iterator();
		StateHistoryTuple prev = it.next();
		while (it.hasNext())
		{
			StateHistoryTuple next = it.next();
			if (time >= prev.getTime() && time < next.getTime())
				return prev;

			prev = next;
		}
		
		return null;
	}
//	
//	@Override
//	public String toString()
//	{
//		// TODO Auto-generated method stub
//		return super.toString();
//	}
}
