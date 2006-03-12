
/*
 * Dungeon.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 2:50:22 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.GameMembershipDetail;

import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.Map;


/**
 * This implementation of <code>Game</code> is what players actually play
 * with. This represents the named games that a client sees in the lobby, and
 * manages interaction with boards and artificial intelligence.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Dungeon implements Game
{

    // a reference to ourself, which we'll get lazily
    private GLOReference<Dungeon> selfRef = null;

    // the channel used for all players currently in this dungeon
    private ChannelID channel;

    // the name of this particular dungeon
    private String name;

    // the map of sprites that this dungeon uses
    private int spriteMapId;

    // a reference to the game change manager
    private GLOReference<GameChangeManager> gcmRef;

    // the connection into the dungeon
    private GLOReference<GameConnector> connectorRef;

    // the set of players in the lobby, mapping from uid to account name
    private HashMap<UserID,String> playerMap;

    /**
     * Creates a new instance of a <code>Dungeon</code>.
     *
     * @param task the task that is running this game
     * @param name the name of this dungeon
     * @param spriteMapId the sprite map used by this dungeon
     * @param connectorRef the entry <code>Connector</code>
     */
    public Dungeon(SimTask task, String name, int spriteMapId,
                   GLOReference<GameConnector> connectorRef) {
        this.name = name;
        this.spriteMapId = spriteMapId;
        this.connectorRef = connectorRef;

        // create a channel for all clients in this dungeon, but lock it so
        // that we control who can enter and leave the channel
        channel = task.openChannel(NAME_PREFIX + name);
        task.lock(channel, true);

        // initialize the player list
        playerMap = new HashMap<UserID,String>();

        // get a reference to the membership change manager
        gcmRef = task.findGLO(GameChangeManager.IDENTIFIER);
    }

    /**
     * Private helper that provides a reference to ourselves. Since we can't
     * get this in the constructor (we're not registered as a GLO at that
     * point), we provide a lazy-assignment accessor here. All code in this
     * class should use this method rather than accessing the reference
     * directly.
     */
    private GLOReference<Dungeon> getSelfRef() {
        if (selfRef == null)
            selfRef = SimTask.getCurrent().findGLO(NAME_PREFIX + getName());

        return selfRef;
    }

    /**
     * Adds the given <code>Player</code> to this <code>Game</code>.
     *
     * @param player the <code>Player</code> that is joining
     */
    public void join(Player player) {
        SimTask task = SimTask.getCurrent();

        // update all existing members about the new uid's name
        UserID uid = player.getCurrentUid();
        String playerName = player.getName();
        UserID [] users = playerMap.keySet().
            toArray(new UserID[playerMap.size()]);
        Messages.sendUidMap(task, uid, playerName, channel, users);

        // add the player to the dungeon channel and the local map
        task.join(uid, channel);
        playerMap.put(uid, playerName);

        // update the player about all uid to name mappings on the channel
        Messages.sendUidMap(task, playerMap, channel, uid);

        // notify the manager that our membership count changed
        sendCountChanged(task);

        // notify the client of the sprites we're using
        GLOReference<SpriteMap> spriteMapRef =
            task.findGLO(SpriteMap.NAME_PREFIX + spriteMapId);
        Messages.sendSpriteMap(task, spriteMapRef.peek(task), channel, uid);

        // finally, throw the player into the game through the starting
        // connection point ... the only problem is that the channel info
        // won't be there when we try to send a board (because we still have
        // the lock on the Player, so its userJoinedChannel method can't
        // have been called yet), so set the channel directly
        player.userJoinedChannel(channel, uid);
        PlayerCharacter pc =
            (PlayerCharacter)(player.getCharacterManager().
                              peek(task).getCurrentCharacter());
        player.sendCharacter(task, pc);
        connectorRef.get(task).
            enteredConnection(player.getCharacterManager());
    }

    /**
     * Removed the given <code>Player</code> from this <code>Game</code>.
     *
     * @param player the <code>Player</code> that is leaving
     */
    public void leave(Player player) {
        SimTask task = SimTask.getCurrent();

        // remove the player from the dungeon channel and the player map
        UserID uid = player.getCurrentUid();
        task.leave(uid, channel);
        playerMap.remove(uid);

        // just to be paranoid, we should make sure that they're out of
        // their current level...for instance, if we got called because the
        // player logged out, or was killed
        player.leaveCurrentLevel();

        // notify the manager that our membership count changed
        sendCountChanged(task);
    }

    /**
     * Private helper that notifies the membership manager of an updated
     * membership count for this game.
     */
    private void sendCountChanged(SimTask task) {
        GameMembershipDetail detail =
                new GameMembershipDetail(getName(), numPlayers());
        gcmRef.get(task).notifyMembershipChanged(detail);

        // FIXME: we used to do the following, but the classloader bug got
        // tripped...now that classloading is fixed, should we go back
        // to a task-based approach?
        /*
        try {
            Method method =
                MembershipChangeManager.class.
                getMethod("notifyMembershipChanged",
                          GameMembershipDetail.class);
            GameMembershipDetail detail =
                new GameMembershipDetail(getName(), numPlayers());
            task.queueTask(mcmRef, method, new Object [] {detail});
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException(nsme.getMessage());
        }
        */
    }

    /**
     * Creates a new instance of a <code>DungeonMessageHandler</code>.
     *
     * @return a <code>DungeonMessageHandler</code>
     */
    public MessageHandler createMessageHandler() {
        return new DungeonMessageHandler(getSelfRef());
    }

    /**
     * Returns the name of this dungeon.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the number of players currently in this dungeon.
     *
     * @return the number of players in this dungeon
     */
    public int numPlayers() {
        return playerMap.size();
    }

}
