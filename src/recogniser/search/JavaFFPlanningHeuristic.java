package recogniser.search;

import java.io.File;

import javaff.JavaFF;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.Plan;
import javaff.parser.ParseException;
import javaff.search.UnreachableGoalException;

/**
 * A "heuristic" which uses JavaFF to do full blown planning and returns plan length as the estimate.
 * @author David Pattison
 *
 */
public final class JavaFFPlanningHeuristic implements IHeuristic
{
	private JavaFF ff;
	private GroundProblem gp;

	public JavaFFPlanningHeuristic(GroundProblem problem)
	{
		this.gp = problem;
		
		reset();
	}
	
	public JavaFF getJavaFF()
	{
		return this.ff;
	}
	
	public GroundProblem getGroundProblem()
	{
		return this.gp;
	}
	
	/**
	 * Sets the ground problem used by JavaFF when planning and calls {@link #reset()}
	 * @param gp
	 */
	public void setGroundProblem(GroundProblem gp)
	{
		this.gp = gp;
		this.reset();
	}

	@Override
	public double getEstimate(Fact goal) throws UnreachableGoalException
	{
		this.gp.setGoal((GroundFact) goal);
		Plan p = this.ff.plan(this.gp);
		if (p == null)
			return IHeuristic.Unreachable;
		
		int c = p.getActionCount();
		return c;
	}
	
	public Object clone()
	{
		GroundProblem cloneGP = (GroundProblem) this.gp.clone();
//		cloneGP.getSTRIPSInitialState(); //FIXME should be done elsewhere, like clone() itself
		
		JavaFFPlanningHeuristic clone = new JavaFFPlanningHeuristic(cloneGP);
		return clone;
	}

	/**
	 * Recreates this heuristics JavaFF field.
	 * @see #getJavaFF()
	 */
	@Override
	public final void reset()
	{
		File dom = null;
		this.ff = new JavaFF(dom);
	}

}
