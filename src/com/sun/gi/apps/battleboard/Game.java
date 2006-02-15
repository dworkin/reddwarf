package com.sun.gi.apps.battleboard;

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
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

public class Game implements SimChannelListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard");

    protected Game() {
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
