package recogniser.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import javaff.graph.StandardGraph;

import org.jgrapht.EdgeFactory;
import org.jgrapht.GraphPath;
import org.jgrapht.VertexFactory;
import org.jgrapht.alg.BellmanFordShortestPath;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.generate.CompleteGraphGenerator;
import org.jgrapht.generate.LinearGraphGenerator;
import org.jgrapht.generate.RandomGraphGenerator;
import org.jgrapht.generate.RingGraphGenerator;
import org.jgrapht.generate.StarGraphGenerator;
import org.jgrapht.generate.WheelGraphGenerator;
import org.jgrapht.generate.GridGraphGenerator;
import org.jgrapht.graph.DirectedMultigraph;

public class GraphGenerator
{
	private static String Usage = "Generates a series of graphs of varying topologies and sizes. Graphs "
			+ "have a mimum and maximum number of nodes specified, with N graphs being produced where, "
			+ "N = (max - min)/increment\n"
			+ "Usage: GraphGenerator <output directory> <minimum graph size> <maximum graph size> "
			+ "<increment>";

	public static void main(String[] args) throws IOException
	{
		String outputDir = null;
		Integer minSize = null;
		Integer maxSize = null;
		Integer increment = null;
		try
		{
			outputDir = args[0];
			minSize = Integer.parseInt(args[1]);
			maxSize = Integer.parseInt(args[2]);
			increment = Integer.parseInt(args[3]);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.out.println(GraphGenerator.Usage);
			System.exit(1);
		}

		GraphGenerator gen = new GraphGenerator();

		try
		{
			//create dirs and any missing parents
			new File(outputDir+"/grid/solutions").mkdirs();
			new File(outputDir+"/line/solutions").mkdirs();
			new File(outputDir+"/complete/solutions").mkdirs();
			new File(outputDir+"/wheel/solutions").mkdirs();
			new File(outputDir+"/ring/solutions").mkdirs();
			new File(outputDir+"/random/solutions").mkdirs();
			new File(outputDir+"/city/solutions").mkdirs();

			gen.writeDomain(new File(outputDir + "/grid/domain.pddl"));
			gen.writeDomain(new File(outputDir + "/line/domain.pddl"));
			gen.writeDomain(new File(outputDir + "/complete/domain.pddl"));
			gen.writeDomain(new File(outputDir + "/wheel/domain.pddl"));
			gen.writeDomain(new File(outputDir + "/ring/domain.pddl"));
			gen.writeDomain(new File(outputDir + "/random/domain.pddl"));
			gen.writeDomain(new File(outputDir + "/city/domain.pddl"));
		}
		catch (IOException e)
		{
			System.err.println("Unable to create domain file");
			e.printStackTrace();
			System.exit(1);
		}

		
		DOTExporter<String, String> exporter = new DOTExporter<String, String>();
		for (int i = minSize; i <= maxSize; i += increment)
		{
			System.out.println("Generating graphs for " + i);

			String goal;
			GraphPath<String, String> longestPath = null;

//			PddlGraph ring = gen.generateRing(i);
//			goal = "at bob s" + (gen.vertexFactory.counter - 1);
//			longestPath = ring.findLongestPath(); //cheap to compute on rings
//			// ring.addReverseEdges(); //dont want reverse
//			goal = "at bob "+longestPath.getEndVertex();
//			ring.generatePddlFile(new File(outputDir + "/ring/ring_" + i + ".pddl"),
//					goal);
//			GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/ring/solutions/ring_"+i+".soln"));
//			String targetDirectory = "/tmp/dot/ring/";
//			new File(targetDirectory).mkdirs();
//			exporter.export(new FileWriter(targetDirectory
//					+ "ring_"+i+".dot"), ring);

			
			
//			PddlGraph grid = gen.generateGrid(i, i);
////			goalNode = grid.findFurthestNode();
//			grid.addReverseEdges();
//			goal = "at bob s" + (gen.vertexFactory.counter - 1); //last node added is always furthest away
//			grid.generatePddlFile(new File(outputDir + "/grid/grid_" + i + ".pddl"),
//					goal);
//			GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/grid/solutions/grid_"+i+".soln"));
////			targetDirectory = "/tmp/dot/grid/";
////			new File(targetDirectory).mkdirs();
////			exporter.export(new FileWriter(targetDirectory
////					+ "grid"+i+".dot"), grid);
//
////			
//			PddlGraph line = gen.generateLinear(i);
//			goal = "at bob s" + (gen.vertexFactory.counter - 1);
//			longestPath = line.findLongestPath(); //cheap to compute
//			line.addReverseEdges();
//			goal = "at bob "+longestPath.getEndVertex();
//			line.generatePddlFile(new File(outputDir + "/line/line_" + i + ".pddl"),
//					goal);
//			GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/line/solutions/line_"+i+".soln"));

//			PddlGraph rand = gen.generateRandom(i, i);
//			goal = "at bob s" + (gen.vertexFactory.counter - 1);
//			rand.addReverseEdges();
//			longestPath = rand.findLongestPath();
//			goal = "at bob "+longestPath.getEndVertex();
//			rand.generatePddlFile(new File(outputDir+"/rand_"+i+".pddl"), goal);
//			GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/random/solutions/rand_"+i+".soln"));
//			exporter.export(new BufferedWriter(new FileWriter(new File("/tmp/rand_"+i+".dot"))), rand);
			

			PddlGraph rand = gen.generateCity(i, 5);
			goal = "at bob " + (gen.vertexFactory.counter - 1);
			rand.addReverseEdges();
			longestPath = rand.findLongestPath();
			goal = "at bob "+longestPath.getEndVertex();
			rand.generatePddlFile(new File(outputDir+"/city"+i+".pddl"), goal);
			GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/city/solutions/city"+i+".soln"));
			exporter.export(new BufferedWriter(new FileWriter(new File("/tmp/city"+i+".dot"))), rand);

//			PddlGraph star = gen.generateStar(i);
//			goal = "at bob s" + (gen.vertexFactory.counter - 1);
//			star.addReverseEdges();
//			// goalNode = star.findFurthestNode();
//			// goal = "(at bob "+goalNode+")";
//			// star.generatePddlFile(new File(outputDir+"/star_"+i+".pddl"),
//			// goal);

//			PddlGraph comp = gen.generateComplete(i);
//			goal = "at bob s" + (gen.vertexFactory.counter - 1);
//			// comp.addReverseEdges(); //technically shouldnt be needed.
//			 longestPath = comp.findLongestPath();
//			 goal = "at bob "+longestPath.getEndVertex();
//			 comp.generatePddlFile(new File(outputDir+"/complete/complete_"+i+".pddl"),  goal);
//				GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/complete/solutions/complete_"+i+".soln"));
//
//			PddlGraph wheel = gen.generateWheel(i);
//			goal = "at bob s" + (gen.vertexFactory.counter - 1);
//			 longestPath = wheel.findLongestPath();
//			// wheel.addReverseEdges(); //don't want to reverse wheel
//			 goal = "at bob "+longestPath.getEndVertex()+"";
//			wheel.generatePddlFile(new File(outputDir + "/wheel/wheel_" + i + ".pddl"), goal);
//			GraphGenerator.writePlanFile(longestPath, new File(outputDir+"/wheel/solutions/wheel_"+i+".soln"));
		}
	}

