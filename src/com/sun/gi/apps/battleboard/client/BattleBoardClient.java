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


public class BattleBoardClient implements ClientConnectionManagerListener {

    // DJE: why is this static, and package scope?

    static Logger log = Logger.getLogger("com.sun.gi.apps.battleboard.client");

    protected BattleBoard board;

    protected ClientConnectionManager mgr;
    protected ClientChannel gameChannel;

    protected BufferedReader reader;
    protected Callback[] validationCallbacks = null;

    protected String myPlayerName = "player";

    protected byte[] serverID = null;

    enum State {
	CONNECTING,
	JOINING_GAME,
	PLAY_AWAIT_TURN_ORDER,
    }

    State state = State.CONNECTING;

    public BattleBoardClient() {
	reader = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
	try {
	    mgr = new ClientConnectionManagerImpl("BattleBoard",
		      new URLDiscoverer(
			  new File("FakeDiscovery.xml").toURI().toURL()));
	    mgr.setListener(this);

	    String[] classNames = mgr.getUserManagerClassNames();

	    mgr.connect(classNames[0]);

	    // DJE: is this where the game play goes?

	} catch (Exception e) {
	    e.printStackTrace();
	    return;
	}
    }

/*
    protected void doServerMessage(String message) {
	ByteBuffer out = ByteBuffer.allocate(message.length());
	out.put(message.getBytes());
	mgr.sendToServer(out,true);

    }

    protected void doMultiDCCMessage(byte[][] targetList, String message) {
	ByteBuffer out = ByteBuffer.allocate(message.length());
	out.put(message.getBytes());
	dccChannel.sendMulticastData(targetList, out, true);

    }

    protected void doDCCMessage(byte[] target, String message) {
	ByteBuffer out = ByteBuffer.allocate(message.length());
	out.put(message.getBytes());
	dccChannel.sendUnicastData(target, out, true);

    }
*/

    protected void showPrompt(String prompt) {
	System.out.print(prompt + ": ");
	System.out.flush();
    }
 
    public void visitNameCallback(NameCallback cb) {
	log.finer("visitNameCallback");
	showPrompt(cb.getPrompt());
	String line = getLine();
        myPlayerName = line;
	cb.setName(line);
    }

    public void visitPasswordCallback(PasswordCallback cb) {
	log.finer("visitPasswordCallback");
	showPrompt(cb.getPrompt());
	cb.setPassword(getLine().toCharArray());
    }

    // ClientConnectionManagerListener methods

    public void validationRequest(Callback[] callbacks) {
	log.info("validationRequest");

	for (Callback cb : callbacks) {
	    try {
		if (cb instanceof NameCallback) {
		    visitNameCallback((NameCallback) cb);
		} else if (cb instanceof PasswordCallback) {
		    visitPasswordCallback((PasswordCallback) cb);
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    } 
	}

	mgr.sendValidationResponse(callbacks);
    }

    protected void sendJoinReq(ClientChannel chan) {
	ByteBuffer data = ByteBuffer.allocate(64);
	data.put("join ".getBytes());
	data.put(myPlayerName.getBytes());
	// XXX how do we find out the serverID?
	//chan.sendUnicastData(serverID, buf, true);
    }

    protected String getLine() {
	try {
	    String line = reader.readLine();
	    return (line == null) ? "" : line;
	} catch (IOException e) {
	    e.printStackTrace();
	    return "";
	}
    }

    public void connected(byte[] myID) {
	log.info("connected");

	switch (state) {
	case CONNECTING:
	    break;

	default:
	    log.warning("connected(): unexpected state " + state);
	    break;
	}
    }

    public void connectionRefused(String message) {
	log.info("connectionRefused");
    }

    public void disconnected() {
	log.info("disconnected");
    }

    public void userJoined(byte[] userID) {
	log.info("userJoined");
    }

    public void userLeft(byte[] userID) {
	log.info("userLeft");
    }

    public void failOverInProgress() {
	log.info("failOverInProgress - client choosing to exit");
	System.exit(1);
    }

    public void reconnected() {
	log.info("reconnected");
    }

    public void channelLocked(String chan, byte[] userID) {
	log.warning("Channel `" + chan + "' is locked");
    }

    public void joinedChannel(final ClientChannel channel) {
	log.info("joinedChannel " + channel.getName());

	if (channel.getName().equals("matchmaker")) {

	    showPrompt("Enter your handle [" + myPlayerName + "]");
	    String line = getLine();
	    if (line.length() > 0) {
                myPlayerName = line;
	    }

	    channel.setListener(new ClientChannelListener(){
		public void playerJoined(byte[] playerID) {
		    log.info("playerJoined on " + channel.getName());
		}

		public void playerLeft(byte[] playerID) {
		    log.info("playerJoined on " + channel.getName());
		}

		public void dataArrived(byte[] uid, ByteBuffer data,
			boolean reliable) {
		    log.info("dataArrived on " + channel.getName());

		    byte[] bytes = new byte[data.remaining()];
		    data.get(bytes);

		    log.info("got matchmaker data `" + bytes + "'");
		}

		public void channelClosed() {
		    log.info("channel " + channel.getName() + " closed");
		}
	    });

	    state = State.JOINING_GAME;
	    sendJoinReq(channel);
	    return;
	}

	// Ok, must be a new game channel we've joined
	if (state == State.JOINING_GAME) {
	    channel.setListener(new BattleBoardPlayer(channel, myPlayerName, board));
	    state = State.PLAY_AWAIT_TURN_ORDER;
	}
    }

    class BattleBoardPlayer implements ClientChannelListener {

	final ClientChannel channel;
	final List<String> playerNames = new LinkedList<String>();
	final Map<String, BattleBoard> playerBoards =
		new HashMap<String, BattleBoard>();

	// XXX:  These are game parameters and MUST be known before
	// the game can begin.  MUST change the constructor to make
	// this explicit.

	final String myName;
	final BattleBoard myBoard;

	public BattleBoardPlayer(ClientChannel chan, String name, BattleBoard board) {
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

	    {
		// XXX: debugging only!  Remove when tested.
		int pos = 0;
		for (String t : tokens) {
		    log.info("\tpos = " + pos++ + " token = " + t);
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

    // main()

    public static void main(String[] args) {
	new BattleBoardClient().run();
    }

}
