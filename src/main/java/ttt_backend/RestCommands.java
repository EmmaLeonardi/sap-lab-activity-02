package ttt_backend;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import ttt_backend.entities.Game;
import ttt_backend.exceptions.InvalidJoinException;

public class RestCommands extends VerticleBase {
    static Logger logger = Logger.getLogger("[RestCommands]");
    private final CommandsInterface backend;
    private final Vertx vertx;
    private final int port;
    private final HttpServer server;

    public RestCommands(final Vertx vertx, final CommandsInterface backend, final HttpServer server, final int httpPort) {
        this.vertx = vertx;
        this.backend = backend;
        this.server = server;
        this.port = httpPort;
    }

    public Future<?> start() {
        logger.log(Level.INFO, "Rest Commands initializing...");
        var router = Router.router(this.vertx);
        router.route(HttpMethod.POST, "/api/registerUser").handler(this::registerUser); // Mi dice risorsa non trovata
        router.route(HttpMethod.POST, "/api/createGame").handler(this::createNewGame);
        router.route(HttpMethod.POST, "/api/joinGame").handler(this::joinGame);
        router.route(HttpMethod.POST, "/api/makeAMove").handler(this::makeAMove);
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

    public void registerUser(RoutingContext context) {
        logger.log(Level.INFO, "RegisterUser request");
        context.request().handler(buf -> {
            /* add the new user */
            JsonObject userInfo = buf.toJsonObject();
            var userName = userInfo.getString("userName");
            var user = this.backend.registerUser(userName);
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

    public void createNewGame(RoutingContext context) {
        var game = this.backend.createNewGame();
        var reply = new JsonObject();
        reply.put("gameId", game.getId());
        try {
            sendReply(context.response(), reply);
        } catch (Exception ex) {
            sendError(context.response());
        }
    }

    public void joinGame(RoutingContext context) {
        logger.log(Level.INFO, "JoinGame request - " + context.currentRoute().getPath());
        context.request().handler(buf -> {
            JsonObject joinInfo = buf.toJsonObject();
            String userId = joinInfo.getString("userId");
            String gameId = joinInfo.getString("gameId");
            String symbol = joinInfo.getString("symbol");
            var gameSym = symbol.equals("cross") ? Game.GameSymbolType.CROSS : Game.GameSymbolType.CIRCLE;
            var reply = new JsonObject();
            try {
                this.backend.joinGame(userId, gameId, gameSym);
                reply.put("result", "accepted");
                try {
                    sendReply(context.response(), reply);
                    logger.log(Level.INFO, "Join succeeded");
                } catch (Exception ex) {
                    sendError(context.response());
                }

            } catch (InvalidJoinException e) {
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

    public void makeAMove(RoutingContext context) {
        logger.log(Level.INFO, "MakeAMove request - " + context.currentRoute().getPath());
        context.request().handler(buf -> {
            var reply = new JsonObject();
            try{
            JsonObject moveInfo = buf.toJsonObject();
            logger.log(Level.INFO, "move info: " + moveInfo);

            String userId = moveInfo.getString("userId");
            String gameId = moveInfo.getString("gameId");
            String symbol = moveInfo.getString("symbol");
            int x = Integer.parseInt(moveInfo.getString("x"));
            int y = Integer.parseInt(moveInfo.getString("y"));

            var gameSym = symbol.equals("cross") ? Game.GameSymbolType.CROSS : Game.GameSymbolType.CIRCLE;
            this.backend.makeAMove(userId, gameId, x, y, gameSym);
            reply.put("result", "accepted");
            try {
                    sendReply(context.response(), reply);
                } catch (Exception ex) {
                    sendError(context.response());
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

}
