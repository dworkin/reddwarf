/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.battleboard.server;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.security.auth.Subject;

/**
 * A server-side BattleBoard Player holds basic data (such as username),
 * and contains login/logout logic. As the SimUserDataListener for a
 * single user, it also dispatches messages to the higher-level Game or
 * Matchmaker that this Player is participating in.
 */
public class Player implements SimUserDataListener {

    private static final long serialVersionUID = 1;

    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    /**
     * The username for the user.
     */
    private String myUserName;

    /**
     * The current UserID of this user, if logged in.
     */
    private UserID myUserID;

    /**
     * The name under which the player is playing (i.e., a screen name
     * for this user). This need not be related in any way to the user
     * name!
     */
    private String myPlayerName;

    private GLOReference<Game> myGameRef;

    /**
     * Creates a Player instance with the given userName and uid.
     * 
     * @param userName the name of the user (which is not necessarily
     * the same as the playerName)
     * 
     * @param uid the UID of this user
     */
    protected Player(String userName, UserID uid) {
        myUserName = userName;
        myUserID = uid;
        myGameRef = null;
        myPlayerName = null;
    }

    public static GLOReference<Player> getRef(UserID uid) {
        SimTask task = SimTask.getCurrent();
        return task.findGLO(gloKeyForUID(uid));
    }

    public static Player get(UserID uid) {
        SimTask task = SimTask.getCurrent();
        GLOReference<Player> ref = getRef(uid);
        return ref.get(task);
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
        SimTask task = SimTask.getCurrent();
        myGameRef = gameRef;
        Game game = gameRef.peek(task);

        String playerHistoryName = myUserName + ".history";
        GLOReference<PlayerHistory> historyRef = task.createGLO(
                new PlayerHistory(myUserName), playerHistoryName);
        if (historyRef == null) {
            log.fine("GLO already exists for " + playerHistoryName);
            historyRef = task.findGLO(playerHistoryName);
        } else {
            log.fine("created GLO for " + playerHistoryName);
        }
        game.addHistory(myUserName, historyRef);
    }

    public void gameEnded(GLOReference<Game> gameRef) {
        myGameRef = null;
    }

    public String getPlayerName() {
        return myPlayerName;
    }

    public void setPlayerName(String playerName) {
        myPlayerName = playerName;
    }

    protected static String gloKeyForUID(UserID uid) {
        return Pattern.compile("\\W+").matcher(uid.toString()).replaceAll("");
    }

    // Static versions of the SimUserListener methods

    public static void userJoined(UserID uid, Subject subject) {
        log.fine("User " + uid + " joined server, subject = " + subject);

        SimTask task = SimTask.getCurrent();

        String gloKey = gloKeyForUID(uid);

        // check that the player doesn't already exist
        /*
         * GLOReference<Player> playerRef = getRef(uid);
	 * if (playerRef != null) {
	 *  // XXX delete it? update it with this uid?
	 *  // kick the new guy off? kick the old guy?
	 * }
         */

        String userName = subject.getPrincipals().iterator().next().getName();
        Player player = new Player(userName, uid);

        GLOReference<Player> playerRef = task.createGLO(player, gloKey);

        // We're interested in direct server data sent by the user.
        task.addUserDataListener(uid, playerRef);
        Matchmaker.get().addUserID(uid);
    }

    public static void userLeft(UserID uid) {
        log.fine("User " + uid + " left server");

        GLOReference<Player> playerRef = getRef(uid);
	if (playerRef != null) {
	    // The PlayerHistory GLO stays, but in this implementation
	    // we delete the Player GLO on logout.

	    log.finer("Deleting Player GLO");
	    SimTask.getCurrent().destroyGLO(playerRef);
	} else {
	    log.warning("userLeft for unknown uid " + uid);
	}
    }

    // SimUserDataListener methods

    public void userJoinedChannel(ChannelID cid, UserID uid) {
        log.finer("Player: User " + uid + " joined channel " + cid);

        if (!uid.equals(myUserID)) {
            log.warning("Player: Got UID " + uid + " expected " + myUserID);
            return;
        }

        SimTask task = SimTask.getCurrent();
        if (myGameRef != null) {
            // We currently support only one game per player
	    Game game = myGameRef.get(task);
	    if (game != null) {
		game.joinedChannel(cid, uid);
	    }
        } else {
            // If no game, dispatch to the matchmaker
            Matchmaker matchmaker = Matchmaker.get();
	    if (matchmaker != null) {
		matchmaker.joinedChannel(cid, uid);
	    }
        }
    }

    public void userLeftChannel(ChannelID cid, UserID uid) {
        log.finer("Player: User " + uid + " left channel " + cid);

        if (!uid.equals(myUserID)) {
            log.warning("Player: Got UID " + uid + " expected " + myUserID);
            return;
        }

        SimTask task = SimTask.getCurrent();
        if (myGameRef != null) {
            // We currently support only one game per player
	    Game game = myGameRef.get(task);
	    if (game != null) {
		game.leftChannel(cid, uid);
	    }
        } else {
            // If no game, dispatch to the matchmaker
            Matchmaker matchmaker = Matchmaker.get();
	    if (matchmaker != null) {
		matchmaker.leftChannel(cid, uid);
	    }
        }
    }

    public void userDataReceived(UserID uid, ByteBuffer data) {
        log.fine("Player: User " + uid + " direct data");

        if (!uid.equals(myUserID)) {
            log.warning("Player: Got UID " + uid + " expected " + myUserID);
            return;
        }

        SimTask task = SimTask.getCurrent();
        if (myGameRef != null) {
            // We currently support only one game per player
	    Game game = myGameRef.get(task);
	    if (game != null) {
		game.userDataReceived(myUserID, data);
	    }
        } else {
            // If no game, dispatch to the matchmaker
            Matchmaker matchmaker = Matchmaker.get();
	    if (matchmaker != null) {
		matchmaker.userDataReceived(uid, data);
	    }
        }
    }

    public void dataArrivedFromChannel(ChannelID cid, UserID uid,
            ByteBuffer data)
    {
	// no-op, since we don't evesdrop channel data in this app
    }
}
