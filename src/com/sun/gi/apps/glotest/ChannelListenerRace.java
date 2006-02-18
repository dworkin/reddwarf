/*
 * $Id$
 */

package com.sun.gi.apps.glotest;

import java.nio.ByteBuffer;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import java.util.logging.Logger;

import javax.security.auth.Subject;

public class ChannelListenerRace implements SimBoot, SimUserListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.glotest");

    private GLOReference thisRef;
    private ChannelID    channel;

    // SimBoot methods

    public void boot(SimTask task, boolean firstBoot) {
	log.info("Booting ChannelListenerRace as appID " + task.getAppID());

	// Get a reference to this object as a GLO
	if (firstBoot) {
	    thisRef = task.findGLO("BOOT");
	}

	channel = task.openChannel("ChannelListenerRace");

	// Register for user join/leave
	task.addUserListener(thisRef);
    }

    // SimUserListener methods

    public void userJoined(SimTask task, UserID uid, Subject subject) {

	log.info("Registering newly-created GLO as listener for " + uid);

	Player player = new Player(uid);
	GLOReference ref = task.createGLO(player);
	task.addUserDataListener(uid, ref);

	log.info("Joining " + uid + " to a channel");
	task.join(uid, channel);

	// Give the player's callback time to try to run...
	log.info("Sleeping before we allow the commit");
	try {
	    Thread.sleep(1000);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	log.info("Returning to Simulator");
    }

    public void userLeft(SimTask task, UserID uid) {
	// no-op
    }
}
