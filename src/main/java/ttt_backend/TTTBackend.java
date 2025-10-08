package ttt_backend;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.*;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.StaticHandler;
import ttt_backend.entities.Game;

/**
 *
 * Big raw monolithic back end with no clean (software) architecture
 * 
 * It features a reactive/event-loop control architecture, based on Vert.x
 * 
 * @author aricci
 *
 */
public class TTTBackend extends VerticleBase {

	static Logger logger = Logger.getLogger("[TicTacToe Backend]");

	// static final String TTT_CHANNEL = "ttt-events";

	/* list on ongoing games */
	private HashMap<String, Game> games;

	/* counters to create ids */
	private int gamesIdCount;

	/* port of the endpoint */
	private int port;

	final private UserRepoInterface repo;
	final private HttpServer server;

	public TTTBackend(int port, UserRepoInterface repo, HttpServer server) {
		this.port = port;
		logger.setLevel(Level.INFO);
		this.repo = repo;
		this.server=server;
	}

	public Future<?> start() {
		logger.log(Level.INFO, "TTT Server initializing...");

		gamesIdCount = 0;

		games = new HashMap<>();

		/* configuring API routes */

		// Websocket->Java
		Router router = Router.router(vertx);
		router.route(HttpMethod.POST, "/api/registerUser").handler(this::registerUser);
		router.route(HttpMethod.POST, "/api/createGame").handler(this::createNewGame);
		router.route(HttpMethod.POST, "/api/joinGame").handler(this::joinGame);
		router.route(HttpMethod.POST, "/api/makeAMove").handler(this::makeAMove);

		/* configuring websocket handler */

		/* enabling access to static files (web app page) */

		router.route("/public/*").handler(StaticHandler.create());

		/* start the server */

		var fut = server
				.requestHandler(router)
				.listen(port);

		fut.onSuccess(res -> {
			logger.log(Level.INFO, "TTT Game Server ready - port: " + port);
		});

		return fut;
	}

	/* List of handlers mapping the API */

	/**
	 * 
	 * Register a new user
	 * 
	 * @param context
	 */
	protected void registerUser(RoutingContext context) {
		logger.log(Level.INFO, "RegisterUser request");

		context.request().handler(buf -> {

			/* add the new user */
			JsonObject userInfo = buf.toJsonObject();
			var userName = userInfo.getString("userName");
			var user = this.repo.addUser(userName);

			var reply = new JsonObject();
			reply.put("userId", user.id());
			reply.put("userName", user.name());
			try {
				sendReply(context.response(), reply);
			} catch (Exception ex) {
				sendError(context.response());
			}
		});
	}

	/**
	 * 
	 * Create a New Game
	 * 
	 * @param context
	 */
	protected void createNewGame(RoutingContext context) {
		logger.log(Level.INFO, "CreateNewGame request - " + context.currentRoute().getPath());
		gamesIdCount++;
		var newGameId = "game-" + gamesIdCount;
		var game = new Game(newGameId);
		games.put(newGameId, game);
		var reply = new JsonObject();
		reply.put("gameId", newGameId);
		try {
			sendReply(context.response(), reply);
		} catch (Exception ex) {
			sendError(context.response());
		}
	}

	/**
	 * 
	 * Join a Game
	 * 
	 * @param context
	 */
	protected void joinGame(RoutingContext context) {
		logger.log(Level.INFO, "JoinGame request - " + context.currentRoute().getPath());
		context.request().handler(buf -> {
			JsonObject joinInfo = buf.toJsonObject();
			String userId = joinInfo.getString("userId");
			String gameId = joinInfo.getString("gameId");
			String symbol = joinInfo.getString("symbol");
			var gameSym = symbol.equals("cross") ? Game.GameSymbolType.CROSS : Game.GameSymbolType.CIRCLE;
			var user = this.repo.getUserById(userId);
			var game = games.get(gameId);

			var reply = new JsonObject();
			try {
				game.joinGame(user, gameSym);
				reply.put("result", "accepted");
				try {
					sendReply(context.response(), reply);
					logger.log(Level.INFO, "Join succeeded");
				} catch (Exception ex) {
					sendError(context.response());
				}

			} catch (Exception ex) {
				reply.put("result", "denied");
				try {
					sendReply(context.response(), reply);
					logger.log(Level.INFO, "Join failed");
				} catch (Exception ex2) {
					sendError(context.response());
				}
			}
		});
	}

	/**
	 * 
	 * Make a move in a game
	 * 
	 * @param context
	 */
	protected void makeAMove(RoutingContext context) {
		logger.log(Level.INFO, "MakeAMove request - " + context.currentRoute().getPath());
		context.request().handler(buf -> {
			var reply = new JsonObject();
			try {
				JsonObject moveInfo = buf.toJsonObject();
				logger.log(Level.INFO, "move info: " + moveInfo);

				String userId = moveInfo.getString("userId");
				String gameId = moveInfo.getString("gameId");
				String symbol = moveInfo.getString("symbol");
				int x = Integer.parseInt(moveInfo.getString("x"));
				int y = Integer.parseInt(moveInfo.getString("y"));

				var gameSym = symbol.equals("cross") ? Game.GameSymbolType.CROSS : Game.GameSymbolType.CIRCLE;
				var user = this.repo.getUserById(userId);
				var game = games.get(gameId);

				game.makeAmove(user, gameSym, x, y);
				reply.put("result", "accepted");
				try {
					sendReply(context.response(), reply);
				} catch (Exception ex) {
					sendError(context.response());
				}

				/* notifying events */

				var eb = vertx.eventBus();

				/* about the new move */

				var evMove = new JsonObject();
				evMove.put("event", "new-move");
				evMove.put("x", x);
				evMove.put("y", y);
				evMove.put("symbol", symbol);

				/* the event is notified on the event bus 'address' of the specific game */

				var gameAddress = getBusAddressForAGame(gameId);
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

			} catch (Exception ex) {
				reply.put("result", "invalid-move");
				try {
					sendReply(context.response(), reply);
				} catch (Exception ex2) {
					sendError(context.response());
				}
			}
		});
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

	/* Aux methods */

	private void sendReply(HttpServerResponse response, JsonObject reply) {
		response.putHeader("content-type", "application/json");
		response.end(reply.toString());
	}

	private void sendError(HttpServerResponse response) {
		response.setStatusCode(500);
		response.putHeader("content-type", "application/json");
		response.end();
	}

	static final int BACKEND_PORT = 8080;

	/**
	 * 
	 * Main method to launch the backend.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		var vertx = Vertx.vertx();
		var server = vertx.createHttpServer();
		var backend = new TTTBackend(BACKEND_PORT, new JsonDAO(), server);
		var webSocketAcceptor = new WebSocketAcceptor(server, backend);
		vertx.deployVerticle(backend);
	}

}
