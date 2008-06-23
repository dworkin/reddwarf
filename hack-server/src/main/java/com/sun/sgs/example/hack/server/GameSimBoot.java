/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


/**
 * This is the root element of the game demo app. It gets invoked when
 * when the server is starting up, and is responsible for setting up the
 * rest of the game. Other than acting as a boot-strap service, it does
 * little except listen for joining and leaving connections.
 */
public class GameSimBoot implements AppListener, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * Standard location for the "dungeons file" that defines all the
     * games available through this app instance.
     */
    public static final String DUNGEON_FILE_DEFAULT =
        "gameData/dungeons";

    /**
     * The property you define to override the standard dungeons file
     * location.
     */
    public static final String DUNGEON_FILE_PROPERTY =
        "com.sun.sgs.example.hack.server.DungeonFile";

    /**
     * The number of milliseconds between update calls to the membership
     * change manager.
     */
    public static final int CHANGE_MANAGER_FREQUENCY = 4000;

    // the lobby reference
    private ManagedReference<Lobby> lobbyRef = null;

    // the creator reference
    private ManagedReference<Creator> creatorRef = null;

    /**
     * Called by the game server to actually start this game application.
     * This method will install the initial listeners and create the basic
     * logic held throughout the lifetime of the game app.
     *
     * @param properties the application properties loaded from the
     *                   application's configuration file
     * 
     */
    public void initialize(Properties properties) {
        DataManager dataManager = AppContext.getDataManager();

        // first, we get a reference to the GameChangeManager, used to
        // manage all membership updates to the games, and set it up as
        // a timer that gets called back periodically
        GameChangeManager gcm = GameChangeManager.getInstance();

        // next, setup the "lobby" where players will choose characters and
        // games to play
        Lobby lobby = Lobby.getInstance(gcm);
        lobbyRef = dataManager.createReference(lobby);

        // add the lobby as a listener for membership change events
        gcm.addGameChangeListener(lobby);

        // setup the creator game, which is used for character management
        creatorRef = dataManager.createReference(Creator.getInstance());

        // finally, load and setup all the games that are defined for this
        // app, by looking in the dungeons file
        try {
            String appRoot = properties.getProperty("com.sun.sgs.app.root") +
                File.separator;
            String dFile = properties.getProperty(DUNGEON_FILE_PROPERTY,
                                                  DUNGEON_FILE_DEFAULT);
            DungeonDataLoader.setupDungeons(appRoot, dFile, lobby, gcm);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to load dungeon data", ioe);
        }

        // Now that things are set up, run the event updater
        AppContext.getTaskManager().schedulePeriodicTask(gcm, 1000,
            GameChangeManager.CHANGE_MANAGER_FREQUENCY);
    }

    /**
     * Called when a new user joins the game app.
     *
     * @param session the user's client session
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        // get a reference to the player, creating the object if needed
        Player player = Player.getInstance(session.getName());

        // make sure that the player isn't already logged in and playing
        if (player.isPlaying()) {
            // NOTE: we currently don't handle this case, but in a
            //       more robust system, the application should take a
            //       specific action to either disconnect the extra
            //       session, or to perform some application-specific
            //       logic for handling a client with two session
        }

        // let the player know what their new session is
        player.setCurrentSession(session);

        // now that we have a valid Player, send it off to the lobby,
        // unless they have no characters, in which case they need to go
        // to the creator first

        ManagedReference<? extends Game> gameRef = null;
        if (player.getCharacterManager().getCharacterCount() == 0)
            gameRef = creatorRef;
        else
            gameRef = lobbyRef;
        AppContext.getTaskManager().
            scheduleTask(new MoveGameTask(player, gameRef.get()));

        // NOTE WELL: At this point, the Player is being passed off to a
        // game, but until the moveToGame method is invoked, this Player is
        // still listed as not playing a game. This means that there's a
        // small window open for userJoined to be called again and get the
        // player's lock. It's extremely difficult to trip this case, but
        // theoretically it can be done. To protect against it, the Player
        // should mark "logged in" and "playing" as separate states.

        return player;
    }

}
