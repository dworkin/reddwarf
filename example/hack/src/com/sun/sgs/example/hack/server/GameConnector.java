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
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.Connector;
import com.sun.sgs.example.hack.server.level.Level;

import java.io.Serializable;


/**
 * This is a <code>Connector</code> that is used to connect a
 * <code>game</code> to an initial <code>Level</code>. The common use for
 * this class is to connect to the <code>Lobby</code>, though in practice
 * this class may be used to move between any games.
 */
public class GameConnector implements Connector, Serializable {

    private static final long serialVersionUID = 1;

    // the game at one end of the connection
    private ManagedReference<Game> connectedGame;

    // the level at the other end of the connection
    private ManagedReference<Level> connectedLevel;

    // the position on the level where we connect
    private int startX;
    private int startY;

    /**
     * Creates an instance of <code>GameConnector</code>.
     *
     * @param gameRef the game this connects to
     * @param levelRef the level this connects to
     * @param startX the x-coodinate on the level
     * @param startY the y-coodinate on the level
     */
    public GameConnector(Game game, Level level, int startX, int startY) {
        DataManager dataManager = AppContext.getDataManager();
        connectedGame = dataManager.createReference(game);
        connectedLevel = dataManager.createReference(level);

        this.startX = startX;
        this.startY = startY;
    }
    
    /**
     * Transitions the given character to the game if they're in the level,
     * and to the level if they're in the game.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(CharacterManager mgr) {
        Level level = mgr.getCurrentLevel();

        // see what state the character is in, which tells us which direction
        // they're going in
        if (level == null) {
            // they're not currently on a level, which means that they're
            // not yet playing a game, so move them in
            connectedLevel.get().addCharacter(mgr, startX, startY);
        } else {
            // they're leaving the game for the lobby...only players can
            // move into the lobby, so make sure there are no AIs trying
            // to sneak through
            return false;
            /*
            if (! (mgr instanceof PlayerCharacterManager))
                return false;

            // FIXME: should this be queued?
            Player player = ((PlayerCharacterManager)mgr).getPlayer();
            player.moveToGame(connectedGame.get(Game.class));*/
        }

        return true;
    }

}
