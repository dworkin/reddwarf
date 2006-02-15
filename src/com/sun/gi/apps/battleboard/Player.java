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

import javax.security.auth.Subject;

public class Player implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard");

    protected GLOReference myGame;
    protected String       myName;

    // XXX: return a GLOReference, not a Player(!?)
    public static Player instance(SimTask task, UserID uid) {

	Player player = new Player(task, uid);

	// Link this uid to the Player object
	// XXX: for persistent identity, we can't use UID; use Subject
	GLOReference playerRef = task.createGLO(player, uid.toString());

	// We're interested in direct server data sent by the user.
	task.addUserDataListener(uid, playerRef);

	// Put this user on the matchmaker channel initially
	Matchmaker.addUser(task, uid);

	return player;
    }

    protected Player(SimTask task, UserID uid) {
	myGame = null;
	myName = null;
    }

    public GLOReference game() {
	return myGame;
    }

    public void game(GLOReference game) {
	myGame = game;
    }

    public String getName() {
	return myName;
    }

    public void setName(String name) {
	myName = name;
    }

    // SimUserDataListener methods

    public void userJoinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Player: User " + uid + " joined channel " + cid);
    }

    public void userLeftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Player: User " + uid + " left channel " + cid);
    }

    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.info("Player: User " + uid + " direct data");
    }

}
