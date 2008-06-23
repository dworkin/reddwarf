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
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;


/**
 * This implementation of <code>Game</code> is what players actually play
 * with. This represents the named games that a client sees in the lobby, and
 * manages interaction with boards and artificial intelligence.
 */
public class Dungeon implements Game, Serializable {

    private static final long serialVersionUID = 1;

    // a reference to the channel used for all players currently in
    // this dungeon
    private ManagedReference<Channel> channelRef;

    // the name of this particular dungeon
    private String name;

    // the map of sprites that this dungeon uses
    private int spriteMapId;

    // a reference to the game change manager
    private ManagedReference<GameChangeManager> gcmRef;

    // the connection into the dungeon
    private ManagedReference<GameConnector> connectorRef;

    // the set of players in the lobby, mapping from a reference to
    // the player's ClientSession to account name
    private HashMap<ManagedReference<ClientSession>,String> playerMap;

    /**
     * Creates a new instance of a <code>Dungeon</code>.
     *
     * @param name the name of this dungeon
     * @param spriteMapId the sprite map used by this dungeon
     * @param connectorRef the entry <code>Connector</code>
     */
    public Dungeon(String name, int spriteMapId, GameConnector connector) {
        this.name = name;
        this.spriteMapId = spriteMapId;

        DataManager dataManager = AppContext.getDataManager();
        connectorRef = dataManager.createReference(connector);

        // create a channel for all clients in this dungeon, but lock it so
        // that we control who can enter and leave the channel
        Channel channel = // ChannelManager.instance().
	    AppContext.getChannelManager().
            createChannel(NAME_PREFIX + name, null, Delivery.RELIABLE);

        channelRef = dataManager.createReference(channel);

        // initialize the player list
        playerMap = new HashMap<ManagedReference<ClientSession>,String>();

        // get a reference to the membership change manager
        gcmRef = dataManager.createReference(
	    (GameChangeManager) dataManager.getBinding(
		GameChangeManager.IDENTIFIER));
    }

    /**
     * Gets the {@code Channel} associated with {@link #channelRef}.
     *
     * @return the channel or {@code null} if {@code channelRef} is
     *         {@code null}.
     */
    private Channel channel() {
        return channelRef.get();
    }

    /**
     * Adds the given <code>Player</code> to this <code>Game</code>.
     *
     * @param player the <code>Player</code> that is joining
     */
    public void join(Player player) {
        DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);

        // update all existing members about the new uid's name
        ClientSession session = player.getCurrentSession();
        String playerName = player.getName();
	ClientSession [] users = new ClientSession[playerMap.size()];
	int i = 0;
	for (ManagedReference<ClientSession> sessionRef : playerMap.keySet()) {
	    users[i++] = sessionRef.get();
	}
        Messages.sendUidMap(session, playerName, channel(), users);

        // add the player to the dungeon channel and the local map
        channel().join(session);
        playerMap.put(dataManager.createReference(session), playerName);

        // update the player about all uid to name mappings on the channel
        Messages.sendUidMap(playerMap, channel(), session);

        // notify the manager that our membership count changed
        sendCountChanged();

        // notify the client of the sprites we're using
        SpriteMap spriteMap = (SpriteMap) dataManager.getBinding(
	    SpriteMap.NAME_PREFIX + spriteMapId);
        Messages.sendSpriteMap(spriteMap, channel(), session);

        Messages.sendPlayerJoined(player.getCurrentSession(), channel());

        // finally, throw the player into the game through the starting
        // connection point ... the only problem is that the channel info
        // won't be there when we try to send a board (because we still have
        // the lock on the Player, so its userJoinedChannel method can't
        // have been called yet), so set the channel directly
        player.userJoinedChannel(channel());
        PlayerCharacter pc =
            (PlayerCharacter)(player.getCharacterManager().
                              getCurrentCharacter());
        player.sendCharacter(pc);
        connectorRef.get().enteredConnection(player.getCharacterManager());
    }

    /**
     * Removed the given <code>Player</code> from this <code>Game</code>.
     *
     * @param player the <code>Player</code> that is leaving
     */
    public void leave(Player player) {
        AppContext.getDataManager().markForUpdate(this);

        Messages.sendPlayerLeft(player.getCurrentSession(), channel());

        // remove the player from the dungeon channel and the player map
        ClientSession session = player.getCurrentSession();
        channel().leave(player.getCurrentSession());
        playerMap.remove(AppContext.getDataManager().createReference(session));

        // just to be paranoid, we should make sure that they're out of
        // their current level...for instance, if we got called because the
        // player logged out, or was killed
        player.leaveCurrentLevel();

        // notify the manager that our membership count changed
        sendCountChanged();
    }

    /**
     * Private helper that notifies the membership manager of an updated
     * membership count for this game.
     */
    private void sendCountChanged() {
        GameMembershipDetail detail =
                new GameMembershipDetail(getName(), numPlayers());
        gcmRef.get().notifyMembershipChanged(detail);
    }

    /**
     * Creates a new instance of a <code>DungeonMessageHandler</code>.
     *
     * @return a <code>DungeonMessageHandler</code>
     */
    public MessageHandler createMessageHandler() {
        return new DungeonMessageHandler(this);
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
