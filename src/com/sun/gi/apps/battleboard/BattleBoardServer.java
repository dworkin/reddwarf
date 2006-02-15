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
	implements SimBoot, SimUserListener {

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

	// Register for user join/leave
	task.addUserListener(thisRef);
    }

    // SimUserListener methods

    public void userJoined(SimTask task, UserID uid, Subject subject) {
	log.info("User " + uid + " joined server, subject = " + subject);

	Player player = Player.instanceFor(task, uid, subject);
	Matchmaker.addPlayer(task, matchmakerRef, player);
    }

    public void userLeft(SimTask task, UserID uid) {
	log.info("User " + uid + " left server");

	// XXX: Here, we might want to delete the Player object.
	// Or in the future we may want it to persist.

	// FIXME: There's no way to destroy a GLO association in SimTask
	/*
	// workaround:
	long objId = task.getTransaction().lookup(uid.toString());
	try {
	    task.getTransaction().destroy(objId);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	*/
    }
}
