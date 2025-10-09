package ttt_backend;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

public class WebSocketAcceptor {

    private static Logger logger = Logger.getLogger("[WebSocketAcceptor]");

    public WebSocketAcceptor(final HttpServer server, final TTTBackend backend) {
        server.webSocketHandler(webSocket -> {
            logger.log(Level.INFO, "New TTT subscription accepted.");

            /*
             * 
             * Receiving a first message including the id of the game
             * to observe
             * 
             */
            webSocket.textMessageHandler(openMsg -> {
                logger.log(Level.INFO, "For game: " + openMsg);
                JsonObject obj = new JsonObject(openMsg);
                String gameId = obj.getString("gameId");
                backend.subscribeToGameEvents(gameId, new WebSocketListener(webSocket));
            });
        });

    }

}
