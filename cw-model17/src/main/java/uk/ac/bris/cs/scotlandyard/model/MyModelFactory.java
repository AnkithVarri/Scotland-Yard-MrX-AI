package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import jakarta.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull
	@Override
	public Model build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		// Create initial GameState via MyGameStateFactory, then wrap in a Model and return it.
		Board.GameState initialState = new MyGameStateFactory().build(setup, mrX, detectives);
		return new MyModel(initialState);
	}
}
