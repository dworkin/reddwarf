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
 * Matchmaker is responsible for collecting Players together until
 * there are enough to start a new Game.  <p>
 *
 * This very basic implementation starts a new game as soon as
 * <code>PLAYERS_PER_GAME</code> players have arrived.  More
 * sophisticated implementations would allow players to specify
 * constraints on the games (for example, if there are other players
 * that prefer to play together, or if players are ranked and want to
 * play against players of roughly equal skill, etc) and the
 * Matchmaker would attempt to satisfy those constraints when placing
 * players into Games.  <p>
 *
 * Users specify what playerName they wish to use.  In this example,
 * there is no persistant binding between a user and a playerName; a
 * user can use as many different playerNames as he or she wishes, and
 * different users can use the same playerName.  The only restriction
 * is that all of the playerNames joined into a particular game must
 * be unique. XXX: DJE: is that correct?
 */
public class Matchmaker implements GLO {

    private static final long serialVersionUID = 1;

    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    private static final String MATCHMAKER_GLO_NAME = "matchmaker";

    private final ChannelID channel;

    private int PLAYERS_PER_GAME = 2;

    private Set<GLOReference<Player>> waitingPlayers =
	    new HashSet<GLOReference<Player>>();

    public static Matchmaker get() {
	SimTask task = SimTask.getCurrent();
	return (Matchmaker) task.findGLO(MATCHMAKER_GLO_NAME).get(task);
    }

    public static GLOReference<Matchmaker> create() {
	SimTask task = SimTask.getCurrent();

	/*
	 * Check for pre-existing matchmaker object.
	 *
	 * In BattleBoard, this isn't necessary because only the boot
	 * object is supposed to call this method in order to create
	 * the matchmaker, and it does so with a mutex (or "GET-lock"
	 * held).  But better safe than sorry...
	 */

	GLOReference<Matchmaker> ref = task.findGLO(MATCHMAKER_GLO_NAME);
	if (ref != null) {
	    log.severe("matchmaker GLO already exists");
	    return ref;
	}

	ref = task.createGLO(new Matchmaker(), MATCHMAKER_GLO_NAME);

	/*
	 * More extra caution:  for the reasons given above, this
	 * particular use of createGLO will succeed (unless something
	 * is terribly wrong), so this is purely defensive against
	 * errors elsewhere.
	 */

	if (ref == null) {
	    ref = task.findGLO(MATCHMAKER_GLO_NAME);
	    if (ref == null) {
		log.severe("matchmaker createGLO failed");
		throw new RuntimeException("matchmaker createGLO failed");
	    } else {
		log.severe("matchmaker GLO creation race");
	    }
	}

	return ref;
    }

    /**
     * Creates the matchmaker channel so we can talk to non-playing
     * clients.
     */
    protected Matchmaker() {
	SimTask task = SimTask.getCurrent();
	channel = task.openChannel("matchmaker");
	task.lock(channel, true);
    }

    /**
     * Adds a new user to the channel.
     *
     * @param uid the UserID of the new user
     */
    public void addUserID(UserID uid) {
	SimTask.getCurrent().join(uid, channel);
    }

    /**
     * Informs the user that a player with the same player name that
     * they have requested is already waiting to join a game.  <p>
     *
     * @param uid the UserID of the user
     */
    protected void sendAlreadyJoined(UserID uid) {
	ByteBuffer byteBuffer = ByteBuffer.wrap("already-joined".getBytes());
	byteBuffer.position(byteBuffer.limit());
	SimTask task = SimTask.getCurrent();
	task.sendData(channel, new UserID[] { uid }, byteBuffer, true);
    }

    /**
     * Dispatches messages from users.  <p>
     *
     * For a simple matchmaker like this one, the only expected
     * message is a request to "join".  Messages that do not begin
     * with the prefix <code>"join"</code> are rejected out of hand.
     *
     * @param uid the UserID of the user from whom the message is
     *
     * @param data the contents of the message
     */
    public void userDataReceived(UserID uid, ByteBuffer data) {
	log.fine("Matchmaker: data from user " + uid);

	byte[] bytes = new byte[data.remaining()];
	data.get(bytes);
	String text = new String(bytes);

	String[] tokens = text.split("\\s+");
	if (tokens.length == 0) {
	    log.warning("empty message");
	    return;
	}

	if (tokens.length != 2) {
	    log.warning("bad join (" + text + ")");
	    return;
	}

	String command = tokens[0];
	String playerName = tokens[1];

	if (!"join".equals(command)) {
	    log.warning("Matchmaker got non-join command: `" + command + "'");
	    return;
	}

	log.fine("Matchmaker: join from `" + playerName + "'");

	/* XXX:  DJE:  this is confusing:  can we not have two users
	 * with the same playerName WAITING, but there could be two
	 * users using the same playerName but in two different games? 
	 * I'm not sure I like that.
	 */

	/*
	 * Before adding this user under the given playerName, check
	 * that the playerName is not already in use.  If so, then
	 * reject the join.
	 */

	SimTask task = SimTask.getCurrent();
	for (GLOReference<Player> ref : waitingPlayers) {
	    Player player = ref.peek(task);
	    if (playerName.equals(player.getPlayerName())) {
		log.warning("Matchmaker already has `" + playerName);
		sendAlreadyJoined(uid);
		return;
	    }
	}

	/*
	 * Get a reference to the player object for this user, set the
	 * name of that player to playerName, and add the playerRef to
	 * the set of waiting players.
	 */

	GLOReference<Player> playerRef = Player.getRef(uid);
	Player player = playerRef.get(task);
	player.setPlayerName(playerName);
	waitingPlayers.add(playerRef);

	/*
	 * If there are enough players waiting, create a game.
	 *
	 * Another technique would be to queue a new task to check
	 * whether we have enough players to create a game and/or
	 * partition the set of players into games.  This has the
	 * possible advantage of releasing the lock on this player,
	 * which we otherwise continue to hold.
	 */

	/* XXX:  DJE:  I removed the whole "check if it's more than
	 * PLAYERS_PER_GAME" logic because that never seems to happen
	 * and I thought it was impossible anyway...  Can it happen
	 * that waitingPlayers can be updated by another thread at the
	 * same time?  (if so, then we have more synching to do!)
	 */

	if (waitingPlayers.size() == PLAYERS_PER_GAME) {
	    Game.create(waitingPlayers);
	    waitingPlayers.clear();
	}
    }

    // Channel Join/Leave methods

    /**
     * {@inheritDoc}
     */
    public void joinedChannel(ChannelID cid, UserID uid) {
	log.finer("Matchmaker: User " + uid + " joined channel " + cid);
    }

    /**
     * {@inheritDoc}
     */
    public void leftChannel(ChannelID cid, UserID uid) {
	log.finer("Matchmaker: User " + uid + " left channel " + cid);

	GLOReference<Player> playerRef = Player.getRef(uid);
	waitingPlayers.remove(playerRef);
    }
}