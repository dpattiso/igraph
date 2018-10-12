package recogniser;

import recogniser.hypothesis.AllFalseGoal;
import recogniser.hypothesis.BoundedGoalHypothesis;
import recogniser.hypothesis.ConjunctiveGoalHypothesis;
import recogniser.hypothesis.IGoalHypothesis;
import recogniser.hypothesis.IGoalSpace;
import recogniser.hypothesis.MutexGoalSpace;
import recogniser.hypothesis.VariableGoalSpace;
import recogniser.learning.CausalGraphAnalyser;
import recogniser.learning.FactStabilitySpace;
import recogniser.learning.FactVector;
import recogniser.learning.IConjunctionGenerator;
import recogniser.learning.PredicatePartitioner;
import recogniser.learning.agent.IAgent;
import recogniser.search.IHeuristic;
import recogniser.search.ThreadedHeuristicManager;
import recogniser.util.GoalTieOrderingPreference;
import recogniser.util.IGRAPHPreferences;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.IGRAPHPreferences.WorkFunctionType;
import recogniser.util.InitialProbabilityDistributionType;
import recogniser.util.RecognitionException;
import recogniser.util.StateHistory;
import recogniser.util.StateHistoryTuple;
import recogniser.util.UnknownEstimateException;
import recogniser.util.IGRAPHPreferences.GoalSpaceType;
import recogniser.util.IGRAPHPreferences.HypothesisFilterType;
import sas.data.CausalGraph;
import sas.data.DomainTransitionGraph;
import sas.data.SASDomainObject;
import sas.data.SASProblem;
import threader.IterativeThreadScheduler;
import threader.ThreaderScheduler;
import threader.util.ActionStateTuple;
import threader.util.MergeAction;
import threader.util.PlanScheduleState;
import threader.util.PlanThread;
import threader.util.PlanThreadGraph;
import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.MutexSet;
import javaff.data.MutexSpace;
import javaff.data.NullInstantAction;
import javaff.data.Parameter;
import javaff.data.Plan;
import javaff.data.TimeStampedAction;
import javaff.data.TotalOrderPlan;
import javaff.data.strips.And;
import javaff.data.strips.Equals;
import javaff.data.strips.Not;
import javaff.data.strips.NullPlan;
import javaff.data.strips.Proposition;
import javaff.data.strips.SingleLiteral;
import javaff.graph.FactMutex;
import javaff.planning.STRIPSState;
import javaff.scheduling.SchedulingException;
import javaff.search.UnreachableGoalException;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Performs goal recognition by estimating the number of steps which each goal
 * has become closer by, after each observed action.
 * 
 * @author David Pattison
 * 
 */
public class BayesianGoalRecogniser
{
	protected HybridSasPddlProblem problem;
	protected StateHistory history;

	protected STRIPSState currentState, initialState;
	// protected IHeuristic heuristic;
	protected ThreadedHeuristicManager heuristicManager;
	protected TotalOrderPlan planSoFar;
	protected IterativeThreadScheduler threader;

	protected IAgent agent;
	protected IGoalSpace goalSpace;

	private long startTime;
	private boolean updateAfterObservation;

	protected CausalGraph causalGraph;

	protected PredicatePartitioner predicatePartitioner;
	protected IConjunctionGenerator conjunctionGenerator;

	protected HashMap<Fact, List<Double>> prevGoalDistMap;
	protected HashMap<Action, Double> currentActionDistMap;
	// 5/5/12 -- commented out because it causes MASSIVE overhead during
	// observation of
	// large scale domains. Much more efficient to just use direct lookups as
	// needed.
	// protected HashMap<Action, List<Double>> prevActionDistMap;
	protected HashMap<Fact, Double> usefulDistanceMap, notUsefulDistanceMap,
			totalDistanceMap, goalSupportLengthMap;
	protected HashMap<Fact, Set<PlanThread>> goalSupportThreadMap;
	protected List<PlanThread> allPlanThreads; //which plan thread the action at time T was added to
	
	// protected Set<Parameter> leafVars;
	// protected Set<Parameter> rootVars;

	protected HashSet<Fact> pastLeafVarProps;
	protected HashSet<SingleLiteral> allDeletedProps, allMovedAwayProps;
	protected HashSet<Fact> terminalPcs;

	/**
	 * All facts which are mutex with Strictly Terminal facts, which have also
	 * been achieved during execution. These will probably all be
	 * {@link AllFalseGoal}s for most domains.
	 */
	protected HashSet<Fact> stAchievedMutexFacts;
	/**
	 * The set of Strictly Terminal facts which have been achieved during plan
	 * execution.
	 */
	protected HashSet<Fact> stAchievedFacts;

	/**
	 * The set of actions which were applicable at time t-1.
	 */
	protected Collection<Action> prevApplicable;

	protected FactStabilitySpace stabilitySpace;

	protected int stagnantLimit;

	protected HashMap<Fact, List<Fact>> goalLandmarks;

	protected HashMap<Fact, Set<Action>> goalAchievers, goalRemovers,
			goalPreconditions;

	protected IGoalSpace initialHypothesisSpace;
	protected final IGoalHypothesis initialHypothesis;

	protected Random rand;

	// if true, no movement between observations are classed as "helpful"
	protected boolean zeroStepsAreHelpful;
	// private MutexSpace mutexSpace;

	/**
	 * The level of error which is acceptable when computing the sum of
	 * normalised mutex goals in the goal space. Because of rounding errors,
	 * this will never be equal to 1 exactly, so without this would appear to be
	 * incorrect. Default value is 1x10^6. Error is 1/this.
	 */
	public static double NormalisedError = 1000000f; // 1 x 10^6
	protected CausalGraphAnalyser cgAnalyser;
	private HashMap<Fact, Double> minimumCgLayers;
	private int minCGLayer, maxCGLayer;
	
	private HashMap<MutexGoalSpace, HashMap<Fact, List<Double>>> historicalBayesianProbabilities;

	// private Set<MutexGoalSpace> varGoalSpaces; //stored locally because
	// initialiseGoalSpace needs it.

	private ExecutorService threadPool;

	/**
	 * Causality value map
	 */
	private HashMap<Fact, Double> cvMap;

	/**
	 * Create a goal recogniser based upon Bayesian principles. Many of the
	 * settings used in setup are taken from {@link IGRAPHPreferences}.
	 * 
	 * @param prob
	 *            The problem to base recognition upon.
	 * @param agent
	 *            Unused. The agent being observed in this recognition activity.
	 * @throws RecognitionException
	 *             Thrown if there was a problem setting up the observation
	 *             process.
	 * @see #validateGoalSpace(IGoalSpace) The initial goal space is always
	 *      validated prior to returning.
	 */
	public BayesianGoalRecogniser(HybridSasPddlProblem prob, IAgent agent)
			throws RecognitionException
	{
		this.problem = prob;

		this.initialState = (STRIPSState) this.problem.getSTRIPSInitialState()
				.clone();
		this.currentState = (STRIPSState) this.initialState.clone();
		this.planSoFar = new TotalOrderPlan(prob.getGoal());

		this.agent = agent;

		this.setZeroStepsAreHelpful(true);

		this.usefulDistanceMap = new HashMap<Fact, Double>();
		this.notUsefulDistanceMap = new HashMap<Fact, Double>();
		this.totalDistanceMap = new HashMap<Fact, Double>();
		this.goalSupportLengthMap = new HashMap<Fact, Double>();
		this.goalSupportThreadMap = new HashMap<Fact, Set<PlanThread>>();
		this.allPlanThreads = new ArrayList<>();

		this.minimumCgLayers = new HashMap<Fact, Double>();
		this.cvMap = new HashMap<Fact, Double>();
		
		this.historicalBayesianProbabilities = new HashMap<MutexGoalSpace, HashMap<Fact,List<Double>>>();

		this.currentActionDistMap = new HashMap<Action, Double>(); // convenient
																	// lookup
																	// for each
																	// iteration

		this.startTime = System.nanoTime();
		this.history = new StateHistory();
		this.history.add((STRIPSState) this.initialState.clone(),
				this.getCurrentTimeOffset(), new HashSet<Fact>(),
				new HashSet<Fact>(),
				new HashSet<Fact>(this.initialState.getTrueFacts()));

		this.updateAfterObservation = true;

		this.rand = new Random(1234); // FIXME hack for consistent test results

		// int count = 0;
		// System.out.println("Creating DTG dot data...");
		// for (DomainTransitionGraph dtg :
		// this.problem.getCausalGraph().getDTGs())
		// dtg.generateDotGraph(new java.io.File("DTGs/dtg_"+count+++".dot"));

		this.causalGraph = this.problem.getCausalGraph();
		System.out.println(this.causalGraph.vertexSet().size() + " nodes,\n\t"
				+ this.causalGraph.getLeaves().size()
				+ " leaves in causal graph: " + this.causalGraph.getLeaves()
				+ "\n\t" + this.causalGraph.getRoots().size()
				+ " roots in causal graph: " + this.causalGraph.getRoots());
		System.out.println(this.causalGraph.edgeSet().size()
				+ " edges in causal graph");
		// this.causalGraph.generateDotGraph(new
		// java.io.File("causal_graph.dot"));

		// this.leafVars = this.computeCausalLeaves(this.problem);
		// this.leafVars = new HashSet<Parameter>();
		// for (DomainTransitionGraph dtg : causalGraph.getLeaves())
		// this.leafVars.add(dtg.sasVariable.getObject().convertToPDDL(problem.sasproblem,
		// this.problem));
		// System.out.println(this.leafVars.size()+" valid leaves found");

		// detect roots
		// this.rootVars = this.computeCausalRoots(this.problem);
		// this.rootVars = new HashSet<Parameter>();
		// for (DomainTransitionGraph dtg : problem.getCausalGraph().getRoots())
		// {
		// this.rootVars.add(dtg.sasVariable.getObject().convertToPDDL(problem.sasproblem,
		// this.problem));
		// }
		// System.out.println(this.rootVars.size()+" valid roots found");

		System.out.println("Partitioning propositions...");
		this.predicatePartitioner = new PredicatePartitioner();
		this.predicatePartitioner.analyse(problem);
		System.out.println("Found "
				+ this.predicatePartitioner.getUnstableTerminalSet().size()
				+ " unstable terminal props: "
				+ this.predicatePartitioner.getUnstableTerminalSet());
		System.out.println("Found "
				+ this.predicatePartitioner.getStrictlyTerminalSet().size()
				+ " strictly terminal props: "
				+ this.predicatePartitioner.getStrictlyTerminalSet());
		System.out.println("Found "
				+ this.predicatePartitioner.getUnstableActivatingSet().size()
				+ " unstable activating props: "
				+ this.predicatePartitioner.getUnstableActivatingSet());
		System.out.println("Found "
				+ this.predicatePartitioner.getStrictlyActivatingSet().size()
				+ " strictly activating props: "
				+ this.predicatePartitioner.getStrictlyActivatingSet());

		this.pastLeafVarProps = new HashSet<Fact>();
		this.allDeletedProps = new HashSet<SingleLiteral>();
		this.allMovedAwayProps = new HashSet<SingleLiteral>();
		this.stAchievedMutexFacts = new HashSet<Fact>();
		this.stAchievedFacts = new HashSet<Fact>();

		this.stagnantLimit = Integer.MAX_VALUE; // TODO find appropriate
												// stagnant limit

		// intermediate propositions are any add effect which appears from any
		// action applicable in the first state
		// HashSet<Fact> intermediatePropositions = new HashSet<Fact>();

		// check for preconditions to strictly activating facts
		// 24/9/10- Tightened to only include facts which appear ONLY as a PC to
		// actions which
		// contain a ST fact- ie facts which appear in N actions of which M < N
		// have
		// ST facts are NOT added.
		this.terminalPcs = this.getTerminalPreconditions(
				this.problem.getActions(),
				this.predicatePartitioner.getStrictlyTerminalSet());
		// intermediatePropositions.addAll((Collection<Fact>) this.terminalPcs);

		for (Fact p : this.terminalPcs)
			System.out.println("Intermediate goal: " + p);

		this.prevGoalDistMap = new HashMap<Fact, List<Double>>();
		// this.prevActionDistMap = new HashMap<Action, List<Double>>();
		this.goalLandmarks = new HashMap<Fact, List<Fact>>();

		int afgCount = 1;
		Collection<MutexSpace> mutexSpaces = new HashSet<MutexSpace>();
		// new for IPC5 domains -- have to remove spaces containing axioms
		for (MutexSpace ms : this.problem.singleMutexSpaces) // known mutex
																// spaces -- not
																// guaranteed to
																// be all
																// possible
																// mutex
																// relations
		{

			// while we're here, remove any SA or UA facts -- these are never
			// considered to be goals
			// for (Fact sa :
			// this.predicatePartitioner.getStrictlyActivatingSet())
			// {
			// // sa.setStatic(true);
			// ms.removeMutexes(sa);
			// }
			// for (Fact ua :
			// this.predicatePartitioner.getUnstableActivatingSet())
			// {
			// ms.removeMutexes(ua);
			// }

			HashSet<Fact> unreachable = new HashSet<Fact>();
			for (Fact f : ms.getKeys())
			{
				if (this.predicatePartitioner.getUnstableActivatingSet()
						.contains(f) == true
						|| this.predicatePartitioner.getStrictlyActivatingSet()
								.contains(f) == true)
				{
					unreachable.add(f);
				}

				if (problem.getReachableFacts().contains(f) == false)
					unreachable.add(f);
			}
			for (Fact u : unreachable)
			{
				ms.removeMutexes(u);
			}
		}
		for (MutexSpace ms : this.problem.singleMutexSpaces)
		{
			// mutex spaces with only a single member must be checked-for, in
			// case they contains only
			// a SA fact, which will cause exceptions to be thrown further down
			// the line when
			// initial probabilities are calculated. So if |MS| = 1, we check
			// the sole member to see
			// if it is indeed SA, and if so, don't add the MS
			if (ms.getKeys().size() == 1)
			{
				Fact single = ms.getKeys().iterator().next();
				if (this.predicatePartitioner.isStrictlyActivating(single)
						|| this.predicatePartitioner
								.isUnstableActivating(single))
				{
					continue;
				}
			}

			// need to add an All False mutex to the set, as it is possible that
			// none of the facts
			// in the parsed set are the goal. SAS+ does not detect this at
			// runtime, so we just assume
			// that it can be the case. In practice, one of the mutex facts will
			// often be true at any time.
			// However, in the case of single-literal mutex spaces, this is not
			// the case, so this kills two
			// birds with one stone.
			AllFalseGoal afg = new AllFalseGoal("AllFalse_" + afgCount++);

			// Mutex from AFG to all other goals in mutex space
			FactMutex newMutex = new FactMutex(afg);
			// add the AFG to all existing goals in the mutex set
			for (Entry<Fact, FactMutex> f : ms.getMutexMap().entrySet())
			{
				f.getValue().addMutex(afg);

				newMutex.addMutex(f.getKey());
			}

			// add the AFG itself
			ms.addMutex(newMutex);

			// add the modified MGS to the set of all known MGS
			mutexSpaces.add(ms);
		}

		// construct N goal spaces
		// System.out.println("Constructing "+IGRAPHPreferences.GoalSpace.toString()+" goal space");
		System.out.println("Constructing " + mutexSpaces.size()
				+ " variable goal-spaces");
		Set<MutexGoalSpace> subGoalSpaces = this
				.constructMutexGoalSpaces(mutexSpaces);

		if (IGRAPHPreferences.GoalSpace == GoalSpaceType.Map)
		{
			this.initialiseGoalSpace(subGoalSpaces);
		}
		// else if (IGRAPHPreferences.GoalSpace == GoalSpaceType.BDD)
		// {
		// this.goalSpace = new BDDGoalSpace(goalMutexSet, order);
		// }
		System.out.println("Finished goal space");

		Collection<Fact> allGoals = new HashSet<Fact>(this.goalSpace.getGoals());

		// initialise stability space
		this.stabilitySpace = new FactStabilitySpace();
		this.initialiseStabilitySpace(allGoals);

		// initialise goal achievers/destroyers
		this.goalAchievers = new HashMap<Fact, Set<Action>>();
		this.goalRemovers = new HashMap<Fact, Set<Action>>();
		this.goalPreconditions = new HashMap<Fact, Set<Action>>();
		this.initaliseGoalAchievers();

		/*
		 * Ignore anything which is an unstable or strictly activating fact, or
		 * is static.
		 */
		HashSet<Fact> ignored = new HashSet<Fact>();
		ignored.addAll(this.predicatePartitioner.getUnstableActivatingSet());
		ignored.addAll(this.predicatePartitioner.getStrictlyActivatingSet());
		ignored.addAll(this.problem.getStaticFacts());

		// initialise heuristics
		this.problem.setGoal(new And(allGoals));
		int cores = Runtime.getRuntime().availableProcessors();
		System.out.println("There are " + cores + " cores available");
		if (IGRAPHPreferences.MultiThreaded == false)
		{
			System.out.println("Running in single-core mode!");
			cores = 1;

			this.threadPool = Executors.newSingleThreadExecutor();
		}
		else
		{
			this.threadPool = Executors.newFixedThreadPool(cores); // I dont
																	// think
																	// this
																	// actually
																	// makes any
																	// difference
																	// in terms
																	// of
																	// runtime...
																	// the work
																	// is
																	// performed
																	// too
																	// quickly
																	// for
																	// threading
																	// to speed
																	// it up
		}

		this.heuristicManager = new ThreadedHeuristicManager(this.problem,
				cores);

		// update the heuristic being used
		this.heuristicManager.updateHeuristic(this.problem, allGoals);

		// initialise goal distances
		Map<Fact, Double> gdists = null;
		try
		{
			gdists = this.calculateCurrentGoalDistances();
		}
		catch (Exception e)
		{
			throw new RecognitionException("Error in instantiating goal-space",
					e);
		}

		for (Fact g : gdists.keySet())
		{
			// if dist is max_value, then it can never be reached
			if (gdists.get(g) == IHeuristic.Unreachable)
			{
				if (g instanceof Proposition)
				{
					ignored.add((Proposition) g);
				}
			}

			ArrayList<Double> list = new ArrayList<Double>();
			list.add(gdists.get(g));
			this.prevGoalDistMap.put(g, list);

			this.usefulDistanceMap.put(g, 0d);
			this.notUsefulDistanceMap.put(g, 0d);
			this.totalDistanceMap.put(g, 0d);
			this.goalSupportLengthMap.put(g, 0d);
			this.goalSupportThreadMap.put(g, new HashSet<PlanThread>());
		}

		// construct the CG analyser which will assign each variable a layer
		this.cgAnalyser = new CausalGraphAnalyser(this.causalGraph);
		System.out.println("Found " + cgAnalyser.getLevelCount()
				+ " layers in Causal Graph");
		for (Entry<DomainTransitionGraph, Integer> dtg : cgAnalyser.getLevels()
				.entrySet())
		{
			System.out.println(dtg.getKey().getVariable().getObject() + ", V("
					+ dtg.getKey().getVariable().getId() + ") = "
					+ dtg.getValue());
		}

		// use the output from the CG analyser to detect the minimum layer each
		// fact "appears" at
		this.setupInitialGoalLayers();

		// set the initial distribution based upon the requested format
		if (IGRAPHPreferences.InitialDistribution == InitialProbabilityDistributionType.UNIFORM)
		{
			this.goalSpace = this.setUniformInitialDistribution(true);

			HashSet<MutexGoalSpace> empty = new HashSet<MutexGoalSpace>();
			for (MutexGoalSpace mgs : ((VariableGoalSpace) this.goalSpace)
					.getVariableGoalSpaces())
			{
				if (mgs.size() == 0
						|| (mgs.size() == 1 && mgs.getGoals().iterator().next() instanceof AllFalseGoal))
					empty.add(mgs);
			}
			((VariableGoalSpace) this.goalSpace).getVariableGoalSpaces()
					.removeAll(empty);

		}
		else if (IGRAPHPreferences.InitialDistribution == InitialProbabilityDistributionType.CAUSALITYVALUE)
		{
			this.goalSpace = this.setCausalValueInitialDistribution();

			HashSet<MutexGoalSpace> empty = new HashSet<MutexGoalSpace>();
			for (MutexGoalSpace mgs : ((VariableGoalSpace) this.goalSpace)
					.getVariableGoalSpaces())
			{
				if (mgs.size() == 0
						|| (mgs.size() == 1 && mgs.getGoals().iterator().next() instanceof AllFalseGoal))
					empty.add(mgs);
			}
			((VariableGoalSpace) this.goalSpace).getVariableGoalSpaces()
					.removeAll(empty);

			// this call is not strictly necessary but is useful for debugging
			if (IGRAPHPreferences.VerifyGoalSpace)
				this.validateGoalSpace(this.goalSpace);

			// now update again using the CG layers as a normaliser. Lower level
			// will be more likely to be
			// part of a goal.
			this.updateInitialDistributionUsingLayers();
		}
		
		//set the initial probabilieis for each MutexGoalSpace
		this.saveBayesianProbabilities(true);

		
//		for (MutexGoalSpace mgs : ((VariableGoalSpace) this.goalSpace)
//				.getVariableGoalSpaces())
//		{
//			for (Fact f : mgs.getGoals())
//			{
//				System.out.println(f + " = " + mgs.getProbability(f));
//			}
//		}

		if (IGRAPHPreferences.VerifyGoalSpace)
			this.validateGoalSpace(this.goalSpace);

		// now set up normalised weighted probs
		this.initialHypothesisSpace = (IGoalSpace) this.goalSpace.clone();
		// this.initialHypothesisSpace = this.setWeightedInitialHypothesis();
		// //have a guess

		// now copy the initial goal space back into the working GS
		// this.goalSpace = (IGoalSpace) this.initialHypothesisSpace.clone();
		System.out.println("Goal space is of size "
				+ this.goalSpace.getGoals().size());
		System.out.println("Action space is of size "
				+ this.problem.getActions().size());

		// initialise the plan threader. This is used for the work function only
		// if needed, but
		// is also used by the next action prediction method
		// if (IGRAPHPreferences.WorkFunction == WorkFunctionType.MLThreaded)
		// {
		this.threader = new IterativeThreadScheduler(
				this.problem.getSTRIPSInitialState()); // disable looking for
														// controllers -- no
														// longer used
		// this.threader = new
		// IterativeThreadScheduler(this.problem.getSTRIPSInitialState(),
		// this.rootVars);
		// }

		// finally, have a guess at the final hypothesis using only the initial
		// distribution
		// try
		// {
		// int n = this.getEstimatedStepsRemaining();
		// this.initialHypothesis = this.getSingleBoundedHypothesis(n);
		// }
		// catch (UnknownEstimateException | InterruptedException |
		// ExecutionException e)
		// {
		// System.err.println("Unable to produce initial hypothesis estimation. Using immediate hypothesis instead.");
		//
		this.initialHypothesis = this.getImmediateGoalHypothesis();
		// }
	}

