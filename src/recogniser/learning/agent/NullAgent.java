package recogniser.learning.agent;

import javaff.data.Action;
import javaff.data.NullInstantAction;
import javaff.planning.State;

/**
 * NullAgent represents an agent with no policy or discernible properties.
 * @author David Pattison
 *
 */
public class NullAgent extends AbstractAgent
{
	private final NullInstantAction nullInstantAction;
	
	private static NullAgent instance;
	
	private NullAgent()
	{
		this.nullInstantAction = new NullInstantAction();
	}
	
	public static NullAgent getInstance()
	{
		if (instance == null)
			instance = new NullAgent();
		
		return instance;		
	}
	
	/**
	 * Returns a NullInstantAction for all queries.
	 */
	public Action getAction(State s)
	{
		return this.nullInstantAction;
	}

	
	/**
	 * Simple extension of NullAgent. This class is used to indicate that the specific agent used is unknown, only that 
	 * *someone* is associated, ie some agent will complete goal x.
	 * @author David Pattison
	 *
	 */
	public final class UnknownAgent extends NullAgent
	{
		
	}
}
