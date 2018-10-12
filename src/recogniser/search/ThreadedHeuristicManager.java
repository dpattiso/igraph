package recogniser.search;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javaff.JavaFF;
import javaff.data.CompoundLiteral;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.Plan;
import javaff.data.strips.And;
import javaff.planning.RelaxedPlanningGraph;
import javaff.planning.STRIPSState;

import recogniser.hypothesis.AllFalseGoal;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.IGRAPHPreferences;
import recogniser.util.StripsRpg;
import recogniser.util.IGRAPHPreferences.RecognitionHeuristicType;
import javaff.search.UnreachableGoalException;

public class ThreadedHeuristicManager
{
	protected IHeuristic heuristic;
	protected HybridSasPddlProblem problem;

	private Map<Fact, Double> estimates;

	private ExecutorService threadPool;
	private final int maxThreads;

	public ThreadedHeuristicManager(HybridSasPddlProblem problem, int maxThreads)
	{
		this.problem = problem;
		this.estimates = new HashMap<Fact, Double>();

		this.maxThreads = maxThreads;

		this.threadPool = Executors.newFixedThreadPool(this.maxThreads);
//		this.threadPool = Executors.newSingleThreadExecutor();
//		this.threadPool = Executors.newCachedThreadPool();
		
		this.initialiseHeuristic();
	}

//	public ThreadedHeuristicManager(HybridSasPddlProblem problem)
//	{
//		this(problem, Runtime.getRuntime().availableProcessors());
//	}

	public void getEstimates(Collection<Fact> goals) throws InterruptedException, ExecutionException
	{
		final IHeuristic baseHeuristic = this.heuristic;
//		if (this.heuristic instanceof JavaFFHeuristic)
//		{
//			this.initialiseHeuristic();
//		}

		Set<Future<FactFloatPair>> futures = new HashSet<Future<FactFloatPair>>();

		for (Fact g : goals)
		{
			if (this.estimates.containsKey(g))
				continue;
			
			IHeuristic goalHeuristic = null;
			//the existing FF relaxed plan extraction is destructive with regard to the internal 
			//state of the RPG etc, so it must be recreated -- which probably seriously offsets any 
			//benefits from threading.
			if (this.heuristic instanceof JavaFFHeuristic)
			{
				
				goalHeuristic = (IHeuristic) ((JavaFFHeuristic)baseHeuristic).clone();
				((JavaFFHeuristic) goalHeuristic).setGoal(g);
				
			}
			else if (this.heuristic instanceof GraphplanHeuristic)
			{
				goalHeuristic = (IHeuristic) ((GraphplanHeuristic)baseHeuristic).clone();
				((GraphplanHeuristic) goalHeuristic).setGoal((GroundFact) g);
			}
			else if (this.heuristic instanceof JavaFFPlanningHeuristic)
			{
				goalHeuristic = (IHeuristic) baseHeuristic.clone();
			}
			else if (this.heuristic instanceof CEAHeuristic)
			{
				goalHeuristic = ((CEAHeuristic) baseHeuristic).branch();
//				goalHeuristic = (IHeuristic) ((CEAHeuristic) baseHeuristic).clone();
			}
			else
			{
				//Both the Max and CG heuristics are non-destructive, so the original heuristic object can be used
				goalHeuristic = baseHeuristic;
			}
			
			HeuristicRunnable callable = new HeuristicRunnable(goalHeuristic, g);
			Future<FactFloatPair> future = this.threadPool.submit(callable);
			futures.add(future);
		}

		for (Future<FactFloatPair> future : futures)
		{
			FactFloatPair pair = future.get(); //wait for future to return. 

			this.estimates.put(pair.fact, pair.value);
		}
	}

