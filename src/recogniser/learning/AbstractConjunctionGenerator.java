package recogniser.learning;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import recogniser.hypothesis.CombinationGenerator;

import javaff.data.GroundFact;
import javaff.data.MutexSpace;
import javaff.data.strips.Proposition;
import javaff.graph.FactMutex;

public abstract class AbstractConjunctionGenerator implements IConjunctionGenerator
{
	private boolean earlyComplexityExit;
	
	private MutexSpace mutexSet;
	
	private int conjunctionWanted;
	private int minConjunctionSize, maxConjunctionSize;
	
	private BigInteger maxCombinations;
	private HashMap<Integer, BigInteger> maxSetSizes;
	private List<? extends Collection<Proposition>> variableDomains;
	
	public static int DEFAULT_MAX_CONJUNCTIONS = 100;
	
	public AbstractConjunctionGenerator(MutexSpace ms, Collection<? extends Collection<Proposition>> varSets, int minCjSize, int maxCjSize)
	{
		this(ms, varSets, minCjSize, maxCjSize, DEFAULT_MAX_CONJUNCTIONS);
	}
	
	public AbstractConjunctionGenerator(MutexSpace ms, Collection<? extends Collection<Proposition>> varSets, int minCjSize, int maxCjSize, int maxConjunctions)
	{
		this.mutexSet = ms;
		this.variableDomains = new ArrayList<Collection<Proposition>>(varSets);
		this.conjunctionWanted = maxConjunctions;
		
		this.minConjunctionSize = minCjSize;
		this.maxConjunctionSize = maxCjSize;
		
		this.maxSetSizes = new HashMap<Integer, BigInteger>();		
		this.earlyComplexityExit = true;
		
		this.computeMaximumConjunctions();
	}

	
	@Override
	public abstract Set<FactMutex> generateConjunctions();

	protected BigInteger getMaxSetSize(int conjunctionLength)
	{
		return this.maxSetSizes.get(conjunctionLength);
	}
	
	/**
	 * Helper method to generate conjunction completeMutexSpace for and from a collection of conjunctions.
	 *  
	 * @param conjunctions
	 */
	protected void generateConjunctionMutexes(Collection<FactMutex> conjunctions)
	{
		//now have to add in conjunction completeMutexSpace for the generated conjunctions
		//loop over all conjunctions, and add in the conjunctions 
		for (FactMutex f : conjunctions)
		{
			if (f.getOwner() instanceof GroundFact)
			{
				for (Object ownerFacto : ((GroundFact)f.getOwner()).getFacts())
				{
					Proposition ownerFact = (Proposition) ownerFacto;
					for (FactMutex g : conjunctions)
					{
						if (f == g)
							continue;
						
						if (((GroundFact)g.getOwner()).getFacts().contains(ownerFact))
							f.addMutex(g.getOwner());
					}
				}
			}
		}	
	}
	
	/**
	 * Computes the maximum number of combinations for the current set of goals in super.mutexSet using
	 * the static DTG set to compute mutex sets.
	 * @param singletonCount The number of variables which have no completeMutexSpace- as these are not
	 * detected by the SAS+ translator.
	 */
	protected void computeMaximumConjunctions()
	{
		this.maxSetSizes.clear();		
		
		int maxCjSize = this.maxConjunctionSize;
		if (maxCjSize > variableDomains.size())
		{
			System.out.println("Cannot generate conjunctions of size "+maxCjSize+", can only achieve "+variableDomains.size()+".\nSetting max conjunction length to "+variableDomains.size());;
			maxCjSize = variableDomains.size();
			this.maxConjunctionSize = maxCjSize;
		}
		if (this.minConjunctionSize > variableDomains.size())
			throw new IllegalArgumentException("Cannot generate minimum conjunction length of "+this.minConjunctionSize+"- maximum size is "+variableDomains.size());
		
		int n = variableDomains.size();
		if (n == 0)
			throw new IllegalArgumentException("Cannot compute max combinations because size of sets to use is 0");
		
		//if k >= n we will get a divide by 0 error
		if (maxCjSize >= n)
		{
			System.out.println("Warning, k >= n in permutation calculation.");
//			maxCjSize--;
		}
		
		System.out.println("Computing number of possible combinations...");
		if (this.earlyComplexityExit)
			System.out.println("Will exit combination computation once total >= conjunctions wanted ("+this.conjunctionWanted+")");

//		for (int k = this.minConjunctionSize; k <= maxCjSize; k++)
//		{
//			this.maxSetSizes.put(k, new BigInt()
//		}
		
		BigInteger conjunctionsWantedBI = new BigInteger(""+this.conjunctionWanted);
		BigInteger allConjunctions = new BigInteger("0");
		out : for (int k = this.minConjunctionSize; k <= maxCjSize; k++)
		{
			BigInteger kTotal = new BigInteger("0");
			CombinationGenerator gen = new CombinationGenerator(n, k);
//			System.out.println("left "+gen.getNumLeft());
			while (gen.hasMore())
			{
				BigInteger singleTotal = new BigInteger("1");
				int[] com = gen.getNext();
				for (int i = 0; i < com.length; i++)
				{
					int size = this.variableDomains.get(com[i]).size();
					singleTotal = singleTotal.multiply(new BigInteger(""+size));
				}
				
				kTotal = kTotal.add(singleTotal);
				allConjunctions = allConjunctions.add(singleTotal);
				
				if (allConjunctions.compareTo(conjunctionsWantedBI) >= 0)
				{
					System.out.println("Number of conjunctions wanted is possible- stopping calculation early at "+allConjunctions.toString());
					break out;
				}
			}

			System.out.println(kTotal+" possible combination of size "+k);
			this.maxSetSizes.put(k, kTotal);
//			allConjunctions = allConjunctions.add(kTotal);
			
//			if (allConjunctions.compareTo(conjunctionsWantedBI))
//			{
//				
//			}
		}
		
//		BigInteger nFac = new BigInteger("1");
//		for (int i = n; i > 0; i--)
//			nFac = nFac.multiply(new BigInteger(sortedSets.get(i).size()+""));
//
//		for (int k = this.minConjunctionSize; k <= maxCjSize; k++)
//		{
////			if (k >= n)
////				continue;
//			
//			BigInteger kFac = new BigInteger("1");
//			for (int i = k; i > 0; i--)
//				kFac = kFac.multiply(new BigInteger(sortedSets.get(i).size()+""));
//			
//			BigInteger kMax = nFac.divide(kFac.multiply(new BigInteger((n-k)+"")));
//			
//			this.maxSetSizes.put(k, kMax);
//			allConjunctions = allConjunctions.add(kMax);
//		}
		
		this.maxCombinations = allConjunctions;
		
		if (!this.earlyComplexityExit)
			System.out.println("Maximum number of possible conjunctions is "+this.maxCombinations);
		
	}
		
	
	@Override
	public MutexSpace getMutexSet()
	{
		return mutexSet;
	}

