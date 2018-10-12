package recogniser.util;

import java.util.Collection;
import java.util.HashSet;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.planning.STRIPSState;

/**
 * Represents the time a state and the action which was applied in order to achieve the state.
 *  
 * @author David Pattison
 *
 */
public class StateHistoryTuple
{
	private long time;
	STRIPSState state;
	private Action prevAction;
	private Collection<Fact> nearer, further, unmoved; //farther, further, who knows? this is a grammatical minefield! SAVE YOURSELF!!!!!!!!!!!
	
	/**
	 * Constructs a state history with no previous action and a time of System.currentTimeMillis().
	 * @param s The successor state.
	 */
	public StateHistoryTuple(STRIPSState s, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.time = System.currentTimeMillis();
		this.state = s;
		this.prevAction = null;
		
		this.nearer = factsNearer; //new HashSet<GroundCondition>();
		this.further = factsFurther; //new HashSet<GroundCondition>();
		this.unmoved = factsUnmoved; //new HashSet<GroundCondition>();
	}	

	/**
	 * Constructs a state history with no previous action and a time of System.currentTimeMillis().
	 * @param s The state.
	 * @param time The time the state was true.
	 */
	public StateHistoryTuple(STRIPSState s, long time, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.time = time;
		this.state = s;
		this.prevAction = null;
		
		this.nearer = factsNearer; //new HashSet<GroundCondition>();
		this.further = factsFurther; //new HashSet<GroundCondition>();
		this.unmoved = factsUnmoved; //new HashSet<GroundCondition>();
	}

	/**
	 * Constructs a state history with no previous action and a time of System.currentTimeMillis().
	 * @param s The state.
	 * @param previousAction The action which was applied to achieve the state.
	 */	
	public StateHistoryTuple(STRIPSState s, Action previousAction, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.time = System.currentTimeMillis();
		this.state = s;
		this.prevAction = previousAction;
		
		this.nearer = factsNearer; //new HashSet<GroundCondition>();
		this.further = factsFurther; //new HashSet<GroundCondition>();
		this.unmoved = factsUnmoved; //new HashSet<GroundCondition>();
	}


	/**
	 * Constructs a state history with no previous action and a time of System.currentTimeMillis().
	 * @param s The state.
	 * @param time The time the state was true.
	 * @param previousAction The action which was applied to achieve the state.
	 */
	public StateHistoryTuple(STRIPSState s, long time, Action previousAction, Collection<Fact> factsNearer, Collection<Fact> factsFurther, Collection<Fact> factsUnmoved)
	{
		this.time = time;
		this.state = s;
		this.prevAction = previousAction;
		
		this.nearer = factsNearer; //new HashSet<GroundCondition>();
		this.further = factsFurther; //new HashSet<GroundCondition>();
		this.unmoved = factsUnmoved; //new HashSet<GroundCondition>();
	}


	public long getTime()
	{
		return time;
	}

	public void setTime(long time)
	{
		this.time = time;
	}

	public STRIPSState getState()
	{
		return state;
	}

	public void setState(STRIPSState state)
	{
		this.state = state;
	}

	public Action getPreviousAction()
	{
		return prevAction;
	}

	public void setPreviousAction(Action prevAction)
	{
		this.prevAction = prevAction;
	}
	
	@Override
	public String toString()
	{
		return this.time+": "+this.prevAction+" "+this.state+"\n";
	}

	public Collection<Fact> getNearer()
	{
		return nearer;
	}

	public void setNearer(HashSet<Fact> nearer)
	{
		this.nearer = nearer;
	}

	public Collection<Fact> getFurther()
	{
		return further;
	}

	public void setFurther(HashSet<Fact> further)
	{
		this.further = further;
	}

	public Collection<Fact> getUnmoved()
	{
		return unmoved;
	}

	public void setUnmoved(HashSet<Fact> unmoved)
	{
		this.unmoved = unmoved;
	}
}