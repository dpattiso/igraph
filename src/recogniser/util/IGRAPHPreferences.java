package recogniser.util;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Map;


/**
 * Stores preferences for IGRAPH. Terrible OO design, but I'm past caring.
 * @author David Pattison
 *
 */
public abstract class IGRAPHPreferences
{
	/**
	 * If the probability of a fact being the goal is tied with another, this may be used to break the tie.
	 * Essentially, it is up to the developer as to whether they prefer nearer or further away goals 
	 * to be included in any hypotheses. That is, if goal A has estimate h(A) = 4, and goal B has estimate
	 * h(B) = 5, but both have equal probability and are mutex, the goal added to the hypothesis is determined
	 * by this flag (other factors not withstanding!) 
	 */
	public static GoalTieOrderingPreference TiedGoalPreference = GoalTieOrderingPreference.PreferNearerGoals;

	/**
	 * Stability threshold for including facts in final hypotheses.
	 */
	public static double StabilityThreshold = 1d;
	
	/**
	 * If true, then the problem is partially observable.
	 * @deprecated POIGRAPH code needs seriously updated to reflect new IGRAPH changes
	 */
	public static boolean PartialObservability = false;
	
	/**
	 * If true, the recogniser will use N threads in parallel to compute heuristic estimates for
	 * members of the goal-space, where N is the number returned by Runtime.getRuntime().availableProcessors().
	 */
	public static boolean MultiThreaded = true;

	/**
	 * The type of initial probability distribution across the goal-space. Defaults to {@InitialProbabilityDistributionType}.Uniform.
	 */
	public static InitialProbabilityDistributionType InitialDistribution = InitialProbabilityDistributionType.CAUSALITYVALUE; 
	
	/**
	 * The heuristic to use. Defaults to RecognitionHeuristicType.Max.
	 */
	public static RecognitionHeuristicType Heuristic = RecognitionHeuristicType.Max;
	
	/**
	 * The hypothesis filter type. Defaults to {@link HypothesisFilterType}.Greedy.
	 */
	public static HypothesisFilterType HypothesisFilter = HypothesisFilterType.Greedy;
	
	/**
	 * This is the value at which a probability is regarded as being zero. This is
	 * helpful in minimising hypotheses where a goal will always be added to it, 
	 * despite having a trivial probability (such as unary, Strictly Terminal facts which
	 * are not being pursued).
	 */
	
	public static Double Epsilon = 0.0001d;
	/**
	 * The minimum stability of a goal in order to be considered for hypotheses. Defaults to 0.
	 */
	public static double MinimumStability = 0f;
	
	/**
	 * The minimum probability of a goal in order to be considered for hypotheses. Defaults to 
	 * {@link IGRAPHPreferences#Epsilon}.
	 */
	public static double MinimumProbability = Epsilon;
	
	/**
	 * The laplace constant for Bayesian updates. Defaults to 1.
	 * @deprecated No longer used in IGRAPH
	 */
	public static double LaplaceConstant = 1;
	
	/**
	 * The default value of lambda in computing work. Defaults to 0.8.
	 */
	public static double Lambda = 0.8f;
	
	/**
	 * The type of goal space. Defaults to {@link GoalSpaceType}.Map. 
	 */
	public static GoalSpaceType GoalSpace = GoalSpaceType.Map;
	
	/**
	 * The domain file.
	 */
	public static File DomainFile;
	
	/**
	 * The problem file.
	 */
	public static File ProblemFile;
	
	/**
	 * The file which contains the solution/plan to parse.
	 */
	public static File SolutionFile;
	
	/**
	 * The name of the file which output should be sent to.
	 * @deprecated Not used anywhere in IGRAPH
	 */
	public static String OutputFilePrefix;
	
	/**
	 * Whether the goal space should be visualised or hidden. Defaults to false.
	 */
	public static boolean Visual = false;
	
	/**
	 * If true, the goal space will be verified after each update. A valid goal-space is one in which the probabilities of all mutually-exclusive facts sum to 1.
	 * Defaults to false.
	 */
	public static boolean VerifyGoalSpace = false;
	
	/**
	 * The type of work function. Defaults to {@link WorkFunctionType}.ML.
	 */
	public static WorkFunctionType WorkFunction = WorkFunctionType.ML;

