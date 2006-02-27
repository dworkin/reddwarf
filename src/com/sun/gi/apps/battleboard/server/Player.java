/*
 * $Id$
 *
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.apps.battleboard.server;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;

import java.security.Principal;
import javax.security.auth.Subject;

/**
 *
 * @author  James Megquier
 * @version $Rev$, $Date$
 */
public class Player implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected String             myUserName;
    protected UserID             myUserID;
    protected GLOReference<Game> myGameRef;
    protected String             myNick;

    public static GLOReference<Player> getRef(UserID uid) {
	SimTask task = SimTask.getCurrent();
	return task.findGLO(gloKeyForUID(uid));
    }

    public static Player get(UserID uid) {
	SimTask task = SimTask.getCurrent();
	GLOReference<Player> ref = getRef(uid);
	return ref.get(task);
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

    public void gameStarted(GLOReference<Game> gameRef) {
	//myGameRefs.add(gameRef);
	myGameRef = gameRef;
    }

    public void gameEnded(GLOReference<Game> gameRef) {
	myGameRef = null;
	// If there are no more games, join the matchmaker channel
    }

    public String getNickname() {
	return myNick;
    }

    public void setNickname(String nickname) {
	myNick = nickname;
    }


    protected static String gloKeyForUID(UserID uid) {
	return Pattern.compile("\\W+").matcher(uid.toString()).replaceAll("");
    }


    // Static versions of the SimUserListener methods

    public static void userJoined(UserID uid, Subject subject) {
	log.info("User " + uid + " joined server, subject = " + subject);

	SimTask task = SimTask.getCurrent();

	// Idea: return as quickly as possible, queueing any extra work
	// so the parent userJoin handler has less contention.

	String gloKey = gloKeyForUID(uid);

	// check that the player doesn't already exist
	/*
	GLOReference playerRef = getRef(uid);
	if (playerRef != null) {
	    // delete it? update it with this uid?
	    // kick the new guy off? kick the old guy?
	}
	*/

	String userName = subject.getPrincipals().iterator().next().getName();
	Player player = new Player (userName, uid);

	GLOReference<Player> playerRef = task.createGLO(player, gloKey);

	// We're interested in direct server data sent by the user.
	task.addUserDataListener(uid, playerRef);

	Matchmaker.get().addUserID(uid);
    }

    public static void userLeft(UserID uid) {
	log.info("User " + uid + " left server");

	SimTask task = SimTask.getCurrent();

	// XXX in the future we may want the player object to persist.
	GLOReference playerRef = Player.getRef(uid);
	if (playerRef != null) {
	    playerRef.delete(task);
	}

    }

    // SimUserDataListener methods

    public void userJoinedChannel(ChannelID cid, UserID uid) {
	log.info("Player: User " + uid + " joined channel " + cid);

	if (! uid.equals(myUserID)) {
	    log.warning("Player: Got UID " + uid + " expected " + myUserID);
	    return;
	}

	SimTask task = SimTask.getCurrent();
	if (myGameRef != null) {
	    // XXX: this currently supports only one game per player
	    myGameRef.get(task).joinedChannel(cid, uid);
	} else {
	    // If no game, dispatch to the matchmaker
	    Matchmaker.get().joinedChannel(cid, uid);
	}
    }

    public void userLeftChannel(ChannelID cid, UserID uid) {
	log.info("Player: User " + uid + " left channel " + cid);

	if (! uid.equals(myUserID)) {
	    log.warning("Player: Got UID " + uid + " expected " + myUserID);
	    return;
	}

	SimTask task = SimTask.getCurrent();
	if (myGameRef != null) {
	    // XXX: this currently supports only one game per player
	    myGameRef.get(task).leftChannel(cid, uid);
	} else {
	    // If no game, dispatch to the matchmaker
	    Matchmaker.get().leftChannel(cid, uid);
	}
    }

    public void userDataReceived(UserID uid, ByteBuffer data) {
	log.info("Player: User " + uid + " direct data");

	if (! uid.equals(myUserID)) {
	    log.warning("Player: Got UID " + uid + " expected " + myUserID);
	    return;
	}

	SimTask task = SimTask.getCurrent();
	if (myGameRef != null) {
	    // XXX: this currently supports only one game per player
	    myGameRef.get(task).userDataReceived(myUserID, data);
	} else {
	    // If no game, dispatch to the matchmaker
	    Matchmaker.get().userDataReceived(uid, data);
	}
    }

    public void dataArrivedFromChannel(ChannelID cid, UserID uid,
	    ByteBuffer data) {
	// no-op
    }
}
