package recogniser.learning;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.MutexSpace;
import javaff.data.strips.And;
import javaff.data.strips.Proposition;
import javaff.graph.FactMutex;

public class RandomConjunctionGenerator extends AbstractConjunctionGenerator
{
	private Random rand;
	
	public RandomConjunctionGenerator(MutexSpace ms, Collection<? extends Collection<Proposition>> varMap, int minCjSize, int maxCjSize)
	{
		super(ms, varMap, minCjSize, maxCjSize);
		
		this.rand = new Random();
	}
	
	public RandomConjunctionGenerator(MutexSpace ms, Collection<? extends Collection<Proposition>> varMap, int minCjSize, int maxCjSize, int maxConjunctions)
	{
		super(ms, varMap, minCjSize, maxCjSize, maxConjunctions);
		
		this.rand = new Random();
	}
	
	/**
	 * Generates a set of proposition conjunctions using this object's Goal Space.
	 */
	@Override
	public Set<FactMutex> generateConjunctions()
	{
		if (new BigInteger(""+this.getNumberOfConjunctions()).compareTo(this.getMaxPossibleCombinations()) > 0)
		{
			System.out.println("Cannot generate "+super.getNumberOfConjunctions()+" because only "+this.getMaxPossibleCombinations()+" are possible");
			System.out.println("Continuing...");
			this.setNumberOfConjunctions(this.getMaxPossibleCombinations().intValue());
		}
		
//		System.out.println("generating conjunctions for "+this.mutexSet.getKeys());
		HashSet<GroundFact> keys = new HashSet<GroundFact>();
		HashSet<FactMutex> conjunctions = new HashSet<FactMutex>();
		
		//this keeps track of how many conjunctions of size k have been generated so far
		HashMap<Integer, Integer> conjunctionSetSizes = new HashMap<Integer, Integer>();
		ArrayList<Integer> legalLengths = new ArrayList<Integer>();
		for (int i = super.getMinConjunctionSize(); i <= super.getMaxConjunctionSize(); i++)
		{
			conjunctionSetSizes.put(i, 0);
			legalLengths.add(i);
		}
		
		//rule out any conjunctions that may be in the goal space
//		ArrayList<Set<Proposition>> freeSets = new ArrayList<Set<Proposition>>(super.variableSets);
//		ArrayList<Proposition> originalPropositionList = new ArrayList<Proposition>();
//		for (GroundCondition g : super.getMutexSet().getMutexMap().keySet())
//			if (g instanceof Proposition)
//				originalPropositionList.add((Proposition) g);

		ArrayList<Proposition> originalPropositionList = new ArrayList<Proposition>();
		for (Collection<Proposition> s : super.getVariableDomains())
			for (Proposition p : s)
				if (super.getMutexSet().containsFact(p))
						originalPropositionList.add(p);
				
		out : for (int i = 0; i < super.getNumberOfConjunctions(); ) //no i++
		{
			int size = legalLengths.get(0) + (this.rand.nextInt(legalLengths.get(legalLengths.size()-1) - legalLengths.get(0) + 1));

			ArrayList<Proposition> propositionList = new ArrayList<Proposition>(originalPropositionList);
			ArrayList<Fact> cj = new ArrayList<Fact>();
			//select n mutex propositions
			mid : for (int j = 0; j < size; ) //no j++
			{				
				if (propositionList.size() == 0)
					continue out;
				
				Proposition chosen = propositionList.get(this.rand.nextInt(propositionList.size()));
				
				//check chosen is not mutex with anything already in conjunction
				for (Fact ap : cj)
				{
					if (ap.equals(chosen) || super.getMutexSet().isMutex(ap, chosen) == true)
					{
						continue mid;
					}
				}
				
				propositionList.remove(chosen);
				propositionList.removeAll(super.getMutexSet().getMutexes(chosen).getOthers());	
				
				cj.add(chosen);
				
				j++;
			}
			

			//create a mutex and add in all PROPOSITION completeMutexSpace - deal with ANDs later
			And and = new And(cj);
			if (keys.contains(and))
				continue out;
			
			FactMutex mut = new FactMutex(and);
			for (Fact p : cj)
			{
				if (super.getMutexSet().hasMutexes(p))
					mut.addMutex(super.getMutexSet().getMutexes(p).getOthers());
			}
	
			keys.add(and);
			conjunctions.add(mut);
			conjunctionSetSizes.put(size, conjunctionSetSizes.get(size)+1);
			
//			System.out.println("conjunctions: "+conjunctions.size());
			
			if (super.willExitComplexityCalculationEarly() == false)
			{
				if (new BigInteger(conjunctionSetSizes.get(size)+"").compareTo(super.getMaxSetSize(size)) >= 0)
				{
					//must remove "size" from legalLengths
					legalLengths.remove(legalLengths.indexOf(size));
					System.out.println("All conjunctions of size "+size+" used up");
				}
			}
			
			i++;
		}
		
		//now have to add in conjunction completeMutexSpace for the generated conjunctions
		//loop over all conjunctions, and add in the conjunctions 
		super.generateConjunctionMutexes(conjunctions);
		
		
		return conjunctions;
	}


}
