/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BattleBoardPlayer implements ClientChannelListener {

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private final ClientChannel channel;
    private final ClientConnectionManager connectionManager;
    private final TextDisplay display;
    private List<String> playerNames = null;
    private List<BattleBoard> playerBoards = null;
    private Map<String, BattleBoard> nameToBoard = null;
    private String myName;
    private BattleBoard myBoard;
    private boolean lost = false;

    /**
     * The game play is in one of several states:  waiting for a board
     * to be provided by the server (NEED_BOARD), waiting for a player
     * list to be provided by the server (NEED_TURN_ORDER), then
     * iterating through turns (BEGIN_MOVE, END_MOVE), until some
     * player has won (GAME_OVER).
     */
    private enum GameState {
	NEED_BOARD,
	NEED_TURN_ORDER,
	BEGIN_MOVE,
	END_MOVE,
	GAME_OVER
    }

    private GameState gameState = GameState.NEED_BOARD;

    public BattleBoardPlayer(ClientConnectionManager connectionManager,
	    ClientChannel chan, String playerName)
    {
	this.connectionManager = connectionManager;
	this.channel = chan;
	this.myName = playerName;
	this.display = new TextDisplay(playerName);
    }

    /**
     * Used only for standalone tests.
     */
    BattleBoardPlayer(String playerName) {
	this(null, null, playerName);
    }

    /**
     * {@inheritDoc}
     */
    public void playerJoined(byte[] playerID) {
	log.fine("playerJoined on " + channel.getName());
    }

    /**
     * {@inheritDoc}
     */
    public void playerLeft(byte[] playerID) {
	log.fine("playerJoined on " + channel.getName());
    }

    /**
     * {@inheritDoc}
     */
    public void dataArrived(byte[] uid, ByteBuffer data, boolean reliable) {

	// XXX: sanity checks?

	log.fine("dataArrived on " + channel.getName());

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.finer("dataArrived: (" + text + ")");

	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    log.warning("empty message");
	    return;
	}

	if (log.isLoggable(Level.FINER)) {
	    int pos = 0;
	    for (String t : tokens) {
		log.finer("\tpos = " + pos++ + " token = " + t);
	    }
	}

	playGame(tokens);
    }

    /**
     * {@inheritDoc}
     */
    public void channelClosed() {
	log.fine("channel " + channel.getName() + " closed");
    }

    /**
     * Performs the game-play for the given array of tokens.
     *
     * @param tokens an array of Strings containing the tokens
     * of the message from the server
     */
    void playGame(String[] tokens) {
	String cmd = tokens[0];

	if ((gameState == GameState.NEED_BOARD) && "ok".equals(cmd)) {
	    gameState = setBoard(tokens);
	} else if ((gameState == GameState.NEED_TURN_ORDER) &&
		"turn-order".equals(cmd)) {
	    gameState = setTurnOrder(tokens);
	} else if ((gameState == GameState.BEGIN_MOVE) &&
		"move-started".equals(cmd)) {
	    if (myName.equals(tokens[1])) {
		gameState = yourTurn();
	    } else {
		gameState = moveStarted(tokens);
	    }
	    gameState = GameState.END_MOVE;
	} else if ((gameState == GameState.END_MOVE) &&
		"move-ended".equals(cmd)) {
	    gameState = moveEnded(tokens);
	} else {
	    log.severe("Illegal game state: cmd " + cmd +
		    " gameState " + gameState);
	}

	/*
	 * If there's only one player left, that last remaining player
	 * is the winner.
	 */
	if (playerBoards != null && playerBoards.size() == 1) {
	    if (myName.equals(playerBoards.get(0).getPlayerName())) {
		display.message("YOU WIN!\n");
	    } else {
		display.message(playerBoards.get(0).getPlayerName() +
			" WINS!\n");
	    }
	    gameState = GameState.GAME_OVER;
	    connectionManager.disconnect();
	    System.exit(0);
	}
    }

    /**
     * Implements the operations for the "ok" message, which tells the
     * user what board the server has chosen for them.
     *
     * @param args an array of Strings containing the tokens of the
     * message from the server
     *
     * @return <code>true</code> if the message was valid and executed
     * correctly, <code>false</code> otherwise
     */
    private GameState setBoard(String[] args) {
	if (args.length < 4) {
	    log.severe("setBoard: incorrect number of arguments");
	    return GameState.NEED_BOARD;
	}

	int boardWidth = (int) new Integer(args[1]);
	int boardHeight = (int) new Integer(args[2]);
	int numCities = (int) new Integer(args[3]);

	if ((boardWidth < 1) || (boardHeight < 1)) {
	    log.severe("bad board dimensions (" +
		    boardWidth + ", " + boardHeight + ")");
	    return GameState.NEED_BOARD;
	}

	if (numCities < 1) {
	    log.severe("bad numCities (" + numCities + ")");
	    return GameState.NEED_BOARD;
	}

	BattleBoard tempBoard = new BattleBoard(myName,
		boardWidth, boardHeight, numCities);

	if (((args.length - 4) % 2) != 0) {
	    log.severe("bad list of city positions");
	    return GameState.NEED_BOARD;
	}

	for (int base = 4; base < args.length; base += 2) {
	    int x = (int) new Integer(args[base]);
	    int y = (int) new Integer(args[base+1]);

	    if ((x < 0) || (x >= boardWidth) || (y < 0) || (y >= boardHeight)) {
		log.severe("improper city position (" + x + ", " + y + ")");
		return GameState.NEED_BOARD;
	    }

	    tempBoard.update(x, y, BattleBoard.PositionValue.CITY);
	}
	myBoard = tempBoard;

	return GameState.NEED_TURN_ORDER;
    }

    /**
     * Implements the operations for the "turn-order" message, which
     * tells the player what the order of turns will be among the
     * players.
     *
     * @param args an array of Strings containing the tokens of the
     * message from the server
     *
     * @return <code>BEGIN_MOVE</code> if the message was valid and
     * executed correctly and moves may begin,,
     * <code>NEED_TURN_ORDER</code> otherwise
     */
    private GameState setTurnOrder(String[] args) {

	if (playerBoards != null) {
	    log.severe("setTurnOrder has already been done");
	    return GameState.NEED_TURN_ORDER;
	}

	if (args.length < 3) {
	    log.severe("setTurnOrder: " +
		    "incorrect number of args: " + args.length + " != 3");
	    return GameState.NEED_TURN_ORDER;
	}

	playerNames = new LinkedList<String>();
	playerBoards = new LinkedList<BattleBoard>();
	nameToBoard = new HashMap<String, BattleBoard>();

	for (int i = 1; i < args.length; i++) {
	    String playerName = args[i];
	    playerNames.add(playerName);

	    if (myName.equals(playerName)) {
		playerBoards.add(myBoard);
		nameToBoard.put(myName, myBoard);
	    } else {
		BattleBoard newBoard = new BattleBoard(playerName,
			myBoard.getWidth(), myBoard.getHeight(),
			myBoard.getStartCities());
		playerBoards.add(newBoard);
		nameToBoard.put(playerName, newBoard);
	    }
	}

	return GameState.BEGIN_MOVE;
    }

    /**
     * Implements the operations for the "move-started" message, for
     * the player whose move it is.
     *
     * @return <code>END_MOVE</code> if the move was executed
     * correctly, <code>BEGIN_MOVE</code> otherwise
     */
    private GameState yourTurn() {
	display.showBoards(playerBoards, myName);
	display.message("Your move!\n");

	for (;;) {
	    String[] move = BattleBoardUtils.getKeyboardInputTokens(
			"player x y, or pass ");

	    if ((move.length == 1) && "pass".equals(move[0])) {
		ByteBuffer buf = ByteBuffer.wrap("pass".getBytes());
		buf.position(buf.limit());
		connectionManager.sendToServer(buf, true);
		break;
	    } else if (move.length == 3) {
		String bombedPlayer = move[0];
		if (!playerNames.contains(bombedPlayer)) {
		    display.message("Error: player (" + bombedPlayer +
			    ") is not in the game\n");
		    display.message("Please try again.\n");
		    continue;
		}

		int x = (int) new Integer(move[1]);
		int y = (int) new Integer(move[2]);

		if ((x < 0) || (x >= myBoard.getWidth()) ||
			(y < 0) || (y >= myBoard.getHeight())) {
		    display.message("Illegal (x,y)\n");
		    display.message("Please try again.\n");
		    continue;
		}

		String moveMessage = "move " + bombedPlayer + " " +
			x + " " + y;

		ByteBuffer buf = ByteBuffer.wrap(moveMessage.getBytes());
		buf.position(buf.limit());
		connectionManager.sendToServer(buf, true);
		break;
	    } else {
		display.message(
			"Improperly formatted move.  Please try again.\n");
	    }

	    return GameState.END_MOVE;
	}

	return GameState.END_MOVE;
    }

    /**
     * Implements the operations for the "move-started" message, for
     * any player whose move it is not.
     *
     * @param args an array of Strings containing the tokens of the
     * message from the server
     *
     * @return <code>END_MOVE</code> if the move was executed
     * correctly, <code>BEGIN_MOVE</code> otherwise
     */
    private GameState moveStarted(String[] args) {

	if (args.length != 2) {
	    log.severe("moveStarted: " +
		    "incorrect number of args: " + args.length + " != 2");
	    return GameState.BEGIN_MOVE;
	}

	String currPlayer = args[1];
	log.fine("move-started for " + currPlayer);

	if (!playerNames.contains(currPlayer)) {
	    log.severe("moveStarted: nonexistant player (" + currPlayer + ")");
	    return GameState.BEGIN_MOVE;
	}

	display.showBoards(playerBoards, currPlayer);
	display.message(currPlayer + " is making a move...\n");
	return GameState.END_MOVE;
    }

    /**
     * Implements the operations for the "move-ended" message.
     *
     * @param args an array of Strings containing the tokens of the
     * message from the server
     *
     * @return <code>BEGIN_MOVE</code> if the move was executed
     * correctly, <code>END_MOVE</code> otherwise
     */
    private GameState moveEnded(String[] args) {

	if (args.length < 3) {
	    log.severe("moveEnded: " +
		    "incorrect number of args: " + args.length + " < 3");
	    return GameState.END_MOVE;
	}

	String currPlayer = args[1];
	String action = args[2];

	log.fine("move-ended for " + currPlayer);

	if ("pass".equals(action)) {
	    if (args.length != 3) {
		log.severe("moveEnded: " +
			"incorrect number of args: " + args.length + " != 3");
		return GameState.END_MOVE;
	    }
	    log.fine(currPlayer + " passed");

	    display.message(currPlayer + " passed.\n");
	    return GameState.BEGIN_MOVE;
	} else if ("bomb".equals(action)) {
	    if (args.length != 7) {
		log.severe("moveEnded: " +
			"incorrect number of args: " + args.length + " != 7");
		return GameState.END_MOVE;
	    }

	    String bombedPlayer = args[3];
	    BattleBoard board = nameToBoard.get(bombedPlayer);
	    if (board == null) {
		log.severe("nonexistant player (" + bombedPlayer + ")");
		return GameState.END_MOVE;
	    }

	    int x = Integer.parseInt(args[4]);
	    int y = Integer.parseInt(args[5]);

	    if ((x < 0) || (x >= myBoard.getWidth()) ||
		    (y < 0) || (y >= myBoard.getHeight())) {
		log.warning("impossible board position " +
			"(" + x + ", " + y + ")");
		return GameState.END_MOVE;
	    }

	    String outcome = args[6];

	    log.fine(bombedPlayer + " bombed ("
		    + x + ", " + y + ") with outcome " + outcome);
	    display.message(currPlayer + " bombed " + bombedPlayer +
		    " at " + x + "," + y + " with outcome " + outcome + "\n");

	    if ("HIT".equals(outcome) || "LOSS".equals(outcome)) {
		board.update(x, y, BattleBoard.PositionValue.HIT);
		board.hit();

		if ("LOSS".equals(outcome)) {
		    playerBoards.remove(nameToBoard.get(bombedPlayer));
		    playerNames.remove(bombedPlayer);
		    if (bombedPlayer.equals(myName)) {
			display.message("You lose!\n");
			display.message("Better luck next time.\n");
			lost = true;
		    } else {
			display.message(bombedPlayer +
				" lost their last city.\n");
		    }
		} else {
		    if (bombedPlayer.equals(myName)) {
			display.message("You just lost a city!\n");
		    } else {
			display.message(bombedPlayer + " lost a city.\n");
		    }
		}
	    } else if ("NEAR_MISS".equals(outcome)) {
		board.update(x, y, BattleBoard.PositionValue.NEAR);
	    } else if ("MISS".equals(outcome)) {
		board.update(x, y, BattleBoard.PositionValue.MISS);
	    }

	    display.showBoards(playerBoards, bombedPlayer);
	} else {
	    log.severe("moveEnded: invalid command");
	    return GameState.END_MOVE;
	}

	return GameState.BEGIN_MOVE;
    }

    /**
     * Implements the operations for the "withdraw" message. <p>
     *
     * <em>This method is not used in the current game.</em>
     *
     * @param args an array of Strings containing the tokens of the
     * message from the server
     *
     * @return <code>true</code> if the move was executed correctly,
     * <code>false</code> otherwise
     */
    private boolean withdraw(String[] args) {
	if (playerBoards == null) {
	    log.severe("setTurnOrder has not yet been done");
	    return false;
	}

	if (args.length != 2) {
	    log.severe("withdraw: incorrect number of args: " +
		    args.length + " != 2");
	    return false;
	}

	String withdrawnPlayer = args[1];
	if (!playerNames.remove(withdrawnPlayer)) {
	    log.warning("withdraw: nonexistant player (" +
		    withdrawnPlayer + ")");
	    return false;
	} else {
	    log.fine(withdrawnPlayer + " has withdrawn");

	    display.showBoards(playerBoards, null);
	    display.message(withdrawnPlayer + " has withdrawn.");
	}

	return true;
    }

    /**
     * Returns <code>true</code> if this player has lost the game,
     * <code>false</code> otherwise.
     *
     * @return <code>true</code> if this player has lost the game,
     * <code>false</code> otherwise
     */
    public boolean lost() {
	return lost;
    }
}
