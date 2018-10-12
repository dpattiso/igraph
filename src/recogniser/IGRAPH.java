package recogniser;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import recogniser.hypothesis.IGoalHypothesis;
import recogniser.learning.agent.IAgent;
import recogniser.learning.agent.NullAgent;
import recogniser.util.HybridSasPddlProblem;
import recogniser.util.RecognitionException;
import sas.data.DomainTransitionGraph;

import javaff.JavaFF;
import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundProblem;
import javaff.data.Plan;
import javaff.data.strips.And;
import javaff.data.strips.Not;
import javaff.data.strips.STRIPSInstantAction;
import javaff.planning.STRIPSState;
import javaff.scheduling.SchedulingException;
import javaff.search.UnreachableGoalException;

/**
 * A goal recogniser engine which ties into a landmark-based planner to complete the plan.
 * 
 * To be honest this is more of the testbed than the recogniser.
 * 
 * @author David Pattison
 *
 */
//This is IGRAPH2/AUTOGRAPH3 -- which now uses a goal-space over other goal-spaces. These sub-goal-spaces
//are probability distributions across mutex sets. The previous version used a single goal-space and over
//estimated completeMutexSpace, but this just leads to hacks all round.
public class IGRAPH
{	
	private JavaFF planner;
	private HybridSasPddlProblem hybridProblem;
	
	private STRIPSState initialState;
	
	private BayesianGoalRecogniser goalRecogniser;
		
	private File domainFile;

	private Collection<Plan> hypotheses;
	
	/**
	 * Construct a landmark recogniser which works with the specified domain and problem definition.
	 * @param domainDef The domain to parse.
	 * @param pFile The problem definition to parse.
	 * @throws RecognitionException 
	 */
	public IGRAPH(HybridSasPddlProblem problem, File domain) throws RecognitionException
	{
		this.init(problem, domain);
	}
	
	
	private void init(HybridSasPddlProblem problem, File domain) throws RecognitionException
	{
		this.hybridProblem = problem;
		this.domainFile = domain;
		
		//expand out any NoneOfThose transitions 
//		this.decompileDTGs();
		
		this.planner = new JavaFF(domain, null);
		
//		//FIXME (yet another) hack to get rid of Pause mutexes- SAS+ removes them and statics from the reachable facts, but not from actions
//		for (Action a : this.hybridProblem.getActions())
//		{
////			Set<Fact> newPC = new HashSet<Fact>(a.getPreconditions());
////			newPC.retainAll(this.hybridProblem.reachableFacts);
//			
//			Set<Fact> newAdd = new HashSet<Fact>(a.getAddPropositions());
//			newAdd.retainAll(this.hybridProblem.getReachableFacts());
//			
//			Set<Fact> newDel = new HashSet<Fact>();
//			for (Not d : a.getDeletePropositions())
//			{
//				newDel.add(d.literal);
//			}
//			newDel.retainAll(this.hybridProblem.getReachableFacts());
//
//			And newEffect = new And(newAdd);
//			for (Fact del : newDel)
//				newEffect.add(new Not(del));
//			
////			((STRIPSInstantAction)a).setCondition(new And(newPC));
//			((STRIPSInstantAction)a).setEffect(newEffect);
//		}	
		
		
		this.initialiseGoalRecogniser();
		
		this.hypotheses = new HashSet<Plan>();
	}
	
	protected void initialiseGoalRecogniser() throws RecognitionException
	{
		this.goalRecogniser = new BayesianGoalRecogniser(this.hybridProblem, NullAgent.getInstance()); 
		this.goalRecogniser.setUpdateAfterObservation(true);
	}


	/**
	 * Computes a full plan to the goal by first deciphering which goals are most probable,
	 * then planning from the current state to those goals.
	 * @return A plan from the current state to the goal.
	 */
	public Collection<Plan> computePlan(IAgent agent)
	{
		IGoalHypothesis probableGoal = goalRecogniser.getImmediateGoalHypothesis(); 
		GroundProblem gpClone = (GroundProblem)this.hybridProblem.clone();
		//TODO adjust planner settings for specific agent policies here
		gpClone.setGoal(probableGoal.getGoals());
		
		try
		{
			Plan plan = this.planner.plan(gpClone);//, this.pFile.getAbsolutePath());
			//TODO add multiple plans/hypotheses and timestamp
			
			this.hypotheses.clear();
			this.hypotheses.add(plan);
		}
		catch (javaff.search.UnreachableGoalException e)
		{
			System.out.println("Cannot find plan for hypothesis "+probableGoal);
		}
		
		return hypotheses;
	}
	
	/**
	 * Compiles DTGs which contain NoneOfThose transition states out to all possible 
	 * "true" states. For example, if the DTG has transitions (2) -> (1), (3) -> (1), (4) -> (1)
	 * , where 1 is a SAS+ NoneOfThose state, the compiled DTG would be 
	 * (2) -> (3)
	 * (2) -> (4)
	 * (3) -> (2)
	 * (3) -> (4)
	 * (4) -> (2)
	 * (4) -> (3)
	 * 
	 * Note that action transitions currently may not make sense because the original edge from 
	 * NoneOfThose to X is only of size 1, when there may be N possibilities.
	 * @param dtgs
	 * @return
	 */
//	private void decompileDTGs()
//	{
//		for (DomainTransitionGraph d : this.hybridProblem.getCausalGraph().getDTGs())
//		{
//			d.decompileUniversalTransitions();
//		}
//	}
	


	/**
	 * Feeds an action into a goal recogniser. Actions are assumed to be added in a linear order.
	 * 
	 * @param a The action performed.
	 * @param source The agent which performed the action.
	 * @throws SchedulingException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws UnreachableGoalException 
	 * @throws RecognitionException 
	 */
	public void actionObserved(Action a, IAgent source) throws SchedulingException, UnreachableGoalException, InterruptedException, ExecutionException, RecognitionException
	{
		this.goalRecogniser.actionObserved(a, source);
	}
	
	

	
	public Collection<Plan> getHypotheses()
	{
		return hypotheses;
	}

	public void setHypotheses(Collection<Plan> hypotheses)
	{
		this.hypotheses = hypotheses;
	}

	public JavaFF getPlanner()
	{
		return planner;
	}

	public STRIPSState getInitialState()
	{
		return initialState;
	}

	public BayesianGoalRecogniser getGoalRecogniser()
	{
		return goalRecogniser;
	}

	public File getDomainFile()
	{
		return domainFile;
	}


	public void terminate()
	{
		this.goalRecogniser.terminate();
	}


	public HybridSasPddlProblem getHybridProblem()
	{
		return hybridProblem;
	}


	public void setHybridProblem(HybridSasPddlProblem hybridProblem)
	{
		this.hybridProblem = hybridProblem;
	}


	public void setPlanner(JavaFF planner)
	{
		this.planner = planner;
	}


	public void setInitialState(STRIPSState initialState)
	{
		this.initialState = initialState;
	}


	public void setGoalRecogniser(BayesianGoalRecogniser goalRecogniser)
	{
		this.goalRecogniser = goalRecogniser;
	}


	public void setDomainFile(File domainFile)
	{
		this.domainFile = domainFile;
	}
	
}
