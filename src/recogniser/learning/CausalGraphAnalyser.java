package recogniser.learning;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.traverse.BreadthFirstIterator;

import javaff.data.Fact;

import sas.data.CausalGraph;
import sas.data.DomainTransitionGraph;
import sas.data.SASLiteral;
import sas.data.SASVariable;
import sas.util.CausalGraphLink;

/**
 * Analyses a causal graph to determine the layers which each DTG lies on. Leaves are classed as layer 1
 * whle roots are layer N, where 1 < N < |CG.vertices()|
 * @author David Pattison
 *
 */
public class CausalGraphAnalyser
{
	private CausalGraph cg;
	private Map<DomainTransitionGraph, Integer> levels;
	private boolean hasLayers;
	private int uniqueLayers;
	
	protected CausalGraphAnalyser()
	{
		this.cg = null;
		this.levels = new HashMap<DomainTransitionGraph, Integer>();
		this.hasLayers = false;
		this.uniqueLayers = 0;
	}
	
	/**
	 * Analyses the specified CG.
	 * @param cg
	 */
	public CausalGraphAnalyser(CausalGraph cg)
	{
		this();
		this.cg = cg;
		
		this.analyse();
	}
	
	
	private void analyse()
	{
		//first, populate the table with everything having the same layer
		for (DomainTransitionGraph dtg : this.cg.vertexSet())
		{
			this.levels.put(dtg, 1);
		}
		
		//if there are no leaves or roots, just exit
		if (this.cg.hasLeaves() == false && this.cg.hasRoots() == false)
		{
			this.hasLayers = false;
			this.uniqueLayers = 1;
			return;
		}
		this.hasLayers = true;
		
		HashSet<DomainTransitionGraph> closed = new HashSet<DomainTransitionGraph>();
		Queue<DomainTransitionGraph> queue = new LinkedList<DomainTransitionGraph>();
//		this.cg.generateDotGraph(new java.io.File("/tmp/cg.dot"));
		//prefer leaves because they have a better start value of 1 - easier to measure
		if (this.cg.hasLeaves())
		{
			for (DomainTransitionGraph dtg : this.cg.getLeaves())
			{
				for (DomainTransitionGraph other : this.cg.getDTGs())
				{
					DijkstraShortestPath<DomainTransitionGraph, CausalGraphLink> dij = new DijkstraShortestPath<DomainTransitionGraph, CausalGraphLink>(this.cg, other, dtg);
					List<CausalGraphLink> path = dij.getPathEdgeList();
					if (path == null)
						continue;
					
					int length = path.size();
					
					if (this.levels.containsKey(other) == false ||
						(this.levels.get(other) < length+1)) //+1 removes zero lengths/layers
					{
						this.levels.put(other, length+1);
					}
				}
				
				
			}
		}
		else //must have roots to be here, early out would prevent otherwise
		{
			int maxLayer = 0;
			for (DomainTransitionGraph dtg : this.cg.getRoots())
			{
				for (DomainTransitionGraph other : this.cg.getDTGs())
				{
//					if (other == dtg || this.cg.getRoots().contains(other))
//						continue;
					
					DijkstraShortestPath<DomainTransitionGraph, CausalGraphLink> dij = new DijkstraShortestPath<DomainTransitionGraph, CausalGraphLink>(this.cg, dtg, other);
					List<CausalGraphLink> path = dij.getPathEdgeList();
					if (path == null)
						continue;
					
					int length = path.size();
					if (length > maxLayer)
						maxLayer = length;
					
					if (this.levels.containsKey(other) == false ||
						(this.levels.get(other) < length))
					{
						this.levels.put(other, length);
					}
				}
				
				
			}
			
			//because we are dealing with roots, we need to invert all the distances, i.e. the min layer becomes the max etc.
			HashMap<DomainTransitionGraph, Integer> flippedLayers = new HashMap<DomainTransitionGraph, Integer>();
			for (Entry<DomainTransitionGraph, Integer> currLayer : this.levels.entrySet())
			{
				int oldLayer = currLayer.getValue();
				int newLayer = maxLayer - oldLayer + 1; //add 1 to prevent layers being 0 (no path or self paths)
				flippedLayers.put(currLayer.getKey(), newLayer);
			}
			
			this.levels = flippedLayers;
		} 
		//find the max level
		this.uniqueLayers = Collections.max(this.levels.values());


		//verify that the levels are correct
		assert(this.levels.size() == this.cg.getDTGs().size());
		
		Integer curr = 1;
		for (int i = curr; i <= new HashSet(this.levels.values()).size(); i++)
		{
			boolean contains = this.levels.containsValue(curr);
			if (i > this.uniqueLayers && contains)
				throw new NullPointerException("CG hierarchy has more labels than layers");

			if (!contains)
				throw new NullPointerException("CG hierarchy is inconsistent");
			
			curr = curr + 1;
		}

		//find the max level
		this.uniqueLayers = Collections.max(this.levels.values());
		
		
	}
	
	public int getLevelCount()
	{
		return this.uniqueLayers;
	}

	public Map<DomainTransitionGraph, Integer> getLevels()
	{
		return this.levels;
	}
	
	public int getMinLevel(SASLiteral f)
	{
		int min = Integer.MAX_VALUE;
		for (Entry<DomainTransitionGraph, Integer> e : this.levels.entrySet())
		{
			if (e.getKey().vertexSet().contains(f) && e.getValue() < min)
			{
				min = e.getValue();
			}
		}
		
		return min;		
	}
	
	public int getMaxLevel(SASLiteral f)
	{
		int max = Integer.MIN_VALUE;
		for (Entry<DomainTransitionGraph, Integer> e : this.levels.entrySet())
		{
			if (e.getKey().vertexSet().contains(f) && e.getValue() > max)
			{
				max = e.getValue();
			}
		}
		
		return max;		
	}
	
	/**
	 * Does the causal graph have any layers, or are all nodes non-roots/leaves.
	 * @return
	 */
	public boolean hasLayers()
	{
		return hasLayers;
	}
}

