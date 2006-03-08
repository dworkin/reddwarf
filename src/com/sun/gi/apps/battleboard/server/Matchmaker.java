/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.apps.battleboard.server;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 * Matchmaker is responsible for collecting Players together until there
 * are enough to start a new Game.
 * <p>
 * 
 * This very basic implementation starts a new game as soon as
 * <code>PLAYERS_PER_GAME</code> players have arrived. More
 * sophisticated implementations would allow players to specify
 * constraints on the games (for example, if there are other players
 * that prefer to play together, or if players are ranked and want to
 * play against players of roughly equal skill, etc) and the Matchmaker
 * would attempt to satisfy those constraints when placing players into
 * Games.
 * <p>
 * 
 * Users specify what playerName they wish to use. In this example,
 * there is no persistant binding between a user and a playerName; a
 * user can use as many different playerNames as he or she wishes, and
 * different users can use the same playerName. The only restriction is
 * that all of the playerNames joined into a particular game must be
 * unique. XXX: DJE: is that correct?
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
         * object is supposed to call this method in order to create the
         * matchmaker, and it does so with a mutex (or "GET-lock" held).
         * But better safe than sorry...
         */

        GLOReference<Matchmaker> ref = task.findGLO(MATCHMAKER_GLO_NAME);
        if (ref != null) {
            log.severe("matchmaker GLO already exists");
            return ref;
        }

        ref = task.createGLO(new Matchmaker(), MATCHMAKER_GLO_NAME);

        /*
         * More extra caution: for the reasons given above, this
         * particular use of createGLO will succeed (unless something is
         * terribly wrong), so this is purely defensive against errors
         * elsewhere.
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
     * they have requested is already waiting to join a game.
     * <p>
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
     * Dispatches messages from users.
     * <p>
     * 
     * For a simple matchmaker like this one, the only expected message
     * is a request to "join". Messages that do not begin with the
     * prefix <code>"join"</code> are rejected out of hand.
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

        /*
         * XXX: DJE: this is confusing: can we not have two users with
         * the same playerName WAITING, but there could be two users
         * using the same playerName but in two different games? I'm not
         * sure I like that.
         */

        /*
         * Before adding this user under the given playerName, check
         * that the playerName is not already in use. If so, then reject
         * the join.
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
         * partition the set of players into games. This has the
         * possible advantage of releasing the lock on this player,
         * which we otherwise continue to hold.
         */

        /*
         * XXX: DJE: I removed the whole "check if it's more than
         * PLAYERS_PER_GAME" logic because that never seems to happen
         * and I thought it was impossible anyway... Can it happen that
         * waitingPlayers can be updated by another thread at the same
         * time? (if so, then we have more synching to do!)
         */

        if (waitingPlayers.size() == PLAYERS_PER_GAME) {
            Game.create(waitingPlayers);
            waitingPlayers.clear();
        }
    }

    // Channel Join/Leave methods

    public void joinedChannel(ChannelID cid, UserID uid) {
        log.finer("Matchmaker: User " + uid + " joined channel " + cid);
    }

    public void leftChannel(ChannelID cid, UserID uid) {
        log.finer("Matchmaker: User " + uid + " left channel " + cid);

        GLOReference<Player> playerRef = Player.getRef(uid);
        waitingPlayers.remove(playerRef);
    }
}
