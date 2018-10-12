package recogniser.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import sas.data.CausalGraph;
import sas.data.DomainTransitionGraph;
import sas.data.NoneOfThoseProposition;
import sas.data.SASAction;
import sas.data.SASDerivedProposition;
import sas.data.SASDomainObject;
import sas.data.SASLiteral;
import sas.data.SASMutexGroup;
import sas.data.SASProblem;
import sas.data.SASProposition;
import sas.data.SASState;
import sas.data.SASVariable;
import sas.util.SASException;
import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundProblem;
import javaff.data.MutexSet;
import javaff.data.MutexSpace;
import javaff.data.NullInstantAction;
import javaff.data.Parameter;
import javaff.data.strips.And;
import javaff.data.strips.Proposition;
import javaff.planning.STRIPSState;
import javaff.planning.State;
import javaff.search.UnreachableGoalException;

/**
 * Uses a standard PDDL GroundProblem and refines its actions and reachable facts using SAS+.
 * @author David Pattison
 *
 */
public class HybridSasPddlProblem extends GroundProblem
{
	public SASProblem sasproblem;
	public final GroundProblem originalProblem;
	
	public MutexSpace completeMutexSpace;
	public Set<MutexSpace> singleMutexSpaces;
	
	private HashMap<String, SASAction> sasActionLookup;
//	private HashMap<String, Action> pddlActionLookup;
//	
//	private HashMap<String, SASLiteral> sasFactLookup;
//	private HashMap<String, Fact> pddlFactLookup;

	protected HybridSasPddlProblem(GroundProblem gp)
	{
		super(gp.getActions(), gp.getInitial(), gp.getGoal(), gp.getFunctionValues(), gp.getMetric());
		this.originalProblem = (GroundProblem) gp.clone();
		
		this.sasproblem = null;
		
		this.completeMutexSpace = new MutexSpace();
		this.sasActionLookup = new HashMap<String, SASAction>();
//		this.pddlActionLookup = new HashMap<String, Action>();
//		this.sasFactLookup = new HashMap<String, SASLiteral>();
//		this.pddlFactLookup = new HashMap<String, Fact>();
	}

	public HybridSasPddlProblem(GroundProblem gp, SASProblem sp) throws UnreachableGoalException
	{
		this(gp);
		
		this.sasproblem = sp;
		
		this.createLookups();
		this.filterReachableFacts();
	}

	/**
	 * Creates a clone of this hybrid domain. Note that internal lookup lists are only shallow copies.
	 */
	@Override
	public Object clone()
	{
		GroundProblem gproblemClone = (GroundProblem) super.clone();
		SASProblem sasProblemClone = (SASProblem) this.sasproblem.clone();
		
		HybridSasPddlProblem clone = new HybridSasPddlProblem(gproblemClone);
//		clone.state = (STRIPSState) gproblemClone.state.clone();
				
		clone.sasproblem = sasProblemClone;
		clone.completeMutexSpace = (MutexSpace) this.completeMutexSpace.clone();
		
		
		//the following are shallow-clones
		clone.sasActionLookup = (HashMap<String, SASAction>) this.sasActionLookup.clone();
//		clone.sasFactLookup = (HashMap<String, SASLiteral>) this.sasFactLookup.clone();
//		clone.pddlActionLookup = (HashMap<String, Action>) this.pddlActionLookup.clone();
//		clone.pddlFactLookup = (HashMap<String, Fact>) this.pddlFactLookup.clone();
		
		return clone;
	}

	private void createLookups()
	{
		this.sasActionLookup = new HashMap<String, SASAction>();
		for (SASAction a : this.sasproblem.actions.values())
		{
			this.sasActionLookup.put(a.toString(), a);
		}
		
//		this.pddlActionLookup = new HashMap<String, Action>();
//		for (Action a : this.getActions())
//		{
//			this.pddlActionLookup.put(a.toString(), a);
//		}
//
//		this.sasFactLookup = new HashMap<String, SASLiteral>();
//		for (SASLiteral f : this.sasproblem.reachableFacts)
//		{
//			this.sasFactLookup.put(f.toString(), f);
//		}
//		
//		this.pddlFactLookup = new HashMap<String, Fact>();
//		for (Fact f : this.getReachableFacts())
//		{
//			this.pddlFactLookup.put(f.toString(), f);
//		}
	}

