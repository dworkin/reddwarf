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


/*
 * GameSimBoot.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Feb 17, 2006	 4:39:10 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;

import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;


/**
 * This is the root element of the game demo app. It gets invoked when
 * when the server is starting up, and is responsible for setting up the
 * rest of the game. Other than acting as a boot-strap service, it does
 * little except listen for joining and leaving connections.
 * <p>
 * Note that when you write your implementation of <code>SimBoot</code>
 * you don't have to also implement <code>SimUserListener</code> in the
 * same class. Because the two interfaces do very little, however, this
 * is an easy way to start building your game.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameSimBoot implements SimBoot<GameSimBoot>, SimUserListener
{

    /**
     * Standard location for the "dungeons file" that defines all the
     * games available through this app instance.
     */
    public static final String DUNGEON_FILE_DEFAULT =
	"apps/hack/data/dungeons";

    /**
     * The Java property you define to override the standard dungeons
     * file location.
     * FIXME: what should this be?
     */
    public static final String DUNGEON_FILE_PROPERTY =
        "com.sun.gi.apps.hack.server.DungeonFile";

    /**
     * The number of milliseconds between update calls to the membership
     * change manager.
     */
    public static final int CHANGE_MANAGER_FREQUENCY = 4000;

    // the lobby reference
    private GLOReference<Lobby> lobbyRef = null;

    // a local map of uids to references to the players the uids represent
    private HashMap<UserID,GLOReference<Player>> playerMap;

    /**
     * Creates an instance of <code>GameSimBoot</code>. Each instance will
     * represent a unique app running in the game server.
     */
    public GameSimBoot() {
        playerMap = new HashMap<UserID,GLOReference<Player>>();
    }

    /**
     * Called by the game server to actually start this game application.
     * This method will install the initial listeners and create the basic
     * logic held throughout the lifetime of the game app.
     *
     * @param thisGLO a reference to this boot object
     * @param firstBoot true if this is the first time the app has been booted
     */
    public void boot(GLOReference<? extends GameSimBoot> thisGLO,
                     boolean firstBoot) {
        SimTask task = SimTask.getCurrent();

        // register ourselves as a user listener, so we will be notified
        // whenever a client joins or leaves the server ... note that this
        // must be done even if we've been booted before
        task.addUserListener(thisGLO);

        // first, we get a reference to the GameChangeManager, used to
        // manage all membership updates to the games, and set it up as
        // a timer that gets called back periodically
        GLOReference<GameChangeManager> gcmRef =
            GameChangeManager.getInstance();

        // next, setup the "lobby" where players will choose characters and
        // games to play
        lobbyRef = Lobby.getInstance(gcmRef);

        // add the lobby as a listener for membership change events
        gcmRef.get(task).addGameChangeListener(lobbyRef);

        // FIXME: create the character creator here

        // finally, load and setup all the games that are defined for this
        // app, by looking in the dungeons file
        try {
            String dFile = System.getProperty(DUNGEON_FILE_PROPERTY,
		    DUNGEON_FILE_DEFAULT);
            DungeonDataLoader.setupDungeons(dFile, lobbyRef, gcmRef);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Now that things are set up, run the event updater
        task.registerTimerEvent(GameChangeManager.CHANGE_MANAGER_FREQUENCY,
                                true, gcmRef);
    }

    /**
     * Called when a new user joins the game app.
     *
     * @param uid the user's identifier
     * @param subject the user's credentials and other details
     */
    public void userJoined(UserID uid, Subject subject) {
        SimTask task = SimTask.getCurrent();

        // get the user's name, which is how we'll key their Player GLO
        String name = subject.getPrincipals().iterator().next().getName();

        // get a reference to the player, creating the object if needed
        GLOReference<Player> playerRef = Player.getInstance(name);
        Player player = playerRef.get(task);

        // make sure that the player isn't already logged in and playing
        if (player.isPlaying()) {
            // FIXME: how do we close this connection and return?
        }

        // register the player object to listen for messages to its uid (in
        // this app, these are always command messages from the client)
        task.addUserDataListener(uid, playerRef);

        // add the mapping to our local map, and let the player know what
        // their new uid is
        playerMap.put(uid, playerRef);
        player.setCurrentUid(uid);

        // now that we have a valid Player ref, send it off to the lobby
        // FIXME: This should actually go to the creator if the player
        // doesn't yet have any characters
        try {
            Method method =
                Player.class.getMethod("moveToGame", GLOReference.class);
            task.queueTask(playerRef, method, new Object [] {lobbyRef});
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException(nsme.getMessage());
        }

        // NOTE WELL: At this point, the Player is being passed off to a
        // game, but until the moveToGame method is invoked, this Player is
        // still listed as not playing a game. This means that there's a
        // small window open for userJoined to be called again and get the
        // player's lock. It's extremely difficult to trip this case, but
        // theoretically it can be done. To protect against it, the Player
        // should mark "logged in" and "playing" as separate states.
    }

    /**
     * Called when a user leaves the game app.
     *
     * @param uid the user's identifier
     */
    public void userLeft(UserID uid) {
        SimTask task = SimTask.getCurrent();

        // find the player logic ... we have to check for null, because
        // if the player was kicked out during login, they may not actually
        // have an object here
        if (playerMap.containsKey(uid)) {
            // move the player to the null game, and remove them from the
            // map of logged in players
            playerMap.get(uid).get(task).moveToGame(null);
            playerMap.remove(uid);
        }
    }

}
