package recogniser.learning;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import javaff.data.Action;
import javaff.data.GroundProblem;
import javaff.data.strips.Proposition;
import javaff.planning.TemporalMetricState;

/**
 * Analyses a grounded domain to find orderings naturally present between actions.
 * @author David Pattison
 * @deprecated Of no real use in recognition.
 */
@Deprecated
public class DomainAnalyser
{
	private Set<CausalLink> requiredOrderings;
	private Set<CausalLink> strictCausalLinks;
	private Set<CausalLink> totalCausalLinks;
	private Set<CausalLink> causalLinks;
	private int maxCausalChainSize;
	private boolean allowRepeatActions, allowReverseActions; //TODO add in LoopAction class
	
	public DomainAnalyser()
	{
		this.requiredOrderings = new HashSet<CausalLink>();
		this.strictCausalLinks = new HashSet<CausalLink>();
		this.totalCausalLinks = new HashSet<CausalLink>();
		this.causalLinks = new HashSet<CausalLink>();
		this.maxCausalChainSize = 2;
		this.allowRepeatActions = false;
		this.allowReverseActions = false;
		
	}


	public void clearAll()
	{
		this.requiredOrderings.clear();
		this.strictCausalLinks.clear();
		this.totalCausalLinks.clear();
		this.causalLinks.clear();
	}
	
	/**
	 * Analyses the specified grounded problem for causal links.
	 * @param gp
	 */
	public void analyse(GroundProblem gp)
	{		
		//find actions which require another action to be applied immediately prior to it- causal links
		generateCausalLinks(gp);
		
		//find propositions which are only achieved by 1 action- unlikely to actually produce any for most domains
		//good candidate for disjunctive landmarks?
		generateTotalOrderings(gp);
		
		
		generateStrictOrderings(gp);
		
//		generateRequiredOrderings(gp);
				
		System.out.println("found "+this.strictCausalLinks.size()+" strict causal links");// "+this.strictCausalLinks);
		System.out.println("found "+this.totalCausalLinks.size()+" total causal links");//+: "+this.totalCausalLinks);
		System.out.println("found "+this.causalLinks.size()+" causal links");
//		System.out.println("found "+this.requiredOrderings.size()+" required orderings: "+this.requiredOrderings);
		
		//"relevant actions"- causal link exists from Ai to G, or A to B to G exists, and A < B (ie landmark)
	}


	private void generateCausalLinks(GroundProblem gp)
	{
		this.causalLinks.clear();
		for (Object ao : gp.getActions())
		{
			Action head = (Action)ao;
			CausalLink link = new CausalLink();
			link.addAction(head);
	
			Queue<CausalLink> queue = new LinkedList<CausalLink>();
			queue.add(link);
			
			while (queue.isEmpty() == false)
			{
//				System.out.println("queue is "+queue.size());
				CausalLink cur = queue.poll();
				if (cur.size() >= this.maxCausalChainSize)
				{
					this.causalLinks.add(cur);
					continue;
				}
				
//				System.out.println("cur is "+cur);
				Action tail = cur.getActions().get(cur.size()-1);
				TemporalMetricState originalState = new TemporalMetricState(null, tail.getPreconditions(), null, null, null); //FIXME stub code
				//new STRIPSState(null, tail.getAddPropositions(), null);
				
//				System.out.println("tail is "+tail);
				for (Object ai : gp.getActions())
				{
					Action next = (Action)ai;
					TemporalMetricState reverseState = (TemporalMetricState)originalState.clone();
					tail.apply(reverseState);
					next.apply(reverseState);
					int count = 0;
					for (Object fo : originalState.getTrueFacts())
					{
						if (reverseState.isTrue((Proposition)fo))
							count++;
						else
							break;
					}
					if (count == originalState.getTrueFacts().size())
						continue;
					
//					System.out.println("next is "+next);
					//tail.equals(next) && 
					if (this.allowRepeatActions == false && cur.getActions().contains(next)) //skip identical //FIXME should these be valid? would they be any use?
						continue;
					
					if (this.hasCausalLink(tail, next) == true) //if link can be extended
					{
						CausalLink extended = new CausalLink(cur.getActions());
						extended.addAction(next);
//						System.out.println("adding new link "+extended);
						if (queue.contains(extended) == false)
							queue.add(extended);
					}
					else //cannot extend with "next", so just add if length is >= 2
					{
						if (cur.size() >= 2)
							this.causalLinks.add(cur);
					}
				}
			}
		}
	}
	

	private void generateTotalOrderings(GroundProblem gp)
	{
		this.totalCausalLinks.clear();
		for (Object ao : gp.getActions())
		{
			Action prev = (Action)ao;
			for (Object ai : gp.getActions())
			{
				Action next = (Action)ai;
				if (prev.equals(next))
					continue;
				
				if (this.hasTotalCausalLink(prev, next) == true)
				{
					CausalLink cl = new CausalLink();
					cl.addAction(prev);
					cl.addAction(next);
					this.totalCausalLinks.add(cl);
				}
			}
		}
	}
	
