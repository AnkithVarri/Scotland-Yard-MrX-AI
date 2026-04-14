package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;

public class MyAi implements Ai {

	private static final double score_win = 1_000_000.0;
	private static final double score_lose = -1_000_000.0;
	private static final int unreachable = 10_000;

	@Nonnull
	@Override
	public String name() {
		return "reelSigma";
	}

	public int dijkstra(
			ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph,
			int start,
			int goal,
			Set<Integer> blocked) {
		if (start == goal) {
			return 0;
		}
		record Node(int v, int d) implements Comparable<Node> {
			@Override
			public int compareTo(Node other) {
				return Integer.compare(this.d, other.d);
			}
		}
		PriorityQueue<Node> pq = new PriorityQueue<>();
		Map<Integer, Integer> best = new HashMap<>();
		pq.add(new Node(start, 0));
		best.put(start, 0);

		while (!pq.isEmpty()) {
			Node cur = pq.poll();
			if (cur.d() != best.getOrDefault(cur.v(), unreachable)) {
				continue;
			}
			if (cur.v() == goal) {
				return cur.d();
			}
			for (int w : graph.adjacentNodes(cur.v())) {
				if (w != goal && blocked.contains(w)) {
					continue;
				}
				int nd = cur.d() + 1;
				if (nd < best.getOrDefault(w, unreachable)) {
					best.put(w, nd);
					pq.add(new Node(w, nd));
				}
			}
		}
		return unreachable;
	}

	public double score(Board.GameState state, int mrxLocation) {
		if (isMrXCaught(state, mrxLocation)) {
			return score_lose;
		}

		ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph = state.getSetup().graph;
		List<Integer> detDists = new ArrayList<>();
		for (Piece p : state.getPlayers()) {
			if (!p.isDetective()) {
				continue;
			}
			Piece.Detective det = (Piece.Detective) p;
			Optional<Integer> detLoc = state.getDetectiveLocation(det);
			if (detLoc.isEmpty()) {
				continue;
			}
			int loc = detLoc.get();
			if (loc == mrxLocation) {
				detDists.add(0);
				continue;
			}
			Set<Integer> blocked = collectBlockedDetectiveLocations(state, det);
			int dist = dijkstra(graph, loc, mrxLocation, blocked);
			detDists.add(dist);
		}

		if (detDists.isEmpty()) {
			return score_win * 0.5;
		}

		int minD = unreachable;
		for (int d : detDists) {
			if (d < minD) {
				minD = d;
			}
		}
		if (minD == 0) {
			return score_lose;
		}
		minD = Math.min(minD, 24);

		List<Integer> capped = new ArrayList<>(detDists.size());
		for (int d : detDists) {
			capped.add(Math.min(d, unreachable));
		}
		Collections.sort(capped);

		int secondMin = minD;
		if (capped.size() >= 2) {
			secondMin = capped.get(1);
		}
		secondMin = Math.min(secondMin, 24);

		double pressure = 0.0;
		for (int d : detDists) {
			int dd = Math.min(d, 24);
			pressure += 24.0 / (dd + 1.0);
		}

		int secrets = 0;
		int doubles = 0;
		Optional<Board.TicketBoard> mrxTickets = state.getPlayerTickets(Piece.MrX.MRX);
		if (mrxTickets.isPresent()) {
			Board.TicketBoard tb = mrxTickets.get();
			secrets = tb.getCount(Ticket.SECRET);
			doubles = tb.getCount(Ticket.DOUBLE);
		}

		int far = 0;
		for (int d : detDists) {
			if (d >= 4) {
				far++;
			}
		}

		double s = 0.0;
		s += 95.0 * minD;
		s += 35.0 * secondMin;
		s -= 4.2 * pressure;
		s += 14.0 * secrets;
		s += 9.0 * doubles;
		s += 5.0 * far;
		s += graph.degree(mrxLocation) * 2.0;
		return s;
	}

	public boolean isMrXCaught(Board state, int mrxLocation) {
		for (Piece p : state.getPlayers()) {
			if (p.isDetective()) {
				int loc = state.getDetectiveLocation((Piece.Detective) p).orElse(-1);
				if (loc == mrxLocation) {
					return true;
				}
			}
		}
		return false;
	}

	private static int mrXDestination(Move move) {
		if (move instanceof Move.SingleMove singleMove) {
			return singleMove.destination;
		}
		if (move instanceof Move.DoubleMove doubleMove) {
			return doubleMove.destination2;
		}
		throw new IllegalArgumentException("Unsupported move type: " + move.getClass().getName());
	}

	private Double terminalScore(Board.GameState state, int mrxLocation, long deadlineNanos) {
		if (System.nanoTime() > deadlineNanos) {
			return score(state, mrxLocation);
		}
		ImmutableSet<Piece> winner = state.getWinner();
		if (!winner.isEmpty()) {
			if (winner.contains(Piece.MrX.MRX)) {
				return score_win;
			}
			return score_lose;
		}
		if (state.getAvailableMoves().isEmpty()) {
			return score(state, mrxLocation);
		}
		return null;
	}

