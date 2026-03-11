package uk.ac.bris.cs.scotlandyard.model;
import org.checkerframework.checker.nullness.qual.NonNull;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import java.util.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

public final class MyGameStateFactory implements Factory<GameState> {
	private final class MyGameState implements GameState {
		private final GameSetup setup;
		private final int round;
		private final int currentPlayerIndex;
		private final ImmutableList<LogEntry> log;
		private final Player mrX;
		private final List<Player> detectives;

		private MyGameState(final GameSetup setup, final ImmutableSet<Piece> remaining, final ImmutableList<LogEntry> log, final Player mrX, final List<Player> detectives){
			this.setup = Objects.requireNonNull(setup);
			this.round = 0;
			this.currentPlayerIndex = 0;
			this.log = Objects.requireNonNull(log);
			this.mrX = Objects.requireNonNull(mrX);
			this.detectives = new ArrayList<>(Objects.requireNonNull(detectives));

			if (!mrX.piece().isMrX()) {
				throw new IllegalArgumentException("Cannot have same mrX piece");
			}
			if (setup.moves.isEmpty()) throw new IllegalArgumentException("Moves is empty!");
			if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Nodes are empty!");

			for (Player p : this.detectives) {
				if (p.has(Ticket.DOUBLE)) {
					throw new IllegalArgumentException("Detectives cannot have double tickets");
				}
				if (p.has(Ticket.SECRET)) {
					throw new IllegalArgumentException("Detectives cannot have secret tickets");
				}
			}

			Set<Integer> sameNodeDetective = new HashSet<>();
			for (Player p : this.detectives) {
				if (!sameNodeDetective.add(p.location())) {
					throw new IllegalArgumentException("Multiple detectives cannot be at the same location");
				}
			}
		}

		private MyGameState(final GameSetup setup, final int round, final int currentPlayerIndex,
		                   final ImmutableList<LogEntry> log, final Player mrX, final List<Player> detectives) {
			this.setup = Objects.requireNonNull(setup);
			this.round = round;
			this.currentPlayerIndex = currentPlayerIndex;
			this.log = Objects.requireNonNull(log);
			this.mrX = Objects.requireNonNull(mrX);
			this.detectives = new ArrayList<>(Objects.requireNonNull(detectives));
		}

		private Set<Integer> occupiedLocations() {
			Set<Integer> occupied = new HashSet<>();
			occupied.add(mrX.location());
			for (Player d : detectives) occupied.add(d.location());
			return occupied;
		}

		private ImmutableSet<Move> getMrXMoves() {
			Set<Integer> occupied = occupiedLocations();
			occupied.remove(mrX.location());
			Set<Move> result = new HashSet<>();
			var graph = setup.graph;
			int from = mrX.location();

			for (Integer to : graph.adjacentNodes(from)) {
				if (occupied.contains(to)) continue;
				Optional<ImmutableSet<Transport>> edgeOpt = graph.edgeValue(from, to);
				if (edgeOpt.isEmpty()) continue;
				for (Transport t : edgeOpt.get()) {
					Ticket ticket = t.requiredTicket();
					if (mrX.has(ticket)) {
						result.add(new SingleMove(mrX.piece(), from, ticket, to));
					}
				}
			}

			if (round + 1 < setup.moves.size() && mrX.has(Ticket.DOUBLE)) {
				for (Integer mid : graph.adjacentNodes(from)) {
					if (occupied.contains(mid)) continue;
					Optional<ImmutableSet<Transport>> edge1Opt = graph.edgeValue(from, mid);
					if (edge1Opt.isEmpty()) continue;
					Set<Integer> occupiedAfterFirst = new HashSet<>(occupied);
					occupiedAfterFirst.add(mid);
					for (Transport t1 : edge1Opt.get()) {
						Ticket ticket1 = t1.requiredTicket();
						if (ticket1 == Ticket.DOUBLE) continue;
						if (!mrX.has(ticket1)) continue;
						for (Integer to : graph.adjacentNodes(mid)) {
							if (occupiedAfterFirst.contains(to)) continue;
							Optional<ImmutableSet<Transport>> edge2Opt = graph.edgeValue(mid, to);
							if (edge2Opt.isEmpty()) continue;
							for (Transport t2 : edge2Opt.get()) {
								Ticket ticket2 = t2.requiredTicket();
								if (ticket2 == Ticket.DOUBLE) continue;
								int need2 = (ticket1 == ticket2 ? 2 : 1);
								if (mrX.hasAtLeast(ticket2, need2)) {
									result.add(new DoubleMove(mrX.piece(), from, ticket1, mid, ticket2, to));
								}
							}
						}
					}
				}
			}

			return ImmutableSet.copyOf(result);
		}


