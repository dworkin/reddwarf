package com.sun.gi.apps.battleboard.client;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

// XXX import static?  what's that?
import static java.util.logging.Level.*;

public class BattleBoardPlayer implements ClientChannelListener {

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    private final ClientChannel channel;

    private List<String> playerNames = null;
    private Map<String, BattleBoard> playerBoards = null;
    private String myName;
    private BattleBoard myBoard;
    private boolean youLose = false;

    public BattleBoardPlayer(ClientChannel chan, String name,
	    BattleBoard board) {
	channel = chan;
	myName = name;
	myBoard = board;
    }

    public void playerJoined(byte[] playerID) {
	log.info("playerJoined on " + channel.getName());
    }

    public void playerLeft(byte[] playerID) {
	log.info("playerJoined on " + channel.getName());
    }

    public void dataArrived(byte[] uid, ByteBuffer data, boolean reliable) {

	// XXX: sanity checks?

	log.info("dataArrived on " + channel.getName());

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.info("dataArrived: (" + text + ")");

	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    // XXX: something horrible has happened.
	    // XXX: log it, and move on.
	    return;
	}

	if (log.isLoggable(FINER)) {
	    int pos = 0;
	    for (String t : tokens) {
		log.finer("\tpos = " + pos++ + " token = " + t);
	    }
	}