	public double getEstimate(Fact goal) throws UnreachableGoalException, InterruptedException, ExecutionException
	{
		if (this.estimates.containsKey(goal))
			return this.estimates.get(goal);
		
		IHeuristic clone = this.heuristic;
		if (this.heuristic instanceof JavaFFHeuristic)
		{
			clone = new JavaFFHeuristic(this.problem, goal);
//			clone = new JavaFFHeuristic((GroundProblem) this.problem.clone(), goal);
		}
		else if (this.heuristic instanceof GraphplanHeuristic)
		{
			clone = this.heuristic = new GraphplanHeuristic(this.problem);
		}
		else if (this.heuristic instanceof JavaFFPlanningHeuristic)
		{
			clone = (IHeuristic) heuristic.clone();
		}
		else if (this.heuristic instanceof CEAHeuristic)
		{
			clone = ((CEAHeuristic) heuristic).branch();
		}
		else
		{
			clone = (IHeuristic) heuristic.clone();
		}

		HeuristicRunnable callable = new HeuristicRunnable(clone, goal);
		Future<FactFloatPair> future = this.threadPool.submit(callable);
	
		FactFloatPair pair = future.get();

		this.estimates.put(pair.fact, pair.value);
		
		return pair.value;
	}
	
	/**
	 * Immediately terminate the manager and any exicuting threads.
	 */
	public void shutdown()
	{
		this.threadPool.shutdownNow();
	}

	@Override
	protected void finalize() throws Throwable
	{
		this.shutdown();

		super.finalize();
	}