		private int[] getEffectiveRoundAndIndex() {
			int r = round;
			int i = currentPlayerIndex;
			while (true) {
				if (i == 0) {
					if (!getMrXMoves().isEmpty()) return new int[]{r, 0};
					// MrX stuck but maybe detectives can move next round - no, when it's MrX's turn and he's stuck, game over
					return new int[]{r, 0};
				}
				if (i > 0 && i <= detectives.size()) {
					Player det = detectives.get(i - 1);
					if (!getDetectiveMoves(det).isEmpty()) return new int[]{r, i};
					i++;
					if (i > detectives.size()) {
						r++;
						i = 0;
					}
				}
			}
		}



		private ImmutableSet<Move> getDetectiveMoves(Player detective) {
			Set<Integer> occupied = new HashSet<>();
			for (Player d : detectives) if (d.piece() != detective.piece()) occupied.add(d.location());
			// Detectives can move to MrX's location (capture)
			Set<Move> result = new HashSet<>();
			var graph = setup.graph;
			int from = detective.location();

			for (Integer to : graph.adjacentNodes(from)) {
				if (occupied.contains(to)) continue;
				Optional<ImmutableSet<Transport>> edgeOpt = graph.edgeValue(from, to);
				if (edgeOpt.isEmpty()) continue;
				for (Transport t : edgeOpt.get()) {
					Ticket ticket = t.requiredTicket();
					if (ticket == Ticket.SECRET) continue;
					if (detective.has(ticket)) {
						result.add(new SingleMove(detective.piece(), from, ticket, to));
					}
				}
			}
			return ImmutableSet.copyOf(result);
		}



		@Override public GameSetup getSetup() {
			return setup;
		}



		@Override public ImmutableSet<Piece> getPlayers() {
			List<Piece> allPieces = new ArrayList<>();
			allPieces.add(mrX.piece());
			for (Player p : detectives){
				allPieces.add(p.piece());
			}
			return ImmutableSet.copyOf(allPieces);
		}




		@Override public GameState advance(Move move) {
			int[] effective = getEffectiveRoundAndIndex();
			int effIdx = effective[1];
			ImmutableSet<Move> available = getAvailableMoves();
			if (available.isEmpty() || !available.contains(move)) {
				throw new IllegalArgumentException("Move not in getAvailableMoves()");
			}

			if (move.commencedBy().isMrX()) {
				return move.accept(new Move.Visitor<GameState>() {
					@Override public GameState visit(SingleMove m) {
						Player newMrX = mrX.use(m.ticket).at(m.destination);
						boolean reveal = setup.moves.get(round);
						LogEntry entry = reveal ? LogEntry.reveal(m.ticket, m.destination) : LogEntry.hidden(m.ticket);
						ImmutableList<LogEntry> newLog = ImmutableList.<LogEntry>builder().addAll(log).add(entry).build();
						int nextRound = round;
						int nextIndex = 1;
						if (detectives.isEmpty()) {
							nextRound = round + 1;
							nextIndex = 0;
						}
						return new MyGameState(setup, nextRound, nextIndex, newLog, newMrX, detectives);
					}
					@Override public GameState visit(DoubleMove m) {
						Player newMrX = mrX.use(m.ticket1).use(m.ticket2).use(Ticket.DOUBLE).at(m.destination2);
						boolean reveal1 = setup.moves.get(round);
						boolean reveal2 = round + 1 < setup.moves.size() && setup.moves.get(round + 1);
						LogEntry e1 = reveal1 ? LogEntry.reveal(m.ticket1, m.destination1) : LogEntry.hidden(m.ticket1);
						LogEntry e2 = reveal2 ? LogEntry.reveal(m.ticket2, m.destination2) : LogEntry.hidden(m.ticket2);
						ImmutableList<LogEntry> newLog = ImmutableList.<LogEntry>builder().addAll(log).add(e1).add(e2).build();
						int nextRound = round + 2;
						int nextIndex = detectives.isEmpty() ? 0 : 1;
						return new MyGameState(setup, nextRound, nextIndex, newLog, newMrX, detectives);
					}
				});
			} else {
				SingleMove sm = (SingleMove) move;
				Player detective = detectives.stream().filter(d -> d.piece() == sm.commencedBy()).findFirst().orElseThrow();
				Player newDetective = detective.use(sm.ticket).at(sm.destination);
				Player newMrX = mrX.give(sm.ticket);
				List<Player> newDetectives = new ArrayList<>(detectives);
				for (int i = 0; i < newDetectives.size(); i++) {
					if (newDetectives.get(i).piece() == sm.commencedBy()) {
						newDetectives.set(i, newDetective);
						break;
					}
				}
				int nextIndex = effIdx + 1;
				int nextRound = round;
				if (nextIndex > detectives.size()) {
					nextRound = round + 1;
					nextIndex = 0;
				}
				MyGameState candidate = new MyGameState(setup, nextRound, nextIndex, log, newMrX, newDetectives);
				while (candidate.currentPlayerIndex > 0 && candidate.currentPlayerIndex <= candidate.detectives.size()) {
					Player nextDetective = candidate.detectives.get(candidate.currentPlayerIndex - 1);
					if (!candidate.getDetectiveMoves(nextDetective).isEmpty()) break;
					nextIndex++;
					if (nextIndex > candidate.detectives.size()) {
						nextRound = candidate.round + 1;
						nextIndex = 0;
						candidate = new MyGameState(setup, nextRound, nextIndex, log, newMrX, newDetectives);
						break;
					}
					candidate = new MyGameState(setup, candidate.round, nextIndex, log, newMrX, newDetectives);
				}
				return candidate;
			}
		}



