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

package com.sun.gi.apps.jnwn;

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
public class User implements SimUserDataListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.jnwn");

    private String  name;
    private UserID  uid;
    private ChannelID  controlChannel;
    private GLOReference<Character>  characterRef;
    private boolean connected;

    protected User(String userName, UserID userID) {
	name = userName;
	uid  = userID;
	characterRef = null;
	connected = true;
    }

    protected void boot(GLOReference<User> thisRef, ChannelID controlChan) {

	controlChannel = controlChan;

	if (characterRef == null) {
	    // This is a new user

	    // Create a new character for this user
	    characterRef = Character.create(thisRef);
	}
    }

    public static User get(String name) {
	SimTask task = SimTask.getCurrent();
	GLOReference<User> ref = task.findGLO(name);
	return ref == null ? null : ref.get(task);
    }

    public String getName() {
	return name;
    }

    public UserID getUID() {
	return uid;
    }

    protected void setUID(UserID userID) {
	uid = userID;
    }

    public GLOReference<Character> getCharacterRef() {
	return characterRef;
    }

    public void setCharacterRef(GLOReference<Character> ref) {
	characterRef = ref;
    }

    public static GLOReference<User> findOrCreate(UserID userID,
	    String userName, ChannelID controlChan) {

	SimTask task = SimTask.getCurrent();
	GLOReference<User> ref =
	    findOrCreateGLO(userID, userName);

	// Register the new User GLO as the event-handler for this uid.
	task.addUserDataListener(userID, ref);

	// Do any additional init
	ref.get(task).boot(ref, controlChan);

	return ref;
    }

    protected boolean isConnected() {
	return connected;
    }

    protected static GLOReference<User> findOrCreateGLO(UserID userID,
	    String userName) {

	SimTask task = SimTask.getCurrent();
	GLOReference<User> ref = task.findGLO(userName);
	if (ref != null) {
	    User user = ref.get(task);
	    if (user != null) {
		UserID oldUserID = user.getUID();
		if (user.isConnected() && oldUserID != null) {
		    log.severe("User " + userName +
			" still logged in as uid " + oldUserID);
		    // user.kick();
		    //   - or -
		    // task.disconnect(oldUserID);
		}
		user.setUID(userID);
		return ref;
	    }
	    // Else we have a reference to an non-existant object, so
	    // fall-though and create a new one.
	}

	return task.createGLO(new User(userName, userID), userName);
    }

    public void joinedGame() {
	log.fine("User " + name + " [" + uid + "] joined game");

	// Put this user on the global control channel
	SimTask.getCurrent().join(uid, controlChannel);

	connected = true;
    }

    public void leftGame() {
	log.fine("User " + name + " [" + uid + "] left game");
	connected = false;
    }

    // SimUserDataListener methods

    public void userJoinedChannel(ChannelID channelID, UserID userID) {
	log.fine("User " + userID + " joined channel " + channelID);

	SimTask task = SimTask.getCurrent();

	// Paranoia
	if (! uid.equals(userID)) {
	    log.warning("User: Got UID " + userID + " expected " + uid);
	    return;
	}

	if (characterRef == null) {
	    log.severe("Channel joined, but no character assigned");
	    return;
	}

	Character ch = characterRef.peek(task);

	if (controlChannel.equals(channelID)) {
	    String message = "userid " + ch.getCharacterID();
	    ByteBuffer buf = ByteBuffer.wrap(message.getBytes());
	    buf.position(buf.limit());
	    task.sendData(controlChannel, uid, buf, true);

	    // Join the map we're going to be playing
	    ch.joinArea();

	    return;
	}

	// Otherwise, let my Character handle it
	ch.joinedChannel(channelID);
    }

    public void userLeftChannel(ChannelID channelID, UserID userID) {
	log.fine("User " + userID + " left channel " + channelID);

	// Paranoia
	if (! uid.equals(userID)) {
	    log.warning("User: Got UID " + userID + " expected " + uid);
	    return;
	}

	if (characterRef == null) {
	    log.severe("Channel joined, but no character assigned");
	    return;
	}

	if (controlChannel.equals(channelID)) {
	    // ignore it; we're shutting down
	    return;
	}

	// Let my Character handle it
	characterRef.peek(SimTask.getCurrent()).leftChannel(channelID);
    }

    public void userDataReceived(UserID userID, ByteBuffer data) {
	log.finer("User: User " + userID + " direct data");

	// Paranoia
	if (! uid.equals(userID)) {
	    log.warning("User: Got UID " + userID + " expected " + uid);
	    return;
	}

	if (characterRef == null) {
	    log.severe("Channel joined, but no character assigned");
	    return;
	}

	// Let my Character handle it
	characterRef.peek(SimTask.getCurrent()).dataReceived(data);
    }

    public void dataArrivedFromChannel(ChannelID channelID, UserID userID,
	    ByteBuffer data) {
	// no-op
    }
}