	/**
	 * Takes in a path through a graph, and writes this path out as a plan.
	 * @param longestPath
	 * @throws IOException 
	 */
	private static void writePlanFile(GraphPath<String, String> longestPath, File outputFile) throws IOException
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
		
		//go through each edge, which has the form "linked x y", where x and y are nodes
		for (String e : longestPath.getEdgeList())
		{
			String action = e.replace("linked", "move bob");
			writer.write("("+action+")\n");
			
		}
		
		writer.close();
	}

	private PddlVertexFactory vertexFactory;

	public GraphGenerator()
	{
		this.vertexFactory = new PddlVertexFactory();
	}

	public void writeDomain(File outputFile) throws IOException
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

		writer.write("(define (domain graph)\n");
		writer.write("(:requirements :strips :typing)\n");

		writer.write("(:types loc agent)\n");

		writer.write("(:predicates (at ?agent ?loc) (linked ?a ?b))\n");

		writer.write("(:action move\n");
		writer.write("\t:parameters (?a - agent ?from - loc ?to - loc)\n");
		writer.write("\t:precondition (and (at ?a ?from) (linked ?from ?to))\n");
		writer.write("\t:effect (and (not (at ?a ?from)) (at ?a ?to))\n");
		writer.write(")\n");

		writer.write(")");

		writer.close();
	}

	public PddlGraph generateRing(int size)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		RingGraphGenerator<String, String> g = new RingGraphGenerator<String, String>(
				size);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}

	public PddlGraph generateStar(int size)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		StarGraphGenerator<String, String> g = new StarGraphGenerator<String, String>(
				size);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}