	public void filterReachableFacts() throws UnreachableGoalException
	{
		//setup reachable facts -- ignore gp.groundedPropositions, because this contains ALL facts, both illegal and legal
		
		//calling the super method will populate reachableFacts with all those facts that can be accessed through an RPG analysis
		//we want to further refine this set if possible, but only by removing facts. SAS+ translation itself may still fail to pick
		//up some unreachable facts (possibly due to the modified LAMA translator which IGRAPH uses.
		super.filterReachableFacts(true);
		HashSet<Fact> rpgReachableFacts = new HashSet<Fact>(super.getReachableFacts());
		HashSet<Action> rpgReachableActions = new HashSet<Action>(super.getActions());
		int rpgFacts = rpgReachableFacts.size();
		int rpgActions = rpgReachableActions.size();
		
		HashSet<Fact> sasReachableFacts = new HashSet<Fact>();
		HashSet<Action> sasReachableActions = new HashSet<Action>();
		
		for (SASProposition p : this.sasproblem.reachableFacts)
		{
			if (p instanceof SASDerivedProposition || p instanceof NoneOfThoseProposition)
				continue;
			
			Proposition pddl = null;
			try
			{
				pddl = p.convertToPDDL(this.sasproblem, this);
			}
			catch (NullPointerException e1)
			{
				;
			}
			
			if (pddl == null)
			{
				System.err.println("Could not find "+p.toString());
			}
			else// if (pddl.isStatic() == false)
			{
				sasReachableFacts.add(pddl);
//				super.getReachableFacts().add(pddl);
			}
		}
		
		HashSet<Fact> achieved = new HashSet<Fact>();
		for (SASAction a : this.sasproblem.actions.values())
		{
			try
			{
				Action pddl = a.convertToPDDL(this.sasproblem, this);
				
				if (pddl == null)
				{
					System.out.println("Could not find "+a.toString());
				}
				else
				{
					sasReachableActions.add(pddl);
					achieved.addAll(pddl.getAddPropositions());
					achieved.addAll(pddl.getDeletePropositions());
					achieved.addAll(pddl.getPreconditions());
				}
			}
			catch (NullPointerException e)
			{
				System.out.println(e.getMessage());
			}

		}
		
		//retain only those facts and actions which are reachable through SAS+ reachability analysis
		rpgReachableFacts.retainAll(sasReachableFacts);
		rpgReachableFacts.retainAll(achieved);
		
		rpgReachableActions.retainAll(sasReachableActions);
		
		super.setReachableFacts(rpgReachableFacts);
		super.setActions(rpgReachableActions);
		
//		super.setActions(refinedActions);
		
		super.setState(this.sasproblem.getCurrentState().convertToPDDL(this.sasproblem, this));
		super.recomputeSTRIPSInitialState();
		super.setInitial(((STRIPSState)super.getState()).getTrueFacts());
		
		MutexSpace superMutexSet = new MutexSpace();
		this.singleMutexSpaces = new HashSet<MutexSpace>();
		for (SASMutexGroup sm : this.sasproblem.mutexes.values())
		{
			MutexSet pddl = sm.convertToPDDL(this.sasproblem, this);
			superMutexSet.addMutexes(pddl);
			
			this.singleMutexSpaces.add(new MutexSpace(pddl));
		}
		this.completeMutexSpace = superMutexSet;
		
		System.out.println("Hybrid reachability analysis: "+rpgFacts+" facts before, "+rpgReachableFacts.size()+
				" now. "+rpgActions+" actions, "+rpgReachableActions.size()+" actions after");
	}
	
	public CausalGraph getCausalGraph()
	{
		return this.sasproblem.causalGraph;
	}
	
	public Collection<DomainTransitionGraph> getDTGs()
	{
		return this.sasproblem.causalGraph.getDTGs();
	}

	public void updateState(Action a)
	{
		//apply the JavaFF action to the current state
		this.setState((STRIPSState) this.getState().apply(a)); 
		
		//apply the appropriate JavaSAS SASAction to the SAS+ state
		if (a instanceof NullInstantAction == false)
		{
			SASState next = this.sasproblem.state.apply(this.sasActionLookup.get(a.toString()));
			this.sasproblem.setCurrentState(next);
		}
	}
}