		@Override public Optional<Integer> getDetectiveLocation(Detective detective) {
			for (Player d : detectives) {
				if (d.piece() == detective) {
					return Optional.of(d.location());
				}
			}
			return Optional.empty();
		}



		@Override public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			Player player = null;
			if (piece.isMrX()) {
				player = mrX;
			}
			else {
				for (Player p : detectives) {
					if (p.piece() == piece) {
						player = p;
					}
				}
			}
			if (player == null) return Optional.empty();

			final Player target = player;
			return Optional.of(new TicketBoard() {
				@Override
				public int getCount(@NonNull Ticket ticket) {
					return target.tickets().getOrDefault(ticket, 0);
				}
			});
		}



		@Override public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}



		@Override public ImmutableSet<Piece> getWinner() {
			for (Player d : detectives) {
				if (d.location() == mrX.location()) {
					return detectives.stream().map(Player::piece).collect(ImmutableSet.toImmutableSet());
				}
			}
			if (round >= setup.moves.size()) {
				return ImmutableSet.of(Piece.MrX.MRX);
			}
			if (round == 0 && currentPlayerIndex == 0 && detectives.size() == 1) {
				Player only = detectives.get(0);
				if (only.tickets().values().stream().allMatch(c -> c == 0)) {
					return ImmutableSet.of(Piece.MrX.MRX);
				}
			}
			if (currentPlayerIndex == 0) {
				if (getMrXMoves().isEmpty()) {
					return detectives.stream().map(Player::piece).collect(ImmutableSet.toImmutableSet());
				}
				// MrX's turn but all detectives stuck -> MrX wins
				boolean anyDetectiveCanMove = false;
				for (Player d : detectives) {
					if (!getDetectiveMoves(d).isEmpty()) {
						anyDetectiveCanMove = true;
						break;
					}
				}
				if (!anyDetectiveCanMove) {
					return ImmutableSet.of(Piece.MrX.MRX);
				}
			} else {
				boolean anyCanMove = false;
				for (Player d : detectives) {
					if (!getDetectiveMoves(d).isEmpty()) {
						anyCanMove = true;
						break;
					}
				}
				if (!anyCanMove) {
					return ImmutableSet.of(Piece.MrX.MRX);
				}
			}
			return ImmutableSet.of();
		}

		@Override public ImmutableSet<Move> getAvailableMoves() {
			if (!getWinner().isEmpty()) return ImmutableSet.of();
			int[] effective = getEffectiveRoundAndIndex();
			int effIdx = effective[1];
			if (effIdx == 0) return getMrXMoves();
			return getDetectiveMoves(detectives.get(effIdx - 1));
		}
	}

	@Nonnull @Override public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
	}

}
