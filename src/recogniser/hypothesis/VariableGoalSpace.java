package recogniser.hypothesis;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javaff.data.Fact;
import javaff.data.MutexSpace;

/**
 * A goal space which encapsulates X other goal-spaces.
 * @author David Pattison
 *
 */
public class VariableGoalSpace implements IGoalSpace //extends MutexGoalSpace
{
	public enum GoalSetSelectorType
	{
		Max,
		Min,
		Average,
	}
		
	private Set<MutexGoalSpace> varGoalSpaces;
	private HashMap<Fact, Set<MutexGoalSpace>> allGoalMutexes;
	private GoalSetSelectorType selector;
	private MutexSpace completeMutexSpace;
	
	private VariableGoalSpace()
	{
		this.varGoalSpaces = new HashSet<MutexGoalSpace>();
		this.selector = GoalSetSelectorType.Max;
		this.allGoalMutexes = new HashMap<Fact, Set<MutexGoalSpace>>();
		this.completeMutexSpace = new MutexSpace();
	}
	
	public VariableGoalSpace(Set<MutexGoalSpace> varSpaces)
	{
		this();
		this.varGoalSpaces = new HashSet<MutexGoalSpace>();
		out: for (MutexGoalSpace mgs : varSpaces) //not interested in any goal spaces which contain axioms
		{
			for (Fact f : mgs.getGoals())
			{
				if (f.toString().contains("axiom"))
					continue out;
			}
			
			this.varGoalSpaces.add(mgs);
		}
		
		this.selector = GoalSetSelectorType.Max;
		
		this.initialiseFields();
	}
	
	/**
	 * Populate the fact->(mutexgoalspaces) lookup table and complete mutex goalspace.
	 */
	protected void initialiseFields()
	{
		this.allGoalMutexes = new HashMap<Fact, Set<MutexGoalSpace>>();
		this.completeMutexSpace.clear();
		for (MutexGoalSpace mgs : this.varGoalSpaces)
		{
			//merge together all completeMutexSpace across multiple variable domains
			//we have to clone because if we don't, other mutex spaces can be
			//disrupted becuase compleMutexSpace is referring to them in merge().
			MutexSpace clonedSpace = (MutexSpace) mgs.getMutexSpace().clone();
			this.completeMutexSpace.merge(clonedSpace);
			
			for (Fact g : mgs.getGoals())
			{
				if (this.allGoalMutexes.containsKey(g) == false)
					this.allGoalMutexes.put(g, new HashSet<MutexGoalSpace>());
				
				this.allGoalMutexes.get(g).add(mgs);
			}
		}
	}
	
	
	@Override
	public int size()
	{
		return this.allGoalMutexes.size();
	}
	
	
	@Override
	public Object clone()
	{
		VariableGoalSpace clone = new VariableGoalSpace();
		clone.selector = this.selector;
		for (MutexGoalSpace gs : this.varGoalSpaces)
		{
			clone.varGoalSpaces.add((MutexGoalSpace) gs.clone());
		}
		clone.initialiseFields();
		
		return clone;
	}
	
	@Override
	public String toString()
	{
		return "Variable Goal Space: "+this.varGoalSpaces.size()+" sub goal-spaces";
	}
	

		



	@Override
	public Collection<? extends Fact> getGoals()
	{
		return this.allGoalMutexes.keySet();
	}



	/**
	 * This method does nothing!!!!
	 */
	@Override
	public void setGoals(Collection<? extends Fact> goals)
	{
		return;
	}


	public void setGoalProbabilitySelector(GoalSetSelectorType t)
	{
		this.selector = t;
	}

	public GoalSetSelectorType getGoalProbabilitySelector()
	{
		return this.selector;
	}
	
	
	/**
	 * Returns the probability of the fact, given the selection type specified 
	 * by setSetSelector().
	 */
	@Override
	public double getProbability(Fact g)
	{
		double p;
		if (this.selector == GoalSetSelectorType.Max)
		{
			p = this.getMaxGoalProbability(g);
		}
		else if (this.selector == GoalSetSelectorType.Min)
		{
			p = this.getMinGoalProbability(g);
		}
		else //Average
		{
			p = this.getAverageGoalProbability(g);
		}
		
		return p;
	}