	/**
	 * Create the goal space which this recogniser will use. This implementation
	 * uses a {@link VariableGoalSpace}.
	 */
	protected void initialiseGoalSpace(Set<MutexGoalSpace> subGoalSpaces)
	{
		this.goalSpace = new VariableGoalSpace(subGoalSpaces);
		((VariableGoalSpace) this.goalSpace)
				.setGoalProbabilitySelector(VariableGoalSpace.GoalSetSelectorType.Average);
	}

	/**
	 * Returns the roots of the causal graph.
	 * 
	 * @param problem
	 *            The problem containing the causal graph.
	 * @return A set of parameters which are roots in the causal graph, or the
	 *         empty set if there are none.
	 */
	protected Set<Parameter> computeCausalRoots(HybridSasPddlProblem problem)
	{
		HashSet<Parameter> controllers = new HashSet<Parameter>();
		SASProblem sasproblem = problem.sasproblem;

		// the Roots of the CG are controller objects
		for (DomainTransitionGraph dtg : sasproblem.causalGraph.getRoots())
		{
			System.out.println("CG thinks " + dtg.getVariable().getObject()
					+ " is a root");
			if (dtg.getVariable().getObject() instanceof SASDomainObject)
				continue;

			// SAS+ has no types, so everything is of root type "Object". Need
			// to force types back in
			// by looking through PDDL problem for a matching signature
			Parameter sasParam = dtg.getVariable().getObject()
					.convertToPDDL(sasproblem);
			boolean found = false;
			for (Parameter pddl : problem.getObjects())
			{
				if (pddl.toString().equals(sasParam.toString()))
				{
					sasParam = pddl;
					found = true;
					controllers.add(sasParam);
					break;
				}
			}
			if (!found)
				throw new NullPointerException(
						"Could not find controller object \"" + sasParam
								+ "\" in PDDL domain");
		}

		return controllers;
	}

	/**
	 * Returns the leaves of the causal graph.
	 * 
	 * @param problem
	 *            The problem containing the causal graph.
	 * @return A set of parameters which are leaves in the causal graph, or the
	 *         empty set if there are none.
	 */
	protected Set<Parameter> computeCausalLeaves(HybridSasPddlProblem problem)
	{
		HashSet<Parameter> leaves = new HashSet<Parameter>();
		SASProblem sasproblem = problem.sasproblem;

		// the Roots of the CG are controller objects
		for (DomainTransitionGraph dtg : sasproblem.causalGraph.getLeaves())
		{
			System.out.println("CG thinks " + dtg.getVariable().getObject()
					+ " is a leaf");
			if (dtg.getVariable().getObject() instanceof SASDomainObject)
				continue;

			// SAS+ has no types, so everything is of root type "Object". Need
			// to force types back in
			// by looking through PDDL problem for a matching signature
			Parameter sasParam = dtg.getVariable().getObject()
					.convertToPDDL(sasproblem);
			boolean found = false;
			for (Parameter pddl : problem.getObjects())
			{
				if (pddl.toString().equals(sasParam.toString()))
				{
					sasParam = pddl;
					found = true;
					leaves.add(sasParam);
					break;
				}
			}
			if (!found)
				throw new NullPointerException("Could not find leaf object \""
						+ sasParam + "\" in PDDL domain");
		}

		return leaves;
	}

	/**
	 * Constructs N goal-spaces which correspond to the domains of SAS+
	 * variables/mutex groups.
	 * 
	 * @return
	 */
	protected Set<MutexGoalSpace> constructMutexGoalSpaces(
			Collection<MutexSpace> sets)
	{
		Set<MutexGoalSpace> spaces = new HashSet<MutexGoalSpace>();

		for (MutexSpace ms : sets)
		{
			if (ms.getMutexes().isEmpty())
				continue;

			MutexGoalSpace gs = new MutexGoalSpace(ms);
			spaces.add(gs);
		}

		return spaces;
	}

	/**
	 * Uses analysis of the causal graph to modify facts in the domain, such
	 * that a fact's initial probability will be equal to P(G) = CS / argmin(L)
	 * where CS is the causality score of the goal, and L is the minimum layer
	 * on which G appears as a member of the associated variable's DTG.
	 */
	protected void setupInitialGoalLayers()
	{
		// this.minimumCgLayers.clear();
		// int min = Integer.MAX_VALUE;
		// int max = Integer.MIN_VALUE;
		// for (Entry<DomainTransitionGraph, Integer> dtg :
		// this.cgAnalyser.getLevels().entrySet())
		// {
		// Integer curr = dtg.getValue();
		// for (SASLiteral sas : dtg.getKey().getVariable().getValues())
		// {
		// Fact pddl = sas.convertToPDDL(this.problem.sasproblem, this.problem);
		// if (pddl == null || this.goalSpace.getGoals().contains(pddl) ==
		// false)
		// continue;
		//
		// Integer prev = minimumCgLayers.get(pddl); //get previous min layer
		// if (prev == null) //if unseen, just add current layer value
		// {
		// minimumCgLayers.put(pddl, curr);
		// }
		// else //else, compare this layer against previous
		// {
		// if (curr < prev)
		// minimumCgLayers.put(pddl, curr);
		// }
		// }
		// }

		this.minimumCgLayers.clear();

		Map<DomainTransitionGraph, Integer> dtgLevels = this.cgAnalyser
				.getLevels();
		Map<Parameter, Integer> pddlLevels = new HashMap<Parameter, Integer>();
		for (Entry<DomainTransitionGraph, Integer> dtg : dtgLevels.entrySet())
		{
			try
			{
				Parameter pddlObject = dtg.getKey().getVariable().getObject()
						.convertToPDDL(this.problem.sasproblem, this.problem);

				pddlLevels.put(pddlObject, dtg.getValue());
			}
			catch (NullPointerException e)
			{
				// fail silently
			}
		}

		for (Fact f : this.goalSpace.getGoals())
		{
			double minLevel = Double.MAX_VALUE;
			double total = 0;
			SingleLiteral l = (SingleLiteral) f;
			// AFGs have a unique layer
			if (l instanceof AllFalseGoal)
			{
				// int lf = dtgLevels.get(((AllFalseGoal)l).);
				continue;
			}

			double validParamCount = 0;
			for (Parameter p : l.getParameters())
			{
				Integer paramLevel = pddlLevels.get(p);
				if (paramLevel == null)
				{
					// System.out.println("No level for "+p.toString());
					continue;
				}
				if (paramLevel < minLevel)
					minLevel = paramLevel;

				++validParamCount;
				total += paramLevel;
			}

			double avg = 0;
			if (validParamCount == 0)
			{
				// System.err.println("No valid parameters for "+f);
				avg = 1d;
			}
			else
			{
				avg = (total / validParamCount);
			}

			// do the average level
			this.minimumCgLayers.put(l, avg);

			// do the minimum level
			// if (minLevel == Double.MAX_VALUE)
			// minLevel = 1;
			// this.minimumCgLayers.put(l, minLevel);
		}
	}

	/**
	 * Updates the initial probability distribution using the layer which each
	 * fact exists on in the causal graph. This is simply the division of the
	 * current value by the minimum layer the fact exists on. The principle
	 * being that facts which exist near the bottom of the causal graph exist
	 * only to be moved around and influenced by others, meaning they are more
	 * likely to be goals. Conversely, if a goal is at the top of the graph, it
	 * is less likely to be involved direclty in a goal and more in the
	 * achievement of other goals.
	 */
	protected void updateInitialDistributionUsingLayers()
	{
		// now update the probabilities in the goal-space
		// for (Entry<Fact, Integer> minLayer : minimumCgLayers.entrySet())
		// {
		// double currProb = this.goalSpace.getProbability(minLayer.getKey());
		// double newProb = currProb / minLayer.getValue();
		//
		// this.goalSpace.setProbability(minLayer.getKey(), newProb);
		// }
		for (Fact g : this.goalSpace.getGoals())
		{
			double currProb = this.goalSpace.getProbability(g);
			double minLayer;
			double newProb;
			if (g instanceof AllFalseGoal)
			{
				minLayer = 0; // set AFGs to be have minimal probability
				// newProb = IGRAPHPreferences.Epsilon;
				newProb = currProb;
			}
			else
			{
				minLayer = this.minimumCgLayers.get(g);
				newProb = currProb / minLayer;

				// //FIXME this is only here as a convenient hack, move to
				// another method
				// double h = this.getCurrentPropDist(g);
				// if (h <= 0 || h == IHeuristic.Unreachable)
				// {
				// newProb = IGRAPHPreferences.Epsilon;
				// }
				// else
				// {
				// double hProb = newProb * (1 - (1d / h));
				//
				// newProb = hProb;
				// }
			}

			this.goalSpace.setProbability(g, newProb);
		}

		// remember to normalise!!!!
		this.normaliseGoalSpace(this.goalSpace);
	}

	/**
	 * Determines the actions which add, delete and require each fact in the
	 * {@link GroundProblem} used in observation.
	 */
	private void initaliseGoalAchievers()
	{
		for (Fact g : this.problem.getReachableFacts())
		{
			this.goalAchievers.put(g, new HashSet<Action>());
			this.goalRemovers.put(g, new HashSet<Action>());
			this.goalPreconditions.put(g, new HashSet<Action>());
		}

		for (Action a : this.problem.getActions())
		{
			for (Fact p : a.getAddPropositions())
			{
				if (this.goalAchievers.containsKey(p))
				{
					this.goalAchievers.get(p).add(a);
				}
			}

			for (Not p : a.getDeletePropositions())
			{
				if (this.goalRemovers.containsKey(p.getLiteral()))
				{
					this.goalRemovers.get(p.getLiteral()).add(a);
				}
			}

			// will also contain NOTs
			for (Fact p : a.getPreconditions())
			{
				if (this.goalPreconditions.containsKey(p))
				{
					this.goalPreconditions.get(p).add(a);
				}
			}
		}
	}

	/**
	 * Creates the {@link FactStabilitySpace} based upon the specified facts.
	 * 
	 * @param allFacts
	 *            The facts to add to the stability space.
	 */
	protected void initialiseStabilitySpace(Collection<Fact> allFacts)
	{
		this.stabilitySpace.clear();
		for (Fact f : allFacts)
		{
			boolean inInit = false;
			if (this.initialState.getTrueFacts().contains(f))
				inInit = true;

			// if (this.predicatePartitioner.isStrictlyTerminal(f))
			// this.stabilitySpace.addFact(new TerminalFactStability(f,
			// inInit));
			// else
			this.stabilitySpace.addFact(f, inInit);
			// this.stabilitySpace.addFact(f, true);
		}
	}

//	/**
//	 * Gets the total, absolute distance moved since observation started. This
//	 * is the sum of movement both from and towards the goal.
//	 * 
//	 * @param g
//	 *            The goal
//	 * @return The total distance moved.
//	 * @throws NullPointerException
//	 *             Thrown if the goal is unknown.
//	 */
//	public double getTotalDistanceMoved(Fact g) throws NullPointerException
//	{
//		if (this.totalDistanceMap.containsKey(g))
//			return this.totalDistanceMap.get(g);
//		else
//			return 0;
//	}

	/**
	 * Get the total distance which a goal has moved towards being achieved
	 * since observation started.
	 * 
	 * @param g
	 *            The goal
	 * @return The total distance moved nearer.
	 * @throws NullPointerException
	 *             Thrown if the fact is unknown.
	 */
	public double getDistanceMovedTowards(Fact g) throws NullPointerException
	{
		if (this.usefulDistanceMap.containsKey(g))
			return this.usefulDistanceMap.get(g);
		else
			return 0;
	}

	/**
	 * Get the total distance which a goal has moved away from being achieved
	 * since observation started. Note that this does not mean that the fact is
	 * not actually true in the current state.
	 * 
	 * @param g
	 *            The fact.
	 * @return The total distance moved away from being achieved.
	 * @throws NullPointerException
	 *             Thrown if the fact is unknown.
	 */
	public double getDistanceMovedAway(Fact g) throws NullPointerException
	{
		if (this.notUsefulDistanceMap.containsKey(g))
			return this.notUsefulDistanceMap.get(g);
		else
			return 0;
	}

	/**
	 * Find propositions which appear as an add effect *somewhere*, but are only
	 * used as preconditions to actions which achieve Strictly Terminal facts.
	 * 
	 * @param allActions
	 * @param stFacts
	 * @return
	 */
	private HashSet<Fact> getTerminalPreconditions(Set<Action> allActions,
			Set<Fact> stFacts)
	{
		HashSet<Fact> allEffects = new HashSet<Fact>();
		HashSet<Fact> allPcs = new HashSet<Fact>();
		HashSet<Fact> stPcs = new HashSet<Fact>();
		HashSet<Action> stActions = new HashSet<Action>();

		for (Action a : allActions)
		{
			allEffects.addAll(a.getAddPropositions());
			allPcs.addAll(a.getPreconditions());

			HashSet<Fact> aST = new HashSet<Fact>(a.getAddPropositions());
			aST.retainAll(stFacts);

			// if this actions effects contain any ST facts, note it's PCs
			// because
			// if these only appear as add effects in other non-ST actions, it
			// is probably not going to be a goal
			if (!aST.isEmpty())
			{
				stPcs.addAll(a.getPreconditions());
				stActions.add(a);
			}
		}

		HashSet<Fact> intermediates = new HashSet<Fact>();
		for (Fact stPc : stPcs)
		{
			boolean foundInNonSTAction = false;
			for (Action a : allActions)
			{
				if (stActions.contains(a))
					continue;

				if (a.getPreconditions().contains(stPc))
				{
					foundInNonSTAction = true;
					break;
				}
			}

			if (!foundInNonSTAction)
				intermediates.add(stPc);
		}

		return intermediates;
	}

	// /**
	// * Detects facts/predicates which only appear as a precondition to actions
	// which
	// * achieve Strictly Terminal facts.
	// * @param p
	// * @param actions
	// * @return
	// */
	// protected boolean getPrecursorFacts(Collection<Proposition> p,
	// Set<Action> actions)
	// {
	// HashSet<Proposition> precursors = new HashSet<Proposition>();
	//
	// for(Proposition st : this.predicatePartitioner.getStrictlyTerminalSet())
	// {
	//
	// }
	// }
	//

	/**
	 * Get the number of steps which have been observed since recognition began.
	 * 
	 * @return
	 */
	public int getObservedStepCount()
	{
		return this.history.states().size() - 1; // -1 for initial state
	}

	/**
	 * Gets the causal graph.
	 * 
	 * @return
	 */
	public CausalGraph getCausalGraph()
	{
		return causalGraph;
	}

	// /**
	// * Finds all landmarks which have been passed during transitions from the
	// initial to current state.
	// * @return A landmark graph.
	// */
	// protected LandmarkGraph<Action> findPastLandmarks()
	// {
	// this.regLmg.clearLandmarks();
	// this.regLmg.generateLandmarks(this.currentState.facts);
	//
	// return this.regLmg.getLandmarkGraph();
	// }

	// /**
	// * Construct landmarks for individual propositions.
	// */
	// protected void generateIndividualLandmarks()
	// {
	// this.goalLandmarks.clear();
	//
	// for (Fact p : this.goalSpace.getGoals())
	// {
	// if (p instanceof Proposition)
	// continue;
	//
	// regLmg.clearLandmarks();
	//
	// ArrayList<Fact> lms = new ArrayList<Fact>();
	// this.regLmg.generateLandmarks(p);
	// // this.dtgLmg.generateLandmarks(this.regLmg.getLandmarks());
	// //TODO add dtg lmg code
	//
	// if (this.regLmg.getLandmarks().size() > 1) //if more than one landmark
	// (original prop is included in list)
	// {
	// ArrayList<Fact> linear =
	// this.lineariseLandmarks(this.regLmg.getLandmarkGraph());
	// for (Fact lm : linear)
	// {
	// lms.add((Proposition)lm);
	// }
	// }
	//
	// this.goalLandmarks.put(p, lms);
	// }
	// }
	// @Deprecated
	// private ArrayList<Fact> lineariseLandmarks(LandmarkGraph graph)
	// {
	// LandmarkGraph graphCopy = graph.clone();
	// ArrayList<Fact> linearList = new ArrayList<Fact>();
	//
	// //do breadth first search over every node to find path costs
	// LinkedList<Fact> queue = new LinkedList<Fact>();
	// int depth = 0;
	//
	// Collection<Fact> verts = graphCopy.vertexSet();
	// for (Fact v : verts) //get root nodes of landmark graph
	// {
	// if (graphCopy.outDegreeOf(v) == 0)
	// {
	// queue.add(v);
	// }
	// }
	//
	// while (queue.isEmpty() == false)
	// {
	// Collection<Fact> heads = new HashSet<Fact>(queue);
	// queue.clear();
	//
	// Collection<Fact> pred = new HashSet<Fact>();
	// for (Fact hv : heads)
	// {
	// queue.addAll(graphCopy.getIncomingVertices(hv));
	//
	// linearList.add(0, hv);
	// }
	// }
	//
	// return linearList;
	// }