	/**
	 * Should IGRAPH call the SAS+ translations scripts at runtime, or have they already been translated.
	 * Defaults to true.
	 */
	public static boolean DoSASTranslation = true;


	
	/**
	 * Map of parameters in the (unordered) form:
	 * 
	 * key -> value
	 * 
	 * keys- "heuristic", "filter", "minStability", "bayesLambda", "bayesLaplace", "goalSpaceType"
	 * 
	 * @param prefs
	 * @throws IllegalArgumentException
	 * @throws NullPointerException
	 */
	public static void initialise(Map<String, String> prefs) throws IllegalArgumentException, NullPointerException, NumberFormatException
	{
		//Create a default set of values
		IGRAPHPreferences.createDefaultPreferences();
		
		IGRAPHPreferences.DomainFile = new File(prefs.get("domainFile"));
		IGRAPHPreferences.ProblemFile = new File(prefs.get("problemFile"));
		IGRAPHPreferences.SolutionFile = new File(prefs.get("solutionFile"));
		IGRAPHPreferences.OutputFilePrefix = prefs.get("outputFile");
		
		if (prefs.containsKey("heuristic"))
		{
			if (prefs.get("heuristic").equalsIgnoreCase("MAX"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.Max;
			else if (prefs.get("heuristic").equalsIgnoreCase("FF"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.FF;
			else if (prefs.get("heuristic").equalsIgnoreCase("CG"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.CEA; //FIXME swap Cg value to avoid having to redo all scripts
			else if (prefs.get("heuristic").equalsIgnoreCase("CEA"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.CEA;
			else if (prefs.get("heuristic").equalsIgnoreCase("GP") || prefs.get("heuristic").equalsIgnoreCase("PG"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.GP;
			else if (prefs.get("heuristic").equalsIgnoreCase("JavaFF"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.JavaFFPlanning;
			else if (prefs.get("heuristic").equalsIgnoreCase("Random"))
				IGRAPHPreferences.Heuristic = RecognitionHeuristicType.Random;
			else
				throw new IllegalArgumentException("Unknown Heuristic");
		}

		if (prefs.containsKey("work"))
		{
			if (prefs.get("work").equalsIgnoreCase("ML"))
				IGRAPHPreferences.WorkFunction = WorkFunctionType.ML;
			else if (prefs.get("work").equalsIgnoreCase("MLT"))
				IGRAPHPreferences.WorkFunction = WorkFunctionType.MLThreaded;
			else if (prefs.get("work").equalsIgnoreCase("SA"))
				IGRAPHPreferences.WorkFunction = WorkFunctionType.SingleAction;
			else
				throw new IllegalArgumentException("Unknown work function type");
		}

		if (prefs.containsKey("filter"))
		{
			if (prefs.get("filter").equalsIgnoreCase("Greedy"))
				IGRAPHPreferences.HypothesisFilter = HypothesisFilterType.Greedy;
			else if (prefs.get("filter").equalsIgnoreCase("Stability"))
				IGRAPHPreferences.HypothesisFilter = HypothesisFilterType.Stability;
			else
				throw new IllegalArgumentException("Unknown hypothesis filter: "+prefs.get("filter"));
		}
		
		if (prefs.containsKey("minProbability"))
			IGRAPHPreferences.MinimumProbability = Double.parseDouble(prefs.get("minProbability"));
		if (prefs.containsKey("minStability"))
			IGRAPHPreferences.MinimumStability = Double.parseDouble(prefs.get("minStability"));
		
		//parse min stability
//		if (IGRAPHPreferences.HypothesisFilter == HypothesisFilterType.Stability)
//		{
//			IGRAPHPreferences.MinimumStability = Double.parseDouble(prefs.get("minStability"));
//		}
//		else if (IGRAPHPreferences.HypothesisFilter == HypothesisFilterType.Greedy)
//		{
//			IGRAPHPreferences.MinimumProbability = Double.parseDouble(prefs.get("minProbability"));
//		}
//		else
//		{
//			IGRAPHPreferences.MinimumStability = 0f;
//			IGRAPHPreferences.MinimumProbability = 0f;
//		}

		//lambda
		if (prefs.containsKey("bayesLambda"))
			IGRAPHPreferences.Lambda = Double.parseDouble(prefs.get("bayesLambda"));
		
		//goal space type
		if (prefs.containsKey("goalSpace"))
		{
			if (prefs.get("goalSpace").equalsIgnoreCase("Map"))
				IGRAPHPreferences.GoalSpace = GoalSpaceType.Map;
			else if (prefs.get("goalSpace").equalsIgnoreCase("BDD"))
				IGRAPHPreferences.GoalSpace = GoalSpaceType.BDD;
			else
				throw new IllegalArgumentException("Unknown goal space type: "+prefs.get("goalSpaceType"));
		}
		
		if (prefs.containsKey("visual"))
		{
			IGRAPHPreferences.Visual = (prefs.get("visual").equals("1")) ? true : false;
		}
		
		if (prefs.containsKey("verifyGoalSpace"))
		{
			IGRAPHPreferences.VerifyGoalSpace = (prefs.get("verifyGoalSpace").equals("1")) ? true : false;
		}
		

		if (prefs.containsKey("translate"))
		{
			IGRAPHPreferences.DoSASTranslation = prefs.get("translate").equals("1");
		}
		
		
		if (prefs.containsKey("initial"))
		{
			if (prefs.get("initial").equalsIgnoreCase("UNIFORM"))
				IGRAPHPreferences.InitialDistribution = InitialProbabilityDistributionType.UNIFORM;
			else if (prefs.get("initial").equalsIgnoreCase("CV"))
				IGRAPHPreferences.InitialDistribution = InitialProbabilityDistributionType.CAUSALITYVALUE;
			else
				throw new IllegalArgumentException("Illegal initial probability distribution type: "+prefs.get("initial"));
		}
		
		if (prefs.containsKey("partial"))
		{
			if (prefs.get("partial").equals("1"))
				IGRAPHPreferences.PartialObservability = true;
			else
				IGRAPHPreferences.PartialObservability = false;
		}
		
		if (prefs.containsKey("stabilityThreshold"))
		{
			double st = Double.parseDouble(prefs.get("stabilityThreshold"));
			IGRAPHPreferences.StabilityThreshold = st;
		}
		
		if (prefs.containsKey("multithreaded"))
		{
			boolean mt = Integer.parseInt(prefs.get("multithreaded")) == 1 ? true : false;
			IGRAPHPreferences.MultiThreaded = mt;
		}
			
	}
	
	/**
	 * Sets all parameters to default values.
	 */
	public static void createDefaultPreferences() 
	{

		IGRAPHPreferences.DomainFile = null;
		IGRAPHPreferences.ProblemFile = null;
		IGRAPHPreferences.SolutionFile = null;
		IGRAPHPreferences.OutputFilePrefix = null;
		
		IGRAPHPreferences.StabilityThreshold = 1d;
		
		IGRAPHPreferences.PartialObservability = false;

		IGRAPHPreferences.InitialDistribution = InitialProbabilityDistributionType.CAUSALITYVALUE;

		IGRAPHPreferences.Heuristic = RecognitionHeuristicType.Max;
		
		IGRAPHPreferences.WorkFunction = WorkFunctionType.ML;
		
		IGRAPHPreferences.DoSASTranslation = true;
		
		IGRAPHPreferences.HypothesisFilter = HypothesisFilterType.Greedy;
	
		IGRAPHPreferences.Epsilon = 0.00001d;
		IGRAPHPreferences.MinimumProbability = IGRAPHPreferences.Epsilon;
		IGRAPHPreferences.MinimumStability = 0;

		//lambda
		IGRAPHPreferences.Lambda = 0.8f;
		//laplace
		IGRAPHPreferences.LaplaceConstant = 1;
		
		//lambda
		IGRAPHPreferences.GoalSpace = GoalSpaceType.Map;
		
		IGRAPHPreferences.Visual = false;
		IGRAPHPreferences.VerifyGoalSpace = true;
	
		
		IGRAPHPreferences.MultiThreaded = true;
		
		IGRAPHPreferences.TiedGoalPreference = GoalTieOrderingPreference.PreferNearerGoals;
	
	}
	
	/**
	 * Sets relevant parameters to values which assist in debugging.
	 */
	public static void createDebugPreferences() 
	{
		IGRAPHPreferences.createDefaultPreferences();
		

		IGRAPHPreferences.InitialDistribution = InitialProbabilityDistributionType.UNIFORM;
		IGRAPHPreferences.MultiThreaded = false;
	}
	
	
	/**
	 * Sets relevant parameters to optimal speed/performance values.
	 */
	public static void createReleasePreferences() 
	{
		IGRAPHPreferences.createDefaultPreferences();
		

		IGRAPHPreferences.InitialDistribution = InitialProbabilityDistributionType.CAUSALITYVALUE;
		IGRAPHPreferences.MultiThreaded = true;

		IGRAPHPreferences.Heuristic = RecognitionHeuristicType.CEA;
		
		IGRAPHPreferences.WorkFunction = WorkFunctionType.SingleAction;
	}
	
	

	public static void printPreferences(PrintStream stream)
	{
		try
		{
			Class c = IGRAPHPreferences.class;//Class.forName("recogniser.util.IGRAPHPreferences");
			
			stream.println("IGRAPH preferences: ");
			Field[] fields = c.getDeclaredFields();
			for (int i = 0; i < fields.length; i++)
			{
				stream.print("\t"+fields[i].getName() + " = "+fields[i].get(null)+"\n");
			}
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
		}
		catch (IllegalAccessException e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * @deprecated No longer used in current version
	 * @author David Pattison
	 *
	 */
	public enum ConjunctionGeneratorType
	{
		None, 
		Random,
		Genetic,
	}
	
	public enum GoalSpaceType 
	{
		Map,
		@Deprecated
		BDD,
	}
	
	public enum HypothesisFilterType
	{
		None,
		Greedy,
		/**
		 * @deprecated Not supported in current version
		 */
		Stability,
	}
	
	/**
	 * The type of heuristic used in recognition.
	 * @author David Pattison
	 *
	 */
	public enum RecognitionHeuristicType
	{
		/**
		 * Max heuristic
		 */
		Max,
		/**
		 * FF heuristic (as implemented by JavaFF)
		 */
		FF,
		/**
		 * Causal Graph heuristic
		 */
		CG,
		/**
		 * Context enhanced additive heuristic
		 */
		CEA, 
		/**
		 * Graphplan (full planning!) heuristic
		 */
		GP,
		/**
		 * Use fully blown, valid JavaFF plans as the heuristic. This is not the same as {@link #FF}!
		 */
		JavaFFPlanning, 
		/**
		 * Use a random number as the heuristic estimate.
		 */
		Random,
		
	}
	
	public enum WorkFunctionType
	{
		ML, 
		MLThreaded,
		SingleAction,
	}
}
