package ttt_backend;

import ttt_backend.entities.Game.GameSymbolType;
import ttt_backend.exceptions.InvalidJoinException;
import ttt_backend.exceptions.InvalidMoveException;
import ttt_backend.entities.Game;
import ttt_backend.entities.User;

public interface CommandsInterface {

    User registerUser(final String username);

    Game createNewGame();

    void joinGame(final String userID, final String gameID, final GameSymbolType symbol) throws InvalidJoinException;

    void makeAMove(final String userID, final String gameID, final int x, final int y, final GameSymbolType symbol)
            throws InvalidMoveException;
}
