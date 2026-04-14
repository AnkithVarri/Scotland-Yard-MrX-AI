package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nonnull;

import jdk.jfr.Event;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.units.qual.A;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import javax.swing.plaf.nimbus.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class 	MyModelFactory implements Factory<Model> {

	@Nonnull @Override
	public Model build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		return new MyModel(setup, mrX, detectives);
	}
	private final class MyModel implements Model {


		// 3. Put your fields here
		private Board.GameState currentState;
		private final List<Model.Observer> observers = new ArrayList<>();

		// 4. Create a constructor to set up the initial state
		private MyModel(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
			this.currentState = new MyGameStateFactory().build(setup, mrX, detectives);
			// TODO: You need to use your GameStateFactory to generate the very first GameState here
		}

		@Override
		public @NonNull Board getCurrentBoard() {
			return this.currentState;
		}

		@Override
		public void registerObserver(@NonNull Observer observer) {
			if(observer == null) {
				throw new NullPointerException("The observer cannot be null");
			}
			if(this.observers.contains(observer)) {
				throw new IllegalArgumentException("There can only be one observer");
			}
			observers.add(observer);
		}

		@Override
		public void unregisterObserver(@NonNull Observer observer) {
			if(observer == null) {
				throw new NullPointerException("The observer cannot be null");
			}
			if(!this.observers.contains(observer)) {
				throw new IllegalArgumentException("The observer is not in the list");
			}
			observers.remove(observer);

		}

		@Override
		public @NonNull ImmutableSet<Observer> getObservers() {
			return ImmutableSet.copyOf(observers);
		}
		@Override
		public void chooseMove(@Nonnull Move move) {
			Observer.Event event;
			this.currentState = this.currentState.advance(move);
			if (this.currentState.getWinner().isEmpty()) {
				event = Observer.Event.MOVE_MADE;
			}
			else {
				event = Observer.Event.GAME_OVER;
			}
			for(Observer observer : this.observers) {
				observer.onModelChanged(getCurrentBoard(), event);
			}

		}
	}
}
