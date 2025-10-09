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
 * Big raw monolithic back end with no clean (software) architecture
 * 
 * It features a reactive/event-loop control architecture, based on Vert.x
 * 
 * @author aricci
 *
 */
public class TTTBackend implements CommandsInterface {

	static Logger logger = Logger.getLogger("[TicTacToe Backend]");

	// static final String TTT_CHANNEL = "ttt-events";

	/* list on ongoing games */
	private HashMap<String, Game> games;

	/* counters to create ids */
	private int gamesIdCount;

	/* port of the endpoint */
	private final Vertx vertx;

	final private UserRepoInterface repo;

	public TTTBackend(Vertx vertx, UserRepoInterface repo) {
		logger.setLevel(Level.INFO);
		this.repo = repo;
		this.vertx=vertx;
		this.games=new HashMap<>();
	}

	/* List of handlers mapping the API */

	/**
	 * 
	 * Register a new user
	 * 
	 * @param context
	 */
	public User registerUser(String username) {
		return this.repo.addUser(username);
	}

	/**
	 * 
	 * Create a New Game
	 * 
	 * @param context
	 */
	public Game createNewGame() {
		gamesIdCount++;
		var newGameId = "game-" + gamesIdCount;
		var game = new Game(newGameId);
		games.put(newGameId, game);
		return game;
	}

	/**
	 * 
	 * Join a Game
	 * 
	 * @param context
	 */
	public void joinGame(String userId, String gameId, GameSymbolType gameSymbol) throws InvalidJoinException{
		var user = this.repo.getUserById(userId);
		var game = games.get(gameId);
		game.joinGame(user, gameSymbol);
	}

	/**
	 * 
	 * Make a move in a game
	 * 
	 * @param context
	 */
	public void makeAMove(final String userID, final String gameID, final int x, final int y,
			final GameSymbolType symbol) throws InvalidMoveException{
		var user = this.repo.getUserById(userID);
		var game = games.get(gameID);
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

		var gameAddress = getBusAddressForAGame(gameID);
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

	public void subscribeToGameEvents(String gameId, EventListenerInterface listener) {
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
		var game = games.get(gameId);
		if (game.bothPlayersJoined()) {
			try {
				game.start();
				var evGameStarted = new JsonObject();
				evGameStarted.put("event", "game-started");
				eb.publish(gameAddress, evGameStarted);
			} catch (Exception ex) {
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
	private String getBusAddressForAGame(String gameId) {
		return "ttt-events-" + gameId;
	}

}
