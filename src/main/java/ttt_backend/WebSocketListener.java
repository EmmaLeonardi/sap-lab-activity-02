package ttt_backend;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.vertx.core.http.ServerWebSocket;

public class WebSocketListener implements EventListenerInterface {
    private final ServerWebSocket webSocket;
    private static Logger logger = Logger.getLogger("[WebSocketListener]");

    public WebSocketListener(ServerWebSocket webSocket) {
        this.webSocket=webSocket;
    }

    @Override
    public void onEvent(final String eventData) {
        logger.log(Level.INFO, "On event "+eventData);
        webSocket.writeTextMessage(eventData);
    }

}