//	
//	/**
//	 * Generates a city-style topology by growing the graph from a centre point
//	 * @param edges
//	 * @return
//	 */
//	public PddlGraph generateCity(int edges)
//	{
//		int vcount = 0;
//		
//		//create the graph
//		PddlGraph graph = new PddlGraph();
//		graph.addVertex(""+vcount);
//		graph.addVertex(""+(vcount+1));
//		graph.addEdge(""+vcount, ""+(vcount+1));
//		
//		++vcount;
//		++vcount;
//		Random rand = new Random();
//		
//		int bound = 3;
//		
//		while (vcount < edges)
//		{
//			String src = ""+rand.nextInt((vcount - bound <=  0) ? vcount : vcount - bound);
//			String dst = ""+-1;
//			
//			//coin flip -- create a new node or select an existing one
//			double r = rand.nextDouble();
//			if (r < 0.5)
//			{
//				//target is an existing node
//				do
//				{
//					int srcInt = Integer.parseInt(src);
//					int minBound = (srcInt - bound < 0) ? 0 : srcInt - bound;
//					int maxBound = (srcInt + bound > vcount) ? vcount : srcInt + bound;
//					int diff = maxBound - minBound;
//					
//					int dstInt = minBound + rand.nextInt(diff);
//					dst = ""+dstInt;
//					
//				}
//				while (dst.equals(src)); 
//			}
//			else
//			{
//				//else target is a new node
//				dst = ""+vcount;
//				graph.addVertex(dst);
//				++vcount;
//			}
//			
//			System.out.println("Adding "+src+" > "+dst);
//			String e = graph.addEdge(src, dst);
//		}
//		
//		return graph;
//	}
	

	/**
	 * Generates a city-style topology by growing the graph from a centre point
	 * @param edges
	 * @return
	 */
	public PddlGraph generateCity(int edges, int maxEdgeCount)
	{
		int vcount = 0;
		int[] outEdgeCounts = new int[edges];
		
		//create the graph
		PddlGraph graph = new PddlGraph();
		graph.addVertex("s"+vcount);
		graph.addVertex("s"+(vcount+1));
		graph.addEdge("s"+vcount, "s"+(vcount+1));
		
		++vcount;
		++vcount;
		outEdgeCounts[0] = 1;
		Random rand = new Random();
		
		int bound = 3;
		
		while (vcount < edges)
		{
			String src = null;
			int srcInt;
			//target is an existing node
			do
			{
//				src = ""+rand.nextInt((vcount - bound <=  0) ? vcount : vcount - bound);
				src = "s"+rand.nextInt(vcount);
				srcInt = Integer.parseInt(src.substring(1));
			}
			while (outEdgeCounts[srcInt] + 1 >= maxEdgeCount); 
			
			
			
			String dst = ""+-1;

			
			//coin flip -- create a new node or select an existing one
			double r = rand.nextDouble();
			if (r < 0.0)
			{
				//target is an existing node
				do
				{
					int minBound = (srcInt - bound < 0) ? 0 : srcInt - bound;
					int maxBound = (srcInt + bound > vcount) ? vcount : srcInt + bound;
					int diff = maxBound - minBound;
					
					int dstInt = minBound + rand.nextInt(diff);
					dst = ""+dstInt;
					
				}
				while (dst.equals(src)); 
			}
			else
			{
				//else target is a new node
				dst = "s"+vcount;
				graph.addVertex(dst);
				++vcount;
			}
			
			System.out.println("Adding "+src+" > "+dst);
			String e = graph.addEdge(src, dst);
			outEdgeCounts[srcInt] = outEdgeCounts[srcInt] + 1;
		}
		
		ArrayList<Integer> leaves = new ArrayList<Integer>();
		for (int i = 0; i < outEdgeCounts.length; i++)
		{
			if (outEdgeCounts[i] == 0)
				leaves.add(i);
		}
		
		//add K edges between random leaves, equal to 10% of nodes
		int k = vcount / 10;
		for (int i = 0; i < k; i++)
		{
			int start = rand.nextInt(leaves.size());
			int end = start;
			do
			{
				end = rand.nextInt(leaves.size());
			}
			while (start == end);
			
			String e = graph.addEdge("s"+leaves.get(start), "s"+leaves.get(end));
		}
		return graph;
	}

	public PddlGraph generateRandom(int vertices, int edges)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		RandomGraphGenerator<String, String> g = new RandomGraphGenerator<String, String>(
				vertices, edges);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}

	public PddlGraph generateComplete(int size)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		CompleteGraphGenerator<String, String> g = new CompleteGraphGenerator<String, String>(
				size);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}

	public PddlGraph generateGrid(int width, int height)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		GridGraphGenerator g = new GridGraphGenerator<String, String>(width,
				height);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}

	public PddlGraph generateLinear(int size)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		LinearGraphGenerator<String, String> g = new LinearGraphGenerator<String, String>(
				size);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}

	public PddlGraph generateWheel(int size)
	{
		PddlGraph target = new PddlGraph();
		HashMap<String, String> map = new HashMap<String, String>();

		this.vertexFactory.reset();

		WheelGraphGenerator<String, String> g = new WheelGraphGenerator<String, String>(
				size);
		g.generateGraph(target, this.vertexFactory, map);

		return target;
	}

	protected class PddlGraph extends DirectedMultigraph<String, String>
	{
		public PddlGraph()
		{
			super(new PddlEdgeFactory());
		}

		private GraphPath<String, String> findLongestPath()
		{
//			org.jgrapht.alg.BellmanFordShortestPath<String, String> bfsp = new BellmanFordShortestPath<String, String>(this, "s0");
			FloydWarshallShortestPaths<String, String> fw = new FloydWarshallShortestPaths<String, String>(
					this);


			List<GraphPath<String, String>> paths = fw.getShortestPaths("s0"); //always start node

			double longestDist = Double.NEGATIVE_INFINITY;
			GraphPath<String, String> longestPath = null;
			for (GraphPath<String, String> p : paths)
			{
				if (p.getWeight() >= longestDist)
				{
					longestDist = p.getWeight();
					longestPath = p;
				}
			}
				
//			return paths.get(paths.size() - 1).getEndVertex();
//			System.out.println(longestDist+ " = "+ longestPath);
			return longestPath;
		}

		protected void addReverseEdges()
		{
			ArrayList<String> newEdges = new ArrayList<String>();
			ArrayList<String> newSources = new ArrayList<String>();
			ArrayList<String> newTargets = new ArrayList<String>();

			for (String e : super.edgeSet())
			{
				String source = super.getEdgeSource(e);
				String target = super.getEdgeTarget(e);

				String revEdge = "linked " + target + " " + source;
				newEdges.add(revEdge);
				newSources.add(target); // flip
				newTargets.add(source);

			}

			for (int i = 0; i < newEdges.size(); i++)
			{
				String s = newSources.get(i);
				String t = newTargets.get(i);
				String e = newEdges.get(i);

				super.addEdge(s, t, e);
			}
		}

		public void generatePddlFile(File pfile, String goal)
		{
			FileWriter pfileFWriter;
			BufferedWriter pfileBufWriter;
			try
			{
				pfileFWriter = new FileWriter(pfile);

				pfileBufWriter = new BufferedWriter(pfileFWriter);
				// parse out STRIPS pfile here
				pfile.createNewFile();
				// write out requirements
				pfileBufWriter.write("(define (problem "
						+ pfile.getName().replace(".", "_")
						+ ") (:domain graph)\n");
				// write out types
				pfileBufWriter.write("(:objects bob - agent\n");

				for (String v : this.vertexSet())
				{
					pfileBufWriter.write("\t" + v + " - loc\n");
				}
				pfileBufWriter.write(")\n");

				// write out predicates
				pfileBufWriter.write("(:init (at bob s0)\n");
				for (String e : super.edgeSet())
				{
					pfileBufWriter.write("\t(" + e + ")\n");
				}
				pfileBufWriter.write(")\n");
				// write out actions
				pfileBufWriter.write("(:goal (and\n");

				pfileBufWriter.write("\t(" + goal.toLowerCase() + ")\n");

				pfileBufWriter.write(")))\n");

				pfileBufWriter.close();
				pfileFWriter.close();
			}
			catch (IOException ioe)
			{
				System.err
						.println("An error was encountered while converting the domain or problem file: "
								+ ioe.getMessage());
			}
		}
	}

	protected class PddlEdgeFactory implements EdgeFactory<String, String>
	{

		private int counter;
		private String prefix;

		public PddlEdgeFactory()
		{
			this.counter = 0;
			this.prefix = "linked";
		}

		public void reset()
		{
			this.counter = 0;
		}

		@Override
		public String createEdge(String sourceVertex, String targetVertex)
		{
			String edge = new String(this.prefix);
			edge = edge + " " + sourceVertex + " " + targetVertex;

			return edge;
		}

		public String getPrefix()
		{
			return prefix;
		}

		public void setPrefix(String prefix)
		{
			this.prefix = prefix;
		}

	}

	protected class PddlVertexFactory implements VertexFactory<String>
	{

		private int counter;
		private String prefix;

		public PddlVertexFactory()
		{
			this.counter = 0;
			this.prefix = "s";
		}

		public void reset()
		{
			this.counter = 0;
		}

		@Override
		public String createVertex()
		{
			String s = prefix + this.counter;
			++this.counter;
			return s;
		}

		public String getPrefix()
		{
			return prefix;
		}

		public void setPrefix(String prefix)
		{
			this.prefix = prefix;
		}

	}
}
