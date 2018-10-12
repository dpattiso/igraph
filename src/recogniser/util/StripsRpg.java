package recogniser.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.strips.Proposition;
import javaff.planning.STRIPSState;

/**
 * While JavaFF has an RPG of its own, it is harder to get to grips with than a greased porcupine.
 * 
 * 
 * @author David Pattison
 *
 */
public class StripsRpg
{
	private ArrayList<Set<Fact>> factLayers;
	private ArrayList<Set<Action>> actionLayers;
	private HashSet<Action> actions;
	
	/**
	 * Cache of fact layers for faster lookups.
	 */
	protected HashMap<Fact, Integer> layerLookup;

	public StripsRpg(Set<Action> actions)
	{
		this.actions = new HashSet<Action>(actions);
		this.actionLayers = new ArrayList<Set<Action>>();
		this.factLayers = new ArrayList<Set<Fact>>();
		
		this.layerLookup = new HashMap<>();
	}
	
	/**
	 * Returns a clone of this RPG, containing shallow clones of the internal state. 
	 */
	public Object clone()
	{
		StripsRpg clone = new StripsRpg((Set<Action>) this.actions.clone());
		clone.actionLayers = (ArrayList<Set<Action>>) this.actionLayers.clone();
		clone.factLayers = (ArrayList<Set<Fact>>) this.factLayers.clone();
		clone.layerLookup = (HashMap<Fact, Integer>) this.layerLookup.clone();
		
		return clone;
	}
	
	public void reset()
	{
		this.factLayers.clear();
		this.actionLayers.clear();
		
		this.layerLookup.clear();
	}
	
	/**
	 * Constructs an RPG using the specified initial tmstate until fact layers stabilise.
	 * @param initial
	 * @param goal
	 */
	public void constructFullRPG(STRIPSState initial)
	{
		this.reset();
	
		int lastCount = 0;
		int layer = 0;
	
		HashSet<Action> lastActionSet = new HashSet<Action>();
		STRIPSState currentState = (STRIPSState) initial.clone();
		this.factLayers.add(new HashSet<Fact>(initial.getTrueFacts()));
		
		for (Fact f : initial.getTrueFacts())
		{
			this.layerLookup.put(f, layer);
		}
		
		while (true)
		{
			++layer;
			
			HashSet<Proposition> newFacts = new HashSet<Proposition>();
			HashSet<Action> newActions = new HashSet<Action>();
			for (Action a : this.actions)	
			{
				if (a.isApplicable(currentState))
				{
					for (Fact f : a.getAddPropositions())
					{
						newFacts.add((Proposition) f);
					}
					newActions.add(a);
				}
			}
			currentState.getTrueFacts().addAll(newFacts);
			lastActionSet.addAll(newActions);
			
			if (lastCount == currentState.getTrueFacts().size())
				break;
			else
			{
				this.factLayers.add(new HashSet<Fact>(currentState.getTrueFacts()));
				this.actionLayers.add(new HashSet<Action>(lastActionSet));
				lastCount = currentState.getTrueFacts().size();
				
				for (Fact f : newFacts)
				{
					this.layerLookup.put(f, layer);
				}
			}
		}
	}
	
	/**
	 * Gets the relaxed distance from the start of an RPG to the specified goal set.
	 * @param gc
	 * @return The layer containing all facts, or -1 if they are *all* not found.
	 */
	public int getRelaxedDistance(GroundFact gc)
	{	
		for (int i = 0; i < this.factLayers.size(); i++)
		{
			if (this.getFactsAtLayer(i).containsAll(gc.getFacts()))
				return i;	
		}
		
		return -1;
	}
	

	
	public Set<Fact> getFactsAtLayer(int i)
	{
		return this.factLayers.get(i);
	}
	
	/**
	 * Returns the distance/layer which contains the first instance of the specified proposition.
	 * @param p
	 * @return The distance to the proposition, or -1 if it is not found in any layer.
	 */
	public int getLayerContaining(Proposition p)
	{
		int l = 0;
		for (int i = 0; i < this.factLayers.size(); i++)
		{
			if (this.factLayers.get(i).contains(p))
			{
				l = i;
				break;
			}
		}
		
		int c = this.layerLookup.get(p);
		if (l != c)
			throw new IllegalArgumentException("lookpu not same as cache");
		
		return -1;
	}

	public int getFactLayerCount()
	{
		return this.factLayers.size();
	}
	
	/**
	 * Returns the actions applicable at a layer- note that this is 1-indexed
	 * @param layer
	 * @return
	 */
	public Set<Action> getActionsAtLayer(int layer)
	{
		return this.actionLayers.get(layer-1);
	}
}
