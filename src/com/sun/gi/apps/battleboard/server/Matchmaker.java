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
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 */
public class Matchmaker implements /* ChannelListener */ GLO {

    private static final long serialVersionUID = 1L;

    private static final Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    protected static final String MATCHMAKER_GLO_NAME = "matchmaker";

    protected final ChannelID channel;

    protected int PLAYERS_PER_GAME = 2;

    protected Set<GLOReference<Player>> waitingPlayers =
	new HashSet<GLOReference<Player>>();

    public static Matchmaker get() {
	SimTask task = SimTask.getCurrent();
	return (Matchmaker) task.findGLO(MATCHMAKER_GLO_NAME).get(task);
    }

    public static GLOReference<Matchmaker> create() {
	SimTask task = SimTask.getCurrent();

	// Paranoid check for pre-existing matchmaker object.
	// In BattleBoard, this isn't necessary because only the
	// boot object creates the matchmaker, and it does so
	// with a mutex (or "GET-lock" held).
	GLOReference<Matchmaker> ref = task.findGLO(MATCHMAKER_GLO_NAME);
	if (ref != null) {
	    log.severe("matchmaker GLO already exists");
	    return ref;
	}

	ref = task.createGLO(new Matchmaker(), MATCHMAKER_GLO_NAME);

	// More paranoia; for the reasons above, this particular
	// use of createGLO must succeed, so this isn't needed.
	if (ref == null) {
	    GLOReference<Matchmaker> ref2 = task.findGLO(MATCHMAKER_GLO_NAME);
	    if (ref2 == null) {
		log.severe("createGLO failed");
		throw new RuntimeException("createGLO failed");
	    } else {
		log.severe("lost createGLO race");
		return ref2;
	    }
	}

	//((Matchmaker) ref.get(task)).boot(ref);
	return ref;
    }

    protected Matchmaker() {
	// Create the matchmaker channel so we can talk to unjoined clients
	SimTask task = SimTask.getCurrent();
	channel = task.openChannel("matchmaker");
	task.lock(channel, true);
    }

    protected void boot(GLOReference<Matchmaker> thisRef) {
	//ChannelListener.add(channel, thisRef);
    }

    public void addUserID(UserID uid) {
	log.info("Adding to matchmaker");
	SimTask.getCurrent().join(uid, channel);
	log.info("Added to matchmaker");
    }

    protected void sendAlreadyJoined(UserID uid) {
	ByteBuffer byteBuffer = ByteBuffer.wrap("already-joined".getBytes());
	byteBuffer.position(byteBuffer.limit());
	SimTask task = SimTask.getCurrent();
	task.sendData(channel, new UserID[] { uid }, byteBuffer, true);
    }

    // Handle the "join" command in matchmaker mode
    public void userDataReceived(UserID uid, ByteBuffer data) {
	log.fine("Matchmaker: data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String command = new String(bytes);

	if (!command.startsWith ("join ")) {
	    log.warning("Matchmaker got non-join command: `" + command + "'");
	    return;
	}

	final String playerName = command.substring(5);
	log.fine("Matchmaker: join from `" + playerName + "'");

	SimTask task = SimTask.getCurrent();
	for (GLOReference<Player> ref : waitingPlayers) {
	    Player p = ref.peek(task);
	    if (playerName.equals(p.getPlayerName())) {
		log.warning("Matchmaker already has `" + playerName);
		sendAlreadyJoined(uid);
		return;
	    }

	}

	GLOReference<Player> playerRef = Player.getRef(uid);
	Player player = playerRef.get(task);
	player.setPlayerName(playerName);

	waitingPlayers.add(playerRef);

	// XXX the right thing to do here is probably to queue
	// a task to check whether we have a game togther, since
	// we hold the lock on this player but they might not be
	// involved in the next game we can spawn with current waiters.
	// Instead we'll just keep the lock for now.

	checkForEnoughPlayers();
    }

    protected void checkForEnoughPlayers() {

	if (waitingPlayers.size() < PLAYERS_PER_GAME) {
	    return;
	}

	if (waitingPlayers.size() > PLAYERS_PER_GAME) {
	    log.warning("Too many waiting players!  How'd that happen? "
		+ "expected " + PLAYERS_PER_GAME + ", got "
		+ waitingPlayers.size());
	}

	Game.create(waitingPlayers);
	waitingPlayers.clear();
    }

    // SimChannelMembershipListener methods

    public void joinedChannel(ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " joined channel " + cid);
    }

    public void leftChannel(ChannelID cid, UserID uid) {
	log.info("Matchmaker: User " + uid + " left channel " + cid);

	GLOReference<Player> playerRef = Player.getRef(uid);
	waitingPlayers.remove(playerRef);
    }
}