	/**
	 * Sets the completeMutexSpace. Computes maximum number of combinations afterwards.
	 */
	@Override
	public void setMutexSet(MutexSpace mutexSet)
	{
		if (mutexSet instanceof MutexSpace == false)
			throw new IllegalArgumentException("Goal space must be of type MutexGoalSpace");
		
		this.mutexSet = mutexSet;
		this.computeMaximumConjunctions();
	}

	@Override
	public int getNumberOfConjunctions()
	{
		return conjunctionWanted;
	}
	
	/**
	 * Sets the number of conjunctions wanted when generateConjunctions is called. 
	 * Computes maximum number of combinations afterwards. If the number of conjunctions wanted is 
	 * greater than what is possible, it is silently set to this maximum.
	 */
	@Override
	public void setNumberOfConjunctions(int maxConjunctions)
	{
		this.conjunctionWanted = maxConjunctions;
//		this.computeMaximumConjunctions();
//		if (new BigInteger(this.conjunctionWanted+"").compareTo(this.maxCombinations) > 1)
//			this.conjunctionWanted = this.maxCombinations.intValue(); //FIXME this will break, but I'm lazy
	}

	@Override
	public int getMinConjunctionSize()
	{
		return minConjunctionSize;
	}

	/**
	 * Sets minimum conjunction length. Computes maximum number of combinations afterwards.
	 * 
	 * @throws IllegalArgumentException Thrown if the minimum size is greater than the maximum
	 * possible conjunction length.
	 */
	@Override
	public void setMinConjunctionSize(int minConjunctionSize)
	{
		if (minConjunctionSize < 1)
			throw new IllegalArgumentException("Min conjunction size must be greater than 0");
		
		this.minConjunctionSize = minConjunctionSize;
		this.computeMaximumConjunctions();
	}

	@Override
	public int getMaxConjunctionSize()
	{
		return maxConjunctionSize;
	}
	
	/**
	 * Sets maximum conjunction length. Computes maximum number of combinations afterwards.
	 * 
	 * @throws IllegalArgumentException Thrown if the maximum size is greater than the maximum
	 * possible conjunction length.
	 */
	@Override
	public void setMaxConjunctionSize(int maxConjunctionSize)
	{
		if (maxConjunctionSize < minConjunctionSize)
			throw new IllegalArgumentException("Max conjunction size must be greater or equal to minimum conjunction size");
		
		this.maxConjunctionSize = maxConjunctionSize;
		this.computeMaximumConjunctions();
	}

	public BigInteger getMaxPossibleCombinations()
	{
		return maxCombinations;
	}

	public List<? extends Collection<Proposition>> getVariableDomains()
	{
		return variableDomains;
	}

	public void setVariableDomains(List<? extends Collection<Proposition>> varSets)
	{
		this.variableDomains = varSets;
	}

	public boolean willExitComplexityCalculationEarly()
	{
		return earlyComplexityExit;
	}

	public void setEarlyComplexityExit(boolean earlyComplexityExit)
	{
		this.earlyComplexityExit = earlyComplexityExit;
	}

//	public HashMap<Integer, BigInteger> getMaxSetSizes()
//	{
//		return maxSetSizes;
//	}
}