	/**
	 * Computes the distance to each goal in the goal-space, as determined by
	 * the current heuristic.
	 * 
	 * @return A mapping of facts to their estimates.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @see #getGoalSpace() The goal space used.
	 */
	protected Map<Fact, Double> calculateCurrentGoalDistances()
			throws InterruptedException, ExecutionException
	{
		Map<Fact, Double> newDists = new HashMap<Fact, Double>();

		System.out.println("Calculating new goal distances");

		HashSet<Fact> realGoals = new HashSet<Fact>();
		for (Fact g : this.getGoalSpace().getGoals())
		{
			if (g instanceof AllFalseGoal)
				continue; // TODO consider moving this code into heuristics?
							// they should really be able to handle seeing it

			realGoals.add(g);
		}

		this.heuristicManager.getEstimates(realGoals);

		for (Fact g : realGoals)
		{
			double e = this.heuristicManager.getCachedEstimates().get(g);

//			 if (this.prevGoalDistMap.containsKey(g))
//			 System.out.println("h("+g+") = "+e+", previous was "+this.prevGoalDistMap.get(g));

			newDists.put(g, e);
		}

		System.out.println("Finished computing distances");

		return newDists;
	}

	// protected HashMap<Action, Double> calculateCurrentActionDistances()
	// throws UnreachableGoalException, InterruptedException, ExecutionException
	// {
	// HashMap<Action, Double> newDists = new HashMap<Action, Double>();
	// HashMap<Fact, Set<Action>> actionPcMap = new HashMap<Fact,
	// Set<Action>>();
	//
	// Collection<Action> actions = this.problem.actions;
	// for (Action a : actions)
	// {
	// And pcGoal = new And();
	// for (Fact f : a.getPreconditions())
	// {
	// if (f.isStatic() || f instanceof Equals || (f instanceof Not &&
	// ((Not)f).literal instanceof Equals)) //ignore any equality tests -- they
	// are not goals
	// continue;
	//
	// pcGoal.add(f);
	// }
	//
	//
	// if (actionPcMap.containsKey(pcGoal) == false)
	// actionPcMap.put(pcGoal, new HashSet<Action>());
	//
	// actionPcMap.get(pcGoal).add(a);
	// }
	//
	// this.heuristicManager.getEstimates(actionPcMap.keySet());
	//
	// for (Entry<Fact, Set<Action>> e : actionPcMap.entrySet())
	// {
	// for (Action a : e.getValue())
	// {
	// //getEstimate should now provide a lookup value
	// newDists.put(a, this.heuristicManager.getEstimate(e.getKey()));
	// }
	// }
	//
	// return newDists;
	// }

	/**
	 * Convenience method for accessing the current estimate to a member of the
	 * goal-space.
	 * 
	 * @param p
	 * @return
	 */
	protected double getCurrentPropDist(Fact p)
	{
		List<Double> list = this.prevGoalDistMap.get(p);
		return list.get(list.size() - 1);
	}

	// /**
	// * Convenience method for accessing the current estimate to the specified
	// action becoming applicable.
	// * @param p
	// * @return
	// */
	// protected double getCurrentActionDist(Action a)
	// {
	// List<Double> list = this.prevActionDistMap.get(a);
	// return list.get(list.size()-1);
	// }

	/**
	 * Initialise the probability of each fact to be simply 1/(mutexset). If a
	 * fact has no mutexes (.e. is standalone), set it to 0.5.
	 * 
	 * @return
	 */
	protected IGoalSpace setUniformInitialDistribution(boolean resetFirst)
	{
		IGoalSpace defaultGoalSpace = (IGoalSpace) this.goalSpace.clone();
		if (resetFirst)
			defaultGoalSpace.reset();

		this.cvMap = new HashMap<Fact, Double>();
		for (MutexGoalSpace mgs : ((VariableGoalSpace) defaultGoalSpace)
				.getVariableGoalSpaces())
		{
			for (Fact g : mgs.getGoals())
			{
				FactMutex mut = mgs.getMutexSpace().getMutexes(g);
				if (mut == null)
					continue;

				double prob = 0;
				if (mut.hasMutexes() == false)
				{
					prob = 0.5; // if something is without mutexes, then it is
								// binary, as all static facts should be removed
								// by now
				}
				else
				{
					prob = 1d / mut.size();
				}

				mgs.setProbability(g, prob);
				this.cvMap.put(g, 1d);
			}
		}

		return defaultGoalSpace;
	}

