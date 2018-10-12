package recogniser.learning;

import java.util.Set;

import javaff.data.MutexSpace;
import javaff.graph.FactMutex;

public interface IConjunctionGenerator
{
	public Set<FactMutex> generateConjunctions();

	
	public MutexSpace getMutexSet();
	
	public void setMutexSet(MutexSpace gs);

	
	public int getNumberOfConjunctions();
	
	public int getMinConjunctionSize();
	
	public int getMaxConjunctionSize();
	
	
	public void setNumberOfConjunctions(int n);
	
	public void setMinConjunctionSize(int n);
	
	public void setMaxConjunctionSize(int n);
}
