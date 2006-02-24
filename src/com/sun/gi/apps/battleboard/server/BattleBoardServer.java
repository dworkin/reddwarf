/*
 * $Id$
 */

package com.sun.gi.apps.battleboard.server;

import java.nio.ByteBuffer;
import com.sun.gi.logic.SimBoot;
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
	implements SimBoot, SimUserListener,SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

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

	try {
	    task.queueTask(matchmakerRef,
		Matchmaker.class.getMethod(
		    "addUserID", SimTask.class, UserID.class),
		new Object[] { uid });
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void userLeft(SimTask task, UserID uid) {
	log.info("User " + uid + " left server");

	// XXX in the future we may want the player object to persist.
	GLOReference playerRef = Player.getRef(task, uid);
	if (playerRef != null) {
	    playerRef.delete(task);
	}
    }

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimBoot#boot(com.sun.gi.logic.GLOReference, boolean)
	 */
	public void boot(GLOReference thisGLO, boolean firstBoot) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserListener#userJoined(com.sun.gi.comm.routing.UserID, javax.security.auth.Subject)
	 */
	public void userJoined(UserID uid, Subject subject) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserListener#userLeft(com.sun.gi.comm.routing.UserID)
	 */
	public void userLeft(UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userDataReceived(com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void userDataReceived(UserID from, ByteBuffer data) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userJoinedChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userJoinedChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#userLeftChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID)
	 */
	public void userLeftChannel(ChannelID cid, UserID uid) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.logic.SimUserDataListener#dataArrivedFromChannel(com.sun.gi.comm.routing.ChannelID, com.sun.gi.comm.routing.UserID, java.nio.ByteBuffer)
	 */
	public void dataArrivedFromChannel(ChannelID id, UserID from, ByteBuffer buff) {
		// TODO Auto-generated method stub
		
	}
}
