/*
 * $Id$
 */

package com.sun.gi.apps.battleboard;

import java.nio.ByteBuffer;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimChannelListener;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

import java.util.logging.Logger;

import javax.security.auth.Subject;

public class BattleBoardServer
	implements SimBoot, SimUserListener, SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.battleboard");

    protected GLOReference thisRef;
    protected GLOReference matchmakerRef;

    // SimBoot methods

    public void boot(SimTask task, boolean firstBoot) {
	log.info("Booting BattleBoard Server as appID " + task.getAppID());

	// Get a reference to this object as a GLO
	if (firstBoot) {
	    thisRef = task.findGLO("BOOT");
	    matchmakerRef = Matchmaker.instance(task);
	}

	// Register for direct user events (join/leave, sendToServer)
	task.addUserListener(thisRef);
    }

    // SimUserListener methods

    public void userJoined(SimTask task, UserID uid, Subject subject) {
	log.info("User " + uid + " joined server, subject = " + subject);

	Player.instance(task, uid);
    }

    public void userLeft(SimTask task, UserID uid) {
	log.info("User " + uid + " left server");

	// XXX: For now, delete the Player GLO -- but
	//      in the future we may want it to persist.

	// FIXME: There's no way to destroy a GLO association in SimTask

	long objId = task.getTransaction().lookup(uid.toString());
	try {
	    task.getTransaction().destroy(objId);
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    // SimUserDataListener methods

    public void userJoinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " joined channel " + cid);
    }

    public void userLeftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " left channel " + cid);
    }

    /**
     * Dispatch direct-to-server messages to the appropriate handler object.
     */
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.warning("data from user " + uid);

	// Dispatch to the user's game, if any
	Player player =
	    (Player) task.findGLO(uid.toString()).peek(task);

	GLOReference gameRef = player.game();
	if (gameRef != null) {
	    Game game = (Game) gameRef.get(task);
	    game.userDataReceived(task, uid, data);
	} else {
	    // If no game, dispatch to the matchmaker
	    Matchmaker mm = (Matchmaker) matchmakerRef.get(task);
	    mm.userDataReceived(task, uid, data);
	}
    }
}
