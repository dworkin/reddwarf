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
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.io.Serializable;

import java.math.BigInteger;

/**
 * The creator is where all players can create new characters. It maintains
 * a list of who is currently creating characters, so those players can
 * chat with each other. Beyond this, there is no interactivity, and nothing
 * that the creator game pushes out players.
 */
public class Creator implements Game, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * The identifier for the creator
     */
    public static final String IDENTIFIER = NAME_PREFIX + "creator";

    // a reference to the channel used for all players currently in
    // the lobby
    private final ManagedReference<Channel> creatorCommandsChannel;

    // the number of players interacting with the creator
    private int playerCount = 0;


    /**
     * Creates an instance of <code>Creator</code>. In practice there should
     * only ever be one of these, so we don't all direct access to the
     * constructor. Instead, you get access through <code>getInstance</code>
     * and that enforces the singleton.
     */
    private Creator() {
        DataManager dataManager = AppContext.getDataManager();
        // create a channel for all clients in the creator, but lock it so
        // that we control who can enter and leave the channel
        Channel channel = AppContext.getChannelManager().
            createChannel(IDENTIFIER, null, Delivery.RELIABLE);

        creatorCommandsChannel = dataManager.createReference(channel);
    }

    /**
     * Provides access to the single instance of <code>Creator</code>. If
     * the creator hasn't already been created, then a new instance is
     * created and added as a registered <code>GLO</code>. If the creator
     * already exists then nothing new is created.
     * <p>
     * See the comments in <code>Lobby</code> for details on this pattern.
     *
     * @return the single <code>Creator</code> instance
     */
    public static Creator getInstance() {
        DataManager dataManager = AppContext.getDataManager();

        // try to get an existing reference
        Creator creator = null;
        try {
            creator = (Creator) dataManager.getBinding(IDENTIFIER);
        } catch (NameNotBoundException e) {
            creator = new Creator();
            dataManager.setBinding(IDENTIFIER, creator);
        }

        return creator;
    }

    /**
     * Joins a player to the creator. This is done when a player logs into
     * the game app for the very first time, and each time that they want
     * to manage their characters.
     *
     * @param player the <code>Player</code> joining the creator
     */
    public void join(Player player) {

        DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

        playerCount++;

	ClientSession playerSession = player.getCurrentSession();
	BigInteger clientID = 
	    dataManager.createReference(playerSession).getId();
	
        // add the player to the channel
        creatorCommandsChannel.get().join(playerSession);
        player.userJoinedChannel(creatorCommandsChannel.get());
        Messages.broadcastPlayerJoined(creatorCommandsChannel.get(), clientID);

	// send the player an initial set of stats
	CharacterStats currentStats = CreatorMessageHandler.getNewStats(-1);
	player.sendCharacterStats(-1, currentStats);

        // NOTE: the idea of this "game" is that it should be used to
        // manage existing characters, create new ones, and delete ones
        // you don't want any more ... for the present, however, it's
        // just used to create characters one at a time, so we don't
        // actually need to send anything to the user now
    }

    /**
     * Removes a player from the creator.
     *
     * @param player the <code>Player</code> leaving the creator
     */
    public void leave(Player player) {
        DataManager dataManager = AppContext.getDataManager();
	dataManager.markForUpdate(this);

        playerCount--;

	ClientSession playerSession = player.getCurrentSession();
	BigInteger clientID = 
	    dataManager.createReference(playerSession).getId();

        Messages.broadcastPlayerLeft(creatorCommandsChannel.get(), clientID);

        // remove the player from the channel
        creatorCommandsChannel.get().leave(player.getCurrentSession());
    }

    /**
     * Creates a new instance of a <code>CreatorMessageHandler</code>.
     *
     * @return a <code>CreatorMessageHandler</code>
     */
    public MessageHandler createMessageHandler() {
        return new CreatorMessageHandler();
    }

    /**
     * Returns the name of the creator. This is also specified by the local
     * field <code>IDENTIFIER</code>.
     *
     * @return the name
     */
    public String getName() {
        return IDENTIFIER;
    }

    /**
     * Returns the number of players currently in the creator.
     *
     * @return the number of players in the creator
     */
    public int numPlayers() {
        return playerCount;
    }

    public String toString() {
	return getName();
    }

}
