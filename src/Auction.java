
//the list of imports
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
public class Auction implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;

	private StateActionTables stateActionTables;
	private long timeout_setup;
	private long timeout_plan;
	private long timeout_bid;

	private Solution currentSolutionProposition; // The solution we're proposing
	private Solution currentSolution; // The solution we're actually going with
	private static final int AMOUNT_SOLUTIONS_KEPT_BY_SLS = 100;
	private HashSet<Task> tasksToHandle;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
		} catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
		// the bid method cannot execute more than timeout_bid milliseconds
		timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);

		
		/*
		 * I didn't make class vars for these since currently they are only used once to initialize stateActionTable
		 * If we want to change more than just the discount factor of the stateActionTable, we may need to reinitialize
		 * and then we may want local copies of the vars from  the xml
		*/
		double gamma = agent.readProperty("discount-factor", Double.class,0.95);
		double threshold = agent.readProperty("convergence-threshold", Double.class,0.01);
		int maxNumTasks = agent.readProperty("max-tasks", Integer.class,50);
		
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		// TODO : Compute the StateActionTable (Simon)
		this.stateActionTables = new StateActionTables(topology, distribution, gamma,maxNumTasks,threshold,this.agent.vehicles(),2);
		this.currentSolution = new Solution(this.agent.vehicles());
		this.currentSolutionProposition = null;
		this.tasksToHandle = new HashSet<Task>();
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		// TODO : Make the agent handle the new task IF it won the auctions
		if (winner == this.agent.id()) {
			this.tasksToHandle.add(previous);
			this.currentSolution = this.currentSolutionProposition;
		}
	}

	@Override
	public Long askPrice(Task task) {

		// TODO : Only compute if at least one vehicle can handle the task that's
		// auctioned
		// if (vehicle.capacity() < task.weight)
		// return null;

		System.out.println("[START] : Bid for :  " + task);

		// 1. For EACH AGENT, find the best solutions according to centralized (Arthur)
		// TODO: Add the "each agent" component
		TaskSet tasksWithAuctionedTask = TaskSet.copyOf(agent.getTasks());
		tasksWithAuctionedTask.add(task);
		SLS sls = new SLS(agent.vehicles(), tasksWithAuctionedTask, this.timeout_bid,
				Auction.AMOUNT_SOLUTIONS_KEPT_BY_SLS);
		SolutionList solutions = sls.getSolutions();

		// 2. Use this.stateActionTable to discriminate the solutions of each agent,
		// selecting the best one (Simon)
		this.currentSolutionProposition = solutions.getFirstSolution();

		// 3. Place a bid according to results (Simon)
		long bid = (long) (this.currentSolutionProposition.totalCost - this.currentSolution.totalCost);
		System.out.println("BID : " + bid);
		System.out.println();
		bid = bid < 0 ? -bid : bid;
		return bid/2;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		// TODO use the results of askPrice to output the plans (Arthur)
		// TODO MAKE THIS BETTER. YOU ALREADY COMPUTED THE SOLUTION
		ArrayList<Plan> plans = new ArrayList<Plan>();
		SLS sls = new SLS(vehicles, tasks, this.timeout_bid,
				Auction.AMOUNT_SOLUTIONS_KEPT_BY_SLS);
		for (Vehicle v : vehicles) {
			plans.add(new Plan(v.getCurrentCity(), sls.getSolutions().getFirstSolution().getVehicleAgendas().get(v)));
		}
				return plans;
	}
}
