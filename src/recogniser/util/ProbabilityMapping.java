package recogniser.util;

public class ProbabilityMapping<T>
{
	private T key;
	private float probability;
	
	public ProbabilityMapping(T key, float probability)
	{
		this.key = key;
		this.probability = probability;
	}

	public T getKey()
	{
		return key;
	}

	public void setKey(T key)
	{
		this.key = key;
	}

	public float getProbability()
	{
		return probability;
	}

	public void setProbability(float probability)
	{
		this.probability = probability;
	}
}
