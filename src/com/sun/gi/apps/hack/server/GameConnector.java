
/*
 * GameConnector.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Mar  3, 2006	 9:51:41 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Connector;
import com.sun.gi.apps.hack.server.level.Level;


/**
 * This is a <code>Connector</code> that is used to connect a
 * <code>game</code> to an initial <code>Level</code>. The common use for
 * this class is to connect to the <code>Lobby</code>, though in practice
 * this class may be used to move between any games.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameConnector implements Connector
{

    // the game at one end of the connection
    private GLOReference<? extends Game> connectedGame;

    // the level at the other end of the connection
    private GLOReference<? extends Level> connectedLevel;

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
    public GameConnector(GLOReference<? extends Game> gameRef,
                         GLOReference<? extends Level> levelRef,
                         int startX, int startY) {
        this.connectedGame = gameRef;
        this.connectedLevel = levelRef;

        this.startX = startX;
        this.startY = startY;
    }
    
    /**
     * Transitions the given character to the game if they're in the level,
     * and to the level if they're in the game.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(GLOReference<? extends CharacterManager>
                                  mgrRef) {
        SimTask task = SimTask.getCurrent();
        CharacterManager mgr = mgrRef.peek(task);
        GLOReference<? extends Level> levelRef = mgr.getCurrentLevel();

        // see what state the character is in, which tells us which direction
        // they're going in
        if (levelRef == null) {
            // they're not currently on a level, which means that they're
            // not yet playing a game, so move them in
            connectedLevel.get(task).addCharacter(mgrRef, startX, startY);
        } else {
            // they're leaving the game for the lobby...only players can
            // move into the lobby, so make sure there are no AIs trying
            // to sneak through
            if (! (mgr instanceof PlayerCharacterManager))
                return false;

            // FIXME: should this be queued?
            Player player = ((PlayerCharacterManager)mgr).
                getPlayerRef().get(task);
            player.moveToGame(connectedGame);
        }

        return true;
    }

}
