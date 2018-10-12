package recogniser.learning;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import recogniser.util.HybridSasPddlProblem;
import sas.data.DomainTransitionGraph;
import sas.data.NoneOfThoseProposition;
import sas.data.SASLiteral;
import sas.data.SASProblem;
import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundProblem;
import javaff.data.strips.Not;
import javaff.data.strips.PredicateSymbol;
import javaff.data.strips.Proposition;

//FIXME make all partitioning into a single loop- this is REALLY inefficient right now
/**
 * Partitions predicates into sets based on their usage in DTGs and Actions.
 * @version 2.0 Transient, Waypoint, Binary and Waypoint-Terminal sets removed, as they were too
 * broadly defined and of little use in practice.
 */
public class PredicatePartitioner
{
	private Set<Fact> unstableTerminalSet, 
					unstableActivatingSet, 
					strictlyActivatingSet, 
					strictlyTerminalSet;
	
	public PredicatePartitioner()
	{
		this.unstableTerminalSet = new HashSet<Fact>();
		this.unstableActivatingSet = new HashSet<Fact>();
		this.strictlyActivatingSet = new HashSet<Fact>();
		this.strictlyTerminalSet = new HashSet<Fact>();
	}

	
	public boolean isUnstableTerminal(Fact p)
	{
		return this.unstableTerminalSet.contains(p); 
	}
	
	public boolean isStrictlyTerminal(Fact p)
	{
		return this.strictlyTerminalSet.contains(p); 
	}
	
	
	public boolean isStrictlyActivating(Fact p)
	{
		return this.strictlyActivatingSet.contains(p);
	}
	
	public boolean isUnstableActivating(Fact p)
	{
		return this.unstableActivatingSet.contains(p); 
	}
	
	public Set<Fact> getUnstableTerminalSet()
	{
		return unstableTerminalSet;
	}

	public Set<Fact> getUnstableActivatingSet()
	{
		return unstableActivatingSet;
	}

	public Set<Fact> getStrictlyActivatingSet()
	{
		return strictlyActivatingSet;
	}

	public Set<Fact> getStrictlyTerminalSet()
	{
		return strictlyTerminalSet;
	}
	
	/**
	 * Determines whether a Fact is a waypoint by seeing if it is connected only to nodes which have the same
	 * predicate symbol as it does.
	 * @param p
	 * @param dtgs
	 * @return
	 */
	protected boolean isWaypoint(Fact p, SASProblem sproblem)
	{
		//toString() conversions are used because they are much faster than converting to 
		//the equivalent PDDL representation, and they *should* be the same
//		PredicateSymbol sym = p.getPredicateSymbol();//iter.next().state.getPredicateSymbol();
		
		boolean continued = false;
		out : for (DomainTransitionGraph dtg : sproblem.causalGraph.getDTGs())
		{
			for (SASLiteral sas : dtg.vertexSet())
			{
				if (sas.toString().equals(p.toString()))
				{
					Collection<SASLiteral> connectedOut = dtg.getOutgoingVertices(sas);
					if (connectedOut.size() < 2)
					{
						continued = true;
						return false;
//						continue out;
					}
						
					for (SASLiteral c : connectedOut)
					{
						if (c.getPredicateSymbol().toString().equals(sas.getPredicateSymbol().toString()) == false)
							return false;
					}
				}
			}
		}
		
		return continued == false;
	}

	/**
	 * Determines whether a Fact is binary by simply determining whether it appears in a DTG which only has 
	 * 2 possible states.
	 * @param p
	 * @param dtgs
	 * @return
	 */
	protected boolean isBinary(Fact p, Set<DomainTransitionGraph> dtgs)
	{
		for (DomainTransitionGraph dtg : dtgs)
		{			
			for (SASLiteral s : dtg.vertexSet())
			{
				if ((s.toString().equals(p.toString()) && dtg.vertexSet().size() == 2) ||
					(s.toString().equals(p.toString()) && dtg.vertexSet().size() == 1))// && dtg.containsNoneOfThoseState()))
					return true;
			}
		}
		
		return false;
	}

	/**
	 * If the Fact appears in a DTG, and all outgoing transition edges of the Fact
	 * lead to propositions which are all of the same type (but different to the original Fact's type),
	 * then the original Fact is transient.
	 * @param p
	 * @param dtgs
	 * @return
	 */
	protected boolean isTransient(Fact p, SASProblem sproblem, GroundProblem gproblem)
	{
		String symStr = null;	
		if (p instanceof Proposition)
		{
			symStr = ((Proposition)p).getPredicateSymbol().toString();
		}
		else
			return false;
		
		boolean found = false;
		out : for (DomainTransitionGraph dtg : sproblem.causalGraph.getDTGs())
		{
			for (SASLiteral sas : dtg.vertexSet())
			{
//				Fact pddl = s.convertToPDDL(sproblem, gproblem);
				
				if (sas.toString().equals(p.toString()))
				{
					found = true;
					Collection<SASLiteral> connected = dtg.getOutgoingVertices(sas);
					if (connected.size() < 2)
					{
						return false;
					}
					
//					System.out.println(s.state+ " has "+connected.size()+" outgoing edges");
						
					Iterator<SASLiteral> iter = connected.iterator();
//					PredicateSymbol firstSym = iter.next().getPredicateSymbol().convertToPDDL(sproblem, gproblem);
					String firstSymStr = iter.next().getPredicateSymbol().toString();
					while (iter.hasNext())
					{
						SASLiteral next = iter.next();
						
						//if this node has the same symbol to the connected one, immediately exit
						PredicateSymbol connectedSym = next.getPredicateSymbol().convertToPDDL(sproblem, gproblem);
						String connectedSymStr = next.getPredicateSymbol().toString();
						if (connectedSymStr.equals(symStr))
						{
							return false;//continue out; //return false;
						}
						
						//if the connected node does not have the same symbol as the first node
						if (firstSymStr.equals(connectedSymStr) == false)
						{
//							allEdgesSame = false;
							return false;
						}
					}
				}
			}
			
		}

		return (found == true);// && continued == false);
	}
	
