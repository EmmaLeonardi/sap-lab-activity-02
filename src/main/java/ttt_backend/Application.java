package ttt_backend;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;

public class Application extends VerticleBase {
	public static final int HTTP_PORT = 8080;

	public Future<?> start() {
		var server = vertx.createHttpServer();
		var backend = new TTTBackend(vertx, new JsonDAO());
		var restCommands = new RestCommands(vertx, backend, server, HTTP_PORT);
		var webSocketAcceptor = new WebSocketAcceptor(server, backend);
		return restCommands.start();
	}

	/**
	 * 
	 * Main method to launch the backend.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		var vertx = Vertx.vertx();
		vertx.deployVerticle(new Application());
	}

}