	// /**
	// * Sets up a hypothesis space which has goals' initial probabilities
	// weighted depending on the result
	// * of domain analysis.
	// * @return
	// */
	// protected IGoalSpace setCustomInitialDistribution()
	// {
	// /*
	// * Looks for causal graph leaf node variables in parameters which are in
	// the initial state. If any are found, their
	// * probability of being a goal is reduced to a low level, as it is
	// unlikely they will remain unaffected throughout
	// * execution.
	// */
	// // //find init propositions which involve the leaf nodes, because they
	// are unlikely to be goals
	// IGoalSpace weightedGoalSpace = (IGoalSpace) this.goalSpace.clone();
	//
	//
	// Set<Proposition> initLeaves = new HashSet<Proposition>();
	// for (Fact f : this.currentState.getTrueFacts())
	// {
	// for (Parameter v : this.leafVars)
	// {
	// // System.out.println("Comparing prop "+ f.getParameters() +
	// " with var "+v);
	// for (Parameter po : ((SingleLiteral)f).getParameters())
	// {
	// if (po.equals(v))
	// {
	// initLeaves.add((Proposition) f);
	// }
	// }
	// }
	// }
	//
	// for (Fact p : this.predicatePartitioner.getTransientSet())
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p,
	// IGRAPHPreferences.PartitionTransient);
	// }
	// for (Fact p : this.predicatePartitioner.getWaypointSet())
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p, IGRAPHPreferences.PartitionWaypoint);
	// }
	// for (Fact p : this.predicatePartitioner.getUnstableTerminalSet())
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p,
	// IGRAPHPreferences.PartitionUnstableTerminal);
	// }
	// for (Fact p : this.predicatePartitioner.getStrictlyTerminalSet())
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p,
	// IGRAPHPreferences.PartitionStrictlyTerminal);
	// }
	// for (Fact p : this.predicatePartitioner.getBinarySet())
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p, IGRAPHPreferences.PartitionBinary);
	// }
	//
	// //FIXME no need to consider these, as they have been removed from the
	// goal space
	// for (Fact p : this.predicatePartitioner.getUnstableActivatingSet())
	// {
	// if (weightedGoalSpace.getMutexSpace().containsFact(p))
	// weightedGoalSpace.setProbability(p,
	// IGRAPHPreferences.PartitionUnstableActivating);
	// }
	// // for (Proposition p :
	// this.predicatePartitioner.getStrictlyActivatingSet())
	// // {
	// // if (weightedGoalSpace.getMutexSpace().containsFact(p))
	// // weightedGoalSpace.setProbability(p, 0);
	// // }
	//
	// for (Proposition p : initLeaves)
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p, 0.01f);
	// }
	//
	//
	// //explicitly set ignored props to 0 //TODO just remove ignored props from
	// groundproblem?
	// for (Fact p : this.ignoredPropsFilter.getFilterFacts())
	// {
	// if (weightedGoalSpace.getGoals().contains(p))
	// weightedGoalSpace.setProbability(p, 0.0f);
	// }
	//
	// //set conjunctions to highest individual value
	// // for (GroundCondition p : weightedGoalSpace.getGoals())
	// // {
	// // if (p instanceof AND)
	// // {
	// // double max = 0;
	// // for (Object f : p.getFacts())
	// // {
	// // double prob = weightedGoalSpace.getProbability((GroundCondition) f);
	// // if (prob > max)
	// // max = prob;
	// // }
	// // weightedGoalSpace.setProbability(p, max);
	// // }
	// // }
	//
	//
	// //probabilities are now not-normalised over all members of each mutex
	// set, so normalise them now
	// this.normaliseGoalSpace((VariableGoalSpace) weightedGoalSpace); //Ohai,
	// welcome to hackville
	//
	// return weightedGoalSpace;
	// }

	/**
	 * Determines how many actions add, delete and require a set of facts. This
	 * is referred to as the fact's "risk" of being deleted (i.e. not a goal).
	 * 
	 * @param actions
	 *            The actions used to determine these counts.
	 * @param allFacts
	 *            The facts to check.
	 * @return A map wherein each Fact is mapped to a {@link FactVector}, which
	 *         encapsulates these counts.
	 */
	protected Map<Fact, FactVector> detectFactRisks(Collection<Action> actions,
			Collection<Fact> allFacts)
	{
		// first, count up the adds and deletes across the action set
		HashMap<Fact, Integer> adds = new HashMap<Fact, Integer>();
		HashMap<Fact, Integer> deletes = new HashMap<Fact, Integer>();
		HashMap<Fact, Integer> requires = new HashMap<Fact, Integer>();

		for (Fact f : allFacts)
		{
			adds.put(f, 0);
			deletes.put(f, 0);
			requires.put(f, 0);
		}

		for (Action a : actions)
		{
			for (Fact f : a.getPreconditions())
			{
				if (f.isStatic() || f instanceof Equals || (f instanceof Not))// &&
																				// (((Not)f).literal
																				// instanceof
																				// Equals)))
					continue;

				requires.put(f, requires.get(f) + 1);
			}

			for (Fact f : a.getAddPropositions())
			{
				if (f.isStatic())
					continue;

				adds.put(f, adds.get(f) + 1);
			}

			for (Not f : a.getDeletePropositions())
			{
				if (f.getLiteral().isStatic()
						|| allFacts.contains(f.getLiteral()) == false)
					continue;

				deletes.put(f.getLiteral(), deletes.get(f.getLiteral()) + 1);
			}
		}

		HashMap<Fact, FactVector> vectors = new HashMap<Fact, FactVector>();
		// counts have now been assigned, so vectors can be created.
		for (Fact f : allFacts)
		{
			int addCount = adds.get(f);
			int delCount = deletes.get(f);
			int pcCount = requires.get(f);

			FactVector vec = new FactVector(f, addCount, delCount, pcCount);
			vectors.put(f, vec);
		}

		// if the fact is an AllFalseGoal, it is treated differently. Instead we
		// want to know how many times a a mutex fact is deleted, as this will
		// correspond to the likelihood of none of the positive literals being
		// the goal
		for (MutexGoalSpace ms : ((VariableGoalSpace) this.goalSpace)
				.getVariableGoalSpaces())
		{
			for (Fact f : ms.getGoals())
			{
				if (f instanceof AllFalseGoal)
				{
					int totalAdds = 0, totalDeletes = 0;
					for (Fact fOther : ms.getGoals())
					{
						if (f == fOther)
							continue;

						// NOTE the swapping of Adds and Deletes.
						int addCount = deletes.get(fOther);
						int delCount = adds.get(fOther);
						// int pcCount = requires.get(f);

						totalAdds += addCount;
						totalDeletes += delCount;
					}

					int pcCount = 0; // could have negative PC support here
										// instead of 0

					FactVector vec = new FactVector(f, totalAdds, totalDeletes,
							pcCount);
					vectors.put(f, vec);
				}
			}
		}

		return vectors;
	}

	/**
	 * Sets the current goal space to have a "causal value" initial
	 * distribution. This means that the probability of a literal being the goal
	 * is determined by how many actions within the domain can delete it, such
	 * that P(G) = |adds| / (|adds| + |deletes| + |requires|). The resulting
	 * distribution is then normalised.
	 * 
	 * @return A copy of the current goal space with the probabilities weighted
	 *         as specified. The original goal space is not modified.
	 */
	public IGoalSpace setCausalValueInitialDistribution()
	{
		// vector space used for convenience
		Map<Fact, FactVector> vectors = this.detectFactRisks(
				this.problem.getActions(), this.problem.getReachableFacts());

		/*
		 * now compute the actual probabilities as follows:
		 * 
		 * C(G) = (|adds|/(|adds|+|dels|+|reqs|))
		 */

		VariableGoalSpace defaultGoalSpace = (VariableGoalSpace) this.goalSpace
				.clone();
		double alpha = 1d;

		this.cvMap = new HashMap<Fact, Double>();

		for (IGoalSpace sgs : defaultGoalSpace.getVariableGoalSpaces())
		{
			for (Fact g : sgs.getGoals())
			{
				if (g instanceof AllFalseGoal)
				{
					// sgs.setProbability(g, IGRAPHPreferences.Epsilon);

					// double p = 1d/sgs.size();
					// sgs.setProbability(g, p);

					double allAdds = 0, allDels = 0, allPcs = 0;
					for (Fact h : sgs.getGoals())
					{
						if (g == h)
							continue;

						allAdds += vectors.get(h).getAdded();
						allDels += vectors.get(h).getDeleted();
						allPcs += vectors.get(h).getRequires();

					}

					double p = allDels / (allDels + allAdds + allPcs);

					p = (IGRAPHPreferences.Lambda * p)
							+ ((1 - IGRAPHPreferences.Lambda) * 1d / sgs.size());

					sgs.setProbability(g, p);

					continue;
				}

				FactVector vector = vectors.get(g);

				double prob = (double) vector.getAdded();

				// if fact is unachievable -- remove from GS
				// if (prob == 0d)
				// {
				// boolean removed = sgs.removeGoal(g);
				// if (removed == false)
				// throw new
				// NullPointerException("Failed to remove fact "+g+" from goal space");
				//
				//
				// continue;
				// }
				//
				double risk = (double) (vector.getAdded() + vector.getDeleted() + vector
						.getRequires());

				prob = alpha * (prob / risk);
				prob = (IGRAPHPreferences.Lambda * prob)
						+ ((1 - IGRAPHPreferences.Lambda) * 1d / sgs.size());

				this.cvMap.put(g, prob);

				sgs.setProbability(g, (double) prob);
			}

		}

		this.normaliseGoalSpace((VariableGoalSpace) defaultGoalSpace);

		return defaultGoalSpace;
	}

	/**
	 * Normalises all probabilities associated with the specified goal-space,
	 * such that the sum of all probabilities is 1.
	 * 
	 * @param gs
	 *            The goal space to normalise.
	 */
	protected void normaliseGoalSpace(IGoalSpace gs)
	{

		if (gs instanceof VariableGoalSpace)
		{
			for (MutexGoalSpace space : ((VariableGoalSpace) gs)
					.getVariableGoalSpaces())
			{
				this.normaliseGoalSpace(space);
			}
			return;
		}

		double total = 0f;
		for (Fact g : gs.getGoals())
		{
			total += gs.getProbability(g);

		}

		for (Fact g : gs.getGoals())
		{
			double normalised = gs.getProbability(g) / total;
			gs.setProbability(g, normalised);
		}

		// Set<MutexSet> sets = gs.getMutexSpace().getMutexSets();
		// for (MutexSet ms : sets)
		// {
		// double total = 0f;
		// for (Fact f : ms.getFacts())
		// {
		// total += gs.getProbability(f);
		// }
		//
		// for (Fact f : ms.getFacts())
		// {
		// double normalised = gs.getProbability(f) / total;
		// gs.setProbability(f, normalised);
		// }
		// }
	}

	/**
	 * Returns the goal space constructed which represents the initial
	 * hypothesis about the problem. This is assigned when this object was
	 * created.
	 * 
	 * @return
	 */
	public IGoalSpace getInitialHypothesisSpace()
	{
		return initialHypothesisSpace;
	}

	/**
	 * Gets the hypothesis created prior to observation starting. This is final
	 * once computed.
	 * 
	 * @return
	 */
	public IGoalHypothesis getInitialHypothesis()
	{
		return this.initialHypothesis;
	}

	/**
	 * Convenience method for performing updates on the goal-space
	 * probabilities.
	 * 
	 * @param a
	 *            The last observation which will be used to update the
	 *            goal-space.
	 * @see #updateBayesianProbabilities(Action)
	 */
	protected void updateGoalSpace(Action a) throws RecognitionException
	{
		this.updateBayesianProbabilities(a);

		if (IGRAPHPreferences.VerifyGoalSpace)
		{
			this.validateGoalSpace(this.goalSpace);
		}

	}

	/**
	 * Debug method which verifies that the probabilities of each relaxed mutex
	 * set in the goal space adds up to 1. This has a standard error of
	 * {@link BayesianGoalRecogniser#NormalisedError}, because it is unlikely
	 * that 1 will be achieved after rounding errors are accounted for.
	 */
	protected void validateGoalSpace(IGoalSpace gs) throws RecognitionException
	{
		if (gs instanceof VariableGoalSpace)
		{
			for (MutexGoalSpace mgs : ((VariableGoalSpace) gs)
					.getVariableGoalSpaces())
			{
				this.validateGoalSpace(mgs);
			}
			return;
		}

		MutexGoalSpace mgs = (MutexGoalSpace) gs;
		if (mgs.size() == 1)
		{
			Fact g = mgs.getGoals().iterator().next();
			double prob = mgs.getProbability(g);
			if (prob < 0 || prob > 1)
				throw new RecognitionException("Illegal goal space. "
						+ gs.toString() + " is outwith bounds [0:1]");

			return; // else its within 0:1
		}
		else if (mgs.size() == 0) // Conceivable that there could be a zero-size
									// MS hanging around, so ignore it if it is
			return;

		double total = 0;
		// System.out.println("Totalling the mutex set");
		for (Fact f : mgs.getGoals())
		{
			double prob = mgs.getProbability(f);
			total += prob;

			// System.out.println(f.toString()+" = "+prob+" ("+total+")");
		}

		total = Math.round(total * NormalisedError) / NormalisedError;

		if (total != 1d)
			throw new RecognitionException("Illegal goal space. "
					+ gs.toString() + " sums to " + total);

	}

	// /**
	// *
	// * @param a
	// * @throws ExecutionException
	// * @throws InterruptedException
	// * @throws UnreachableGoalException
	// */
	// protected void updateActionSpace(Action observed) throws
	// UnreachableGoalException, InterruptedException, ExecutionException
	// {
	// HashSet<Action> further = new HashSet<Action>();
	// HashSet<Action> nearer = new HashSet<Action>();
	// HashSet<Action> unmoved = new HashSet<Action>();
	//
	// //find propositions which are being moved towards, and those that are
	// being moved away from
	// HashMap<Action, Double> newDists =
	// this.calculateCurrentActionDistances();
	//
	// //have to add newDists for all props here, because the distances to
	// mutexes of a goal G will be required
	// //when computing P(A|G}
	// for (Action g : newDists.keySet())
	// {
	// this.prevActionDistMap.get(g).add(newDists.get(g));
	// }
	//
	// for (Action a : newDists.keySet())
	// {
	// //update with new distance which will be used in the remainder of the
	// loop
	//
	// double oldDist =
	// this.prevActionDistMap.get(a).get(this.prevActionDistMap.get(a).size() -
	// 2); //get t-1 dist before updating with new distances
	// double newDist = newDists.get(a);
	//
	// this.prevActionDistMap.get(a).add(newDists.get(a));
	//
	// if (newDist < oldDist) //if moving towards
	// {
	// // System.out.println("Nearer: " + a);
	// nearer.add(a);
	// // suspects.add(p);
	// }
	// else if (newDist > oldDist)
	// {
	// // System.out.println("Further");
	// further.add(a);
	// // this.allMovedAwayProps.add(p);
	// }
	// else //else dist has remained the same
	// {
	// unmoved.add(a);
	// // System.out.println("No change");//do nothing
	// }
	//
	// //no probabilities to update for now
	// }
	// Collection<Action> highest = new HashSet<Action>();
	// double h = 0;
	//
	// // for (Action a : nearer)
	// // {
	// // double p = this.getActionProbability(a, 0);
	// // if (p > h)
	// // {
	// // highest.clear();
	// // highest.add(a);
	// // }
	// // else if (p == h)
	// // highest.add(a);
	// // }
	// //
	// //
	// System.out.println("Unofficial next action is "+highest.iterator().next());
	//
	// }

	// /**
	// * Determines whether the specified proposition appears as a landmark
	// anywhere in the known set of landmarks.
	// * @param p
	// * @return The set of landmark orderings which the proposition appears in.
	// */
	// protected Collection<List<Fact>> getLandmarkLists(Fact p)
	// {
	// HashSet<List<Fact>> coll = new HashSet<List<Fact>>();
	//
	// for (List<Fact> list : this.goalLandmarks.values())
	// {
	// if (list.contains(p))
	// {
	// coll.add(list);
	// }
	// }
	//
	// return coll;
	// }

	// /**
	// * Determines whether the specified proposition appears as a landmark on
	// only 1 of the known set of landmarks.
	// * @param p
	// * @return The landmark orderings which the proposition appears in. Null
	// if more than one set is found or
	// * no landmarks exist for that proposition.
	// * @deprecated
	// */
	// protected List<Fact> getUniqueLandmarkLists(Fact p)
	// {
	// HashSet<List<Fact>> coll = new HashSet<List<Fact>>();
	//
	// for (List<Fact> list : this.goalLandmarks.values())
	// {
	// if (list.contains(p))
	// {
	// coll.add(list);
	// }
	// }
	//
	//
	// if (coll.size() == 1)
	// return coll.iterator().next();
	// else
	// return null;
	// }

	// /**
	// * Does a specified proposition have any previous landmarks associated
	// with it.
	// * @deprecated
	// */
	// protected boolean hasLandmarks(Fact p)
	// {
	// return this.goalLandmarks.get(p) != null;
	// }
	//
	// /**
	// * Does a specified proposition appear anywhere in another proposition's
	// landmark lists,
	// * @deprecated
	// */
	// protected HashSet<List<Fact>> isInLandmarkList(Fact p)
	// {
	// HashSet<List<Fact>> coll = new HashSet<List<Fact>>();
	//
	// if (this.hasLandmarks(p) == false)
	// return null;
	//
	// for (List<Fact> list : this.goalLandmarks.values())
	// {
	// //if it is in the list but the list tail is not p itself
	// if (list.contains(p) && list.indexOf(p) != list.size() - 1)
	// {
	// coll.add(list);
	// }
	// }
	//
	// return (coll.size() > 0) ? coll : null;
	// }

	// /**
	// * Determines whether the specified proposition has a parameter of the
	// same type as a leaf variable in the
	// * causal graph.
	// * @param p
	// * @return
	// */
	// protected boolean containsLeafVariable(GroundFact p)
	// {
	// for (Object propo : p.getFacts())
	// {
	// Proposition prop = (Proposition) propo;
	// for (Object po : prop.getParameters())
	// {
	// Parameter par = (Parameter)po;
	// for (Parameter v : this.leafVars)
	// {
	// if (par.getType().equals(v.getType()))
	// return true;
	// }
	// }
	// }
	//
	// return false;
	// }

	/**
	 * Gets the number of milliseconds which have passed since execution began.
	 * 
	 * @return
	 */
	protected long getCurrentTimeOffset()
	{
		return System.nanoTime() - this.startTime;
	}

	/**
	 * Indicates that an action has been observed which can be associated with
	 * an agent.
	 * 
	 * @param a
	 * @param source
	 *            Currently not used
	 * @throws ExecutionException
	 * @throws InterruptedException
	 * @throws UnreachableGoalException
	 * @throws RecognitionException
	 */
	public void actionObserved(Action a, IAgent source)
			throws SchedulingException, InterruptedException,
			ExecutionException, RecognitionException
	{
		this.currentActionDistMap.clear();

		for (Fact add : a.getAddPropositions())
		{
			if (this.predicatePartitioner.getStrictlyTerminalSet()
					.contains(add))
			{
				this.stAchievedFacts.add(add);

				for (Fact p : this.goalSpace.getMutexSpace().getMutexes(add)
						.getOthers())
					this.stAchievedMutexFacts.add(p);
			}
		}

//		 this.planSoFar.addAction(a); //add once timestamped instead

		// update the current state using the observation
		this.updateState(a);

		long startTime = System.nanoTime();
		this.updateHeuristic();

		long endTime = System.nanoTime();
		long duration = endTime - startTime;
		System.out.println("Heuristic update took " + duration / 1000000
				+ " millis");

		// update the history
		startTime = System.nanoTime();
		this.updateHistory(a);
		endTime = System.nanoTime();
		duration = endTime - startTime;
		System.out.println("History update took " + duration / 1000000
				+ " millis");

		// update the thread graph. This requires that the heuristic and goal
		// estimates have been updated.
		startTime = System.nanoTime();
		TimeStampedAction tsObservation = this.updateThreader(a);
//		TimeStampedAction tsObservation = new TimeStampedAction(a, new BigDecimal(this.getObservedStepCount() - 1), BigDecimal.ONE); //hack to ignore threading
		this.planSoFar.addAction(tsObservation); //TODO this really should be done prior to this
		endTime = System.nanoTime();
		duration = endTime - startTime;
		System.out.println("Threader update took " + duration / 1000000
				+ " millis");

		// //get actions which were previously applicable for use in Bayesian
		// probability calculation
		// this.prevApplicable =
		// this.getApplicableActions(this.history.get(this.history.states().size()-1).getState());

		// if an unstable activating fact has been deleted, remove any actions
		// which depend on this as a precondition
		// because it will never be re-achieved
		// HashSet<Action> toRemove = new HashSet<Action>();
		Collection<? extends Fact> allGoals = this.goalSpace.getGoals(); // for
																			// faster
																			// lookups
		for (Not del : tsObservation.getDeletePropositions())
		{
			if (this.predicatePartitioner.getUnstableActivatingSet().contains(
					del.getLiteral()))
			{
				// if an UT fact has been removed, then all actions which
				// require it can be ignored from now on
				this.problem.getActions().removeAll(
						this.goalPreconditions.get(del.getLiteral()));

				System.out.println("Unstable Activating fact " + del.getLiteral()
						+ " was deleted. Culling from goal-space...");
				this.goalSpace.removeGoal(del.getLiteral());
			}

			// if the fact is not a UA fact, find out more generally if it can
			// be re-achieved after
			// being deleted -- as the actions which achieve it may have first
			// relied upon a UA fact
			// This can be done quickly by finding out if the preconditions of
			// actions which achieve
			// the deleted literal are still present in the goal-space
			Set<Action> achievers = this.goalAchievers.get(del.getLiteral());
			boolean found = false;
			if (achievers != null)
			{
				out: for (Action ach : achievers)
				{
					for (Fact pc : ach.getPreconditions())
					{
						if (pc instanceof Not || pc instanceof Equals
								|| pc.isStatic())
							continue;

						if (allGoals.contains(pc) == false)
						{
							continue;
						}
					}
					found = true;
					break;

				}
			}
			if (found == false)
			{
				System.out
						.println("Fact "
								+ del.getLiteral()
								+ " is no longer achievable. Culling from goal-space...");
				this.goalSpace.removeGoal(del.getLiteral());
			}
		}

		// this.problem.actions.removeAll(toRemove);

		// TODO this doesn't make much sense- actions observed are not buffered
		// so they are effectively ignored upon update
		if (this.updateAfterObservation == false)
			return;

		// update the stabilities
		this.updateStabilitySpace(tsObservation);

		// update the probabilities
		startTime = System.nanoTime();
		this.updateGoalSpace(tsObservation);
		endTime = System.nanoTime();
		duration = endTime - startTime;
		System.out.println("Goal-space update took " + duration / 1000000
				+ " millis");

		// find new distances to action application
		// startTime = System.nanoTime();
		// this.updateActionSpace(a);
		// endTime = System.nanoTime();
		// duration = endTime - startTime;
		// System.out.println("Action space update took "+duration/1000000+" millis");
	}

	/**
	 * Updates the heuristic used in recognition using the current goal-space.
	 * 
	 * @see ThreadedHeuristicManager#updateHeuristic(HybridSasPddlProblem,
	 *      Collection)
	 */
	protected void updateHeuristic()
	{
		// update heuristics- RPGs, actions etc
		this.heuristicManager.updateHeuristic(this.problem,
				(Collection<Fact>) this.goalSpace.getGoals());

		//debug output
//		for (Fact f : this.goalSpace.getGoals())
//		{
//			if (f instanceof AllFalseGoal)
//				continue;
//			
//			double h = this.getCurrentPropDist(f);
//			System.out.println(f+" = "+h);
//		}
	}

	// /**
	// * Removes the specified goal from the goal space and associated mutex
	// space.
	// * @param literal
	// */
	// protected void cullGoalSpace(IGoalSpace gs, Fact literal)
	// {
	// if (gs instanceof VariableGoalSpace)
	// {
	// for (MutexGoalSpace mgs :
	// ((VariableGoalSpace)gs).getVariableGoalSpaces())
	// {
	// if (mgs.getGoals().contains(literal))
	// this.cullGoalSpace(mgs, literal);
	// }
	// ((VariableGoalSpace)gs).removeGoal(literal);
	// return;
	// }
	//
	// gs.getMutexSpace().removeMutexes(literal);
	// gs.removeGoal(literal);
	// }

	/**
	 * Updates the current state, based upon the effects of an observation.
	 * 
	 * @param a
	 *            The observed action.
	 */
	protected void updateState(Action a)
	{
		// STRIPSState prevState = (STRIPSState) this.currentState.clone();
		this.currentState = (STRIPSState) this.currentState.apply(a); // apply
																		// action
																		// to
																		// current
																		// state
																		// and
																		// add
																		// it to
																		// the
																		// history

		this.problem.updateState(a);
	}
	
	/**
	 * Get the number of steps a fact has been true. That is, if a fact is true in the current state, for how many observations
	 * prior to now has that fact been true.
	 * @param g The fact to check.
	 * @return The number of consecutive timesteps which this fact has been true for.
	 */
	protected int getTrueStepsCount(Fact g)
	{
		if (this.getCurrentState().isTrue(g) == false)
		{
			return 0;
		}
		
		int count = 1;
		StateHistory hist = this.getHistory();
		//can start at 1 and skip the current stat because we know that G is true by this point
		for (int i = hist.states().size() - 1; i >= 0; i--)
		{
			StateHistoryTuple t = hist.get(i);
			if (t.getState().isTrue(g))
				++count;
			else
				break;
		}
		
		return count;
	}
	

	/**
	 * Updates the history. This involves computing the distances to all members
	 * of the goal-space given the observed action. This then stores the current
	 * state, observed action and all goals which have become nearer/further
	 * etc.
	 * 
	 * @see #calculateCurrentGoalDistances() Computes the distances to each goal
	 *      in the goal-space given the state produced by the observation
	 *      specified.
	 * @param a
	 *            An observed action.
	 * @throws InterruptedException
	 *             Thrown if there was a problem related to computing the goal
	 *             distances.
	 * @throws ExecutionException
	 */
	protected void updateHistory(Action a) throws InterruptedException,
			ExecutionException
	{
		//initialise to null -- the last iteration of the for loop (which should be guaranteed to run
		//as long as there has been at least 12 observation), will contain the values we want at the end
		//of the method (i.e. the last observation).
		HashSet<Fact> further = null;
		HashSet<Fact> nearer = null;
		HashSet<Fact> unmoved = null;
		
		this.usefulDistanceMap.clear();
		this.notUsefulDistanceMap.clear();

		// find propositions which are being moved towards, and those that are
		// being moved away from
		Map<Fact, Double> newDists = this.calculateCurrentGoalDistances();

		// have to add newDists for all props here, because the distances to
		// mutexes of a goal G will be required
		// when computing P(A|G}
		for (Fact g : newDists.keySet())
		{
			this.prevGoalDistMap.get(g).add(newDists.get(g));
			
			this.usefulDistanceMap.put(g, 0d);
			this.notUsefulDistanceMap.put(g, 0d); //need to reset the distance maps
		}
		
		
		//need to loop over all observed steps, as a step which was helpful at time t may not be helpful at time t+n
		//FIXME god damn this is some inefficient code
		//the +1 is a semi-hack because we need to update the history, but the number of observed states comes
		//from the size of the history itself. Thus if we have only seen 1 observation, the size of the history
		//returned will be 1, as only the initial state is stored. Therefore, we assume that by calling this method,
		//there has implicitely been an observation which this update is going to add another
		//history tuple for.
		for (int j = 1; j <= this.getObservedStepCount()+1; j++) 
		{

			 //clear at start of iteration -- that means that on 
			//last iteration these will contain the values for the last observation, which can be added to the history

			further = new HashSet<Fact>();
			nearer = new HashSet<Fact>();
			unmoved = new HashSet<Fact>();
			
			boolean allFurther = true;
			
			
			for (Fact g : newDists.keySet())
			{
				// skip AFGs -- they are dealt with in the respective likelihood
				// functions
				if (g instanceof AllFalseGoal)
				{
					continue;
				}

				//the number of consecutive steps which this fact has been true for (if any)
				int stepsTrue = this.getTrueStepsCount(g);
	
				double oldDist = this.prevGoalDistMap.get(g).get(j-1); // dist at t-1
				double newDist = this.prevGoalDistMap.get(g).get(j); // current dist
				// double absDiff = Math.abs(oldDist - newDist); //absolute value
				double absDiff = 0; // the above line assumes optimal heuristics --
									// differences greater than 1 are possible
	
				if (newDist < oldDist) // if moving towards
				{
					allFurther = false;
					
					nearer.add(g);
					absDiff = 1;
											
	
					// update the total distance nearer for this goal
					// This used to read as
					// "this.usefulDistanceMap.put(g, this.usefulDistanceMap.get(g) + (oldDist - newDist));"
					// but, was changed to just +1 because only a maximum of 1 unit
					// of work can have been performed (fully observable!)
					// if there was a difference of > 1, this was down to a poor
					// heuristic estimate
					this.usefulDistanceMap.put(g, this.usefulDistanceMap.get(g)
							+ absDiff);
					// System.out.println("Nearer: "+g);
				}
				else if (newDist > oldDist)
				{
					further.add(g);
					absDiff = 1;
					double notUseful = this.getDistanceMovedAway(g);
					this.notUsefulDistanceMap.put(g, notUseful + absDiff);
					// System.out.println("Further: "+g);
				}
				else
				// else dists are the same
				{
					if (newDist == 0 && oldDist == 0)
					{
						if (this.areZeroStepsHelpful() && stepsTrue > 1) //only apply bonus if fact is true for at least previous 2 timesteps
						{
							double u = this.usefulDistanceMap.get(g) + 1;
							// double t = this.totalDistanceMap.get(g) + 1;
							absDiff = 1;
							this.usefulDistanceMap.put(g, u);
							// this.totalDistanceMap.put(g, t); //add an extra 1
							// here, because diff is 0
							// System.out.println("Unmoved: "+g);
						}
					}

					unmoved.add(g); // always add to correct set
				}
				
				//TODO deprecated-- gets updated on every observed action iteration instead of the final observation
//	
//				// update the total distance nearer for this goal
//				double newTotal = this.totalDistanceMap.get(g) + absDiff;
//				assert(newTotal >= this.totalDistanceMap.get(g) - 1 && newTotal <= this.totalDistanceMap.get(g) + 1);
//				double oldTotal = this.totalDistanceMap.get(g);
//				this.totalDistanceMap.put(g, newTotal);
			}
			
			//update the history for the respective timepoint with the new values for unmoved, nearer and further facts
			this.history.get(j-1).setFurther(further);
			this.history.get(j-1).setNearer(nearer);
			this.history.get(j-1).setUnmoved(unmoved);

		}

		this.history.add((STRIPSState) this.currentState.clone(), a,
				this.getCurrentTimeOffset(), nearer, further, unmoved);
		
//		//sanity check
//		int c = 1;
//		for (StateHistoryTuple t : this.history)
//		{
//			if (t.getNearer().toString().contains("at bob s90"))
//				System.out.println("Found nearer goal at "+c);
//			
//			++c;
//		}
	}
	
	//old iteritive way -- new method recalculates total distance moved based on bonuses