	protected void initialiseHeuristic()
	{
		if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.Max)
		{
			StripsRpg rpg = new StripsRpg(this.problem.getActions());
			rpg.constructFullRPG(this.problem.getSTRIPSInitialState());
			this.heuristic = new MaxHeuristic(rpg);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.FF)
		{
			JavaFFHeuristic ffh = new JavaFFHeuristic(this.problem);
//			ffh.getRpg().constructStableGraph(this.problem.getSTRIPSInitialState());
			this.heuristic = ffh;
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.CG)
		{
			this.heuristic = new CGHeuristic(this.problem);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.CEA)
		{
			this.heuristic = new CEAHeuristic(this.problem);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.GP)
		{
			this.heuristic = new GraphplanHeuristic(this.problem);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.JavaFFPlanning)
		{
			this.heuristic = new JavaFFPlanningHeuristic(this.problem);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.Random)
		{
			this.heuristic = new RandomHeuristic();
		}

	}

	/**
	 * Update the heuristic being used to reflect the new state etc
	 */
	public void updateHeuristic(HybridSasPddlProblem newProblem, Collection<Fact> goals)
	{
		this.problem = newProblem;
		
		//wipe all previous estimates
		this.estimates.clear();
		
		this.heuristic.reset();
		
		// GroundProblem cloneGP = (GroundProblem)this.problem.clone();
		// STRIPSState current = new STRIPSState(cloneGP.actions, currentState.facts, goal);
		And allGoals = new And(goals);
		this.problem.setGoal(allGoals);

		if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.Max)
		{
			StripsRpg rpg = new StripsRpg(this.problem.getActions());
			rpg.constructFullRPG((STRIPSState) newProblem.getState()); // set up the RPG distances, no need for the
												// returned plan (which seems to be broken anyway)
			((MaxHeuristic) this.heuristic).setRpg(rpg);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.FF)
		{
			((JavaFFHeuristic) this.heuristic).rebuildRPG(this.problem);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.CG)
		{
			((CGHeuristic) this.heuristic).reset(); // erase lookup table
			((CGHeuristic) this.heuristic).setProblem(this.problem);
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.CEA)
		{
			//no update required for CEA. The state is passed in as an argument and everything else
			//remains the same.
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.GP)
		{
			//this is a hack because GPHeuristic calls currentState = getSTRIPSInitialState() on each
			//call of reset(). Rather than go around this problem, easier to just
			//change the initial state to be the current state -- 
			this.problem.setInitial(((STRIPSState) this.problem.getState()).getFacts());
			((GraphplanHeuristic) this.heuristic).setProblem(this.problem); //calls reset()
		}
		else if (IGRAPHPreferences.Heuristic == RecognitionHeuristicType.JavaFFPlanning)
		{
			GroundProblem cgp = (GroundProblem) this.problem.clone();
			cgp.setInitial(((STRIPSState) this.problem.getState()).getFacts());
			cgp.recomputeSTRIPSInitialState();
			this.heuristic = new JavaFFPlanningHeuristic(cgp);
			
		}
			
	}

	protected class HeuristicRunnable implements Callable<FactFloatPair> // extends Thread
	{
		private Fact goal;
		private IHeuristic heuristic;

		public HeuristicRunnable(IHeuristic heuristic, Fact goal)
		{
			super();

			this.heuristic = heuristic;
			this.goal = goal;
		}

		@Override
		public FactFloatPair call()
		{
//			System.out.println("Running thread for goal "+this.goal);
//			try
//			{
//				Thread.sleep(1000);
//				System.out.println("done");
//			}
//			catch (InterruptedException e1)
//			{
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
			
			Double val;
			try
			{
				val = this.heuristic.getEstimate(this.goal);
			}
			catch (UnreachableGoalException e)
			{
				val = IHeuristic.Unreachable;
			}
			return new FactFloatPair(this.goal, val);
		}
	}

	protected class FactFloatPair
	{
		public Fact fact;
		public Double value;

		public FactFloatPair(Fact f, Double v)
		{
			this.fact = f;
			this.value = v;
		}
	}
	
	/**
	 * Gets the relaxed plans which were computed during the heuristic update process.
	 * Note that only plans which have a reachable goal are included.
	 * If the {@link JavaFFHeuristic} is the currently selected heuristic, this process returns
	 * immediately, as the relaxed plans have already been cached during this heuristic's update. Otherwise,
	 * the {@link JavaFFHeuristic} is invoked and estimates computed for each goal in the parameter collection.
	 * @param goals The goals to return the plan-space for.
	 * @return
	 */
	public Collection<Plan> getCachedRelaxedPlans(Collection<Fact> goals)
	{
		HashSet<Plan> plans = new HashSet<Plan>();
		
		//set of unsatisfied goals
		HashSet<Fact> remaining = new HashSet<Fact>(goals);
		
		//if the heuristic used is the JavaFFHeuristic, then
		//the relaxed plans are already computed, so just return directly
		//(assuming the goals wanted as the same as those previously processed).
		if (this.heuristic instanceof JavaFFHeuristic)
		{
			Map<Fact, Plan> cache = ((JavaFFHeuristic) this.heuristic).getCachedPlans();
			plans.addAll(cache.values());
			
			remaining.removeAll(cache.keySet());
		}

		if (remaining.isEmpty())
			return plans;
		
		//if reached this point then must need additional goals in the return map
		//need to create a new JavaFFHeuristic object 
		remaining.removeAll(plans);
		
		JavaFFHeuristic h = new JavaFFHeuristic(this.problem);
		h.getRpg().constructStableGraph(this.problem.getSTRIPSInitialState());
		
		HashSet<Fact> toRemove = new HashSet<Fact>();
		Iterator<Fact> remIter = remaining.iterator();
		while (remIter.hasNext())
		{
			Fact g = remIter.next();
			if (g instanceof AllFalseGoal)
			{
				toRemove.add(g);
				continue;
			}
			
			try
			{
				double hUnused = h.computeEstimate(g);
			}
			catch (UnreachableGoalException e)
			{
				//do nothing
				//map.add(null); //add null for unreachable goals
			}
		}
		
		plans.addAll(h.getCachedPlans().values());
		
		remaining.removeAll(toRemove);
		remaining.removeAll(h.getCachedPlans().keySet());
		
		if (remaining.isEmpty() == false)
			throw new NullPointerException("Cannot find relaxed plans for all requested goals: "+remaining);
		
		
		return plans;
	}

	public Map<Fact, Double> getCachedEstimates()
	{
		return estimates;
	}
}
