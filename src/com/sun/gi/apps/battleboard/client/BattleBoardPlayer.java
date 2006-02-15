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
import static java.util.logging.Level.*;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

public class BattleBoardPlayer implements ClientChannelListener {

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.client");

    final ClientChannel channel;
    final List<String> playerNames = new LinkedList<String>();
    final Map<String, BattleBoard> playerBoards =
	new HashMap<String, BattleBoard>();

    final String myName;
    final BattleBoard myBoard;

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
	log.info("dataArrived on " + channel.getName());

	// XXX: error checks?
	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.info(text);

	String[] tokens = text.split("\\s*");
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

    private void playGame(String[] tokens) {

	// Lots of potential error checks here: make sure
	// that the list hasn't already been populated, and
	// that the list contains at least two players, and
	// that no player appears more than once in the list.
	//
	// Could also check that the size of the game the
	// player got is the size they asked for.

	if ("turn-order".equals(tokens[0])) {
	    if (tokens.length < 3) {
		// XXX: bad server.
	    }

	    for (int i = 1; i < tokens.length; i++) {
		String playerName = tokens[i];
		playerNames.add(playerName);

		if (myName.equals(playerName)) {
		    playerBoards.put(myName, myBoard);
		} else {
		    playerBoards.put(playerName, myBoard.clone());
		}
	    }

	    // XXX: initialize the "display"?
	    return;
	}

	if ("your-move".equals(tokens[0])) {
	    if (tokens.length != 1) {
		// XXX: bad server.
	    }

	    // XXX: make your move.
	    return;
	}

	// Lots of potential error checks here:

	if ("move-started".equals(tokens[0])) {
	    if (tokens.length != 1) {
		// XXX: bad server.
	    }

	    String currPlayer = tokens[1];

	    // XXX: Do something.
	    log.info("move-started for " + currPlayer);

	    return;
	}

	if ("move-ended".equals(tokens[0])) {
	    if (tokens.length < 3) {
		// XXX: bad server.
	    }

	    String currPlayer = tokens[1];
	    String action = tokens[2];

	    log.info("move-ended for " + currPlayer);

	    if ("pass".equals(action)) {
		if (tokens.length != 3) {
		    // XXX: bad server.
		}

		// XXX: they're done.  Update display.

		log.info(currPlayer + " passed");

		return;
	    } else if ("bomb".equals(action)) {
		if (tokens.length != 7) {
		    // XXX: bad server.
		}

		String bombedPlayer = tokens[3];
		int x = Integer.parseInt(tokens[4]);
		int y = Integer.parseInt(tokens[5]);
		String result = tokens[6];

		log.info(bombedPlayer + " bombed ("
			+ x + ", " + y + ") with result " + result);
	    } else {
		// XXX: bad server.
	    }

	    return;
	}

	if ("withdraw".equals(tokens[0])) {
	    if (tokens.length != 2) {
		// XXX: bad server.
	    }

	    String withdrawnPlayer = tokens[1];
	    if (!playerNames.remove(withdrawnPlayer)) {
		// XXX: bad server.
	    } else {
		// XXX: update the display list.

		log.info(withdrawnPlayer + " has withdrawn");
	    }

	    return;
	}
    }

    public void channelClosed() {
	log.info("channel " + channel.getName() + " closed");
    }
}
