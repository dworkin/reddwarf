package com.sun.gi.apps.battleboard.server;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

public class Game implements SimChannelListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected String     gameName;
    protected ChannelID  channel;
    protected GLOReference               thisRef;
    protected LinkedList<GLOReference>   players;
    protected Map<String, GLOReference>  playerBoards;
    protected GLOReference               currentPlayerRef;

    protected static int DEFAULT_BOARD_WIDTH  = 8;
    protected static int DEFAULT_BOARD_HEIGHT = 8;
    protected static int DEFAULT_BOARD_CITIES = 8;

    public static GLOReference create(SimTask task,
	    Collection<GLOReference> players) {

	Game game = new Game(task, players);

	// queue a new task to handle game start
	try {
	    task.queueTask(game.thisRef,
		Game.class.getMethod("init", SimTask.class),
		new Object[] { });
	} catch (Exception e) {
	    e.printStackTrace();
	}

	return game.thisRef;
    }

    protected Game(SimTask task, Collection<GLOReference> newPlayers) {
	// XXX store and increment a next-channel-number in the GLO,
	// instead of using the current time(?) -jm
	gameName = "BB-" + System.currentTimeMillis();

	log.finer("Next game channel is `" + gameName + "'");

	thisRef = task.createGLO(this, gameName);

	players = new LinkedList(newPlayers);
	Collections.shuffle(players);

	playerBoards = new HashMap<String, GLOReference>();
	for (GLOReference playerRef : players) {
	    Player p = (Player) playerRef.get(task);
	    playerBoards.put(p.getNickname(),
		createBoard(task, p.getNickname()));
	    p.setGameRef(thisRef);
	}

	channel = task.openChannel(gameName);
	task.lock(channel, true);
	task.addChannelListener(channel, thisRef);
    }

    protected GLOReference createBoard(SimTask task, String playerName) {
	Board board =
	    new Board(playerName,
		DEFAULT_BOARD_WIDTH,
		DEFAULT_BOARD_HEIGHT,
		DEFAULT_BOARD_CITIES);
	board.populate();
	return task.createGLO(board);
    }

    public void init(SimTask task) {
	log.info("Running Game.init");
	sendJoinOK(task);
	sendTurnOrder(task);
	startNextMove(task);
    }

    protected void sendJoinOK(SimTask task) {
	for (GLOReference playerRef : players) {
	    Player p = (Player) playerRef.peek(task);
	    task.join(p.getUID(), channel);
	    sendJoinOK(task, p);
	}
    }
    
    protected void sendJoinOK(SimTask task, Player player) {
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("ok ".getBytes());

	GLOReference boardRef = playerBoards.get(player.getNickname());
	Board board = (Board) boardRef.peek(task);

	buf.put(Integer.toString(board.getWidth()).getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(board.getHeight()).getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(board.getStartCities()).getBytes());

	for (int i = 0; i < board.getWidth(); ++i) {
	    for (int j = 0; j < board.getHeight(); ++j) {
		if (board.getBoardPosition(i, j) == Board.POS_CITY) {
		    buf.put(" ".getBytes());
		    buf.put(Integer.toString(i).getBytes());
		    buf.put(" ".getBytes());
		    buf.put(Integer.toString(j).getBytes());
		}
	    }
	}

	task.sendData(channel, new UserID[] { player.getUID() },
	    buf.asReadOnlyBuffer(), true);
    }

    protected void sendTurnOrder(SimTask task) {
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("turn-order".getBytes());
	for (GLOReference playerRef : players) {
	    Player p = (Player) playerRef.peek(task);
	    buf.put(" ".getBytes());
	    buf.put(p.getNickname().getBytes());
	}
	broadcast(task, buf.asReadOnlyBuffer());
    }

    protected void broadcast(SimTask task, ByteBuffer buf) {
	UserID[] uids = new UserID[players.size()];
	int i = 0;
	for (GLOReference playerRef : players) {
	    Player p = (Player) playerRef.peek(task);
	    uids[i++] = p.getUID();
	}
	log.info("Game: Broadcasting " + buf.position() +
	    " bytes on " + channel);
	task.sendData(channel, uids, buf, true);
    }

    protected void sendMoveStarted(SimTask task, Player player) {
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("move-started ".getBytes());
	buf.put(player.getNickname().getBytes());
	broadcast(task, buf.asReadOnlyBuffer());
    }

    protected void startNextMove(SimTask task) {
	log.info("Running Game.startNextMove");

	currentPlayerRef = players.removeFirst();
	players.addLast(currentPlayerRef);
	Player p = (Player) currentPlayerRef.peek(task);
	sendMoveStarted(task, p);
    }

    protected void handlePass(SimTask task, Player player) {
	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("move-ended ".getBytes());
	buf.put(player.getNickname().getBytes());
	buf.put(" pass".getBytes());
	broadcast(task, buf.asReadOnlyBuffer());

	startNextMove(task);
    }

    protected void handleMove(SimTask task, Player player, String[] tokens) {

	String bombedPlayerNick = tokens[1];

	GLOReference boardRef = playerBoards.get(bombedPlayerNick);
	if (boardRef == null) {
	    log.warning(player.getNickname() +
		    " tried to bomb non-existant player " +
		    bombedPlayerNick);
	    handlePass(task, player);
	    return;

	}
	Board board = (Board) boardRef.get(task);

	int x = Integer.parseInt(tokens[2]);
	int y = Integer.parseInt(tokens[3]);

	// XXX check that x and y are in bounds

	int result = board.bombBoardPosition(x, y);

	String outcome = "";
	switch (result) {
	case Board.HIT:
	    outcome = board.lost() ? "LOST" : "HIT";
	    break;

	case Board.NEAR_MISS:
	    outcome = "NEAR_MISS";
	    break;

	case Board.MISS:
	    outcome = "MISS";
	    break;
	}

	// XXX if board.lost(), remove them from the active players list
	// (or something)

	ByteBuffer buf = ByteBuffer.allocate(1024);
	buf.put("move-ended ".getBytes());
	buf.put(player.getNickname().getBytes());
	buf.put(" bomb ".getBytes());
	buf.put(bombedPlayerNick.getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(x).getBytes());
	buf.put(" ".getBytes());
	buf.put(Integer.toString(y).getBytes());
	buf.put(" ".getBytes());
	buf.put(outcome.getBytes());
	buf.put(" ".getBytes());

	broadcast(task, buf.asReadOnlyBuffer());

	startNextMove(task);
    }

    protected void handleResponse(SimTask task, GLOReference playerRef,
	    String[] tokens) {

	if (! playerRef.equals(currentPlayerRef)) {
	    log.severe("PlayerRef != CurrentPlayerRef");
	    return;
	}

	Player player = (Player) playerRef.peek(task);
	String cmd = tokens[0];

	if ("pass".equals(cmd)) {
	    handlePass(task, player);
	} else if ("move".equals(cmd)) {
	    handleMove(task, player, tokens);
	} else {
	    log.warning("Unknown command `" + cmd + "'");
	    handlePass(task, player);
	}
    }

    /**
     * Handle data that was sent directly to the server.
     */
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.info("Game: Direct data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	log.info("userDataReceived: (" + text + ")");
	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    log.warning("empty message");
	    return;
	}

	GLOReference playerRef = Player.getRef(task, uid);
	// XXX check for null

	handleResponse(task, playerRef, tokens);
    }

    // SimChannelListener methods

    public void joinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Game: User " + uid + " joined channel " + cid);
    }

    public void leftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Game: User " + uid + " left channel " + cid);
    }

}
