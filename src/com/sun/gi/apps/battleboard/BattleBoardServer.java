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

import java.util.logging.Logger;

import javax.security.auth.Subject;

public class BattleBoardServer
	implements SimBoot, SimUserListener,
		   SimUserDataListener, SimChannelListener {

    private static long serialVersionUID = 1L;
    private static Logger log = Logger.getLogger("com.sun.gi.apps.battleboard");

    protected UserID serverUserID = null;
    protected ChannelID matchmakerChannelID;
    protected Map<String, GLOReference> gameList;

    protected GLOReference bootRef;

    public void boot(SimTask task, boolean firstBoot) {
	log.info("Booting BattleBoard Server as appID " + task.getAppID());

	// Register with the SGS boot manager
	bootRef = task.findSO("BOOT");
	task.addUserListener(bootRef);

	gameList = new HashMap<String, GLOReference>();

	// Create the matchmaking channel
	matchmakerChannelID = task.openChannel("matchmaking");
	task.lock(matchmakerChannelID, true);
	task.addChannelListener(matchmakerChannelID, bootRef);
    }

    public void userJoined(SimTask task, UserID uid, Subject subject) {
	log.info("User " + uid + " joined server");

	// We're interested in channel actions for this user
	task.addUserDataListener(uid, bootRef);

	// Put this user on the matchmaker channel to begin with
	task.join(uid, matchmakerChannelID);
    }

    public void userLeft(SimTask task, UserID uid) {
	log.info("User " + uid + " left server");
    }

    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.warning("User " + uid + " sent direct server data -- why?");
    }

    public void userJoinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("User " + uid + " joined channel " + cid);
    }

    public void userLeftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("User " + uid + " left channel " + cid);
    }

    // Handle the "join" command in matchmaker mode
    public void dataArrived(SimTask task, ChannelID cid,
	    UserID uid, ByteBuffer buff) {

	log.info("Data from user " + uid + " on channel " + cid);
	log.fine("data: " +
	    new String(buff.array(),buff.arrayOffset(), buff.limit()));

	if (cid != matchmakerChannelID) {
	    log.warning("Matchmaker mode, but not on matchmaker channel!");
	    return;
	}

	final String cmd =
	    new String(buff.array(),buff.arrayOffset(), buff.limit());

	if (! cmd.startsWith ("join ")) {
	    log.warning("Matchmaker got non-join command!");
	    return;
	}

	final String gameName = cmd.substring(5);
	log.info("Matchmaker: join/create game `" + gameName + "'");
    }
}
