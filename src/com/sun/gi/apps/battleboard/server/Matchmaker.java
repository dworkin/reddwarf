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

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.impl.SGSUserImpl;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

public class Matchmaker implements ChannelListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected static final String MATCHMAKER_GLO_NAME = "matchmaker";

    protected final ChannelID channel;

    protected int PLAYERS_PER_GAME = 3;

    protected Set<GLOReference> waitingPlayers =
	new HashSet<GLOReference>();

    public static Matchmaker get(SimTask task) {
	return (Matchmaker) task.findGLO(MATCHMAKER_GLO_NAME).get(task);
    }

    public static GLOReference create() {
	SimTask task = SimTask.getCurrent();

	// Paranoid check for pre-existing matchmaker object.
	// In BattleBoard, this isn't necessary because only the
	// boot object creates the matchmaker, and it does so
	// with a mutex (or "GET-lock" held).
	GLOReference ref = task.findGLO(MATCHMAKER_GLO_NAME);
	if (ref != null) {
	    log.severe("matchmaker GLO already exists");
	    return ref;
	}

	Matchmaker mm = new Matchmaker(task);
	ref = task.createGLO(mm, MATCHMAKER_GLO_NAME);

	// More paranoia; for the reasons above, this particular
	// use of createGLO must succeed, so this isn't needed.
	if (ref == null) {
	    GLOReference ref2 = task.findGLO(MATCHMAKER_GLO_NAME);
	    if (ref2 == null) {
		log.severe("createGLO failed");
		throw new RuntimeException("createGLO failed");
	    } else {
		log.severe("lost createGLO race");
		return ref2;
	    }
	}

	ChannelListener.add(mm.channel, ref);
	return ref;
    }

    protected Matchmaker(SimTask task) {
	// Create the matchmaker channel so we can talk to unjoined clients
	channel = task.openChannel("matchmaker");
	task.lock(channel, true);
    }

    public void addUserID(SimTask task, UserID uid) {
	log.info("Adding to matchmaker");
	task.join(uid, channel);
	log.info("Added to matchmaker");
    }

    protected void sendAlreadyJoined(SimTask task, UserID uid) {
	ByteBuffer buf = ByteBuffer.allocate(64);
	buf.put("already-joined".getBytes());
	task.sendData(channel, new UserID[] { uid }, buf, true);
    }

    // Handle the "join" command in matchmaker mode
    public void userDataReceived(SimTask task, UserID uid, ByteBuffer data) {
	log.info("Matchmaker: data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String cmd = new String(bytes);

	if (! cmd.startsWith ("join ")) {
	    log.warning("Matchmaker got non-join command: `" + cmd + "'");
	    return;
	}

	final String playerName = cmd.substring(5);
	log.info("Matchmaker: join from `" + playerName + "'");

	for (GLOReference ref : waitingPlayers) {
	    Player p = (Player) ref.peek(task);
	    if (playerName.equals(p.getNickname())) {
		log.warning("Matchmaker already has `" + playerName);
		sendAlreadyJoined(task, uid);
		return;
	    }

	}

	GLOReference playerRef = Player.getRef(task, uid);
	Player player = (Player) playerRef.get(task);
	player.setNickname(playerName);

	waitingPlayers.add(playerRef);

	// XXX the right thing to do here is probably to queue
	// a task to check whether we have a game togther, since
	// we hold the lock on this player but they might not be
	// involved in the next game we can spawn with current waiters.
	// Instead we'll just keep the lock for now.

	checkForEnoughPlayers(task);
    }

    protected void checkForEnoughPlayers(SimTask task) {

	if (waitingPlayers.size() < PLAYERS_PER_GAME) {
	    return;
	}

	if (waitingPlayers.size() > PLAYERS_PER_GAME) {
	    log.warning("Too many waiting players!  How'd that happen? "
		+ "expected " + PLAYERS_PER_GAME + ", got "
		+ waitingPlayers.size());
	}

	Game.create(task, waitingPlayers);
	waitingPlayers.clear();
    }

    // SimChannelMembershipListener methods

    public void joinedChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " joined channel " + cid);
    }

    public void leftChannel(SimTask task, ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " left channel " + cid);

	GLOReference playerRef = Player.getRef(task, uid);
	waitingPlayers.remove(playerRef);
    }
}