	playGame(tokens);
    }

    public void channelClosed() {
	log.info("channel " + channel.getName() + " closed");
    }

    public void playGame(String[] tokens) {

	// Lots of potential error checks here: make sure
	// that the list hasn't already been populated, and
	// that the list contains at least two players, and
	// that no player appears more than once in the list.
	//
	// Could also check that the size of the game the
	// player got is the size they asked for.

	String cmd = tokens[0];

	if ("turn-order".equals(cmd)) {
	    setTurnOrder(tokens);
	} else if ("your-move".equals(cmd)) {
	    yourTurn(tokens);
	} else if ("move-started".equals(cmd)) {
	    moveStarted(tokens);
	} else if ("move-ended".equals(cmd)) {
	    moveEnded(tokens);
	} else if ("withdraw".equals(cmd)) {
	    withdraw(tokens);
	}

	if (youLose) {
	    displayMessage("You have lost all of your cities.\n");
	    displayMessage("Goodbye!");
	} else if (playerNames.size() == 1) {
	    displayMessage("YOU WIN!");
	}
    }

    private boolean setTurnOrder(String[] args) {

	if (playerNames != null) {
	    log.info("ERROR: setTurnOrder has already been done");
	    return false;
	}

	if (args.length < 3) {
	    log.info("ERROR: setTurnOrder: " +
		    "incorrect number of args: " + args.length + " != 3");
	    return false;
	}

	playerNames = new LinkedList<String>();
	playerBoards = new HashMap<String, BattleBoard>();

	for (int i = 1; i < args.length; i++) {
	    String playerName = args[i];
	    playerNames.add(playerName);

	    if (myName.equals(playerName)) {
		playerBoards.put(myName, myBoard);
	    } else {
		playerBoards.put(playerName,
			new BattleBoard(playerName,
				myBoard.getWidth(), myBoard.getHeight(),
				myBoard.getStartCities()));
	    }
	}

	displayMessage("Initial Boards:\n");
	displayBoards(null);
	return true;
    }

    private boolean yourTurn(String[] args) {
	if (args.length != 1) {
	    log.info("ERROR: yourTurn: " +
		    "incorrect number of args: " + args.length + " != 1");
	}

	displayMessage("Your move: ");

	// ALL KINDS OF STUFF MISSING HERE
	// get a line, then go on...

	return true;
    }

    private boolean moveStarted(String[] args) {

	if (playerNames == null) {
	    log.info("ERROR: setTurnOrder has not yet been done");
	    return false;
	}

	if (args.length != 1) {
	    log.info("ERROR: yourTurn: " +
		    "incorrect number of args: " + args.length + " != 1");
	    return false;
	}

	String currPlayer = args[1];
	log.info("move-started for " + currPlayer);

	if (playerNames.contains(currPlayer)) {
	    // XXX: bad server.
	}

	displayMessage(currPlayer + " is making a move...\n");
	// displayBoards(currPlayer);
	return true;
    }

    private boolean moveEnded(String[] args) {

	if (playerNames == null) {
	    log.info("ERROR: setTurnOrder has not yet been done");
	    return false;
	}

	if (args.length < 3) {
	    log.info("ERROR: moveEnded: " +
		    "incorrect number of args: " + args.length + " < 3");
	}

	String currPlayer = args[1];
	String action = args[2];

	log.info("move-ended for " + currPlayer);

	if ("pass".equals(action)) {
	    if (args.length != 3) {
		log.info("ERROR: moveEnded: " +
			"incorrect number of args: " + args.length + " != 3");
	    }
	    log.info(currPlayer + " passed");

	    displayMessage(currPlayer + " passed.\n");
	    return true;
	} else if ("bomb".equals(action)) {
	    if (args.length != 7) {
		log.info("ERROR: moveEnded: " +
			"incorrect number of args: " + args.length + " != 7");
	    }

	    String bombedPlayer = args[3];
	    BattleBoard board = playerBoards.get(bombedPlayer);
	    if (board == null) {
		log.info("ERROR: nonexistant player (" + bombedPlayer + ")");
		return false;
	    }

	    int x = Integer.parseInt(args[4]);
	    int y = Integer.parseInt(args[5]);

	    if ((x < 0) || (x >= myBoard.getWidth()) ||
		    (y < 0) || (y >= myBoard.getHeight())) {
		log.info("ERROR: impossible board position " +
			"(" + x + ", " + y + ")");
		return false;
	    }

	    String outcome = args[6];
	    boolean lost = false;

	    log.info(bombedPlayer + " bombed ("
		    + x + ", " + y + ") with outcome " + outcome);
	    displayMessage(currPlayer + " bombed " + bombedPlayer +
		    "at " + x + "," + y + " with outcome " + outcome + "\n");

	    if ("HIT".equals(outcome)) {
		board.update(x, y, BattleBoard.POS_CITY);
		if (bombedPlayer.equals(myName)) {
		    displayMessage("You just lost a city!");
		}
	    } else if ("NEAR".equals(outcome)) {
		board.update(x, y, BattleBoard.POS_NEAR);
	    } else if ("MISS".equals(outcome)) {
		board.update(x, y, BattleBoard.POS_MISS);
	    } else if ("LOSS".equals(outcome)) {
		board.update(x, y, BattleBoard.POS_CITY);
		if (bombedPlayer.equals(myName)) {
		    youLose = true;
		} else {
		    playerNames.remove(bombedPlayer);
		    displayMessage(bombedPlayer + " has lost.\n");
		}
	    }

	    displayBoards(bombedPlayer);
	} else {
	    log.info("ERROR: moveEnded: invalid command");
	    return false;
	}
	return true;
    }

    private boolean withdraw(String[] args) {
	if (playerNames == null) {
	    log.info("ERROR: setTurnOrder has not yet been done");
	    return false;
	}

	if (args.length != 2) {
	    log.info("ERROR: withdraw: " +
		    "incorrect number of args: " + args.length + " != 2");
	    return false;
	}

	String withdrawnPlayer = args[1];
	if (!playerNames.remove(withdrawnPlayer)) {
	    log.info("ERROR: withdraw: " +
		    "nonexistant player (" + withdrawnPlayer + ")");
	    return false;
	} else {
	    log.info(withdrawnPlayer + " has withdrawn");

	    displayMessage(withdrawnPlayer + " has withdrawn.");
	    displayBoards(null);
	}

	return true;
    }

    public boolean lost() {
	return youLose;
    }

    private void displayMessage(String message) {
	System.out.print(message);
	System.out.flush();
    }

    private void displayBoards(String activePlayer) {

	if ((activePlayer != null) && myName.equals(activePlayer)) {
	    System.out.println("========");
	    myBoard.display();
	    System.out.println("========");
	} else {
	    myBoard.display();
	}

	for (String name : playerNames) {
	    if (name.equals(myName)) {
		continue;
	    }

	    if ((activePlayer != null) && name.equals(activePlayer)) {
		System.out.println("========");
		playerBoards.get(name).display();
		System.out.println("========");
	    } else {
		playerBoards.get(name).display();
	    }
	}
    }
}
