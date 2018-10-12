package recogniser.search;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import recogniser.util.StripsRpg;
import javaff.planning.STRIPSState;
import javaff.search.UnreachableGoalException;

/**
 * Encapsulation of the the h_max heuristic. Uses a {@StripsRpg} for faster access. 
 * 
 * @author David Pattison
 *
 */
public class MaxHeuristic extends AbstractHeuristic
{
	private StripsRpg rpg; //TODO now that JavaFF's RPG is a bit better understood, deprecate StripsRpg

	public MaxHeuristic()
	{
		this.rpg = null;
	}
	
	public MaxHeuristic(StripsRpg rpg)
	{
		this.rpg = rpg;
	}
	
	/**
	 * Constructs a deep copy of the max heuristic and associated RPG.
	 */
	@Override
	public Object clone()
	{
		return new MaxHeuristic((StripsRpg) this.rpg.clone());
	}

	/**
	 * Computes the hmax estimate for the specified goal. This is the minimal number of actions required to 
	 * achieve the fact (if it is conjunctive) which is the maximum distance from the current state. 
	 */
	@Override
	public double computeEstimate(Fact gc) throws UnreachableGoalException
	{
		//we actually make use of the RPG here, as hmax(G) == min_rpg_layer(G). Therefore the distance to 
		//G (even if conjunctive) is just the first layer of the RPG at which all facts appear, as this is the minimal
		//distance to the maximum-distance goal in G.
		int hmax = this.rpg.getRelaxedDistance((GroundFact) gc);
		
		if (hmax < 0)
			throw new UnreachableGoalException(gc, gc + " is unreachable");
		
		return hmax;
	}
	
	public StripsRpg getRpg()
	{
		return rpg;
	}

	public void setRpg(StripsRpg rpg)
	{
		this.rpg = rpg;
	}
	
}
