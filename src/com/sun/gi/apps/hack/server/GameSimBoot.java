
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
        task.registerTimerEvent(GameChangeManager.CHANGE_MANAGER_FREQUENCY,
                                true, gcmRef);

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