//
//	/**
//	 * Updates the history. This involves computing the distances to all members
//	 * of the goal-space given the observed action. This then stores the current
//	 * state, observed action and all goals which have become nearer/further
//	 * etc.
//	 * 
//	 * @see #calculateCurrentGoalDistances() Computes the distances to each goal
//	 *      in the goal-space given the state produced by the observation
//	 *      specified.
//	 * @param a
//	 *            An observed action.
//	 * @throws InterruptedException
//	 *             Thrown if there was a problem related to computing the goal
//	 *             distances.
//	 * @throws ExecutionException
//	 */
//	protected void updateHistory(Action a) throws InterruptedException,
//			ExecutionException
//	{
//		HashSet<Fact> further = new HashSet<Fact>();
//		HashSet<Fact> nearer = new HashSet<Fact>();
//		HashSet<Fact> unmoved = new HashSet<Fact>();
//
//		// find propositions which are being moved towards, and those that are
//		// being moved away from
//		Map<Fact, Double> newDists = this.calculateCurrentGoalDistances();
//
//		// have to add newDists for all props here, because the distances to
//		// mutexes of a goal G will be required
//		// when computing P(A|G}
//		for (Fact g : newDists.keySet())
//		{
//			this.prevGoalDistMap.get(g).add(newDists.get(g));
//		}
//
//		for (Fact g : newDists.keySet())
//		{
//			// skip AFGs -- they are dealt with in the respective likelihood
//			// functions
//			if (g instanceof AllFalseGoal)
//			{
//				continue;
//			}
//			
//			//the number of consecutive steps which this fact has been true for (if any)
//			int stepsTrue = this.getTrueStepsCount(g);
//
//			// update with new distance which will be used in the remainder of
//			// the loop
//
//			double oldDist = this.prevGoalDistMap.get(g).get(
//					this.prevGoalDistMap.get(g).size() - 2); // dist at t-1
//			double newDist = newDists.get(g); // current dist
//			// double absDiff = Math.abs(oldDist - newDist); //absolute value
//			double absDiff = 0; // the above line assumes optimal heuristics --
//								// differences greater than 1 are possible
//
//			if (newDist < oldDist) // if moving towards
//			{
//				nearer.add(g);
//				absDiff = 1;
//
//				// update the total distance nearer for this goal
//				// This used to read as
//				// "this.usefulDistanceMap.put(g, this.usefulDistanceMap.get(g) + (oldDist - newDist));"
//				// but, was changed to just +1 because only a maximum of 1 unit
//				// of work can have been performed
//				// if there was a difference of > 1, this was down to a poor
//				// heuristic estimate
//				this.usefulDistanceMap.put(g, this.usefulDistanceMap.get(g)
//						+ absDiff);
//				// System.out.println("Nearer: "+g);
//			}
//			else if (newDist > oldDist)
//			{
//				further.add(g);
//				absDiff = 1;
//				double notUseful = this.getDistanceMovedAway(g);
//				this.notUsefulDistanceMap.put(g, notUseful + absDiff);
//				// System.out.println("Further: "+g);
//			}
//			else
//			// else dists are the same
//			{
//				if (newDist == 0 && oldDist == 0)
//				{
//					if (this.areZeroStepsHelpful() && stepsTrue > 0) //only apply bonus if fact is true in current state
//					{
//						double u = this.usefulDistanceMap.get(g) + 1;
//						// double t = this.totalDistanceMap.get(g) + 1;
//						absDiff = 1;
//						this.usefulDistanceMap.put(g, u);
//						// this.totalDistanceMap.put(g, t); //add an extra 1
//						// here, because diff is 0
//						// System.out.println("Unmoved: "+g);
//					}
//				}
//				// else
//				// {
//				// //update the total distance nearer for this goal
//				// // this.usefulDistanceMap.put(g,
//				// this.usefulDistanceMap.get(g) + 1);
//				// unmoved.add(g);
//				// }
//				unmoved.add(g); // always add to correct set
//			}
//
//			// update the total distance nearer for this goal
//			double newTotal = this.totalDistanceMap.get(g) + absDiff;
//			this.totalDistanceMap.put(g, newTotal);
//
//		}
//
//		this.history.add((STRIPSState) this.currentState.clone(), a,
//				this.getCurrentTimeOffset(), nearer, further, unmoved);
//	}
	
	/**
	 * Gets the {@link PlanThread} which the last observation was added to.
	 * @return The thread, or null if no action has been observed yet.
	 */
	public PlanThread getLastPlanThread()
	{
		if (this.allPlanThreads.isEmpty())
			return null;
		else
			return allPlanThreads.get(this.allPlanThreads.size() - 1);
	}
	
	/**
	 * Get the list of plan threads of length T which has the same length as the number of observations
	 * processed. The plan thread at index t is that which observation t was added to.
	 * @return
	 */
	public List<PlanThread> getAllPlanThreads()
	{
		return allPlanThreads;
	}
	
	/**
	 * Set the list of plan threads of length T which has the same length as the number of observations
	 * processed. The plan thread at index t is that which observation t was added to.
	 */
	public void setAllPlanThreads(List<PlanThread> allPlanThreads)
	{
		this.allPlanThreads = allPlanThreads;
	}
	
	/**
	 * Sets the {@link PlanThread} which the last observation was added to.
	 */
	public void setLastPlanThread(PlanThread lastPlanThread)
	{
		this.allPlanThreads.add(lastPlanThread);
	}

	/**
	 * Updates the {@link ThreaderScheduler} associated with this recogniser,
	 * which in turn updates the {@link PlanThreadGraph}.
	 * 
	 * @param observation
	 *            The action to append to the graph.
	 * @return The timestamped version of the action provided.
	 * @throws SchedulingException
	 *             Thrown if there is a problem in scheduling the action.
	 * @see #getPlanThreader()
	 */
	protected TimeStampedAction updateThreader(Action observation)
			throws SchedulingException
	{
		// schedule and extract current plan threads
		TreeSet<PlanThread> replacedHeads = new TreeSet<PlanThread>();

		// append the observation to a thread, which is returned
		PlanThread newThread = this.threader.getPlanThread(observation,
				replacedHeads, null);
		this.setLastPlanThread(newThread);
		
		TimeStampedAction timestampedObservation = null;
		for (ActionStateTuple a : newThread.getActions())
		{
			if (a.action.action.getAction().equals(observation))
			{
				timestampedObservation = a.action.action; // TODO assumption of
															// the final
				// break;
			}
		}
		if (timestampedObservation == null)
			throw new SchedulingException(
					"Unable to find scheduled action in returned plan thread");

		// System.out.println("Raw scheduled plan is:");
		// this.threader.getScheduledPlan().print(System.out);

//		System.out.println(this.threader.getLiveThreads().size()
//				+ " threads after observation");
//		 this.threader.getGraph().generateDotGraph(new java.io.File("threadGraph.dot"));

		for (Fact g : this.goalSpace.getGoals())
		{
			if (g instanceof AllFalseGoal)
				continue;

			Double hBeforeO = this.prevGoalDistMap.get(g).get(
					this.prevGoalDistMap.get(g).size() - 2);
			Double hAfterO = this.prevGoalDistMap.get(g).get(
					this.prevGoalDistMap.get(g).size() - 1);
			boolean oWasHelpful = (hAfterO < hBeforeO);// || (hAfterO == 0 &&
														// hBeforeO == 0 &&
														// this.areZeroStepsHelpful());

			// The support of a goal are those threads which exist at t-1 and
			// have at-least one action which reduces the distance to it
			Set<PlanThread> support = this.goalSupportThreadMap.get(g);

			// erase all old threads from this goal's thread-support set
			// and add in the head which the observation has been appended to

			// there are 2 situations in which the support for a goal is
			// modified.
			// 1- when an existing support thread is overwritten by a new one
			// 2- when the last observation was helpful towards the specific
			// goal
			// All other times the support for the goal remains unchanged.

			// check condition 1
			for (PlanThread t : replacedHeads)
			{
				// if the support for G contains a thread which has been
				// updated, remove it and add the new Thread
				if (support.contains(t))
				{
					support.remove(t);
					support.add(newThread); // this will probably be called
											// several times, but as Support is
											// a Set, it makes no difference
				}
			}

			// now check condition 2 -- if the observation was helpful, then the
			// thread which
			// it was appended to becomes helpful.
			if (oWasHelpful)
			{
				support.add(newThread);
			}

			// now compute the total length of all support threads for faster
			// lookups
			Set<TimeStampedAction> realActions = new HashSet<TimeStampedAction>();
			for (PlanThread h : support)
			{
				for (ActionStateTuple tup : h.getActions())
				{
					realActions.add(tup.action.action);
				}
			}

			BigDecimal realLength = BigDecimal.ZERO;
			for (TimeStampedAction tsa : realActions)
			{
				if (tsa.getAction() instanceof NullInstantAction == false)
					realLength = realLength.add(tsa.getCost());
			}
			double dval = realLength.doubleValue();

			this.goalSupportLengthMap.put(g, dval); // update thread support
													// length
			this.goalSupportThreadMap.put(g, support); // update support threads
														// themselves
		}

		return timestampedObservation;
	}

	/**
	 * Updates the stability space, based upon the effects of the given action.
	 * 
	 * @param a
	 */
	protected void updateStabilitySpace(Action a)
	{
		Set<Fact> adds = a.getAddPropositions();
		for (Fact f : adds)
		{
			this.stabilitySpace.update(f, true);
		}

		Set<Not> dels = a.getDeletePropositions();
		for (Not n : dels)
		{
			this.stabilitySpace.update(n.getLiteral(), false);
		}
	}

	/**
	 * Gets the {@link ThreaderScheduler} used to construct thread graphs.
	 * 
	 * @return
	 */
	public ThreaderScheduler getPlanThreader()
	{
		return this.threader;
	}

	/**
	 * Returns this state tuple which is closest to the specified time. Note
	 * that this method will always "round down" to the nearest known state, ie,
	 * if the parameter's time lies between state i and i+1, state i will always
	 * be returned.
	 * 
	 * @param time
	 * @return The tuple associated with time t
	 */
	public StateHistoryTuple getStateAt(long time)
	{
		return this.history.getStateAt(time);
	}

	/**
	 * Returns this state tuple which is closest to the specified time. Note
	 * that this method will always "round down" to the nearest known state, ie,
	 * if the parameter's time lies between state i and i+1, state i will always
	 * be returned.
	 * 
	 * @param time
	 * @return The tuple associated with time t
	 */
	public StateHistoryTuple getStateAt(int time)
	{
		return this.history.get(time);
	}

	/**
	 * Gets the current state of the world.
	 * 
	 * @return
	 */
	public STRIPSState getCurrentState()
	{
		return this.currentState;
	}

	/**
	 * Use at your own risk! This updates the current state as IGRAPH knows it.
	 * heuristic estimates computed before calling this will become invalid, as
	 * will many of the results returned by IGRAPH. A call to
	 * {@link #actionObserved(Action, IAgent)} using an empty
	 * {@link NullInstantAction} would be the best way to recompute estimates.
	 * But this is severely untested!
	 * 
	 * @param newState
	 */
	public void setCurrentState(STRIPSState newState)
	{
		this.currentState = newState;
		this.problem.setState(this.currentState);
	}

	// /**
	// * Gets the relaxed distance from the start of an RPG to the specified
	// goal set.
	// * @param gc
	// * @return The layer containing all facts, or -1 if they are *all* not
	// found.
	// */
	// public int getRelaxedDistance(GroundCondition gc)
	// {
	// return this.rpg.getRelaxedDistance(gc); //TODO make interface to
	// heuristic estimator
	// }

	/**
	 * Computes the number of possible combinations of a set of mutex goals.
	 * 
	 * @param goals
	 *            A collection of mutex goals.
	 * @param gs
	 *            The {@link MutexGoalSpace} used to determine how many
	 *            combinations exist.
	 * @return The total number of combinations of hypotheses which can be
	 *         produced using the specified goals, or 1 if these are already
	 *         non-mutex.
	 */
	protected int calculateNonMutexSetSize(Collection<Fact> goals,
			MutexGoalSpace gs)
	{
		System.out.println("Goal hypothesis is " + goals);
		int finalSize = 1;
		HashSet<Fact> singleProps = new HashSet<Fact>();
		LinkedList<Collection<Fact>> goalMutexes = new LinkedList<Collection<Fact>>();
		for (Object goalo : goals)
		{
			Fact goal = (Fact) goalo;
			FactMutex mutex = gs.getMutexes(goal);

			if (mutex.hasMutexes())
			{
				HashSet<Fact> validMutexes = new HashSet<Fact>();
				validMutexes.add(goal);
				int tmpCount = 1;
				for (Fact mut : mutex.getOthers())
				{
					if (goals.contains(mut))
					{
						validMutexes.add(mut);
						tmpCount++;
					}
				}
				finalSize *= tmpCount;

				goalMutexes.add(validMutexes);
			}
			else
				singleProps.add(goal);
		}

		return finalSize;
	}

	// public static List<IGoalHypothesis> computeNonMutexSets(IGoalHypothesis
	// gh, MutexGoalSpace gs)
	// {
	// System.out.println("gh is "+gh);
	// int finalSize = 1;
	// HashSet<GroundCondition> singleProps = new HashSet<GroundCondition>();
	// LinkedList<Collection<GroundCondition>> goalMutexes = new
	// LinkedList<Collection<GroundCondition>>();
	// for (Object goalo : gh.getGoals().getFacts())
	// {
	// GroundCondition goal = (GroundCondition) goalo;
	// FactMutex mutex = gs.getMutexes(goal);
	//
	// if (mutex.hasMutexes())
	// {
	// HashSet<GroundCondition> validMutexes = new HashSet<GroundCondition>();
	// validMutexes.add(goal);
	// int tmpCount = 1;
	// for (GroundCondition mut : mutex.getOthers())
	// {
	// if (gh.getGoals().getFacts().contains(mut))
	// {
	// validMutexes.add(mut);
	// tmpCount++;
	// }
	// }
	// finalSize *= tmpCount;
	//
	// goalMutexes.add(validMutexes);
	// }
	// else
	// singleProps.add(goal);
	// }
	//
	// System.out.println(finalSize+" possible combinations");
	//
	// ArrayList<IGoalHypothesis> allGoals = new ArrayList<IGoalHypothesis>();
	// AND and = new AND();
	// for (GroundCondition p : singleProps)
	// and.add(p);
	//
	// ConjunctiveGoalHypothesis defaultGoal = new
	// ConjunctiveGoalHypothesis(and, 0f);
	// allGoals.add(defaultGoal); //add to head of queue
	//
	//
	//
	//
	//
	//
	// //go through each mutex and select the highest probability proposition as
	// the hypothesis
	//
	//
	//
	//
	//
	//
	//
	// for (Collection<GroundCondition> mpset : goalMutexes)
	// //while (goalMutexes.isEmpty() == false)
	// {
	// System.out.println("allGoals size is "+allGoals.size());
	// Collection<IGoalHypothesis> newGoals = new HashSet<IGoalHypothesis>();
	// // IGoalHypothesis head = allGoals.remove();
	// // Collection<Proposition> mpset = goalMutexes.remove(); //remove this
	// mutex set
	// for (GroundCondition mp : mpset)
	// {
	// for (IGoalHypothesis oldhyp : allGoals)
	// {
	// GroundCondition cloneGoal = (GroundCondition) oldhyp.getGoals().clone();
	// // IGoalHypothesis clone = (IGoalHypothesis) head.clone();
	//
	// //clone head's goals and add this mutex fact
	// AND bob = new AND(cloneGoal.getFacts());
	// bob.add(mp);
	//
	// newGoals.add(new ConjunctiveGoalHypothesis(bob, 0f));
	// }
	// }
	// allGoals.clear();
	// allGoals.addAll(newGoals);
	// }
	//
	// //now have complete valid goal sets
	// //compute probabilities for each hypothesis and sort
	// for (IGoalHypothesis g : allGoals)
	// {
	// double prob = 0;
	// for (Object gco : g.getGoals().getFacts())
	// {
	// prob += gs.getProbability((GroundCondition)gco);
	// }
	//
	// g.setProbability(prob / g.getGoals().getFacts().size());
	// }
	//
	// Collections.sort(allGoals, new GoalHypothesisComparator());
	// System.out.println("Produced "+allGoals.size()+" hypotheses");
	// return allGoals;
	// }

	/**
	 * Convenience method for accessing the heuristic. If the goal contains an
	 * ignored fact, an early-exit is triggered, which returns
	 * {@link IHeuristic}.Unreachable.
	 * 
	 * @param goal
	 * @return
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public double getEstimate(Fact goal)
	{
		try
		{
			return this.heuristicManager.getEstimate(goal);
		}
		catch (UnreachableGoalException e)
		{
			System.err.println("Goal " + e.getUnreachables()
					+ " is at least partially unreachable");
			// throw new IllegalArgumentException(e);
			return IHeuristic.Unreachable;
		}
		catch (Exception e)
		{
			System.out.println("Error during estimation of distance to " + goal
					+ ". Returning IHeuristic.Unreachable");
			// throw new IllegalArgumentException(e);
			return IHeuristic.Unreachable;
		}
	}

	/**
	 * // * Estimate the length of the plan based on the number of grounded
	 * propositions and // * @return //
	 */
	// public int getEstimatedPlanLength()
	// {
	// //TODO implement version which finds out which likely-goal propositions
	// have not been met so far
	// //and use their heuristic distance as answer
	//
	// int hGoal = this.getEstimatedStepsRemaining();
	// if (hGoal == IHeuristic.Unreachable)
	// {
	//
	// }
	// return this.getObservedStepCount() + hGoal;
	// }

	/**
	 * Estimates the number of steps remaining, based on the immediate goal
	 * hypothesis and the heuristic estimate to achieve it.
	 * 
	 * @return The estimated work remaining to the goal. Or, if the goal is
	 *         unreachable, returns {@IHeuristic}.Unreachable.
	 */
	public int getEstimatedStepsRemaining() throws UnknownEstimateException
	{
		IGoalHypothesis hyp = this.getImmediateGoalHypothesis();

		// long bef = System.nanoTime();
		double hGoal = this.getEstimate(hyp.getGoals());

		if (hGoal == IHeuristic.Unreachable)
			throw new UnknownEstimateException();

		// long aft = System.nanoTime();
		// System.out.println("dist to "+gc+" is "+dist);
		// long htime = aft - bef;
		// double res = htime / 1000000000d;
		// System.out.println("immediate hyp = "+res);

		return (int) hGoal;
	}

	/**
	 * Computes the goals which is most likely to be the agent's final goal,
	 * regardless of how many timesteps are remaining.
	 */
	public IGoalHypothesis getImmediateGoalHypothesis()
	{
		IGoalHypothesis hyp = null;
		if (IGRAPHPreferences.HypothesisFilter == HypothesisFilterType.Greedy)
			hyp = this.getGreedyNonMutexHypothesis(
					(VariableGoalSpace) this.goalSpace, (Collection<Fact>) this
							.getGoalSpace().getGoals());

		Collection<Fact> hypgoals = new HashSet<Fact>(hyp.getGoals().getFacts());

		hyp.setGoals(new And(hypgoals));

		// sanity check for detecting that recall is actually 1 if no goals are
		// filtered
		// hyp = new ConjunctiveGoalHypothesis(this.goalSpace.getGoals(), 1f);

		return hyp;
	}

	/**
	 * Computes the total heuristic distance moved towards achieving this
	 * proposition since execution started;
	 * 
	 * @param p
	 * @return
	 */
	public double computeDistanceMovedTowards(Fact p)// , boolean includeZero)
	{
		if (this.prevGoalDistMap.containsKey(p) == false)
			return 0;

		List<Double> dists = this.prevGoalDistMap.get(p);
		if (dists.size() == 1)
			return this.prevGoalDistMap.get(p).get(0);
		else if (dists.size() == 0)
			return 0;

		int moveTowards = 0;

		for (int i = 0; i < dists.size() - 1; i++)
		{
			double d = dists.get(i) - dists.get(i + 1);
			if (d > 0)// || (d == 0 && includeZero))
				moveTowards += d;
		}

		return moveTowards;
	}

	/**
	 * Computes the total heuristic distance moved away from achieving this
	 * proposition since execution started.
	 * 
	 * @param p
	 * @return
	 */
	public double computeDistanceMovedAway(GroundFact p)// , boolean
														// includeZero)
	{
		if (this.prevGoalDistMap.containsKey(p) == false)
			return 0;

		List<Double> dists = this.prevGoalDistMap.get(p);
		if (dists.size() == 1)
			return this.prevGoalDistMap.get(p).get(0);
		else if (dists.size() == 0)
			return 0;

		double movedAway = 0;
		double d;
		for (int i = 0; i < dists.size() - 1; i++)
		{
			d = dists.get(i + 1) - dists.get(i);
			if (d > 0)// || (d == 0 && includeZero))
				movedAway += d;
		}

		return movedAway;
	}

	/**
	 * Computes the total number of steps which reduced the heuristic estimate
	 * towards achieving this proposition since execution started; Note that
	 * this is not the distance moved towards, just the steps which reduced the
	 * distance.
	 * 
	 * @param p
	 * @return
	 */
	@Deprecated
	public double computeHelpfulSteps(Fact p, boolean includeZeroMove)
	{
		if (this.prevGoalDistMap.containsKey(p) == false)
			return 0;

		List<Double> dists = this.prevGoalDistMap.get(p);
		if (dists.size() == 1)
			return 0;
		else if (dists.size() == 0)
			return 0;

		int moveTowards = 0;

		for (int i = 0; i < dists.size() - 1; i++)
		{
			double d = dists.get(i) - dists.get(i + 1);
			if ((dists.get(i) == 0 && dists.get(i + 1) == 0 && includeZeroMove)
					|| d > 0)
			{
				moveTowards++;
			}
		}

		return moveTowards;
	}

	/**
	 * Computes the number of previous timesteps which have consistently reduced
	 * the heuristic estimate, starting at the current timestep.
	 */
	public int computeContinuouslyHelpfulSteps(Fact p, boolean includeZeroMove)
	{
		if (this.prevGoalDistMap.containsKey(p) == false)
			return 0;

		List<Double> dists = this.prevGoalDistMap.get(p);
		if (dists.size() == 0)
			return 0;
		else if (dists.size() == 1)
			return 0;

		int moveTowards = 0;

		for (int i = dists.size() - 1; i >= 0; i--)
		{
			// if (previous - current) > 0) then moved towards
			double d = dists.get(i - 1) - dists.get(i);
			if ((dists.get(i) == 0 && dists.get(i + 1) == 0 && includeZeroMove)
					|| d > 0)
			{
				moveTowards++;
			}
			else if (d < 0)
				break;
		}

		return moveTowards;
	}

	/**
	 * Computes the total number of steps which increased the heuristic estimate
	 * towards achieving this proposition since execution started.
	 * 
	 * @param p
	 * @return
	 */
	public double computeUnhelpfulSteps(GroundFact p)
	{
		if (this.prevGoalDistMap.containsKey(p) == false)
			return 0;

		List<Double> dists = this.prevGoalDistMap.get(p);
		if (dists.size() == 1)
			return 0;
		else if (dists.size() == 0)
			return 0;

		double movedAway = 0;
		double d;
		for (int i = 0; i < dists.size() - 1; i++)
		{
			d = dists.get(i + 1) - dists.get(i);
			if (d > 0)
				movedAway++;
		}

		return movedAway;
	}

	/**
	 * Get the maximum probability goal from each sub-goal-space this
	 * {@link VariableGoalSpace} encapsulates.
	 * 
	 * @return
	 */
	public Set<Fact> getMaximumFacts(Collection<Fact> validFacts)
	{
		return this.getMaximumFacts(0f, validFacts);
	}

	/**
	 * Get the maximum probability goal from each sub-goal-space this
	 * {@link VariableGoalSpace} encapsulates. As a set is returned, the size of
	 * this may be lower than the number of sub-goal-spaces present (same
	 * literal is highest probability in multiple sub-goal-spaces).
	 * 
	 * @param minimumProbability
	 * @return A set containing the maximum probability facts for the
	 *         {@link VariableGoalSpace}. This may be empty if all facts are
	 *         below the minimum probability.
	 */
	public Set<Fact> getMaximumFacts(double minimumProbability,
			Collection<Fact> validFacts)
	{
		// TODO this was originally in VariableGoalSpace, but I want it to be
		// able to
		// do consistent tie breaking, so moved into here to utilise local
		// methods
		HashSet<Fact> maxes = new HashSet<Fact>();
		for (MutexGoalSpace gs : ((VariableGoalSpace) this.goalSpace)
				.getVariableGoalSpaces())
		{
			Fact max = null;
			double maxp = Double.MIN_VALUE;
			for (Fact g : gs.getGoals())
			{
				if (validFacts.contains(g) == false)
					continue;

				double pg = gs.getProbability(g);
				if (pg < minimumProbability)
					continue; // skip anything which does not meet the minumum
								// probability

				if (pg >= minimumProbability && pg > maxp)
				{
					maxp = pg;
					max = g;
				}
				// tie break code, always prefer to keep a positive literal over
				// a negative one
				else if (pg >= minimumProbability && pg == maxp)
				{
					if (max instanceof AllFalseGoal == true
							&& g instanceof AllFalseGoal == false)
					{
						max = g;
					}
					else
					{
						boolean maxIsBetter = this.doGoalTieBreak(max, g, gs,
								true);

						if (!maxIsBetter)
						{
							max = g;
						}
					}
				}
				// no need to test for maxp < any other -- caught by looping
				// over every literal
			}

			if (max != null && max instanceof AllFalseGoal == false)
				maxes.add(max);
		}

		return maxes;
	}

	/**
	 * Greedily constructs a hypothesis from the specified goal space by keeping
	 * only those facts which have maximum probability from each sub-goal-space.
	 * Ties are broken based upon which fact appears higher in the causal graph
	 * -- lower facts are more likely to be goals rather than enablers of goals.
	 * 
	 * @param hyp
	 * @param gs
	 * @return
	 */
	protected IGoalHypothesis getGreedyNonMutexHypothesis(
			VariableGoalSpace hypGoalSpace, Collection<Fact> validFacts)// ,
																		// Collection<Proposition>
																		// goalSet)
	{
		// map for storing final non-mutex hypothesis
		Map<Fact, Double> highestLiterals = new HashMap<Fact, Double>();

		// get the highest literal from each sub-goal-space
		Set<Fact> maxes = this.getMaximumFacts(validFacts);
		// ArrayList<Fact> maxes = new
		// ArrayList<Fact>(hypGoalSpace.getMaximumFacts());

		LinkedList<Fact> queue = new LinkedList<Fact>(maxes);
		HashSet<Fact> inferior = new HashSet<Fact>();

		out: while (!queue.isEmpty())
		{
			Fact goal = queue.remove();

			// if the highest literal in the sub-goal space is the negation of
			// all positive facts, then
			// it is ommitted from the hypothesis completely. Or it is not a
			// valid fact candidate.
			if (goal instanceof AllFalseGoal || inferior.contains(goal)
					|| validFacts.contains(goal) == false)
			{
				continue;
			}

			// check that the current fact is not mutex with anything else which
			// is a candidate for the hypothesis.
			// if it is and is a better candidate, ignore the current goal and
			// continue;
			Fact bestNonMutex = goal;
			for (Fact o : queue)
			{
				if (goal.equals(o))
					continue out;

				if (hypGoalSpace.getMutexSpace().isMutex(goal, o))
				{
					// delegate to doGoalTieBreak to decide whether goal or o is
					// better
					boolean aIsBetter = this.doGoalTieBreak(goal, o,
							hypGoalSpace, false);
					if (aIsBetter)
					{
						inferior.add(o); // remove the lesser goal from the
											// queue so we don't check it later
					}
					else
					{
						continue out;
					}
				}
			}

			// finally, add the fact which has the highest probability and is
			// non-mutex
			double bestNonMutexProb = hypGoalSpace.getProbability(bestNonMutex);
			highestLiterals.put(bestNonMutex, bestNonMutexProb);
		}

		// test -- assert that all values in highestLiterals are indeed
		// non-mutex (to the best of our knowledge)
		if (IGRAPHPreferences.VerifyGoalSpace)
		{
			for (Fact a : highestLiterals.keySet())
			{
				for (Fact b : highestLiterals.keySet())
				{
					if (a == b)
						continue;

					if (hypGoalSpace.getMutexSpace().isMutex(a, b) == true)
						throw new IllegalArgumentException(
								"Invalid mutex hypothesis was produced " + a
										+ " and " + b + " are mutex:"
										+ highestLiterals.keySet().toString());
				}
			}
		}

		Set<Fact> highestLiteralFacts = new HashSet<Fact>(
				highestLiterals.keySet());
		highestLiteralFacts.addAll(this.stAchievedFacts); // add all ST goals
															// which have been
															// achieved -- just
															// in case they have
															// been omitted.
		highestLiteralFacts.removeAll(this.stAchievedMutexFacts); // remove
																	// anything
																	// which is
																	// mutex
																	// with a ST
																	// goal
		double p = this.getHypothesisProbability(highestLiterals.keySet(),
				hypGoalSpace);
		ConjunctiveGoalHypothesis hyp = new ConjunctiveGoalHypothesis(new And(
				highestLiterals.keySet()), p);
		// ConjunctiveGoalHypothesis hyp = new ConjunctiveGoalHypothesis(new
		// And(validFacts), p); //for debugging -- returns entire goal space

		return hyp;
	}

	/**
	 * Tie breaks 2 goals based upon a variety of criteria
	 * 
	 * @param goal
	 *            The first goal, which is used in the return value.
	 * @param other
	 *            Another, mutex goal which will be compared with the first.
	 * @param hypGoalSpace
	 * @return True if the first goal passed in is a better candidate than the
	 *         second. False otherwise.
	 */
	protected boolean doGoalTieBreak(Fact goal, Fact other,
			IGoalSpace hypGoalSpace, boolean sameMutexSet)
	{
		if (hypGoalSpace.getGoals().contains(goal) == false
				|| hypGoalSpace.getGoals().contains(other) == false)
		{
			throw new IllegalArgumentException(
					"Illegal goals specified. Both must be mutex with one another and in the goal space provided.");
		}

		double gLayer = this.minimumCgLayers.get(goal); // layer on CG of G

		double gProb = hypGoalSpace.getProbability(goal);
		double oProb = hypGoalSpace.getProbability(other);

		if (sameMutexSet && gProb > oProb)
		{
			// do nothing, stick with current fact
			return true;
		}
		else if (sameMutexSet && gProb < oProb)
		{
			return false; // found a better goal candidate
		}
		else
		{
			//else these are in different mutex sets, so probabilities associated with O and G are ignored
			//as they cannot really be compared. Or, probabilities are equal, in which case do the
			//same tie break code anyway.
			
			// find which goal has had more work put towards it
			double gWork = this.getDistanceMovedTowards(goal);
			double oWork = this.getDistanceMovedTowards(other);

			if (gWork < oWork)
			{
				return false;
			}
			else if (gWork > oWork)
			{
				return true;
			}
			else
			{
			  	 //if the other equal probability fact is an AFG, always keep the
//				 positive goal
				 if (other instanceof AllFalseGoal)
					 return true;

				double oLayer = this.minimumCgLayers.get(other);
				// lower layer is better -- makes it more likely to be a goal
				if (gLayer < oLayer)
				{
					// do nothing, stick with current fact
					return true; // remove the lesser goal from the queue so we
									// don't check it later
				}
				else if (gLayer > oLayer)
				{
					return false; // found a better goal candidate, so ignore
									// the current one and skip to the next in
									// the queue
				}
				else
				{

					// the last real test checks to see which is closer -- the
					// goal or the mutex one
					// This is determined by a flag set in the preferences.
					double gEstimate = this.getCurrentPropDist(goal);
					double oEstimate = this.getCurrentPropDist(other);

					if (gEstimate < oEstimate)
					{
						if (IGRAPHPreferences.TiedGoalPreference == GoalTieOrderingPreference.PreferNearerGoals)
							return true;
						else
							return false;
					}
					else if (gEstimate > oEstimate)
					{
						if (IGRAPHPreferences.TiedGoalPreference == GoalTieOrderingPreference.PreferFurtherGoals)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else
					{
						// lastly, just decide by coin flip
						double coinFlip = rand.nextDouble();
						if (coinFlip > 0.5)
						{
							// do nothing, stick with current fact
							return true; // remove the lesser goal from the
											// queue so we don't check it later
						}
						else
						{
							return false; // found a better goal candidate, so
											// ignore the current one and skip
											// to the next in the queue
						}
					}
				}
			}
		}
	}

	/**
	 * Computes a goal hypothesis on the assumption that all actions have been
	 * observed and thus the agent has reached its final goal. This means that
	 * the goal is in this state. Anything which has been untouched since the
	 * initial state is eliminated from the hypothesis.
	 * 
	 * @param agent
	 * @param maxHypothesisCount
	 * @return
	 */
	public List<IGoalHypothesis> computeFinalGoalHypotheses(IAgent agent,
			int maxHypothesisCount)
	{
		ArrayList<IGoalHypothesis> hyps = new ArrayList<IGoalHypothesis>();

		HashSet<Fact> finalState = new HashSet<Fact>(currentState.getFacts());

		// we are only interested in facts which appear in the final state
		// and are actually in the goal-space.
		HashSet<Fact> validFacts = new HashSet<Fact>();
		Collection<? extends Fact> validGoals = this.goalSpace.getGoals();

		for (Fact f : finalState)
		{
			if (f instanceof Not)
				continue;

			double s = this.getStability(f);
			if (validGoals.contains(f)// &&
										// this.getGoalProbability(this.goalSpace,
										// f) > IGRAPHPreferences.Epsilon
					&& s >= IGRAPHPreferences.StabilityThreshold)
			{
				validFacts.add(f);
			}
		}

		double prob = this.getHypothesisProbability(validFacts, this.goalSpace);
		hyps.add(new ConjunctiveGoalHypothesis(validFacts, prob));
		if (hyps.size() > maxHypothesisCount)
			return hyps.subList(0, maxHypothesisCount);
		else
			return hyps;
	}

	/**
	 * Computes the probability of a set of facts being the true goal, as the
	 * sum of their individual probabilities over thei hypothesis size. (P(f1) +
	 * P(f2) + ... P(fn)) / |F|) where F is the set of facts passed in.
	 * Probabilities are taken from the goal space specified.
	 * 
	 * @param validFacts
	 * @param goalSpace
	 * @return The total probability weight associated with the specified facts.
	 */
	public double getHypothesisProbability(Collection<Fact> validFacts,
			IGoalSpace goalSpace)
	{
		double total = 0;

		for (Fact f : validFacts)
		{
			double pFact = goalSpace.getProbability(f);
			total += pFact;
		}

		double pHyp = total / validFacts.size();

		return pHyp;
	}

	// //////////////////////////////////////////
	//
	// New AUTOGRAPH2 methods
	//
	// Individual Probability methods
	//
	// //////////////////////////////////////////

	/**
	 * Gets the curent probability of the goal specified being the true goal.
	 * 
	 */
	public double getGoalProbability(IGoalSpace gs, Fact g)
	{
		return gs.getProbability(g);
	}

	// /**
	// * Gets the probability of the goal specified being the current goal.
	// *
	// */
	// public double getNextGoalProbability(Fact g)
	// {
	// // double stepsTowards = this.computeHelpfulSteps(g, true);
	// double stepsTowards = this.usefulDistanceMap.get(g);
	// double continuous = this.computeContinuouslyHelpfulSteps(g, true);
	//
	// double res = continuous / stepsTowards;// / (2 *
	// this.getObservedStepCount());
	//
	// return res;
	// }

	/**
	 * Gets the stability of the specified goal (once achieved).
	 * 
	 * @param g
	 * @return
	 */
	public double getStability(Fact g)
	{
		return this.stabilitySpace.getStability(g);
	}

//	/**
//	 * Constructs K bounded hypotheses, which are the hypotheses if 
//	 * observation stops in 1..K steps.
//	 * @param kSteps
//	 * @return
//	 * @throws IllegalArgumentException
//	 *             Thrown if k < 1
//	 */
//	public List<BoundedGoalHypothesis> getBoundedHypotheses(int kSteps)
//	{
//		if (kSteps < 1)
//			throw new IllegalArgumentException("Bound must be >= 1");
//
//		IGoalSpace gs = (IGoalSpace) this.getGoalSpace().clone();
//		ArrayList<BoundedGoalHypothesis> hyps = new ArrayList<BoundedGoalHypothesis>();
//
//		Collection<? super WeightedPlan> planSpace = this
//				.getPlanSpace((Collection<Fact>) gs.getGoals());
//
//		STRIPSState currentState = this.getCurrentState();
//		Map<Plan, STRIPSState> statesAtK = new HashMap<>();
//		Map<Plan, STRIPSState> statesAtKMinusOne = new HashMap<Plan, STRIPSState>();
//		statesAtKMinusOne.put(null, this.getCurrentState());
//
//		HashSet<Fact> factsAtK = new HashSet<Fact>();
//		HashSet<Fact> factsAtKMinusOne = new HashSet<Fact>(this
//				.getCurrentState().getFacts());
//
//		for (int k = 1; k <= kSteps; k++)
//		{
//			// get the states which are present at the bound, given the plan
//			// space (relaxed)
//			statesAtK = this.getStatesAtBound(
//					(Collection<? extends Plan>) planSpace, currentState, k);
//
//			// get the union of all facts at K
//			factsAtK = new HashSet<Fact>();
//			for (STRIPSState s : statesAtK.values())
//			{
//				factsAtK.addAll(s.getFacts());
//			}
//			// remove all those which exist at K-1, so leave only those added by
//			// observation K-1
//			factsAtK.removeAll(factsAtKMinusOne);
//
//			// if there is no difference between states, then k must have
//			// reached the maximum plan length.
//			if (factsAtK.isEmpty())
//				return hyps;
//			// throw new NullPointerException("No difference between states");
//
//			IGoalHypothesis boundHyp = this.getGreedyNonMutexHypothesis(
//					(VariableGoalSpace) gs, factsAtK);
//			BoundedGoalHypothesis bhyp = new BoundedGoalHypothesis(boundHyp,
//					this.getObservedStepCount(), k);
//
//			hyps.add(bhyp);
//
//			statesAtKMinusOne = statesAtK;
//			factsAtKMinusOne = factsAtK;
//		}
//
//		return hyps;
//	}

	/**
	 * Constructs K bounded hypotheses, which are the hypotheses if 
	 * observation stops in 1..K steps. This is based simply upon 
	 * whether a goal can be achieved within the next K steps and
	 * its probability at the time of calling.
	 * @param kSteps The number of steps to produce an estimate for.
	 * @return K {@link BoundedGoalHypothesis} objects.
	 * @throws IllegalArgumentException
	 *             Thrown if k < 1
	 */
	public List<BoundedGoalHypothesis> getBoundedHypotheses(int kSteps)
	{
		if (kSteps < 1)
			throw new IllegalArgumentException("Bound must be >= 1");

		ArrayList<BoundedGoalHypothesis> hyps = new ArrayList<BoundedGoalHypothesis>();

		for (int k = 1; k <= kSteps; k++)
		{
			HashSet<Fact> validFacts = new HashSet<>();
			for (Fact goal : this.getGoalSpace().getGoals())
			{
				if (goal instanceof AllFalseGoal)
				{
					validFacts.add(goal);
					continue;
				}
					
				double currEst = this.getCurrentPropDist(goal);
				if (currEst <= k && currEst > 0)
					validFacts.add(goal);
				
			}
//			Collection<Fact> highestFactsPossibleAtK = this.getMaximumFacts(validFacts);
//			IGoalHypothesis hyp = this.getGreedyNonMutexHypothesis((VariableGoalSpace) this.getGoalSpace(), highestFactsPossibleAtK);
			IGoalHypothesis hyp = this.getGreedyNonMutexHypothesis((VariableGoalSpace) this.getGoalSpace(), validFacts);
			
			BoundedGoalHypothesis bhyp = new BoundedGoalHypothesis(hyp, this.getObservedStepCount(), k);
			hyps.add(bhyp);
			
		}

		return hyps;
	}

	/**
	 * Count the number of times each relevant fact appears in the states
	 * provided
	 * 
	 * @return
	 */
	protected SortedSet<WeightedFact> getFactCounts(
			Collection<? extends Fact> relevantFacts,
			Collection<STRIPSState> states)
	{
		HashMap<Fact, Integer> map = new HashMap<Fact, Integer>();

		for (STRIPSState s : states)
		{
			for (Fact f : s.getTrueFacts())
			{
				if (map.containsKey(f) == false)
				{
					map.put(f, 0);
				}

				map.put(f, map.get(f) + 1);
			}
		}

		TreeSet<WeightedFact> set = new TreeSet<BayesianGoalRecogniser.WeightedFact>();
		for (Entry<Fact, Integer> e : map.entrySet())
		{
			WeightedFact wf = new WeightedFact(e.getKey(), e.getValue());
			set.add(wf);
		}

		return set;
	}

	protected class WeightedFact implements Fact
	{
		private Fact fact;
		private double weight;

		public WeightedFact(Fact f, double weight)
		{
			this.fact = f;
			this.weight = weight;
		}

		@Override
		public int compareTo(Fact o)
		{
			if (o instanceof WeightedFact)
				return -Double.compare(this.getWeight(), ((WeightedFact) o).getWeight());
			else
				return this.toString().compareTo(o.toString());
		}

		public double getWeight()
		{
			return weight;
		}

		public void setWeight(double weight)
		{
			this.weight = weight;
		}

		public boolean isStatic()
		{
			return fact.isStatic();
		}

		public void setStatic(boolean value)
		{
			fact.setStatic(value);
		}

		public Object clone()
		{
			return fact.clone();
		}

		public Set<? extends Fact> getFacts()
		{
			return fact.getFacts();
		}

		public void PDDLPrint(PrintStream p, int indent)
		{
			fact.PDDLPrint(p, indent);
		}

		public String toStringTyped()
		{
			return fact.toStringTyped();
		}

		@Override
		public String toString()
		{
			return fact.toString();
		}
	}

	/**
	 * Get the plan space at the moment of calling.
	 * 
	 * @return The set of plans in the plan-space (whatever this is classed as),
	 *         ordered by probability of being the observed plan
	 */
	public SortedSet<WeightedPlan> getPlanSpace(Collection<Fact> goals)
	{
		Collection<Plan> plans = this.heuristicManager
				.getCachedRelaxedPlans(goals);

		// fubd tge
		TreeSet<WeightedPlan> weightedPlans = new TreeSet<>();
		for (Plan p : plans)
		{
			double numMatchingObs = this.getPlanActionsMatchCount(p);
			double weight = numMatchingObs
					/ (double) this.getObservedStepCount();

			WeightedPlan wp = new WeightedPlan(p, weight);
			weightedPlans.add(wp);
		}

		return weightedPlans;
	}

	/**
	 * Get the number of observations which appear in the specified plan.
	 * 
	 * @param p
	 * @return
	 */
	protected int getPlanActionsMatchCount(final Plan p)
	{
		int matches = 0;

		Set<Action> oActions = new LinkedHashSet(this.planSoFar.getActions());

		for (Action a : p.getActions())
		{
			if (oActions.contains(a))
			{
				++matches;
				oActions.remove(a);
			}
		}

		return matches;
	}

	// protected int getPlanActionsMatchCount(Plan p)
	// {
	// int matches = 0;
	//
	// HashMap<Action, Integer> oCounts = new HashMap<Action, Integer>();
	// for (Action o : this.planSoFar)
	// {
	// if (oCounts.containsKey(o) == false)
	// oCounts.put(o, 0);
	//
	// oCounts.put(o, oCounts.get(o) + 1);
	// }
	//
	// HashMap<Action, Integer> aCounts = new HashMap<Action, Integer>();
	// for (Action a : p)
	// {
	// if (oCounts.containsKey(a) == false)
	// continue;
	//
	// if (aCounts.containsKey(a) == false)
	// aCounts.put(a, 0);
	//
	// int oCount = oCounts.get(a);
	// if (oCount < aCounts.get(a))
	// {
	// aCounts.put(a, aCounts.get(a) + 1);
	// ++matches;
	// }
	// }
	//
	// return matches;
	// }

	protected class WeightedPlan implements Plan, Comparable<WeightedPlan>
	{
		private Plan delegate;
		private double weight;

		public WeightedPlan(Plan p, double weight)
		{
			this.delegate = p;
		}
		
		
		@Override
		public BigDecimal getCost()
		{
			return this.delegate.getCost();
		}

		@Override
		public boolean addAction(Action a)
		{
			return delegate.addAction(a);
		}

		public Plan clone()
		{
			Plan c = this.delegate.clone();
			WeightedPlan clone = new WeightedPlan(c, this.getWeight());

			return clone;
		}

		@Override
		public Iterator<Action> iterator()
		{
			return this.delegate.iterator();
		}

		@Override
		public int compareTo(WeightedPlan arg0)
		{
			int w = -Double.compare(this.getWeight(), arg0.getWeight()); // flip
																			// sign
			if (w != 0)
				return w;

			boolean eq = this.delegate.equals(arg0);
			if (!eq)
				return -1; // don't care about sorting plans which aren't equal.
			else
			{
				return 0; // equal probs and equal actions
			}
		}

		public double getWeight()
		{
			return weight;
		}

		public void setWeight(double weight)
		{
			this.weight = weight;
		}

		public void print(PrintStream p)
		{
			delegate.print(p);
		}

		public void print(PrintWriter p)
		{
			delegate.print(p);
		}

		public List<Action> getActions()
		{
			return delegate.getActions();
		}

		public int getActionCount()
		{
			return delegate.getActionCount();
		}

		public Fact getGoal()
		{
			return delegate.getGoal();
		}

		public void setGoal(Fact g)
		{
			delegate.setGoal(g);
		}

		@Override
		public int getPlanLength()
		{
			return delegate.getPlanLength();
		}
	}

	/**
	 * Extract the states at a bounded time, which will occur through the plans
	 * provided. If a plan is shorter than the bound, it is not included in the
	 * returned map. Action preconditions are not checked at ay point, so
	 * relaxed plans can be provided.
	 * 
	 * @param plans
	 *            A collection of plans
	 * @param initialState
	 *            The state each plan starts in
	 * @param bound
	 *            The time at which states are to be returned.
	 * @return A Map of valid plans and the resulting state when actions
	 *         1--bound are applied
	 */
	public Map<Plan, STRIPSState> getStatesAtBound(
			Collection<? extends Plan> plans, STRIPSState initialState,
			int bound)
	{
		HashMap<Plan, STRIPSState> map = new HashMap<Plan, STRIPSState>();

		if (bound <= 0)
			throw new IllegalArgumentException(
					"Bound must be positive and non-zero");

		int max = Integer.MIN_VALUE;

		for (Plan p : plans)
		{
			if (p.getPlanLength() < bound || p instanceof NullPlan)
				continue;

			if (p.getPlanLength() > max)
				max = p.getPlanLength();

			STRIPSState s = initialState;
			for (Action a : p.getActions())
			{
				s = (STRIPSState) s.apply(a);
			}

			map.put(p, s);
		}

		return map;
	}

	/**
	 * Returns a set of actions which have the highest probability of being
	 * executed within n steps time.
	 * 
	 * @return
	 */
	public Collection<Action> getPredictedNextActions(STRIPSState state)
	{
		Collection<Action> highest = new HashSet<Action>();
		double highestProb = 0;

		for (Action a : problem.getActions())
		{
			if (a.isApplicable(state) == false)
				continue;

			double max = 0;
			for (Fact eff : a.getAddPropositions()) // TODO would need changed
													// if negative effects are
													// considered
			{
				if (goalSpace.getGoals().contains(eff) == false)
					continue;

				double p = getGoalProbability(goalSpace, eff);
				if (p > max)
				{
					max = p;
				}
			}

			if (max > highestProb)
			{
				highest.clear();
				highest.add(a);
			}
			else if (max == highestProb)
				highest.add(a);
		}

		return highest;
	}

	/**
	 * Gets the action which is most likely to be executed next. This is the
	 * union of all actions applicable in the thread which last had an actiona
	 * added to it. Ties are broken randomly.
	 * 
	 * @return
	 */
	protected Action getPredictedNextAction()
	{
		PlanThread lastThread = this.threader.getLastThread();
		STRIPSState lastStateUsed = null;
		if (lastThread == null)
			lastStateUsed = this.initialState;
		else
			lastStateUsed = lastThread.computeCurrentState();

		ArrayList<Action> arr = new ArrayList<Action>();
		arr.addAll(this.getPredictedNextAction(lastStateUsed));

		return arr.get(this.rand.nextInt(arr.size()));
	}

	/**
	 * Gets the set of actions which are most likely to be executed next, i.e. n
	 * = 1
	 * 
	 * @return A collection of actions which are most likely to be executed
	 *         next.
	 */
	protected Collection<Action> getPredictedNextAction(STRIPSState state)
	{
		Collection<Action> highest = new HashSet<Action>();
		double highestProb = 0;

		for (Action a : this.problem.getActions())
		{
			if (a.isApplicable(state) == false)
				continue;

			double max = 0;
			for (Fact eff : a.getAddPropositions()) // TODO would need changed
													// if negative effects are
													// considered
			{
				if (this.goalSpace.getGoals().contains(eff) == false)
					continue;

				double p = this.getGoalProbability(this.goalSpace, eff);
				if (p > max)
				{
					max = p;
				}
			}

			if (max > highestProb)
			{
				highest.clear();
				highest.add(a);
			}
			else if (max == highestProb)
				highest.add(a);
		}

		return highest;
	}

	/**
	 * Helper method which selects at random from the set of actions returned
	 * from getPredictedNextActions().
	 * 
	 * @return
	 */
	public Map<PlanScheduleState, Action> getPredictedNextActions()
	{
		Collection<PlanThread> threads = threader.getLiveThreads();

		HashMap<PlanScheduleState, Action> map = new HashMap<PlanScheduleState, Action>();

		for (PlanThread t : threads)
		{
			PlanScheduleState head = t.getHead();
			// STRIPSState s = t.computeCurrentState();
			STRIPSState s = head.state;
			Collection<Action> nextPssActions = this.getPredictedNextAction(s);

			ArrayList<Action> arr = new ArrayList<Action>(nextPssActions);
			Action chosen = arr.get(rand.nextInt(arr.size())); // FIXME
																// randomness in
																// output

			map.put(head, chosen);
		}

		return map;

		// ArrayList<Action> arr = new
		// ArrayList<Action>(this.getPredictedNextAction(this.getCurrentState()));
		// return arr.get(rand.nextInt(arr.size()));
	}

	// //////////////////////////////////////////
	//
	// Mutex splitting/concatenation methods
	//
	// //////////////////////////////////////////

	protected MutexSpace getSplitMutexSpace(MutexSpace currentSpace)
	{
		Collection<MutexSet> mutexSets = new HashSet<MutexSet>(
				currentSpace.getMutexSets());

		// find out how many time each fact appears as a PC and add effect.
		// These will
		// be used to decide which is kept
		// Map<Fact, Integer> factPCs = new HashMap<Fact, Integer>();
		// Map<Fact, Integer> factAdds = new HashMap<Fact, Integer>();
		// for (Action a : this.problem.actions)
		// {
		// for (Fact f : a.getPreconditions())
		// {
		// if (factPCs.containsKey(f))
		// {
		// factPCs.put(f, factPCs.get(f) + 1);
		// }
		// else
		// {
		// factPCs.put(f, 1);
		// }
		// }
		//
		// for (Fact f : a.getAddPropositions())
		// {
		// if (factAdds.containsKey(f))
		// {
		// factAdds.put(f, factAdds.get(f) + 1);
		// }
		// else
		// {
		// factAdds.put(f, 1);
		// }
		// }
		// }

		Map<Fact, MutexSet> multipleSets = new HashMap<Fact, MutexSet>();
		for (MutexSet set : mutexSets)
		{
			for (Fact f : set.getFacts())
			{
				if (multipleSets.containsKey(f))
				{
					// if there is already a mutex set this fact appears in,
					// always prefer
					// the smaller set
					if (set.size() < multipleSets.get(f).size())
					{
						multipleSets.get(f).removeFact(f);
						multipleSets.put(f, set);
					}
				}
				else
				{
					multipleSets.put(f, set);
				}
			}
		}

		// finally, add the new mutex sets to the goal space. All relevant
		// previous mutexes should be overwritten
		// while mutexes for facts which appear in only one mutexset should
		// remain untouched
		MutexSpace newSpace = (MutexSpace) currentSpace.clone();
		for (MutexSet nms : mutexSets)
		{
			newSpace.addMutexSet(nms.getFacts());
		}

		// debug sanity check
		HashSet<Fact> seen = new HashSet<Fact>();
		for (MutexSet ms : multipleSets.values())
		{
			for (Fact f : ms.getFacts())
			{
				if (seen.contains(f))
					throw new IllegalArgumentException(
							f.toString()
									+ " appears in more than one mutex set after set compilation");
				else
					seen.add(f);
			}
		}
		System.out.println("Goal space now has no overlapping mutexes");

		return newSpace;
	}

	/**
	 * Merges mutex sets based on any overlapping literals.
	 * 
	 * @param space
	 * @param legalFacts
	 * @return
	 * @deprecated SAS+ Mutexes are now exclusively used.
	 */
	protected Collection<MutexSet> createMergedMutexSets(MutexSpace space,
			Collection<? extends Fact> legalFacts)
	{
		Collection<MutexSet> currentSets = space.getMutexSets();

		HashMap<Fact, HashSet<MutexSet>> appearsIn = new HashMap<Fact, HashSet<MutexSet>>();
		for (MutexSet ms : currentSets)
		{
			for (Fact f : ms.getFacts())
			{
				if (appearsIn.containsKey(f) == false)
				{
					HashSet<MutexSet> s = new HashSet<MutexSet>(1);
					s.add(ms);
					appearsIn.put(f, s);
				}
				else
				{
					appearsIn.get(f).add(ms);
				}
			}
		}

		HashMap<Fact, MutexSet> newAppearsIn = new HashMap<Fact, MutexSet>();
		for (Fact f : appearsIn.keySet())
		{
			newAppearsIn.put(f, new MutexSet());
		}

		for (Entry<Fact, HashSet<MutexSet>> e : appearsIn.entrySet())
		{

			MutexSet union = new MutexSet();
			for (MutexSet crossSet : e.getValue())
			{
				union.merge(crossSet);
			}

			for (Fact f : union.getFacts())
			{
				newAppearsIn.get(f).merge(union);
			}

		}

		HashSet<MutexSet> singleSets = new HashSet<MutexSet>(
				newAppearsIn.values());
		HashSet<MutexSet> finals = new HashSet<MutexSet>(singleSets);
		for (MutexSet out : singleSets)
		{
			out.getFacts().retainAll(legalFacts);

			for (MutexSet in : singleSets)
			{
				if (in == out)
					continue;

				if (out.containsAll(in.getFacts()))
					finals.remove(in);
			}

		}

		return finals;
	}

	// /**
	// * Some facts can appear in more than one mutex set, for example A, B, C
	// could be mutex, but so can
	// * C, D. This will affect the denominator of Bayesian updates, giving
	// different results when C is checked
	// * with respect to each mutex set. This method resolves this by
	// overestimating the number of mutexes
	// * by unioning such sets. Thus, ABC, CD becomes ABCD. This means that all
	// mutexes will always be
	// * valid hypotheses (assuming they were correct in the first place), but
	// that by overestimating, not
	// * all valid hypotheses can be produced.
	// *
	// * @param currentSpace
	// * @return
	// * @throws IllegalArgumentException Thrown if overlaping mutexes remain
	// even after conversion.
	// * @throws NullPointerException Thrown if converted mutex space is not the
	// same size as the original space.
	// */
	// protected MutexSpace getOverestimatedMutexSpace(MutexSpace currentSpace,
	// Collection<? extends Fact> legalFacts) throws IllegalArgumentException,
	// NullPointerException
	// {
	// MutexSpace cloneSpace = (MutexSpace) currentSpace.clone();
	// Collection<MutexSet> mergedSets = this.createMergedMutexSets(cloneSpace,
	// legalFacts);
	//
	// System.out.println("Overestimated mutexes...");
	// for (MutexSet ms : mergedSets)
	// System.out.println(ms.toString());
	//
	//
	// //debug sanity check
	// HashSet<Fact> seen = new HashSet<Fact>();
	// for (MutexSet ms : mergedSets)
	// {
	// for (Fact f : ms.getFacts())
	// {
	// if (seen.contains(f))
	// throw new
	// IllegalArgumentException(f.toString()+" appears in more than one mutex set after set compilation");
	// else
	// seen.add(f);
	// }
	// }
	// System.out.println("Goal space now has no overlapping mutexes");
	//
	//
	// MutexSpace mergedSpace = new MutexSpace();
	// for (MutexSet ms : mergedSets)
	// {
	// mergedSpace.addMutexes(ms);
	// }
	//
	// Collection<Fact> culled = new HashSet<Fact>(cloneSpace.getKeys());
	// culled.retainAll(legalFacts);
	// int oldSize = culled.size();
	// int newSize = mergedSpace.getKeys().size();
	// if (oldSize != newSize)
	// throw new
	// NullPointerException("Not all facts in original goal space were re-created during mutex merging");
	//
	// return mergedSpace;
	// }

	protected void updateBayesianProbabilities(Action a)
	{
		// references to existing goal-spaces. These will be updated.
		Set<MutexGoalSpace> subGoalSpaces = ((VariableGoalSpace) this.goalSpace)
				.getVariableGoalSpaces();

		double lambda = IGRAPHPreferences.Lambda;
		// final double minLambda = 0.01;
		// final double maxLambda = 0.99;

		for (MutexGoalSpace subGoalSpace : subGoalSpaces)
		{
			HashMap<Fact, Double> singlePosteriors = new HashMap<Fact, Double>();

			double total = 0;

			double nearerCount = 0;
			for (Fact o : subGoalSpace.getGoals())
			{
				if (this.getHistory().get(this.getObservedStepCount())
						.getNearer().contains(o))
					nearerCount++;
			}

			// compute the probability for each goal in the mutex set and sum it
			// to get the denominator
			// this means that probabilities in here are not normalised
			for (Fact g : subGoalSpace.getGoals())
			{
				// double lambda = minLambda;
				// if
				// (this.getHistory().get(this.getObservedStepCount()).getNearer().contains(g))
				// lambda = 1d / nearerCount;
				//
				// if (lambda < minLambda)
				// lambda = minLambda;
				//
				// if (lambda >= 1d)
				// lambda = maxLambda;

				double workRight = (1d - lambda)
						* (1d / (double) subGoalSpace.size());

				// Double hBeforeO =
				// this.prevGoalDistMap.get(g).get(this.prevGoalDistMap.get(g).size()-2);
				// Double hAfterO =
				// this.prevGoalDistMap.get(g).get(this.prevGoalDistMap.get(g).size()-1);
				// boolean oWasHelpful = hAfterO < hBeforeO;

				double work = 0;

				// AllFalseGoals acts as the negation of all facts in the
				// sub-goal-space, so need to be treated differently.
				// Instead of computing the W(G) value for this fictional fact,
				// we compute the relevant value for
				// all the (implicit) negated versions of goal literals

				if (IGRAPHPreferences.WorkFunction == WorkFunctionType.ML)
				{
					work = this.getMaximumLikelihoodScore(g, subGoalSpace);
				}
				else if (IGRAPHPreferences.WorkFunction == WorkFunctionType.MLThreaded)
				{
					work = this.getMaximumLikelihoodThreadedScore(g,
							subGoalSpace);
					// double mlwork = this.getMaximumLikelihoodScore(g,
					// subGoalSpace); //FIXME comment out after debugging
					// if (work < mlwork)
					// throw new
					// IllegalArgumentException("MLT < ML: "+work+", "+mlwork);
					//
					// if
					// (g.toString().contains("communicated_soil_data waypoint0"))
					// System.out.println("ML: "+mlwork+", MLT: "+work);
				}
				else if (IGRAPHPreferences.WorkFunction == WorkFunctionType.SingleAction)
				{
					work = this.getSingleActionLikelihoodScore(g, a,
							subGoalSpace);
//					 work = this.getSingleActionLikelihoodDiscountedScore(g,
//					 (TimeStampedAction) a, subGoalSpace);
				}
				else
					throw new NullPointerException(
							"Unrecognised likelihood function type");

				// debug
				if (singlePosteriors.containsKey(g))
					throw new NullPointerException("Duplicates in goal-space!");

				double stability;
				if (g instanceof AllFalseGoal)
					stability = 1;
				else
					stability = this.getStability(g);

				//double stability = 1;
				double workLeft = lambda * stability * work;
				// double workLeft = lambda * work;

				double aGivenH = workLeft + workRight;

				double prior = subGoalSpace.getProbability(g);
				double post = prior * aGivenH;

				// if (g instanceof AllFalseGoal == false &&
				// this.prevGoalDistMap.get(g).get(this.planSoFar.getPlanLength()-1)
				// == 0 &&
				// this.prevGoalDistMap.get(g).get(this.planSoFar.getPlanLength())
				// == 0 &&
				// this.areZeroStepsHelpful())
				// {
				// post = prior;
				// }

				singlePosteriors.put(g, post); // non normalised posterior

				total += post;
			}

			// else
			// throw new
			// NullPointerException("Empty mutex set encountered during Bayes update");

			double totalPost = 0;
			double denom = total;
			for (Fact g : subGoalSpace.getGoals())
			{
				double bayesPost;
				double numerator = singlePosteriors.get(g);

				bayesPost = numerator / denom;

				double prior = subGoalSpace.getProbability(g); // for debugging

				// totalPost += (double) Math.round(bayesPost *
				// BayesianGoalRecogniser.NormalisedError) /
				// BayesianGoalRecogniser.NormalisedError;
				totalPost += bayesPost;

				subGoalSpace.setProbability(g, bayesPost);
			}
		}
		
		//finally, update historical Bayesian probabilities
		this.saveBayesianProbabilities(false);
	}
	
	/**
	 * Get the probability of a goal over all observations, including the initial state. As a goal can appear in
	 * multiple goal-spaces, there may be multiple answers.
	 * @param g
	 * @return
	 */
	public Map<MutexGoalSpace, List<Double>> getHistoricalProbabilities(Fact g)
	{
		Set<MutexGoalSpace> members = ((VariableGoalSpace) this.goalSpace).getMemberGoalSpaces(g);
		if (members.isEmpty())
			throw new NullPointerException("No goal spaces found containing "+g);
		
		HashMap<MutexGoalSpace, List<Double>> probs = new HashMap<MutexGoalSpace, List<Double>>();
		for (MutexGoalSpace mgs : members)
		{
			probs.put(mgs, this.historicalBayesianProbabilities.get(mgs).get(g));
		}
		
		return probs;
	}
	
	/**
	 * Saves the probabilities of the goals in the current goal-space.
	 * @see get
	 */
	protected void saveBayesianProbabilities(boolean initialise)
	{
		
		//set the initial probabilieis for each MutexGoalSpace
		for (MutexGoalSpace mgs : ((VariableGoalSpace) this.goalSpace).getVariableGoalSpaces())
		{
			if (initialise)
				this.historicalBayesianProbabilities.put(mgs, new HashMap<Fact, List<Double>>());
			
			for (Fact g : mgs.getGoals())
			{
				if (initialise)
					this.historicalBayesianProbabilities.get(mgs).put(g, new ArrayList<Double>());
				
				this.historicalBayesianProbabilities.get(mgs).get(g).add(mgs.getProbability(g));
			}
		}

	}

	/**
	 * Returns all applicable actions in the specified state.
	 * 
	 * @param s
	 * @return
	 */
	public Collection<Action> getApplicableActions(STRIPSState s)
	{
		HashSet<Action> applicable = new HashSet<Action>();
		for (Action a : this.problem.getActions())
		{
			if (a.isApplicable(s))
				applicable.add(a);
		}

		return applicable;
	}

	/**
	 * Returns the number of helpful observations divided by the total length of
	 * all helpful threads for the specified goal.
	 * 
	 * @param g
	 * @return
	 */
	protected double getMaximumLikelihoodThreadedScore(Fact g,
			MutexGoalSpace mgs)
	{

		// if the goal is an AllFalseGoal, we want to know how much work has
		// been put towards NOT acheiving the fact
		// This means how many steps have moved the goal away from being
		// achieved rather than moving towards.
		// This has to be computed manually.
		if (g instanceof AllFalseGoal)
		{
			// if we are interested in finding out how many observations have
			// been put towards
			// NOT achieving the goal, then this is simply the "negated" ML
			// score, as this includes
			// all observations, not just those on the threads which have been
			// helpful in achieving G.
			// Threfore we just pass it onto the ML score function.
			double notAchieveWork = this.getMaximumLikelihoodScore(g, mgs);

			return notAchieveWork;
		}

		// if this point is reached, then the fact is a positive literal and
		// have
		// its MLT score computed normally.
		// double helpful = this.usefulDistanceMap.get(g);
		// if (helpful == 0) //early-out
		// return 0;
		//
		// double threadTotal = this.goalSupportLengthMap.get(g);
		//
		// this.threader.getGraph().generateDotGraph(new
		// File("/tmp/rover_thread.dot"));
		//
		// if (helpful > threadTotal || threadTotal >
		// this.getObservedStepCount())
		// {
		// throw new
		// IllegalArgumentException("Number of helpful steps is greater than total support threads!");
		// }

		// new test code

		/*
		 * This code replaces the idea of |helpful|/|total thread length|
		 * because eventually this converges on the ML score for most problems.
		 * If the threader produced a better graph (a la what it used to do),
		 * this would be less of an issue. Therefore, this code says that the
		 * score is |helpful in last thread|/|length of said thread| This means
		 * that all work assigned to threads which were not the one the previous
		 * action was appended to, is ignored. In a perfect world, every goal
		 * would have its own thread
		 */
		Action lastAction = this.planSoFar.getActions().get(
				this.planSoFar.getActionCount() - 1);
		PlanThread threadAdded = null;
		for (PlanThread t : this.threader.getLiveThreads())
		{
			for (ActionStateTuple st : t.getActions())
			{
				if (st.action.action.equals(lastAction))
				{
					threadAdded = t;
					break;
				}
			}
		}

		double helpfulObs = 0;
//		int consecutiveTrue =  this.getTrueStepsCount(g);
		boolean bonusApplicable = this.isBonusApplicable(g, mgs);
		
		for (ActionStateTuple a : threadAdded.getActions())
		{
			if (a.action instanceof MergeAction)
				continue;

			int idx = this.planSoFar.getActions().indexOf(a.action.action);

			//did the fact get heuristically closer
			StateHistoryTuple h = this.getHistory().get(idx);
			boolean nearer = h.getNearer().contains(g);
//			System.out.println(g+" is nearer "+nearer);
			
			//is the fact unmoved, true at the current timestep, and are zero steps helpful
//			boolean zeroMove = (this.getHistory().get(idx).getUnmoved().contains(g) && this.areZeroStepsHelpful() && consecutiveTrue > 1);
			boolean zeroMove = (this.areZeroStepsHelpful() && bonusApplicable);
			boolean helpfulFlag = nearer || zeroMove;
			
			if (helpfulFlag)
				helpfulObs += 1;
		}

		double helpful = helpfulObs;
		double threadTotal = threadAdded.getActionLength();
		

		if (threadTotal <= 0)
			return 0;
		
		double res = helpful / threadTotal;
		// double res = threadTotal / this.getObservedStepCount();

//		 System.out.println("MLT: "+res + " = "+helpful+ "/"+threadTotal);

		return res;
	}

	protected double getMaximumLikelihoodScore(Fact g, MutexGoalSpace mgs)
	{
		double totalObs = this.getObservedStepCount();
		//early out
		if (this.getObservedStepCount() <= 0)
			return 0;

		/*
		 * If G is an instance of an AllFalseGoal, we compute a different ML
		 * function, which is equivalent to the sum of the ML score for the
		 * *negated* versions of the literals held in the specified goal-space.
		 * This is normalised to get a value [0:1] by dividing by the number of
		 * observations so far, multiplied by the number of goals in the
		 * goal-space. That is, the denominator will equal a perfect ML score.
		 */
		if (g instanceof AllFalseGoal)
		{
			
			// TODO horribly inefficient method
			double uselessSteps = 0;
			out: for (int i = 0; i < this.getObservedStepCount(); i++)
			{
				StateHistoryTuple t = this.history.get(i + 1); // +1 because we
																// want to skip
																// the initial
																// history state
				for (Fact f : mgs.getGoals())
				{
					// assume only 1 AllFalseGoal in a mutex set
					if (f == g)
						continue;

					if (t.getNearer().contains(f)
							|| (this.areZeroStepsHelpful() && this.isBonusApplicable(g, mgs)))
					{
						continue out;
					}
					// if (t.getFurther().contains(f) == false)
					// continue out;
				}

				++uselessSteps; // if we;ve reached here then all steps were
								// useless
			}

			double res = uselessSteps / this.getObservedStepCount(); // ratio of
																		// completely
																		// useless
																		// steps
																		// in
																		// this
																		// plan

			return res;
		}
		
		double helpful = this.usefulDistanceMap.get(g);
		System.out.println(g+" is nearer "+helpful);
		if (helpful == 0) // early-out
			return 0;


		double res = helpful / totalObs;
		
		System.out.println("ML = "+res);

		return res;
	}

	private double discount = 0.1;

	protected double getSingleActionLikelihoodDiscountedScore(Fact g,
			TimeStampedAction a, MutexGoalSpace mgs)
	{
		int t = a.getMajorTime().intValue();

		return this.getSingleActionLikelihoodDiscountedScore(g, a, mgs, t);
	}

	protected double getSingleActionLikelihoodDiscountedScore(Fact g,
			TimeStampedAction a, MutexGoalSpace mgs, int t)
	{
		if (t <= 0)
			return 0;

		double discount = Math.pow(this.discount,
				(a.getMajorTime().intValue() - t));
		double wsa = 0;
		for (int i = 1; i <= t; i++)
			wsa += this.getSingleActionLikelihoodScore(g, a, mgs, i);

		return (wsa * discount)
				+ this.getSingleActionLikelihoodDiscountedScore(g, a, mgs,
						t - 1);
	}
	
	/**
	 * Determines whether the bonus is applicable for a given fact and the associated mutex goal space.
	 * A bonus is applicable if the goal has been true for at least the previous 2 consecutive timesteps 
	 * and all other facts in the associated goal space have gotten further away. 
	 * @param g The goal to find if the bonus is applicable.
	 * @param mgs The goal space the goal is associated with.
	 * @return True if the bonus criteria are met, false otherwise.
	 * @throws IllegalArgumentException Thrown if the goal is not in the goal-space.
	 */
	protected boolean isBonusApplicable(Fact g, MutexGoalSpace mgs) throws IllegalArgumentException
	{		
		if (mgs.getGoals().contains(g) == false)
			throw new IllegalArgumentException("Specified goal is not in the associated goal-space");
		
		int trueSteps = this.getTrueStepsCount(g);
		if (trueSteps <= 1)
			return false;
		
		//find if every other goal in the mutex goal space has gotten further away
		boolean allFurtherOrSame = true;
		StateHistoryTuple lastHistory = this.getHistory().last();
		for (Fact f : mgs.getGoals())
		{
			if (f == g)
				continue;
			
			if (f instanceof AllFalseGoal)
				continue;
			
			if (lastHistory.getNearer().contains(f))
			{
				allFurtherOrSame = false;
				break;
			}
		}
		
		return allFurtherOrSame;
	}

	/**
	 * Computes the likelihood probability of a fact being in the goal, given
	 * the previous observation. This is computed as 1/|G_nearer| where G_nearer
	 * is the number of goals in the mutex set which this fact belongs to, that
	 * have become closer.
	 * 
	 * @param g
	 * @param a
	 * @param mutexSpace
	 * @return
	 */
	protected double getSingleActionLikelihoodScore(Fact g, Action a,
			MutexGoalSpace mgs)
	{
		return this.getSingleActionLikelihoodScore(g, a, mgs,
				this.getObservedStepCount());
	}	
	
	/**
	 * Returns the number of helpful observations divided by the total length of
	 * all helpful threads for the specified goal.
	 * 
	 * @param g
	 * @return
	 */
	// this version uses 1 / |G_nearer|, but 
	protected double getSingleActionLikelihoodScore(Fact g, Action lastAction,
			MutexGoalSpace mgs, int t)
	{
		StateHistoryTuple historyTuple = this.getHistory().get(t);
//		StateHistoryTuple historyTuple = this.getHistory().get(this.getObservedStepCount());
		
		double helpfulObs = 0, unhelpfulObs = 0;

		// if the goal is an AllFalseGoal, we want to know how much work has
		// been put towards NOT acheiving the fact
		// This means how many steps have moved the goal away from being
		// achieved rather than moving towards.
		// This has to be computed manually.
		if (g instanceof AllFalseGoal)
		{
			for (Fact f : mgs.getGoals())
			{
				if (f == g)
					continue;
				
				boolean bonusApplicable = this.isBonusApplicable(f, mgs);
				
				if (historyTuple.getFurther().contains(f))
					++unhelpfulObs;
//				else if (historyTuple.getUnmoved().contains(f) && this.areZeroStepsHelpful() && this.getTrueStepsCount(f) > 1)
				else if (this.areZeroStepsHelpful() && bonusApplicable)
					++helpfulObs;
			}
			
			if (helpfulObs > 0)
				return 0;
			
			if (unhelpfulObs == (mgs.size()-1))
				return 1;
			else 
				return 0;
//			if (unhelpfulObs > 0)
//				return unhelpfulObs / (double) mgs.size();
//			else
//				return 0;
		}
		else
		{
			boolean helpful = historyTuple.getNearer().contains(g);
			if (!helpful)
				return 0;
			
			helpfulObs = 1;
			for (Fact f : mgs.getGoals())
			{
				if (f == g || f instanceof AllFalseGoal)
					continue;
				
				
				if (historyTuple.getNearer().contains(f))
				{
					++helpfulObs;
				}
				else if (historyTuple.getUnmoved().contains(f) && this.areZeroStepsHelpful() && this.getTrueStepsCount(f) > 0)
				{
					++helpfulObs;
				}
				else
				{
					historyTuple.getFurther().contains(f);
					++unhelpfulObs;
				}
			}
		}
			
		if (helpfulObs <= 0)
			return 0;

//		double res = helpfulObs / (helpfulObs + unhelpfulObs);
		double res = 1d / helpfulObs;

		return res;
	}

	
	
//	/**
//	 * Returns the number of helpful observations divided by the total length of
//	 * all helpful threads for the specified goal.
//	 * 
//	 * @param g
//	 * @return
//	 */
//	// this version uses the total distance moved towards / total distance
//	// moved, but only in the context of all threads
//	// which have been helpful towards achieving the goal
//	protected double getSingleActionLikelihoodScore(Fact g, Action lastAction,
//			MutexGoalSpace mgs, int t)
//	{
//		//check for any movement in any of the facts in the mutex set -- if nothing
//		//has moved, move on
//
//		boolean someMovement = false;
//		StateHistoryTuple lastHistoryTuple = this.getHistory().get(this.getObservedStepCount());
//		for (Fact f : mgs.getGoals())
//		{
//			if (f instanceof AllFalseGoal)
//				continue;
//			
//			if (lastHistoryTuple.getUnmoved().contains(f) == false)
//			{
//				someMovement = true;
//				break;
//			}
//		}
//		
//		//early out.
//		if (!someMovement)
//			return 0;
//		
//		
//		
//		double helpfulObs = 0, unhelpfulObs = 0;
//
//		// if the goal is an AllFalseGoal, we want to know how much work has
//		// been put towards NOT acheiving the fact
//		// This means how many steps have moved the goal away from being
//		// achieved rather than moving towards.
//		// This has to be computed manually.
//		if (g instanceof AllFalseGoal)
//		{
//			// //if we are interested in finding out how many observations have
//			// been put towards
//			// //NOT achieving the goal, then this is simply the "negated" ML
//			// score, as this includes
//			// //all observations, not just those on the threads which have been
//			// helpful in achieving G.
//			// //Threfore we just pass it onto the ML score function.
//			// double notAchieveWork = this.getMaximumLikelihoodScore(g, mgs);
//			//
//			HashSet<Action> helpfulActions = new HashSet<Action>();
//			HashSet<Action> supportActions = new HashSet<Action>();
//			for (Fact f : mgs.getGoals())
//			{
//				if (f == g)
//					continue;
//
//				// get all threads which have in some way supported this goal
//				Set<PlanThread> support = this.goalSupportThreadMap.get(f);
//				// TODO horribly inefficient method
//				for (PlanThread s : support)
//				{
//					out: for (ActionStateTuple a : s.getActions())
//					{
//						if (a.action.getTime().intValue() > t)
//							continue;
//
//						if (a.action instanceof MergeAction)
//							continue;
//
//						supportActions.add(a.action.action);
//
//						int idx = this.planSoFar.getActions().indexOf(
//								a.action.action); // 0 indexed
//
//						StateHistoryTuple hist = this.getHistory().get(idx + 1); // 1
//																					// indexed
//																					// because
//																					// of
//																					// initial
//																					// state
//																					// history
//																					// tuple
//
//						// assume only 1 AllFalseGoal in a mutex set
//						if (f == g)
//						{
//							continue;
//						}
//
//						if (hist.getNearer().contains(f) || 
//								(hist.getUnmoved().contains(f) && this.areZeroStepsHelpful() && this.prevGoalDistMap.get(f).get(idx + 1) == 0))
//						{
//							helpfulActions.add(a.action.action);
//
//							continue out;
//						}
//					}
//
//				}
//			}
//
//			HashSet<Action> completelyUnhelpfulActions = supportActions;
//			completelyUnhelpfulActions.removeAll(helpfulActions);
//
//			double allSupportingActionsCount = supportActions.size();
//			double completelyUnhelpfulActionCount = supportActions.size();
//
//			if (completelyUnhelpfulActionCount <= 0)
//				return 0;
//
//			double unhelpfulness = completelyUnhelpfulActionCount / (allSupportingActionsCount);
//			return unhelpfulness;
//		}
//		else
//		{
//			// get all threads which have in some way supported this goal
//			Set<PlanThread> support = this.goalSupportThreadMap.get(g);
//			for (PlanThread s : support)
//			{
//				for (ActionStateTuple a : s.getActions())
//				{
//					if (a.action.getTime().intValue() > t)
//						continue;
//
//					if (a.action instanceof MergeAction)
//						continue;
//
//					int idx = this.planSoFar.getActions().indexOf(
//							a.action.action);
//
//					StateHistoryTuple hist = this.getHistory().get(idx + 1);
//					boolean nearer = hist.getNearer().contains(g);
//					boolean further = hist.getFurther().contains(g);
//
//					boolean isValidZeroMove = false;
//
//					isValidZeroMove = (this.areZeroStepsHelpful()
//							&& hist.getUnmoved().contains(g) && this.prevGoalDistMap
//							.get(g).get(idx + 1) == 0);
//					if (isValidZeroMove)
//					{
//						isValidZeroMove = false;
//						for (Fact f : mgs.getGoals())
//						{
//							if (f == g)
//								continue;
//
//							if (hist.getNearer().contains(f)
//									|| hist.getFurther().contains(f))
//							{
//								isValidZeroMove = true;
//								break;
//							}
//						}
//
//					}
//
//					if (nearer || isValidZeroMove)
//					{
//						helpfulObs += 1;
//					}
//					if (further)
//					{
//						unhelpfulObs += 1;
//					}
//				}
//			}
//		}
//
//		if (mgs.toString().contains("communicated_soil_data waypoint6"))
//			System.out.println("Communicated_soil_data wp6 some movement :"+someMovement);
//			
//		if (helpfulObs <= 0)
//			return 0;
//
//		double res = helpfulObs / (helpfulObs + unhelpfulObs);
//
//		return res;
//	}

	// /**
	// * Computes the likelihood probability of a fact being in the goal, given
	// the previous observation. This
	// * is computed as
	// * 1/|G_nearer| where G_nearer is the number of goals in the mutex set
	// which this fact belongs to, that have
	// * become closer.
	// * @param g
	// * @param a
	// * @param mutexSpace
	// * @param t The timestamp to get the SA score for. Must be greater than 0.
	// * @return
	// */
	// protected double getSingleActionLikelihoodScore(Fact g, Action a,
	// MutexGoalSpace mgs, int t)
	// {
	// StateHistoryTuple hist = this.getStateAt(t);
	//
	// double allNearer = 0, allFurther = 0;
	//
	// for (Fact f : mgs.getGoals())
	// {
	// if (f instanceof AllFalseGoal)
	// continue;
	//
	// boolean fGotNearer = hist.getNearer().contains(f) ||
	// (hist.getUnmoved().contains(f) && this.areZeroStepsHelpful() &&
	// this.prevGoalDistMap.get(g).get(t) == 0);
	// if (fGotNearer)
	// ++allNearer;
	// else if (hist.getFurther().contains(f))
	// ++allFurther;
	// }
	//
	// if (g instanceof AllFalseGoal)
	// {
	// if (allFurther > 0)
	// return allFurther / (allNearer + allFurther);
	// else
	// return 0;
	// }
	// else
	// {
	// boolean gGotNearer = hist.getNearer().contains(g) ||
	// (hist.getUnmoved().contains(g) && this.areZeroStepsHelpful() &&
	// this.prevGoalDistMap.get(g).get(t) == 0);
	// if (gGotNearer && allNearer + allFurther > 0)
	// {
	// return 1d / (allNearer + allFurther);
	// }
	// else
	// return 0;
	// }
	// }

	// //////////////////////////////////////////
	//
	// Getters/Setters
	//
	// //////////////////////////////////////////
	/**
	 * Returns whether the goal hypothesis will be updated after a call to
	 * actionObserved().
	 * 
	 * @return
	 */
	public boolean updatingAfterObservation()
	{
		return updateAfterObservation;
	}

	public void setUpdateAfterObservation(boolean updateAfterObservation)
	{
		this.updateAfterObservation = updateAfterObservation;
	}

	public long getStartTime()
	{
		return startTime;
	}

	public void setStartTime(long startTime)
	{
		this.startTime = startTime;
	}

	/**
	 * Get the goal space this recogniser is using.
	 * 
	 * @return
	 */
	public IGoalSpace getGoalSpace()
	{
		return goalSpace;
	}

	/**
	 * Get the history of observation.
	 * 
	 * @return
	 */
	public StateHistory getHistory()
	{
		return history;
	}

	/**
	 * Gets the {@link PredicatePartitioner}.
	 * 
	 * @return
	 */
	public PredicatePartitioner getPredicatePartitioner()
	{
		return predicatePartitioner;
	}

	/**
	 * Returns whether zero-move steps between observations are classed as
	 * helpful.
	 * 
	 * @return
	 */
	public boolean areZeroStepsHelpful()
	{
		return zeroStepsAreHelpful;
	}

	/**
	 * Sets whether zero-move steps between observations are classed as helpful.
	 * 
	 * @return
	 */
	public void setZeroStepsAreHelpful(boolean zeroStepsAreHelpful)
	{
		this.zeroStepsAreHelpful = zeroStepsAreHelpful;
	}

	/**
	 * Immediately kill the recognition process. Terminates all running threads
	 * without waiting for them to return.
	 */
	public void terminate()
	{
		this.threadPool.shutdownNow();
		this.heuristicManager.shutdown();
	}
}
