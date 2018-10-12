package recogniser.learning;

import javaff.data.Fact;
import javaff.data.strips.NullFact;

/**
 * Encapsulates the number of times a fact is added, deleted and required by an action set.
 *  
 * @author David Pattison
 *
 */
public final class FactVector
{
	private final Fact fact;
	private final double[] arrayCounts; //adds, deletes, pcs
	private final double mag;
	
	/**
	 * Counts of the ways a fact can be added, deleted or required by an action.
	 * 
	 * @param f The fact.
	 * @param counts Of the form, [0] = add count, [1] = delete count, [2] = required count.
	 */
	public FactVector(Fact f, double[] counts)
	{
		this.fact = f;
		this.arrayCounts = new double[]{counts[0], counts[1], counts[2]};
		
		this.mag = Math.sqrt((arrayCounts[0]*arrayCounts[0]) + (arrayCounts[1]*arrayCounts[1]) + (arrayCounts[2]*arrayCounts[2]));
	}

	/**
	 * Returns the sum of two vectors. The fact becomes a NullFact.
	 * @param a
	 * @param b
	 * @return
	 */
	public static FactVector add(FactVector a, FactVector b)
	{
		double[] added = new double[3];
		for (int i = 0; i < 3; i++)
		{
			added[i] = a.arrayCounts[i] + b.arrayCounts[i];
		}
		
		return new FactVector(new NullFact(), added);
	}
	
	/**
	 * Returns the sum of two vectors. The fact becomes a NullFact.
	 * @param a
	 * @param b
	 * @return
	 */
	public static FactVector minus(FactVector a, FactVector b)
	{
		double[] deleted = new double[3];
		for (int i = 0; i < 3; i++)
		{
			deleted[i] = a.arrayCounts[i] - b.arrayCounts[i];
		}
		
		return new FactVector(new NullFact(), deleted);
	}
	
	/**
	 * Counts of the ways a fact can be added, deleted or required by an action.
	 * 
	 * @param f The fact.
	 * @param adds The add count
	 * @param dels The delete count
	 * @param pcs The required count.
	 */
	public FactVector(Fact f, int adds, int dels, int pcs)
	{
		this.fact = f;
		this.arrayCounts = new double[]{adds, dels, pcs};
		
		this.mag = Math.sqrt((arrayCounts[0]*arrayCounts[0]) + (arrayCounts[1]*arrayCounts[1]) + (arrayCounts[2]*arrayCounts[2]));
	}
	
	@Override
	public String toString()
	{
		return this.fact.toString()+" <"+this.getRequires()+", "+this.getAdded()+", "+this.getDeleted()+">";
	}
	
	/**
	 * Gets the magnitude of the 3D vector formed by this fact's counts.
	 * @return
	 */
	public double getMagnitude()
	{
		return this.mag;
	}

	/**
	 * Gets the normalised vector.
	 * @return
	 */
	public double[] getNormalisedArray()
	{
		double mag = this.getMagnitude();
		double a = this.arrayCounts[0] / mag;
		double d = this.arrayCounts[1] / mag;
		double r = this.arrayCounts[2] / mag;
		
		return new double[]{a, d, r};
	}
	
	/**
	 * Gets the normalised vector.
	 * @return
	 */
	public FactVector getNormalisedVector()
	{
		return new FactVector(this.fact, this.getNormalisedArray());
	}
	
	public double[] toArray()
	{
		return this.arrayCounts;
	}

	public Fact getFact()
	{
		return fact;
	}

//	public void setFact(Fact fact)
//	{
//		this.fact = fact;
//	}

	public double getAdded()
	{
		return this.arrayCounts[0];
	}

//	public void setAdded(double added)
//	{
//		this.arrayCounts[0] = added;
//	}

	public double getDeleted()
	{
		return this.arrayCounts[1];
	}

//	public void setDeleted(double deleted)
//	{
//		this.arrayCounts[1] = deleted;
//	}

	public double getRequires()
	{
		return this.arrayCounts[2];
	}

//	public void setRequires(double requiredBy)
//	{
//		this.arrayCounts[2] = requiredBy;
//	}

}
