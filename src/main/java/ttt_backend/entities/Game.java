package ttt_backend.entities;

import java.util.HashMap;
import java.util.Optional;

import ttt_backend.exceptions.CannotStartGameException;
import ttt_backend.exceptions.InvalidJoinException;
import ttt_backend.exceptions.InvalidMoveException;

/**
 * 
 * A TTT game, involving 2 players
 * 
 */
public class Game {

	/* unique id of the game */
	private String id;

	/* data structure representing a player, i.e. a user bound to a symbol */
	public record Player(User user, GameSymbolType symbol) {}
	/* the two players */
	private HashMap<GameSymbolType, Player> players;

	public enum GameSymbolType { CROSS, CIRCLE, EMPTY};

	/* grid */
	private GameSymbolType[][] grid;
	private int numFreeCellsLeft;

	public enum GameState { WAITING_PLAYER, PLAYING, FINISHED }

	/* state of the game */
	private GameState state;

	/* current turn */
	private GameSymbolType currentTurn;
	
	/* the winner (if present) of this game */
	private Optional<Player> winner;
		
	/**
	 * 
	 * A game has its own id
	 * 
	 * @param id
	 */
	public Game(String id) {
		this.id = id;
		grid = new GameSymbolType[3][3];
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				grid[y][x] = GameSymbolType.EMPTY;
			}
		}
		players = new HashMap<>();
		state = GameState.WAITING_PLAYER;
		winner = Optional.empty();
	}	

	public String getId() {
		return id;
	}
		
	/**
	 * Join the game, using the specified symbol
	 * 
	 * @param user
	 * @param symbol
	 * @throws InvalidJoinException
	 */
	public void joinGame(User user, GameSymbolType symbol) throws InvalidJoinException {
		if (!state.equals(GameState.WAITING_PLAYER) || players.containsKey(symbol)) {
			throw new InvalidJoinException();
		}	
		players.put(symbol, new Player(user, symbol));
	}

	/**
	 * 
	 * Start the game
	 * 
	 * @throws CannotStartGameException
	 */
	public void start() throws CannotStartGameException {
		if (players.size() == 2) {
			state = GameState.PLAYING;
			numFreeCellsLeft = 9;
			currentTurn = GameSymbolType.CROSS;
		} else {
			throw new CannotStartGameException();
		}
	}
	
	/**
	 * Make a move
	 * 
	 * @param player
	 * @param symbol
	 * @param x
	 * @param y
	 * @throws InvalidMoveException
	 */
	public void makeAmove(User player, GameSymbolType symbol, int x, int y) throws InvalidMoveException {
		if (state.equals(GameState.PLAYING) && symbol.equals(currentTurn)) {
			var p = players.get(symbol);
			if (p.user().equals(player)) {
				if (grid[y][x].equals(GameSymbolType.EMPTY)) {
					grid[y][x] = symbol;
					currentTurn = adversarial(symbol);
					checkState();			
				} else {
					throw new InvalidMoveException();
				}
			} else {
				throw new InvalidMoveException();				
			}
		} else {
			throw new InvalidMoveException();			
		}
	}

	/**
	 * 
	 * Check if the game is ended
	 * 
	 * @return
	 */
	public boolean isGameEnd() {
		return state.equals(GameState.FINISHED);
	}
	
	/**
	 * 
	 * Get the winner of the game
	 * 
	 * @return
	 */
	public Optional<GameSymbolType> getWinner() {
		if (winner.isPresent()) {
			return Optional.of(winner.get().symbol());
		} else {
			return Optional.empty();
		}
	}

	/**
	 * 
	 * Check if the game is tie
	 * 
	 * @return
	 */
	public boolean isTie() {
		return isGameEnd() && winner.isEmpty();
	}
	
	/**
	 * 
	 * Check if both players joined the game
	 * 
	 * @return
	 */
	public boolean bothPlayersJoined() {
		return players.size() == 2;
	}

	
	private void checkState() {
		for (int y = 0; y < 3; y++) {
			if (!grid[y][0].equals(GameSymbolType.EMPTY) && 
				grid[y][0].equals(grid[y][1]) && 
				grid[y][1].equals(grid[y][2])){
					winner = Optional.of(players.get(grid[y][0]));
					state = GameState.FINISHED;
					return;
			}
		}
		for (int x = 0; x < 3; x++) {
			if (!grid[0][x].equals(GameSymbolType.EMPTY) && 	
				grid[0][x].equals(grid[1][x]) && 
				grid[1][x].equals(grid[2][x])){
					winner = Optional.of(players.get(grid[0][x]));
					state = GameState.FINISHED;
					return;
			}
		}
		if (!grid[0][0].equals(GameSymbolType.EMPTY) && grid[0][0].equals(grid[1][1]) && grid[1][1].equals(grid[2][2])){
			winner = Optional.of(players.get(grid[0][0]));
			state = GameState.FINISHED;
			return;
		}
		if (!grid[2][0].equals(GameSymbolType.EMPTY) && grid[2][0].equals(grid[1][1]) && grid[1][1].equals(grid[0][2])){
			winner = Optional.of(players.get(grid[2][0]));
			state = GameState.FINISHED;
			return;
		}
		if (this.numFreeCellsLeft == 0) {
			state = GameState.FINISHED;
		}
	}
	
	private GameSymbolType adversarial(GameSymbolType sym) {
		return sym.equals(GameSymbolType.CIRCLE) ? GameSymbolType.CROSS : GameSymbolType.CIRCLE;
	}
}
