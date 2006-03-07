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

public class ChannelListenerRace
	implements SimBoot<ChannelListenerRace>, SimUserListener
{

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.glotest");

    private ChannelID    channel;

    // SimBoot methods

    public void boot(GLOReference<? extends ChannelListenerRace> thisRef, boolean firstBoot) {
	SimTask task = SimTask.getCurrent();
	log.info("Booting ChannelListenerRace as appID " + task.getAppID());

	channel = task.openChannel("ChannelListenerRace");
	task.addUserListener(thisRef);
    }

    // SimUserListener methods

    public void userJoined(UserID uid, Subject subject) {

	log.fine("Registering newly-created GLO as listener for " + uid);

	SimTask task = SimTask.getCurrent();

	Player player = new Player(uid);
	GLOReference<Player> ref = task.createGLO(player);
	task.addUserDataListener(uid, ref);

	log.fine("Joining " + uid + " to a channel");
	task.join(uid, channel);

	// Give the player's callback time to try to run...
	log.fine("Sleeping before we allow the commit");
	try {
	    Thread.sleep(1000);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	log.fine("Returning to Simulator");
    }

    public void userLeft(UserID uid) {
	// no-op
    }
}
