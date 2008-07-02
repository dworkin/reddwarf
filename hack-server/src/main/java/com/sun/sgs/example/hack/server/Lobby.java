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
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.io.Serializable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


/**
 * The lobby is where all players go to join a game, and it manages players
 * while they're deciding which game to join. The lobby maintains a list of
 * current players, as well as player counts for each of the games, and is
 * notified when those counts change. The lobby is responsible for telling
 * players this information when they join, and broadcasting any changes in
 * membership counts or available games. It also provides the interface for
 * managing <code>Player<code>'s characters. There is a single
 * <code>Lobby</code> instance for each game app.
 * <p>
 * While in the lobby all players are on the same channel, which is used for
 * chatting. Once moved into another game, players are removed from the
 * lobby channel.
 */
public class Lobby implements Game, GameChangeListener, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * The identifier for the lobby
     */
    public static final String IDENTIFIER = NAME_PREFIX + "lobby";

    // a reference to the game change manager
    private ManagedReference<GameChangeManager> gcmRef;

    // the channel used for all players currently in the lobby
    private ManagedReference<Channel> channelRef;

    // the set of players in the lobby, mapping from the reference to
    // player's session to account name
    private HashMap<ManagedReference<ClientSession>,String> playerMap;

    // the map for player counts in each game
    private HashMap<String,GameMembershipDetail> countMap;
    
    /**
     * Creates an instance of <code>Lobby</code>. In practice there should
     * only ever be one of these, so we don't all direct access to the
     * constructor. Instead, you get access through <code>getInstance</code>
     * and that enforces the singleton.
     *
     * @param task the task this is running in
     * @param gcmRef a reference to the manager we'll notify when lobby
     *               membership counts change
     */
    private Lobby(GameChangeManager gcm) {
        DataManager dataManager = AppContext.getDataManager();

        // create a channel for all clients in the lobby, but lock it so
        // that we control who can enter and leave the channel

	Channel channel = AppContext.getChannelManager().
            createChannel(IDENTIFIER, null, Delivery.RELIABLE);

        channelRef = dataManager.createReference(channel);

        // keep track of the MembershipChangeManager ref
        gcmRef = dataManager.createReference(gcm);

        // initialize the player list
        playerMap = new HashMap<ManagedReference<ClientSession>,String>();

        // initialize the count for each game
        countMap = new HashMap<String,GameMembershipDetail>();
    }

    /**
     * Provides access to the single instance of {@code Lobby}. If the
     * lobby hasn't already been created, then a new instance is
     * created and added as a registered {@code ManagedObject}. If the
     * lobby already exists then nothing new is created.
     *
     * <p>
     *
     * This method implements the pattern described in the
     * programmer's notes document, so that it's safe against multiple
     * simultaneous accesses when the lobby doesn't already exist. In
     * practice, this isn't actually a concern in this app, because
     * this method is never called by more than once party. Still,
     * it's good defensive programming to protect against future
     * models that may change our current access assumptions.
     *
     * @param gcm the manager we'll notify when lobby membership
     *            counts change
     *
     * @return the single <code>Lobby</code>
     */
    public static Lobby getInstance(GameChangeManager gcm) {
        DataManager dataManager = AppContext.getDataManager();

        // try to get an existing reference
        Lobby lobby = null;
        try {
            lobby = (Lobby) dataManager.getBinding(IDENTIFIER);
        } catch (NameNotBoundException e) {
            lobby = new Lobby(gcm);
            dataManager.setBinding(IDENTIFIER, lobby);
        }

        return lobby;
    }

    /**
     * Joins a player to the lobby. This is done when a player first connects,
     * and whenever they leave an active game.
     *
     * @param player the <code>Player</code> joining the lobby
     */
    public void join(Player player) {
        AppContext.getDataManager().markForUpdate(this);

        // send an update about the new lobby membership count
        GameMembershipDetail detail =
            new GameMembershipDetail(IDENTIFIER, numPlayers() + 1);
        gcmRef.get().notifyMembershipChanged(detail);

        // update all existing members about the new uid's name
        ClientSession session = player.getCurrentSession();
	ManagedReference<ClientSession> sessionRef = 
	    AppContext.getDataManager().createReference(session);
        String playerName = player.getName();
        Messages.sendUidMap(session, playerName, channelRef.get(), 
			    getCurrentUsers());

        // add the player to the lobby channel and the player map
        channelRef.get().join(session);
        playerMap.put(sessionRef, playerName);
        player.userJoinedChannel(channelRef.get());
	
        // update the player about all uid to name mappings on the channel
        Messages.sendUidMap(playerMap, channelRef.get(), session);

        Messages.sendPlayerJoined(player.getCurrentSession(), channelRef.get());

        // finally, send the player a welcome message...we need to create
        // a new set around the details because the backing collection
        // provided by Map.values() isn't serializable
        HashSet<GameMembershipDetail> set =
            new HashSet<GameMembershipDetail>(countMap.values());
        Messages.sendLobbyWelcome(set, channelRef.get(), session);
        HashSet<CharacterStats> characters = new HashSet<CharacterStats>();
        for (Character character :
                 player.getCharacterManager().getCharacters())
            characters.add(character.getStatistics());
        Messages.sendPlayerCharacters(characters, channelRef.get(), session);
    }

    /**
     * Removes the player from the lobby.
     *
     * @param player the <code>Player</code> leaving the lobby
     */
    public void leave(Player player) {
        AppContext.getDataManager().markForUpdate(this);

        Messages.sendPlayerLeft(player.getCurrentSession(), channelRef.get());

        // remove the player from the lobby channel and the local map
        ClientSession session = player.getCurrentSession();
	ManagedReference<ClientSession> sessionRef = 
	    AppContext.getDataManager().createReference(session);

        channelRef.get().leave(session);
        playerMap.remove(sessionRef);

        // send an update about the new lobby membership count
        // FIXME: this was going to be a queued task, but that tripped the
        // classloading bug that has now been fixed...should we go back to
        // the queue model?
        GameMembershipDetail detail =
            new GameMembershipDetail(IDENTIFIER, numPlayers());
        gcmRef.get().notifyMembershipChanged(detail);
    }

    /**
     * Creates a new instance of a <code>LobbyMessageHandler</code>.
     *
     * @return a <code>LobbyMessageHandler</code>
     */
    public MessageHandler createMessageHandler() {
        return new LobbyMessageHandler();
    }

    /**
     * Returns the name of the lobby. This is also specified by the local
     * field <code>IDENTIFIER</code>.
     *
     * @return the name
     */
    public String getName() {
        return IDENTIFIER;
    }

    /**
     * Returns the number of players currently in the lobby.
     *
     * @return the number of players in the lobby
     */
    public int numPlayers() {
        return playerMap.size();
    }

    /**
     * Private helper method that bundles the current set of player's UserIDs
     * into an array to use in broadcasting messages.
     */
    private ClientSession [] getCurrentUsers() {
	ClientSession[] users = new ClientSession[playerMap.size()];
	int i = 0;
        for (ManagedReference<ClientSession> sessionRef :  playerMap.keySet()) {
	    users[i++] = sessionRef.get();
	}
	return users;
    }

    /**
     * Notifies the listener that some games were added to the app.
     *
     * @param games the games that were added
     */
    public void gameAdded(Collection<String> games) {
        AppContext.getDataManager().markForUpdate(this);

        ClientSession [] users = getCurrentUsers();

        // send out notice of the new games
        for (String game : games) {
            countMap.put(game, new GameMembershipDetail(game, 0));
            Messages.sendGameAdded(game, channelRef.get(), users);
        }
    }

    /**
     * Notifies the listener that some games were removed from the app.
     *
     * @param games the games that were removed
     */
    public void gameRemoved(Collection<String> games) {
        AppContext.getDataManager().markForUpdate(this);

        ClientSession [] users = getCurrentUsers();

        // send out notice of the removed games
        for (String game : games) {
            Messages.sendGameRemoved(game, channelRef.get(), users);
            countMap.remove(game);
        }
    }

    /**
     * Called when it's time to send out membership change messages. This
     * method will broadcast changes to all current members of the lobby.
     *
     * @param details the membership details
     */
    public void membershipChanged(Collection<GameMembershipDetail> details) {
        AppContext.getDataManager().markForUpdate(this);

        ClientSession [] users = getCurrentUsers();

        // for each change, track the detail locally (to send in welcome
        // messages when players first join) and send a message to all
        // current lobby members

        // NOTE: depending on how the application treats individual
        //       transmissions, the collection could be sent at once,
        //       instead of sending each change separately
        for (GameMembershipDetail detail : details) {
            countMap.put(detail.getGame(), detail);
            Messages.sendGameCountChanged(detail.getGame(), detail.getCount(),
                                          channelRef.get(), users);
        }
    }

}
