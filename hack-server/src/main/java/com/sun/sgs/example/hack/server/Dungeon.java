/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.app.util.ScalableHashMap;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.io.Serializable;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.Map;


/**
 * This implementation of <code>Game</code> is what players actually play
 * with. This represents the named games that a client sees in the lobby, and
 * manages interaction with boards and artificial intelligence.
 */
public class Dungeon implements Game, Serializable {

    private static final long serialVersionUID = 1;

    // a reference to the channel used for sending commands to all
    // players currently in this dungeon
    private ManagedReference<Channel> dungeonCommandsChannel;    

    // the name of this particular dungeon
    private String name;

    // the map of sprites that this dungeon uses
    private int spriteMapId;

    // a reference to the game change manager
    private ManagedReference<GameChangeManager> gcmRef;

    // the connection into the dungeon
    private ManagedReference<GameConnector> connectorRef;

    /**
     * The a mapping of players currently the dungeon to account name.
     * This map is will grow with the number of players and therefore
     * needs to be adaptive.
     */
    private ManagedReference<? extends Map<ManagedReference<ClientSession>,String>>
	playerMapRef;

    /**
     * The number of players currently in the Dungeon.  Because we are
     * using a {@code ScalableHashMap} to keep track of the members,
     * we need to keep track of the size ourselves.
     *
     * @see ScalableHashMap#size()
     */
    private int numPlayers;

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
	
        dungeonCommandsChannel = dataManager.createReference(channel);

        // initialize the player list
	ScalableHashMap<ManagedReference<ClientSession>,String> playerMap = 
	    new ScalableHashMap<ManagedReference<ClientSession>,String>();
	
	playerMapRef = dataManager.createReference(playerMap);

	numPlayers = 0;

        // get a reference to the membership change manager
        gcmRef = dataManager.createReference(
	    (GameChangeManager) dataManager.getBinding(
		GameChangeManager.IDENTIFIER));
    }


    /**
     * Adds the given <code>Player</code> to this <code>Game</code>.
     *
     * @param player the <code>Player</code> that is joining
     *
     * @return the {@code MessageHandler} that will process the
     *         provided player's messages
     */
    public MessageHandler join(Player player) {

        DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);

	ClientSession session = player.getCurrentSession();
	String playerName = player.getName();
	BigInteger playerID = dataManager.createReference(session).getId();

	DungeonMessageHandler messageHandler = new DungeonMessageHandler(this);
	 
        // Update all existing members about the new uid's name
	Messages.broadcastPlayerID(dungeonCommandsChannel.get(), playerName, 
				   playerID);
	
        // Add the player to the dungeon channel and the local map
        dungeonCommandsChannel.get().join(session);
        playerMapRef.get().put(dataManager.createReference(session), playerName);

        // Update the player about all uid to name mappings on the
        // channel.  We'll use each of the client's session's ids for
        // the unique identifier.
	Map<BigInteger,String> idsToNames = new HashMap<BigInteger,String>();
	Messages.sendBulkPlayerIDs(session, playerMapRef.get());

        // Notify the manager that our membership count changed
        sendCountChanged();

        // Notify the client of the sprites we're using
        SpriteMap spriteMap = (SpriteMap) dataManager.getBinding(
	    SpriteMap.NAME_PREFIX + spriteMapId);

        Messages.sendSpriteMap(session, spriteMap);

        Messages.broadcastPlayerJoined(dungeonCommandsChannel.get(), playerID);

        // Finally, throw the player into the game through the starting
        // connection point ... the only problem is that the channel info
        // won't be there when we try to send a board (because we still have
        // the lock on the Player, so its userJoinedChannel method can't
        // have been called yet), so set the channel directly
        player.userJoinedChannel(dungeonCommandsChannel.get());
        PlayerCharacter pc =
            (PlayerCharacter)(player.getCharacterManager().
                              getCurrentCharacter());
        player.sendCharacter(pc);
        connectorRef.get().enteredConnection(player.getCharacterManager());

	numPlayers++;

	return messageHandler;
    }

    /**
     * Removed the given <code>Player</code> from this <code>Game</code>.
     *
     * @param player the <code>Player</code> that is leaving
     */
    public void leave(Player player) {
	DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);

	ClientSession session = player.getCurrentSession();
	ManagedReference<ClientSession> sessionRef = 
	    dataManager.createReference(session);
	BigInteger playerID = sessionRef.getId();

        Messages.broadcastPlayerLeft(dungeonCommandsChannel.get(), playerID);

        // remove the player from the dungeon channel and the player map
        dungeonCommandsChannel.get().leave(session);
        playerMapRef.get().remove(sessionRef);

        // just to be paranoid, we should make sure that they're out of
        // their current level...for instance, if we got called because the
        // player logged out, or was killed
        player.leaveCurrentLevel();

        // notify the manager that our membership count changed
        sendCountChanged();

	numPlayers--;
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
        return numPlayers;
    }

    public String toString() {
	return getName();
    }

}
