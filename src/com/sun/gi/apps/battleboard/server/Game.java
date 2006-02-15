package com.sun.gi.apps.battleboard.server;

import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class Game implements SimChannelListener, GLO {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected String    gameName;
    protected ChannelID channel;
    protected List<GLOReference> players;

    public static GLOReference create(SimTask task,
	    Collection<GLOReference> players) {

	Game game = new Game(task, players);
	GLOReference gameRef = task.createGLO(game, game.gameName);
	task.addChannelListener(game.channel, gameRef);

	// XXX queue a new task to let the game do these things...
	game.sendJoinOK(task);
	game.sendTurnOrder(task);
	// XXX ... have the game start running ...

	return gameRef;
    }

    protected Game(SimTask task, Collection<GLOReference> newPlayers) {
	// XXX store and increment a next-channel-number in the GLO,
	// instead of using the current time(?) -jm
	gameName = "BB-" + System.currentTimeMillis();

	log.finer("Next game channel is `" + gameName + "'");

	players = new ArrayList(newPlayers);
	Collections.shuffle(players);

	channel = task.openChannel(gameName);
	task.lock(channel, true);

    }

    protected void sendJoinOK(SimTask task) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("ok ".getBytes());

	for (GLOReference playerRef : players) {
	    Player p = (Player) playerRef.peek(task);
	    task.join(p.getUID(), channel);
	    task.sendData(channel, new UserID[] { p.getUID() },
		buf.duplicate(), true);
	}
    }

    protected void sendTurnOrder(SimTask task) {
	// TODO
    }

    /**
     * Handle data that was sent directly to the server.
     */
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.info("Game: Direct data from user " + uid);
    }

    // SimChannelListener methods

    /**
     * Handle client-to-client data on this channel.
     *
     * Note that Battleboard doesn't expect any client-to-client
     * communication on a channel (yet).
     */
    public void dataArrived(SimTask task, ChannelID cid,
	    UserID uid, ByteBuffer data) {

	log.info("Game: Unexpected client-to-client data from user " + uid
	    + " on channel " + cid);
    }
}
