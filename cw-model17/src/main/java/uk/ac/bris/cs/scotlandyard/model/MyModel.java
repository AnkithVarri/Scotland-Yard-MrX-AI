package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableSet;

import jakarta.annotation.Nonnull;

/**
 * Minimal stub so that GameStateGameOverTest can run (Model factory compiles).
 */
public final class MyModel implements Model {
	private Board.GameState state;
	private final ImmutableSet<Observer> observers = ImmutableSet.of();

	public MyModel(Board.GameState initialState) {
		this.state = initialState;
	}

	@Nonnull @Override public Board getCurrentBoard() { return state; }
	@Override public void registerObserver(@Nonnull Observer observer) {}
	@Override public void unregisterObserver(@Nonnull Observer observer) {}
	@Nonnull @Override public ImmutableSet<Observer> getObservers() { return observers; }
	@Override public void chooseMove(@Nonnull Move move) {
		state = state.advance(move);
	}
}
