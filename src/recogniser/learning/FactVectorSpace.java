package recogniser.learning;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import recogniser.hypothesis.AllFalseGoal;
import recogniser.util.HybridSasPddlProblem;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.MutexSet;
import javaff.data.strips.Equals;
import javaff.data.strips.Not;
import javaff.data.strips.Proposition;

public class FactVectorSpace
{

	private Collection<MutexSetDomain> domains;
	
	private FactVectorSpace()
	{
		this.domains = new HashSet<MutexSetDomain>();
	}
	
	public FactVectorSpace(Collection<MutexSet> mutexes, Collection<Action> actions, Collection<Fact> reachableFacts)
	{	
		this();
		
		this.setupDomains(mutexes, actions, reachableFacts);
	}
	
	protected void setupDomains(Collection<MutexSet> mutexSets, Collection<Action> actions, Collection<Fact> allFacts)
	{
		//first, count up the adds and deletes across the action set
		HashMap<Fact, Integer> adds = new HashMap<Fact, Integer>();
		HashMap<Fact, Integer> deletes = new HashMap<Fact, Integer>();
		HashMap<Fact, Integer> requires = new HashMap<Fact, Integer>();
				
		//can't do this because some facts are stripped out from mutex sets during preprocessing
		//need to get set of all facts first -- then mutex sets can be used for finalising the vectors
//		for (MutexSet m : mutexSets)
//		{
//			for (Fact f : m.getFacts())
//			{
//				adds.put(f, 0);
//				deletes.put(f, 0);
//				requires.put(f, 0);
//			}
//		}
		
		int hs = 0;
		for (Fact f : allFacts)
		{
			adds.put(f, 0);
			deletes.put(f, 0);
			requires.put(f, 0);
			hs = f.hashCode();
		}
		
		for (Action a : actions)
		{
			for (Fact f : a.getPreconditions())
			{
				if (f.isStatic() || f instanceof Equals || (f instanceof Not))// && (((Not)f).literal instanceof Equals)))
					continue;
				
				int hs2 = f.hashCode();
				requires.put(f, requires.get(f)+1);
			}

			for (Fact f : a.getAddPropositions())
			{
				if (f.isStatic())
					continue;
				
				adds.put(f, adds.get(f)+1);
			}

			for (Not f : a.getDeletePropositions())
			{
				if (f.getLiteral().isStatic())
					continue;
				
				deletes.put(f.getLiteral(), deletes.get(f.getLiteral())+1);
			}
		}
		
		//counts have now been assigned, so vectors can be created.
		for (MutexSet ms : mutexSets)
		{
			HashMap<Fact, FactVector> vectors = new HashMap<Fact, FactVector>();
			for (Fact f : ms.getFacts())
			{
				//if the fact is an AllFalseGoal, it is treated differently. Instead we 
				//want to know how many times a a mutex fact is deleted, as this will
				//correspond to the likelihood of none of the positive literals being the goal
				if (f instanceof AllFalseGoal)
				{
					int totalAdds = 0, totalDeletes = 0;
					for (Fact fOther : ms.getFacts())
					{
						if (f == fOther)
							continue;
						
						//NOTE the swapping of Adds and Deletes.
						int addCount = deletes.get(fOther);
						int delCount = adds.get(fOther);
//						int pcCount = requires.get(f);
						
						totalAdds += addCount;
						totalDeletes += delCount;
					}
					

					int pcCount = 0; //could have negative PC support here instead of 0
					
					FactVector vec = new FactVector(f, totalAdds, totalDeletes, pcCount);
					vectors.put(f, vec);
					
					continue;
				}
				
				int addCount = adds.get(f);
				int delCount = deletes.get(f);
				int pcCount = requires.get(f);
				
				FactVector vec = new FactVector(f, addCount, delCount, pcCount);
				vectors.put(f, vec);
			}
			
			MutexSetDomain domain = new MutexSetDomain(ms, vectors);
			this.domains.add(domain);
		}
	}

	public Collection<MutexSetDomain> getDomains()
	{
		return domains;
	}
}