	private void generateStrictOrderings(GroundProblem gp)
	{
		this.strictCausalLinks.clear();
		for (Object ao : gp.getActions())
		{
			Action prev = (Action)ao;
			for (Object ai : gp.getActions())
			{
				Action next = (Action)ai;
				if (prev.equals(next))
					continue;
				
				if (this.hasStrictCausalLink(prev, next) == true)
				{
					CausalLink cl = new CausalLink();
					cl.addAction(prev);
					cl.addAction(next);
					this.totalCausalLinks.add(cl);
				}
			}
		}
	}
	
//	private void generateRequiredOrderings(GroundProblem gp)
//	{
//		this.requiredOrderings.clear();
//		for (Object ao : gp.actions)
//		{
//			Action prev = (Action)ao;
//			int succCount = 0;
//			CausalLink cl = new CausalLink();
//			cl.addAction(prev);
//			
//			for (Object ai : gp.actions)
//			{
//				Action next = (Action)ai;
//				if (prev.equals(next))
//					continue;
//					
//				STRIPSState s = new STRIPSState(null, prev.getAddPropositions(), null);
//				
//				if (next.isApplicable(s)) //if action next can be applied only through applying previous tmstate
//				{
//					succCount++;
//					cl.addAction(next);
//					
//					if (succCount > 1)
//						break;
//				}
//			}
//			if (succCount == 1) //if only 1 action can be applied to get into a tmstate where action b can be applied
//				this.requiredOrderings.add(cl);
//		}
//	}
	
	public int getMaxCausalChainSize()
	{
		return maxCausalChainSize;
	}

	/**
	 * Determines whether an action sets up all preconditions required by another.
	 * @param prev
	 * @param next
	 * @return
	 */
	public boolean hasTotalCausalLink(Action prev, Action next)
	{
		int found = 0;
		for (Object pco : next.getPreconditions())
		{
			Proposition pc  = (Proposition)pco; //FIXME ignores negative preconditions
			if (prev.getAddPropositions().contains(pc))// || prev.getDeletePropositions().contains(pc))
			{
				found++;
			}
			else
				return false;
		}
		
		return found == next.getPreconditions().size();
	}
	
	/**
	 * Determines whether an action sets up all preconditions required by another, and only those preconditions. These are rare
	 * in most domains.
	 * @param prev
	 * @param next
	 * @return
	 */
	public boolean hasStrictCausalLink(Action prev, Action next)
	{
		//early out
		if (prev.getAddPropositions().size() /*+ prev.getDeletePropositions().size()*/ != next.getPreconditions().size())
			return false;
		
		Collection union = prev.getAddPropositions();
//		union.addAll(prev.getDeletePropositions());
		
		return union.equals(next.getPreconditions());
			
		
//		int found = 0;
//		for (Object pco : next.getConditionalPropositions())
//		{
//			Proposition pc = (Proposition)pco;
//			if (prev.getAddPropositions().contains(pc) || prev.getDeletePropositions().contains(pc))
//			{
//				found++;
//			}
//			else
//				return false;
//		}
//		
//		return found == next.getConditionalPropositions().size();
	}
	
	/**
	 * Determines whether an action's effects sets up any preconditions required by another.
	 * @param prev
	 * @param next
	 * @return
	 */
	public boolean hasCausalLink(Action prev, Action next)
	{
		for (Object pco : next.getPreconditions())
		{
			Proposition pc  = (Proposition)pco;//FIXME delete stuff wont work
			if (prev.getAddPropositions().contains(pc))// || prev.getDeletePropositions().contains(pc))
			{
//				System.out.println("found link from "+pc+" in "+prev+" to "+next);
				return true;
			}
		}
		
		return false;
	}


	public void setMaxCausalChainSize(int maxCausalChainSize)
	{
		if (maxCausalChainSize < 2)
			throw new IllegalArgumentException("Max causal chain length must be >= 2");
		
		this.maxCausalChainSize = maxCausalChainSize;
	}
	
	/**
	 * Returns a list of the normal causal links whose actions contain the specified goal proposition.
	 * @param goal
	 * @return
	 */
	public Set<CausalLink> getCausalLinksTo(Proposition goal) //should this be GC?
	{
		Set<CausalLink> linked = new HashSet<CausalLink>();
		
		outer : for (CausalLink cl : this.causalLinks)
		{
			for (Action a : cl.getActions())
			{
				if (a.getAddPropositions().contains(goal) && linked.contains(cl) == false)
				{
					linked.add(cl);
					continue outer;
				}
			}
		}
			
		return linked;	
	}


	public Set<CausalLink> getTotalCausalLinks()
	{
		return totalCausalLinks;
	}

	public Set<CausalLink> getCausalLinks()
	{
		return causalLinks;
	}

	public Set<CausalLink> getStrictCausalLinks()
	{
		return strictCausalLinks;
	}

	public boolean allowsRepeatActions()
	{
		return allowRepeatActions;
	}

	public void allowRepeatActions(boolean allowRepeatActions)
	{
		this.allowRepeatActions = allowRepeatActions;
	}

//	public Set<CausalLink> getRequiredOrderings()
//	{
//		return requiredOrderings;
//	}
}
