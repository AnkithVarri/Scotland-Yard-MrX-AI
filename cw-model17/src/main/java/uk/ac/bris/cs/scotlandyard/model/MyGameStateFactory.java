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
		private GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;

		private MyGameState(final GameSetup setup, final ImmutableSet<Piece> remaining, final ImmutableList<LogEntry> log, final Player mrX, final List<Player> detectives){
			this.setup = Objects.requireNonNull(setup);
			this.remaining = Objects.requireNonNull(remaining);
			this.log = Objects.requireNonNull(log);

			Set<Piece> sameMrx = new HashSet<>();
			this.mrX = Objects.requireNonNull(mrX);
			if (!sameMrx.add(mrX.piece())) {
				throw new IllegalArgumentException("Cannot have same mrX piece");
			}

			this.detectives = Objects.requireNonNull(detectives);

			if(setup.moves.isEmpty()) throw new IllegalArgumentException("Moves is empty!");
			if(setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Nodes are empty!");

			for (Player p : detectives) {
				if (p.has(Ticket.DOUBLE)) {
					throw new IllegalArgumentException("Detectives cannot have double tickets");
				}
				if (p.has(Ticket.SECRET)) {
					throw new IllegalArgumentException("Detectives cannot have secret tickets");
				}
			}

			Set<Integer> sameNodeDetective = new HashSet<>();
			for (Player p : detectives) {
				if (!sameNodeDetective.add(p.location())) {
					throw new IllegalArgumentException("Multiple detectives cannot be at the same location");
				}
			}
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
			if(!getAvailableMoves().contains(move)) throw new IllegalArgumentException("Illegal move");
			return move.accept(new Move.Visitor<GameState>() {


				@Override
				public GameState visit(Move.SingleMove move) {
					Player player;

					if (move.commencedBy().isMrX()) {
						player = mrX;
					} else {
						player = null;
						for (Player p : detectives) {
							if (p.piece() == move.commencedBy()) {
								player = p;
								break;
							}
						}
					}

					Player updatedPlayer = player.at(move.destination).use(move.ticket);

					Player updatedMrX = mrX;
					List<Player> updatedDetectives = new ArrayList<>(detectives);
					ImmutableList<LogEntry> updatedLog = log;
					Set<Piece> updatedRemaining = new HashSet<>(remaining);

					if (move.commencedBy().isMrX()) {
						updatedMrX = updatedPlayer;

						// Update Log: Check reveal status
						boolean shouldReveal = setup.moves.get(log.size());
						LogEntry entry = shouldReveal ? LogEntry.reveal(move.ticket, move.destination)
								: LogEntry.hidden(move.ticket);
						updatedLog = ImmutableList.<LogEntry>builder().addAll(log).add(entry).build();

						updatedRemaining.clear();
						for (Player d : detectives) updatedRemaining.add(d.piece());
					} else {
						for (int i = 0; i < updatedDetectives.size(); i++) {
							if (updatedDetectives.get(i).piece() == move.commencedBy()) {
								updatedDetectives.set(i, updatedPlayer);
								break;
							}
						}
						updatedMrX = mrX.give(move.ticket);
						updatedRemaining.remove(move.commencedBy());

						// Check if any remaining detectives can still move
						boolean detectivesCanMove = false;
						for (Player d : updatedDetectives) {
							if (updatedRemaining.contains(d.piece())) {
								if (!makeSingleMoves(setup, updatedDetectives, d, d.location()).isEmpty()) {
									detectivesCanMove = true;
									break;
								}
							}
						}

						if (!detectivesCanMove) {
							updatedRemaining.clear();
							updatedRemaining.add(MrX.MRX);
						}
					}

					if (updatedRemaining.isEmpty()) {
						updatedRemaining.add(MrX.MRX);
					}

					return new MyGameState(setup, ImmutableSet.copyOf(updatedRemaining), updatedLog, updatedMrX, updatedDetectives);
				}

				@Override
				public GameState visit(Move.DoubleMove move) {
					Player nextMrX = mrX.at(move.destination2).use(move.ticket1).use(move.ticket2).use(Ticket.DOUBLE);

					boolean firstReveal = setup.moves.get(log.size());
					boolean secondReveal = setup.moves.get(log.size() + 1);

					LogEntry logOne = firstReveal ? LogEntry.reveal(move.ticket1, move.destination1) : LogEntry.hidden(move.ticket1);
					LogEntry logTwo = secondReveal ? LogEntry.reveal(move.ticket2, move.destination2) : LogEntry.hidden(move.ticket2);

					ImmutableList<LogEntry> nextLog = ImmutableList.<LogEntry>builder().addAll(log).add(logOne).add(logTwo).build();

					Set<Piece> updatedRemaining = new HashSet<>();
					for (Player d : detectives) {
						updatedRemaining.add(d.piece());
					}

					return new MyGameState(setup, ImmutableSet.copyOf(updatedRemaining), nextLog, nextMrX, detectives);
				}
			});
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
			return Optional.of(new TicketBoard() {   // ]ANONYMOUS CLASS
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
			// 1. A detective has landed on MrX — all detectives win
			for (Player d : detectives) {
				if (d.location() == mrX.location()) {
					ImmutableSet.Builder<Piece> win = ImmutableSet.builder();
					for (Player x : detectives) {
						win.add(x.piece());
					}
					return win.build();
				}
			}

			// 2. All detectives are stuck (no moves available for any of them) — MrX wins
			boolean allDetectivesStuck = true;
			for (Player d : detectives) {
				if (!makeSingleMoves(setup, detectives, d, d.location()).isEmpty()) {
					allDetectivesStuck = false;
					break;
				}
			}
			if (allDetectivesStuck) {
				return ImmutableSet.of(MrX.MRX);
			}

			// 3. MrX has filled the travel log and it's his turn — MrX wins
			if (log.size() == setup.moves.size() && remaining.contains(MrX.MRX)) {
				return ImmutableSet.of(MrX.MRX);
			}
			if (remaining.contains(MrX.MRX)) {
				boolean mrXCanMove = !makeSingleMoves(setup, detectives, mrX, mrX.location()).isEmpty()
						|| (mrX.has(Ticket.DOUBLE) && (setup.moves.size() - log.size() >= 2)
						&& !makeDoubleMoves(setup, detectives, mrX, mrX.location()).isEmpty());
				if (!mrXCanMove) {
					ImmutableSet.Builder<Piece> win = ImmutableSet.builder();
					for (Player x : detectives) {
						win.add(x.piece());
					}
					return win.build();
				}
			}

			// No winner yet
			return ImmutableSet.of();
		}

		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			// If the game is already over, no moves are available
			if (!getWinner().isEmpty()) return ImmutableSet.of();

			HashSet<Move> moves = new HashSet<>();
			for (Piece piece : this.remaining) {
				Player activePlayer = null;
				if (piece.isMrX()) {
					activePlayer = this.mrX;
				}
				else {
					for (Player d : this.detectives) {
						if (d.piece() == piece) {
							activePlayer = d;
							break;
						}
					}
				}
				moves.addAll(makeSingleMoves(this.setup, this.detectives, activePlayer, activePlayer.location()));
				if (activePlayer.isMrX() && activePlayer.has(Ticket.DOUBLE)   &&   (setup.moves.size() - log.size() >= 2)) {
					moves.addAll(makeDoubleMoves(this.setup, this.detectives, activePlayer, activePlayer.location()));
				}
			}
			return ImmutableSet.copyOf(moves);
		}

		private static Set<DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
			HashSet<DoubleMove> availableDoubleMoves = new HashSet<>();
			Set<SingleMove> firstMoves = makeSingleMoves(setup, detectives, player, source); // Move1
			for (SingleMove move1 : firstMoves) {
				Set<SingleMove> secondMoves = makeSingleMoves(setup, detectives, player, move1.destination);  // Move2
				for (SingleMove move2 : secondMoves) {
					if (move1.ticket == move2.ticket) {
						if (player.hasAtLeast(move1.ticket, 2)) {
							availableDoubleMoves.add(new DoubleMove(player.piece(), source, move1.ticket, move1.destination, move2.ticket, move2.destination));
						}
					} else {
						availableDoubleMoves.add(new DoubleMove(player.piece(), source, move1.ticket, move1.destination, move2.ticket, move2.destination));
					}
				}
			}
			return availableDoubleMoves;
		}

		private static Set<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
			HashSet<SingleMove> availableMoves = new HashSet<>();
			for (int destination : setup.graph.adjacentNodes(source)) {
				boolean isOccupied = false;
				for (Player d : detectives) {
					if (d.location() == destination) {
						isOccupied = true;
						break;
					}
				}
				if (isOccupied) continue;

				for (Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
					if (player.has(t.requiredTicket())) {
						availableMoves.add(new SingleMove(player.piece(), source, t.requiredTicket(), destination));
					}
				}
				// One secret move per valid destination (outside transport loop to avoid duplicates)
				if (player.has(Ticket.SECRET)) {
					availableMoves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
				}
			}
			return ImmutableSet.copyOf(availableMoves);
		}
	}

	/**
	 * cw-model
	 * Stage 1: Complete this class
	 */
	@Nonnull @Override public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
	}
}