package com.sun.gi.apps.battleboard.server;

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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;

import java.security.Principal;
import javax.security.auth.Subject;

public class Player implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected String       myUserName;
    protected UserID       myUserID;
    protected GLOReference myGameRef;
    protected String       myNick;

    public static Player instanceFor(SimTask task, UserID uid,
	    Subject subject) {


	String userName = subject.getPrincipals().iterator().next().getName();
	Player player = new Player (userName, uid);

	// XXX check that the player doesn't already exist
	GLOReference playerRef =
	    task.createGLO(player, gloKeyForUID(uid));

	// We're interested in direct server data sent by the user.
	task.addUserDataListener(uid, playerRef);

	return player;
    }

    public static GLOReference getRef(SimTask task, UserID uid) {
	return task.findGLO(gloKeyForUID(uid));
    }

    public static Player get(SimTask task, UserID uid) {
	GLOReference ref = getRef(task, uid);
	return (Player) ref.get(task);
    }

    protected static String gloKeyForUID(UserID uid) {
	return Pattern.compile("\\W+").matcher(uid.toString()).replaceAll("");
    }

    protected Player(String userName, UserID uid) {
	myUserName = userName;
	myUserID = uid;
	myGameRef = null;
	myNick = null;
    }

    public String getUserName() {
	return myUserName;
    }

    public UserID getUID() {
	return myUserID;
    }

    public void setUID(UserID uid) {
	myUserID = uid;
    }

    public GLOReference getGameRef() {
	return myGameRef;
    }

    public void setGameRef(GLOReference gameRef) {
	myGameRef = gameRef;
    }

    public String getNickname() {
	return myNick;
    }

    public void setNickname(String nickname) {
	myNick = nickname;
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

	if (! uid.equals(myUserID)) {
	    log.warning("Player: Wrong UID data sent to me! Got "
		+ uid + " expected " + myUserID);
	    return;
	}

	if (myGameRef != null) {
	    Game game = (Game) myGameRef.get(task);
	    game.userDataReceived(task, myUserID, data);
	} else {
	    // If no game, dispatch to the matchmaker
	    GLOReference ref = Matchmaker.instance(task);
	    Matchmaker mm = (Matchmaker) ref.get(task);
	    mm.userDataReceived(task, uid, data);
	}
    }

}