	/**
	 * Returns true if the Fact appears in any action's delete effects or preconditions. This means
	 * that if true is returned that the Fact will remain in true throughout execution.
	 * @param p
	 * @param actions
	 * @return
	 */
	protected boolean isStrictlyTerminal(Fact p, Set<Action> actions)
	{
		boolean foundOnce = false;
		for (Action a : actions)
		{
			if (a.adds(p))
				foundOnce = true;
			if (a.requires(p))
				return false;
			if (a.deletes(p))
				return false;
		}
		return foundOnce;					
	}

	/**
	 * Returns true only if the Fact appears as a Precondition, but also as a Delete effect somewhere.
	 * @param p
	 * @param actions
	 * @return
	 */
	protected boolean isUnstableActivating(Fact p, Set<Action> actions)
	{
		int precount = 0, delcount = 0;
		for (Action a : actions)
		{
			if (a.requires(p))
				precount++;
			if (a.adds(p))
				return false;
			else if (a.deletes(p))
			{
				delcount++;
			}
		}

		if (precount > 0 && delcount > 0)
		{
//			System.out.println("found unstable activating: "+p);
			return true;
		}
		else
			return false;
	}	
	
	/**
	 * Returns true if the specified Fact only ever appears as a precondition, and never
	 * as an add OR delete effect. This means that unless true in the initial tmstate, the 
	 * Fact can never become true during execution.
	 * @param p
	 * @param actions
	 * @return
	 */
	protected boolean isStrictlyActivating(Fact p, Set<Action> actions)
	{
		int count = 0;
		for (Action a : actions)
		{
			if (a.adds(p) || a.deletes(p))
				return false;
			if (a.requires(p))
			{
//				String st = p.isStatic() ? "Static" : "Not Static";
//				System.out.println("maybe found strictly activating: "+p+" in "+a+" which is "+st);
				count++;
			}
		}
		
//		if (count > 0)
//			System.out.println("found strictly activating: "+p);
		
		return count > 0;
	}
	
	/**
	 * Returns true if the specified Fact appears in the effects of actions but that it can also be
	 * deleted once made true.
	 * @param p
	 * @param actions
	 * @return True if the Fact appears at least once in any action's add and delete effects.
	 */
	protected boolean isUnstableTerminal(Fact p, Set<Action> actions)
	{
		int addcount = 0, delcount = 0;
		for (Action a : actions)
		{
			if (a.requires(p))
				return false;
			if (a.adds(p))
			{
				addcount++;
			}
			if (a.deletes(p))
			{
				delcount++;
			}
		}
		
		if (addcount > 0 && delcount > 0)
		{
//			System.out.println("found unstable activating: "+p);
			return true;
		}
		else
			return false;
	}
	
	
	public void analyse(HybridSasPddlProblem problem)
	{
		HashSet<Fact> pc = new HashSet<Fact>();
		HashSet<Fact> add = new HashSet<Fact>();
		//12/8/11 -- changed to delPlain, so that it now contains the literal deleted and not the Not-wrapped version
		HashSet<Fact> delPlain = new HashSet<Fact>(); 
		
		for (Action a : problem.getActions())
		{
			for (Fact f : a.getPreconditions())
			{
				if (f instanceof Not)
					continue; //ignore negative PCs
					
				pc.add(f);
				if (f.isStatic())
					this.strictlyActivatingSet.add(f);
			}
			
			for (Fact f : a.getAddPropositions())
			{
				add.add((Fact) f);
			}
			
			//want original versions of deletes for quick set filtering
			for (Not d : a.getDeletePropositions())
			{
				delPlain.add((Fact) d.getLiteral());
			}
		}

		//note- using RETAIN not REMOVE in some cases
		this.strictlyActivatingSet = new HashSet<Fact>(pc);
		this.strictlyActivatingSet.removeAll(add); //keep only those that appear exclusively in PRE
		this.strictlyActivatingSet.removeAll(delPlain);
//		for (Fact sa : this.strictlyActivatingSet)
//			sa.setStatic(true); //just in case

		this.unstableActivatingSet = new HashSet<Fact>(pc);
		this.unstableActivatingSet.retainAll(delPlain); //keep only those which are a PRE and DEL
		this.unstableActivatingSet.removeAll(add); //but remove all those which can also be added anywhere
		
		this.strictlyTerminalSet = new HashSet<Fact>(add);
		this.strictlyTerminalSet.removeAll(delPlain); //keep only those that appear exclusively in ADD
		this.strictlyTerminalSet.removeAll(pc);
		
		this.unstableTerminalSet = new HashSet<Fact>(add);
		this.unstableTerminalSet.retainAll(delPlain); //keep only those which are a ADD and DEL
		this.unstableTerminalSet.removeAll(pc);  //but remove all those which are in PRE
		

	}
}
