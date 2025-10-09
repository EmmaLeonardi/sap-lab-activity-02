package ttt_backend;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.*;
import ttt_backend.entities.Game;
import ttt_backend.entities.User;
import ttt_backend.entities.Game.GameSymbolType;
import ttt_backend.exceptions.InvalidJoinException;
import ttt_backend.exceptions.InvalidMoveException;

/**
 *
 * Back end with some clean (software) architecture
 * 
 * @author emma.leonardi2
 *
 */
public class TTTBackend implements CommandsInterface {

	private static final Logger logger = Logger.getLogger("[TicTacToe Backend]");

	/* list on ongoing games */
	private final HashMap<String, Game> games;

	/* counters to create ids */
	private int gamesIdCount;

	/* port of the endpoint */
	private final Vertx vertx;

	private final UserRepoInterface repo;

	public TTTBackend(final Vertx vertx, final UserRepoInterface repo) {
		logger.setLevel(Level.INFO);
		this.repo = repo;
		this.vertx = vertx;
		this.games = new HashMap<>();
	}

	/* List of handlers mapping the API */

	/**
	 * 
	 * Register a new user
	 * 
	 * @param context
	 */
	public User registerUser(final String username) {
		return this.repo.addUser(username);
	}

	/**
	 * 
	 * Create a New Game
	 * 
	 * @param context
	 */
	public Game createNewGame() {
		this.gamesIdCount++;
		var newGameId = "game-" + gamesIdCount;
		var game = new Game(newGameId);
		this.games.put(newGameId, game);
		return game;
	}

	/**
	 * 
	 * Join a Game
	 * 
	 * @param context
	 */
	public void joinGame(final String userId, final String gameId, final GameSymbolType gameSymbol)
			throws InvalidJoinException {
		var user = this.repo.getUserById(userId);
		var game = this.games.get(gameId);
		game.joinGame(user, gameSymbol);
	}

	/**
	 * 
	 * Make a move in a game
	 * 
	 * @param context
	 */
	public void makeAMove(final String userID, final String gameID, final int x, final int y,
			final GameSymbolType symbol) throws InvalidMoveException {
		var user = this.repo.getUserById(userID);
		var game = this.games.get(gameID);
		game.makeAmove(user, symbol, x, y);

		/* notifying events */

		var eb = this.vertx.eventBus();

		/* about the new move */

		var evMove = new JsonObject();
		evMove.put("event", "new-move");
		evMove.put("x", x);
		evMove.put("y", y);
		evMove.put("symbol", symbol);

		/* the event is notified on the event bus 'address' of the specific game */

		var gameAddress = this.getBusAddressForAGame(gameID);
		eb.publish(gameAddress, evMove);

		/* a game-ended event is notified too if the game is ended */

		if (game.isGameEnd()) {

			var evEnd = new JsonObject();
			evEnd.put("event", "game-ended");

			if (game.isTie()) {
				evEnd.put("result", "tie");
			} else {
				var sym = game.getWinner().get();
				if (sym.equals(Game.GameSymbolType.CROSS)) {
					evEnd.put("winner", "cross");
				} else {
					evEnd.put("winner", "circle");
				}
			}
			eb.publish(gameAddress, evEnd);
		}

	}

	public void subscribeToGameEvents(final String gameId, final EventListenerInterface listener) {
		EventBus eb = vertx.eventBus();

		var gameAddress = getBusAddressForAGame(gameId);
		eb.consumer(gameAddress, msg -> {
			JsonObject ev = (JsonObject) msg.body();
			logger.log(Level.INFO, "Notifying event to the frontend: " + ev.encodePrettily());
			listener.onEvent(ev.encodePrettily());
		});

		/*
		 * 
		 * When both players joined the game and both
		 * have the websocket connection ready,
		 * the game can start
		 * 
		 */
		var game = this.games.get(gameId);
		if (game.bothPlayersJoined()) {
			try {
				game.start();
				var evGameStarted = new JsonObject();
				evGameStarted.put("event", "game-started");
				eb.publish(gameAddress, evGameStarted);
			} catch (final Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * 
	 * Get the address on the Vert.x event bus
	 * to handle events related to a specific game
	 * 
	 * @param gameId
	 * @return
	 */
	private String getBusAddressForAGame(final String gameId) {
		return "ttt-events-" + gameId;
	}

}
