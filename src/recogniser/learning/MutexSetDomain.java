package recogniser.learning;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.MutexSet;
import javaff.data.strips.NullFact;
import javaff.graph.FactMutex;
import javaff.planning.RelaxedPlanningGraph;

/**
 * @deprecated
 * @author David Pattison
 *
 */
public class MutexSetDomain
{
	
	//local copy
	private MutexSet mutexes;
	private Map<Fact, FactVector> factVectors;
	
	private MutexSetDomain()
	{
		this.mutexes = null;
		this.factVectors = 
				new HashMap<Fact, FactVector>();
	}
	public MutexSetDomain(MutexSet mutexFacts, Collection<Action> actionSet)
	{
		this();
		
		this.mutexes = mutexFacts;
		this.initialiseFromActionSet(actionSet);
	}
	
	public MutexSetDomain(MutexSet mutexFacts, Map<Fact, FactVector> newFactVectors)
	{
		this();
		
		if (newFactVectors.keySet().equals(mutexFacts.getFacts()) ==  false)
			throw new IllegalArgumentException("Mutex set and FactVector set keys must be equal."); 
		
		this.mutexes = mutexFacts;
		this.factVectors.putAll(newFactVectors);
	}
	
	/**
	 * Returns a version of this mutex domain in which all member fact
	 * vectors have been normalised.
	 * @return
	 */
	public MutexSetDomain getNormalisedDomain()
	{
		MutexSetDomain ndomain = new MutexSetDomain();
		ndomain.mutexes = this.mutexes;
		for (FactVector v : this.factVectors.values())
		{
			FactVector n = v.getNormalisedVector();
			ndomain.factVectors.put(v.getFact(), v);
		}
		
		return ndomain;
	}
	
	public FactVector getVector(Fact f)
	{
		return this.factVectors.get(f);
	}
	
	/**
	 * Compute the centroid of this vector as the average of all its points.
	 * @return An array of the form [addAverage, deleteAverage, requiresAverage].
	 */
	public double[] getCentroidArray()
	{
		double pct = 0, addt = 0, delt = 0;
		for (FactVector v : this.factVectors.values())
		{
			pct += v.getRequires();
			addt += v.getAdded();
			delt += v.getDeleted();
		}
		
		pct = pct / this.factVectors.size();
		addt = addt / this.factVectors.size();
		delt = delt / this.factVectors.size();
		
		return new double[]{addt, delt, pct};
	}

	/**
	 * Compute the centroid of this vector as the average of all its points.
	 * @return A FactVector, in which the fact is a {@link NullFact}.
	 */
	public FactVector getCentroidVector()
	{
		Fact f = new NullFact();
		FactVector v = new FactVector(f, this.getCentroidArray());
		
		return v;
	}
	
	/**
	 * Gets the total magnitude of all vectors in the FactVector polygon.
	 * @return
	 */
	public double getTotalMagnitude()
	{
		double tot = 0;
		for (FactVector v : this.factVectors.values())
		{
			tot += v.getMagnitude();
		}
		
		return tot;
	}

	private final void initialiseFromActionSet(Collection<Action> actionSet)
	{
		this.factVectors.clear();
		
		for (Fact f : this.mutexes.getFacts())
		{
			int pc = 0, add = 0, del = 0;
			for (Action a : actionSet)
			{
				if (a.requires(f))
					++pc;
				if (a.adds(f))
					++add;
				if (a.deletes(f))
					++del;
			}
			
			FactVector v = new FactVector(f, add, del, pc);
			this.factVectors.put(f, v);
		}
	}
	public MutexSet getMutexes()
	{
		return mutexes;
	}
	public Map<Fact, FactVector> getFactVectors()
	{
		return factVectors;
	}

}