	private List<Move> orderedMrXMoves(Board.GameState state, int mrxLocation, ImmutableSet<Move> moves) {
		List<Move> ordered = new ArrayList<>(moves);
		for (int i = 0; i < ordered.size(); i++) {
			int bestIndex = i;
			double bestScore = heuristicMrXMove(state, mrxLocation, ordered.get(i));
			for (int j = i + 1; j < ordered.size(); j++) {
				double candidateScore = heuristicMrXMove(state, mrxLocation, ordered.get(j));
				if (candidateScore > bestScore) {
					bestScore = candidateScore;
					bestIndex = j;
				}
			}
			if (bestIndex != i) {
				Move tmp = ordered.get(i);
				ordered.set(i, ordered.get(bestIndex));
				ordered.set(bestIndex, tmp);
			}
		}
		return ordered;
	}

	private Set<Integer> collectBlockedDetectiveLocations(Board.GameState state, Piece.Detective exclude) {
		Set<Integer> blocked = new HashSet<>();
		for (Piece q : state.getPlayers()) {
			if (q.isDetective() && q != exclude) {
				Optional<Integer> qLoc = state.getDetectiveLocation((Piece.Detective) q);
				if (qLoc.isPresent()) {
					blocked.add(qLoc.get());
				}
			}
		}
		return blocked;
	}

	private double Minimax(Board.GameState state, int mrxLocation, int mrXPliesToExpand, long deadlineNanos) {
		Double terminal = terminalScore(state, mrxLocation, deadlineNanos);
		if (terminal != null) {
			return terminal;
		}

		ImmutableSet<Move> moves = state.getAvailableMoves();
		Piece mover = moves.iterator().next().commencedBy();
		if (mover.isMrX()) {
			if (mrXPliesToExpand <= 0) {
				return score(state, mrxLocation);
			}
			List<Move> ordered = orderedMrXMoves(state, mrxLocation, moves);
			double best = score_lose;
			for (Move m : ordered) {
				int dest = mrXDestination(m);
				double v = Minimax(state.advance(m), dest, mrXPliesToExpand - 1, deadlineNanos);
				best = Math.max(best, v);
			}
			return best;
		}

		double best = score_win;
		for (Move m : moves) {
			double v = Minimax(state.advance(m), mrxLocation, mrXPliesToExpand, deadlineNanos);
			best = Math.min(best, v);
		}
		return best;
	}



	private double alphaBetaPrune(
			Board.GameState state,
			int mrxLocation,
			int mrXPliesToExpand,
			double alpha,
			double beta,
			long deadlineNanos) {
		Double terminal = terminalScore(state, mrxLocation, deadlineNanos);
		if (terminal != null) {
			return terminal;
		}

		ImmutableSet<Move> moves = state.getAvailableMoves();
		Piece mover = moves.iterator().next().commencedBy();
		if (mover.isMrX()) {
			if (mrXPliesToExpand <= 0) {
				return score(state, mrxLocation);
			}
			List<Move> ordered = orderedMrXMoves(state, mrxLocation, moves);
			double best = score_lose;
			for (Move m : ordered) {
				int dest = mrXDestination(m);
				double v = alphaBetaPrune(state.advance(m), dest, mrXPliesToExpand - 1, alpha, beta, deadlineNanos);
				best = Math.max(best, v);
				alpha = Math.max(alpha, v);
				if (beta <= alpha) {
					break;
				}
			}
			return best;
		}

		double best = score_win;
		for (Move m : moves) {
			double v = alphaBetaPrune(state.advance(m), mrxLocation, mrXPliesToExpand, alpha, beta, deadlineNanos);
			best = Math.min(best, v);
			beta = Math.min(beta, v);
			if (beta <= alpha) {
				break;
			}
		}
		return best;
	}

	private double heuristicMrXMove(Board.GameState state, int from, Move m) {
		int to = mrXDestination(m);
		ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph = state.getSetup().graph;
		double h = graph.degree(to);
		for (Piece p : state.getPlayers()) {
			if (p.isDetective()) {
				Piece.Detective detective = (Piece.Detective) p;
				Optional<Integer> detLoc = state.getDetectiveLocation(detective);
				if (detLoc.isEmpty()) {
					continue;
				}
				Set<Integer> blocked = collectBlockedDetectiveLocations(state, detective);
				h += Math.min(dijkstra(graph, detLoc.get(), to, blocked), 30);
			}
		}
		return h;
	}

	@Nonnull @Override public Move pickMove( @Nonnull Board board,  Pair<Long, TimeUnit> timeoutPair) {
		List<Move> moves = board.getAvailableMoves().asList();
		if (moves.isEmpty()) {
			throw new IllegalStateException("No moves available");
		}
		if (!(board instanceof Board.GameState gameState)) {
			return moves.get(0);
		}

		long deadline = System.nanoTime()
				+ Math.max(timeoutPair.left() * timeoutPair.right().toNanos(1) - 80_000_000L, 10_000_000L);
		int mrxLoc = moves.get(0).source();
		List<Move> rootOrder = orderedMrXMoves(gameState, mrxLoc, board.getAvailableMoves());

		Move bestMove = rootOrder.get(0);
		double bestVal = score_lose;
		for (Move m : rootOrder) {
			if (System.nanoTime() > deadline) {
				break;
			}
			try {
				Board.GameState next = gameState.advance(m);
				int dest = mrXDestination(m);
				double v = alphaBetaPrune(next, dest, 0, score_lose, score_win, deadline);
				if (v > bestVal) {
					bestVal = v;
					bestMove = m;
				}
			} catch (RuntimeException ignored) {
				// Skip invalid branch and keep best legal move found so far.
			}
		}
		return bestMove;
	}
}
