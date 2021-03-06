package com.sots.routing.router;

import java.util.Stack;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import com.sots.LogisticsPipes2;
import com.sots.routing.NetworkNode;
import com.sots.routing.WeightedNetworkNode;
import com.sots.util.data.Tuple;
import com.sots.util.data.Triple;

import net.minecraft.util.EnumFacing;
import net.minecraft.crash.CrashReport;

public class MultiCachedDijkstraRouter{
	private static final int NUM_THREADS = 4;
	//WeightedNetworkNode start, target;
	//private ExecutorService executor = Executors.newSingleThreadExecutor();
	private ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
	private Queue<WeightedNetworkNode> unvisited = new LinkedBlockingQueue<WeightedNetworkNode>();
	private Queue<WeightedNetworkNode> visited = new LinkedBlockingQueue<WeightedNetworkNode>();
	private Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>> routingInfo;

	protected volatile Map<UUID, WeightedNetworkNode> junctions;
	protected volatile Map<UUID, NetworkNode> destinations;
	protected volatile Map<UUID, NetworkNode> nodes;

	private Map<Tuple<NetworkNode, NetworkNode>, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>> cache = new HashMap<Tuple<NetworkNode, NetworkNode>, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>>();

	public MultiCachedDijkstraRouter(Map<UUID, WeightedNetworkNode> junctions, Map<UUID, NetworkNode> destinations, Map<UUID, NetworkNode> nodes) {
		this.junctions = junctions; //I am not sure if these two Maps will be kept updated with each other
		this.destinations = destinations;
		this.nodes = nodes;
	}

	/**
	 * The first part of the output is a boolean, which is false if the route has not yet been calculated, and is true when the route has been calculated
	 * The second part of the output is a triple consisting of the start node, the target node and the route from the start node to the target node
	 */
	public Tuple<Boolean, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>> route(NetworkNode s, NetworkNode t) {
		//Triple<Map<UUID, WeightedNetworkNode>, NetworkNode, NetworkNode> input = new Triple<Map<UUID, WeightedNetworkNode>, NetworkNode, NetworkNode>(super.junctions, s, t);
		Tuple<NetworkNode, NetworkNode> input = new Tuple<NetworkNode, NetworkNode>(s, t);
		try { //DEBUG
		if (cache.containsKey(input)) {
			LogisticsPipes2.logger.info("Got a route from cache"); //DEBUG
			return new Tuple<Boolean, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>>(true, cache.get(input));
		}
		} catch (Exception e) {
			LogisticsPipes2.logger.info(e); //DEBUG
		}


		Tuple<Boolean, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>> result = doActualRouting(s, t);
		return result;
	}

	public Tuple<Boolean, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>> doActualRouting(NetworkNode s, NetworkNode t) {
		WeightedNetworkNode start, target;

		Map<UUID, WeightedNetworkNode> junctions = new HashMap<UUID, WeightedNetworkNode>(this.junctions);
		Map<UUID, NetworkNode> destinations = new HashMap<UUID, NetworkNode>(this.destinations);
		Map<UUID, NetworkNode> nodes = new HashMap<UUID, NetworkNode>(this.nodes);

		if (junctions.containsKey(s.getId())) {
			start = junctions.get(s.getId());
		} else {
			LogisticsPipes2.logger.info("You tried routing from a node, which was not a destination or junction.");
			return null;
		}

		if (junctions.containsKey(t.getId())) {
			target = junctions.get(t.getId());
		} else {
			LogisticsPipes2.logger.info("You tried routing to a node, which was not a destination or junction.");
			return null;
		}

		Tuple<Boolean, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>> result = new Tuple<Boolean, Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>>(false, null);

		FutureTask<Void> routingTask =
			new FutureTask<Void>(
					new Callable<Void>() {
						@Override
						public Void call() 
								throws Exception {
							start.p_cost=0;
							start.parent=null;
							unvisited.add(start);
							while(!unvisited.isEmpty()) {
								WeightedNetworkNode current = unvisited.poll();
								current.getMember().spawnParticle(0f, 1.000f, 0f);
								Thread.sleep(120);

								for (int i = 0; i < 6; i++) {
									Tuple<WeightedNetworkNode, Integer> neighborT = current.weightedNeighbors[i];
									if (neighborT == null) {
										continue;
									}

									WeightedNetworkNode neighbor = neighborT.getKey();
									int distance = neighborT.getVal();
									if (!(unvisited.contains(neighbor) || visited.contains(neighbor))) {
										neighbor.p_cost = Integer.MAX_VALUE;
										unvisited.add(neighbor);
										neighbor.getMember().spawnParticle(0.502f, 0.000f, 0.502f);
									}
									if (current.p_cost + distance < neighbor.p_cost) {
										neighbor.p_cost = current.p_cost + distance;
										neighbor.parent = new Tuple<NetworkNode, EnumFacing>(current, EnumFacing.getFront(i));
									}
								}

								visited.add(current);
								Thread.sleep(120);
							}
							for (NetworkNode n : destinations.values()) {
								NetworkNode help = n;

								Stack<Tuple<UUID, EnumFacing>> route = new Stack<Tuple<UUID, EnumFacing>>();
								while(help.parent != null) {
									pushToRouteUntillParent(help, route);

									help.getMember().spawnParticle(1.0f, 0.549f, 0.0f);
									help = help.parent.getKey();
								}
								Tuple<NetworkNode, NetworkNode> input = new Tuple<NetworkNode, NetworkNode>((NetworkNode) start, n);
								Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>> result = new Triple<NetworkNode, NetworkNode, Stack<Tuple<UUID, EnumFacing>>>((NetworkNode) start, n, route);
								cache.put(input, result);
							}
							result.setVal(cache.get(new Tuple<NetworkNode, NetworkNode>(start, target)));
							result.setKey(true);
							return null;
							//return cache.get(new Tuple<NetworkNode, NetworkNode>(start, target));
						}

						private void pushToRouteUntillParent(NetworkNode current, Stack<Tuple<UUID, EnumFacing>> route) throws InterruptedException {
							NetworkNode parent = current.parent.getKey();
							EnumFacing direction = current.parent.getVal();
							int parentDirection = direction.getOpposite().getIndex();

							NetworkNode help = current;
							while(help.getId() != parent.getId()) {
								help = help.getNeighborAt(parentDirection);
								route.push(new Tuple<UUID, EnumFacing>(help.getId(), direction));
								help.getMember().spawnParticle(1.0f, 0.0f, 0.0f);
								Thread.sleep(120);
							}
						}

					});
		executor.execute(routingTask);
		//try {
			//routingInfo = routingTask.get();
			////executor.shutdownNow();
		//}
		//catch (Exception e) {
			//CrashReport.makeCrashReport(e, "A logistics Pipes router was interrupted!");
		//}
		return result;
	}

	public void clean() {
		unvisited.clear();
		visited.clear();
		//executor = Executors.newSingleThreadExecutor();
		executor = Executors.newFixedThreadPool(NUM_THREADS);
	}

	public void shutdown() {
		executor.shutdownNow();
		//executor = Executors.newSingleThreadExecutor();
		executor = Executors.newFixedThreadPool(NUM_THREADS);
		cache.clear();
	}
}