	public Set<MutexGoalSpace> getMemberGoalSpaces(Fact g)
	{
		return this.allGoalMutexes.get(g);
	}

	protected double getAverageGoalProbability(Fact g)
	{
		double avg = 0;
		for (MutexGoalSpace gs : this.allGoalMutexes.get(g))
		{
			double p = gs.getProbability(g);
			avg += p;
		}
		
		avg = avg / ((double) this.allGoalMutexes.get(g).size());
		
		return avg;
	}

	protected double getMinGoalProbability(Fact g)
	{
		double min = Double.MAX_VALUE;
		for (MutexGoalSpace gs : this.allGoalMutexes.get(g))
		{
			double p = gs.getProbability(g);
			if (p < min)
				min = p;
		}
		
		return min;
	}

	protected double getMaxGoalProbability(Fact g)
	{
		double max = Double.MIN_VALUE;
		for (MutexGoalSpace gs : this.allGoalMutexes.get(g))
		{
			double p = gs.getProbability(g);
			if (p > max)
				max = p;
		}
		
		return max;
	}

	/**
	 * Warning, this directly modifies the probaiblity of the goal across ALL sets which it appears in.
	 */
	@Override
	public void setProbability(Fact gc, double probability)
	{
		for (MutexGoalSpace gs : this.allGoalMutexes.get(gc))
		{
			gs.setProbability(gc, probability);
		}
	}




	@Override
	public void increment(Fact gc, double increment)
	{
		for (MutexGoalSpace gs : this.allGoalMutexes.get(gc))
		{
			gs.increment(gc, increment);
		}
	}




	@Override
	public void decrement(Fact gc, double decrement)
	{
		for (MutexGoalSpace gs : this.allGoalMutexes.get(gc))
		{
			gs.decrement(gc, decrement);
		}
	}




	@Override
	public void multiply(Fact gc, double factor)
	{
		for (MutexGoalSpace gs : this.allGoalMutexes.get(gc))
		{
			gs.multiply(gc, factor);
		}
	}



	/**
	 * NOT IMPLEMENTED. Use setVarGoalSpaces() instead.
	 */
	@Override
	public void setMutexes(MutexSpace ms)
	{
		return;
	}



	/**
	 * Returns the union of all mutex spaces across all goal-spaces associated with variables.
	 */
	@Override
	public MutexSpace getMutexSpace()
	{
		return this.completeMutexSpace;
	}



	@Override
	public boolean removeGoal(Fact g)
	{
		Set<MutexGoalSpace> spaces = this.allGoalMutexes.get(g);
		if (spaces == null)
			return true;
		
		boolean wasRemoved = false;
		for (MutexGoalSpace gs : spaces)
		{
			boolean res = gs.removeGoal(g);
			if (res)
				wasRemoved = true;
		}
		
		this.completeMutexSpace.removeMutexes(g);
		
		return wasRemoved;
	}




	@Override
	public boolean addGoal(Fact g, double probability)
	{
		for (MutexGoalSpace gs : this.allGoalMutexes.get(g))
		{
			gs.addGoal(g, probability);
		}
		
		return true;
	}




	@Override
	public void reset()
	{
		for (MutexGoalSpace gs : this.varGoalSpaces)
		{
			gs.reset();
		}
		
	}

	/**
	 * Return the sub-goal-spaces which make up this abstracted goal space
	 * @return
	 */
	public Set<MutexGoalSpace> getVariableGoalSpaces()
	{
		return varGoalSpaces;
	}

	public void setVarGoalSpaces(Set<MutexGoalSpace> varGoalSpaces)
	{
		this.varGoalSpaces = varGoalSpaces;
		this.initialiseFields();
	}
}
