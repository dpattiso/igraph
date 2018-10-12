package recogniser.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.MutexSpace;
import javaff.data.TotalOrderPlan;
import javaff.graph.FactMutex;

import recogniser.BayesianGoalRecogniser;
import recogniser.hypothesis.IGoalSpace;
import recogniser.hypothesis.MutexGoalSpace;
import recogniser.hypothesis.VariableGoalSpace;

public class VisualGoalSpace extends JFrame implements WindowStateListener,
		WindowListener, MouseListener, ActionListener, AdjustmentListener
{
	private JPanel labelPanel, planPanel;
	private JScrollPane planScrollPane, labelScrollPane, graphPanelScrollPane;
	private GraphPanel graphPanel;
	private JButton buttonStep, buttonPlay, buttonPause;

	private List<GoalLabel> goalLabels;
	private List<JLabel> planLabelsMap;
	private VariableGoalSpace goalSpace;
	private Collection<Fact> goal;
	private TotalOrderPlan plan;

	private BayesianGoalRecogniser recogniser;

	private boolean pauseAfterUpdate, canContinue;

	public VisualGoalSpace(BayesianGoalRecogniser rec, GroundFact goal,
			TotalOrderPlan plan)
	{
		this.recogniser = rec;
		this.goalSpace = (VariableGoalSpace) rec.getGoalSpace();
		this.goal = new HashSet<Fact>(goal.getFacts());
		this.plan = plan;

		this.pauseAfterUpdate = true;
		this.canContinue = false;

		Font f = new Font("Ariel", Font.PLAIN, 10);

		this.buttonPause = new JButton("Pause");
		this.buttonPause.setEnabled(false);
		this.buttonPlay = new JButton("Play");
		this.buttonStep = new JButton("Continue");

		this.buttonPause.setSize(new Dimension(50, 30));
		this.buttonPlay.setSize(new Dimension(50, 30));
		this.buttonStep.setSize(new Dimension(50, 30));

		this.buttonPause.addActionListener(this);
		this.buttonPlay.addActionListener(this);
		this.buttonStep.addActionListener(this);

		JPanel buttonPanel = new JPanel(new GridLayout(1, 4));
		buttonPanel.add(this.buttonPause);
		buttonPanel.add(this.buttonStep);
		buttonPanel.add(this.buttonPlay);

		this.graphPanel = new GraphPanel(this.goalSpace);

		int allGoalsSize = 0;
		for (MutexGoalSpace mgs : this.goalSpace.getVariableGoalSpaces())
		{
			allGoalsSize += mgs.size();
		}
		this.labelPanel = new JPanel(new GridLayout(allGoalsSize, 1));

		this.planLabelsMap = new ArrayList<JLabel>();
		this.planPanel = new JPanel(new GridLayout(plan.getPlanLength(), 1));
		int i = 1;
		for (Action a : plan.getActions())
		{
			JLabel lab = new JLabel(i + ": " + a.toString());

			this.planPanel.add(lab);
			this.planLabelsMap.add(lab);

			i++;
		}

		JPanel scrollPanel = new JPanel();
		scrollPanel.setLayout(new BoxLayout(scrollPanel, BoxLayout.Y_AXIS));
		scrollPanel.add(this.planPanel);
		scrollPanel.add(buttonPanel);
		scrollPanel.setDoubleBuffered(true);
		this.planScrollPane = new JScrollPane(scrollPanel);
		this.planScrollPane.setDoubleBuffered(true);

		this.goalLabels = new ArrayList<GoalLabel>();
		for (MutexGoalSpace mgs : this.goalSpace.getVariableGoalSpaces())
		{
			TreeSet<Fact> sg = new TreeSet<Fact>(new Comparator<Fact>()
			{
				public int compare(Fact o1, Fact o2) 
				{
					return o1.toString().compareTo(o2.toString());
				};
			});
			sg.addAll(mgs.getGoals());
			
			for (Fact g : sg)
			{
				GoalLabel lab = new GoalLabel(g, mgs, g.toString());
				lab.setFont(f);
				lab.setHorizontalAlignment(SwingConstants.RIGHT);
				if (this.goal.contains(g))
					lab.setForeground(Color.red);

				lab.addMouseListener(this);

//				System.out.println("Added " + g.toString() + " with label "
//						+ lab.getText());

				this.goalLabels.add(lab);
				this.labelPanel.add(lab);
			}
		}

		this.labelScrollPane = new JScrollPane(this.labelPanel);
		this.labelScrollPane.getVerticalScrollBar().addAdjustmentListener(this);
		this.labelScrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		this.graphPanelScrollPane = new JScrollPane(this.graphPanel);
		this.graphPanelScrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		this.graphPanelScrollPane.getVerticalScrollBar().addAdjustmentListener(
				this);
		this.graphPanelScrollPane.setPreferredSize(this.labelPanel.getSize());

		this.addWindowStateListener(this);
		this.addWindowListener(this);

		super.setLayout(new GridLayout(1, 3));
		super.add(planScrollPane);
		super.add(labelScrollPane);
		super.add(graphPanelScrollPane);
		super.pack();

		this.graphPanel.setPreferredSize(this.labelPanel.getSize());
	}

	public boolean pauseForUpdate()
	{
		return this.pauseAfterUpdate;
	}

	public boolean paused()
	{
		return !this.canContinue;
	}

	/**
	 * 1-Indexed
	 * 
	 * @param index
	 */
	public void actionObserved(int index)
	{
		if (index >= 2)
			this.planLabelsMap.get(index - 2).setForeground(Color.black);

		this.planLabelsMap.get(index - 1).setForeground(Color.orange);

		if (this.pauseAfterUpdate)
		{
			this.canContinue = false;
		}
	}

	public IGoalSpace getGoalSpace()
	{
		return goalSpace;
	}

	public void setGoalSpace(VariableGoalSpace goalSpace)
	{
		this.goalSpace = goalSpace;
	}

	private class GraphPanel extends JPanel
	{
		private VariableGoalSpace varGoalSpace;
		private Color mutexColour, goalColour;

		public GraphPanel(VariableGoalSpace ms)
		{
			this.varGoalSpace = ms;
			this.goalColour = Color.red;
			this.mutexColour = Color.orange;
		}

		@Override
		public void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			int width = (int) (this.getWidth() * 0.9);
			int quarter = (int) (width * 0.25);
			int half = width / 2;
			int three = (int) (width * 0.75);

			int rulerDepth = this.getHeight();
			// do ruler
			g.setColor(new Color(100, 100, 100, 40));
			g.drawLine(0, 0, this.getWidth(), 0);
			g.drawLine(0, 0, 0, rulerDepth);
			g.drawLine(quarter, 0, quarter, rulerDepth);
			g.drawLine(half, 0, half, rulerDepth);
			g.drawLine(three, 0, three, rulerDepth);
			g.drawLine(width, 0, width, rulerDepth);

			// System.out.println("painting");

			for (GoalLabel e : goalLabels)
			{
				Fact singleGoal = e.goal;
				GoalLabel label = e;
				double prob = label.goalSpace.getProbability(singleGoal); //access the specific goals' probability rather than going through the overall goal space as this will return Average/Min/Max results
				if (goal.contains(singleGoal))
					g.setColor(this.goalColour);
				else
					g.setColor(Color.black);

				double movedTowards = recogniser.getDistanceMovedTowards(e.goal);
				double movedAway = recogniser.getDistanceMovedAway(e.goal);
				double unmoved = recogniser.getHistory().states().size()-1 - movedTowards - movedAway;
//				double movedTotal = recogniser.getTotalDistanceMoved(e.goal);

				int startY = label.getY() + (label.getHeight() / 2);
				int endX = (int) (prob * width);
				g.drawLine(0, startY, endX, startY);
				if (recogniser.getHistory().states().size() > 0)
				{
					StateHistoryTuple t = recogniser.getHistory()
							.get(recogniser.getHistory().states()
									.size() - 1);

					label.setText(singleGoal.toString());
					if (t.getNearer().contains(singleGoal))
					{
						label.setText("<" + singleGoal.toString());
//						System.out.println("Nearer: "+singleGoal);
					}
					else if (t.getFurther().contains(singleGoal))
					{
						label.setText(">" + singleGoal.toString());
//						System.out.println("Further: "+singleGoal);
					}
					else if (t.getUnmoved().contains(singleGoal))
					{
						label.setText("=" + singleGoal.toString());
//						System.out.println("Equal: "+singleGoal);
					}

					if (t.getState().getFacts().contains(singleGoal))
					{
						label.setText("!" + label.getText());
					}
					
//					label.setText("(<"+movedTowards+",>"+movedAway+","+(movedTotal)+",)"+ label.getText());
//					label.setText("(<"+movedTowards+",>"+movedAway+")"+ label.getText());
					label.setText("(<"+movedTowards+",>"+movedAway+",="+unmoved+")"+ label.getText());

				}

			}

			labelPanel.invalidate();
		}

	}

	@Override
	public void windowStateChanged(WindowEvent e)
	{
	}

	@Override
	public void windowActivated(WindowEvent arg0)
	{
	}

	@Override
	public void windowClosed(WindowEvent arg0)
	{
		System.exit(0);
	}

	@Override
	public void windowClosing(WindowEvent arg0)
	{
		System.exit(0);
	}

	@Override
	public void windowDeactivated(WindowEvent e)
	{
	}

	@Override
	public void windowDeiconified(WindowEvent arg0)
	{
	}

	@Override
	public void windowIconified(WindowEvent arg0)
	{
	}

	@Override
	public void windowOpened(WindowEvent arg0)
	{
	}

	@Override
	public void mouseClicked(MouseEvent arg0)
	{

	}
	
	private class GoalLabel extends JLabel
	{
		public Fact goal;
		public MutexGoalSpace goalSpace;
		
		public GoalLabel(Fact goal, MutexGoalSpace goalSpace)
		{
			super();
			
			this.goal = goal;
			this.goalSpace = goalSpace;
		}


		public GoalLabel(Fact goal, MutexGoalSpace goalSpace, String text)
		{
			super(text);

			this.goal = goal;
			this.goalSpace = goalSpace;
		}
	}

	@Override
	public void mouseEntered(MouseEvent e)
	{
		GoalLabel source = (GoalLabel) e.getSource();

		MutexGoalSpace mgs = source.goalSpace;

		double totalProb = 0; // goalSpace.getProbability(mut.getOwner());

		for (GoalLabel gl : goalLabels)
		{
			if (mgs.equals(gl.goalSpace) == false)
				continue;
				
			totalProb += mgs.getProbability(gl.goal);
			gl.setForeground(this.graphPanel.mutexColour);
			
		}

		source.setForeground(Color.green);
		source.setToolTipText(mgs.getProbability(source.goal) + " -- Total = "
				+ totalProb);
	}

	@Override
	public void mouseExited(MouseEvent e)
	{
		for (GoalLabel entry : this.goalLabels)
		{
			if (goal.contains(entry.goal))
				entry.setForeground(this.graphPanel.goalColour);
			else
				entry.setForeground(Color.black);
		}
	}

	@Override
	public void mousePressed(MouseEvent arg0)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseReleased(MouseEvent arg0)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		if (e.getSource() == this.buttonStep)
		{
			this.canContinue = true;
		}
		else if (e.getSource() == this.buttonPause)
		{
			this.canContinue = false;
			this.pauseAfterUpdate = true;

			this.buttonPlay.setEnabled(true);
			this.buttonPause.setEnabled(false);
		}
		else if (e.getSource() == this.buttonPlay)
		{
			this.pauseAfterUpdate = false;
			this.canContinue = true;

			this.buttonPause.setEnabled(true);
			this.buttonStep.setEnabled(true);
		}
	}

	protected class SortedGoalSpace extends VariableGoalSpace
	{

		public SortedGoalSpace(Set<MutexGoalSpace> varSpaces)
		{
			super(varSpaces);
		}

		@Override
		public Set<MutexGoalSpace> getVariableGoalSpaces()
		{
			TreeSet<MutexGoalSpace> sorted = new TreeSet<MutexGoalSpace>(
					new Comparator<MutexGoalSpace>()
					{
						public int compare(MutexGoalSpace a, MutexGoalSpace b)
						{
							if (a.size() < b.size())
								return -1;
							else if (a.size() > b.size())
								return 1;
							else
								return -1;
						}
					});
			sorted.addAll(super.getVariableGoalSpaces());

			return sorted;
		}
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e)
	{
		int newX = e.getValue();
		if (e.getSource() == this.labelScrollPane.getVerticalScrollBar())
		{
			this.graphPanelScrollPane.getVerticalScrollBar().setValue(newX);
		}
		else if (e.getSource() == this.graphPanelScrollPane
				.getVerticalScrollBar())
		{
			this.labelScrollPane.getVerticalScrollBar().setValue(newX);
		}

	}

};
