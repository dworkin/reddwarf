/*
 * Copyright 2007 Sun Microsystems, Inc.
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
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.util.UtilChannel;
import com.sun.sgs.app.util.UtilChannelManager;

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
    private ManagedReference gcmRef;

    // the channel used for all players currently in the lobby
    private ManagedReference channelRef;

    // the set of players in the lobby, mapping from uid to account name
    private HashMap<ClientSession,String> playerMap;

    // the map for player counts in each game
    private HashMap<String,GameMembershipDetail> countMap;

    private UtilChannel channel() {
        return channelRef.get(UtilChannel.class);
    }

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
        UtilChannel channel = UtilChannelManager.instance().
            createChannel(IDENTIFIER, null, Delivery.RELIABLE);

        channelRef = dataManager.createReference(channel);

        // keep track of the MembershipChangeManager ref
        gcmRef = dataManager.createReference(gcm);

        // initialize the player list
        playerMap = new HashMap<ClientSession,String>();

        // initialize the count for each game
        countMap = new HashMap<String,GameMembershipDetail>();
    }

    /**
     * Provides access to the single instance of <code>Lobby</code>. If
     * the lobby hasn't already been created, then a new instance is
     * created and added as a registered <code>GLO</code>. If the lobby
     * already exists then nothing new is created.
     * <p>
     * This method implements the pattern described in the programmer's
     * notes document, so that it's safe against multiple simultaneous
     * accesses when the lobby doesn't already exist. In practice, this
     * isn't actually a concern in this app, because this method is never
     * called by more than once party. Still, it's good defensive
     * programming to protect against future models that may change our
     * current access assumptions.
     *
     * @param gcmRef a reference to the manager we'll notify when lobby
     *               membership counts change
     *
     * @return a reference to the single <code>Lobby</code>
     */
    public static Lobby getInstance(GameChangeManager gcm) {
        DataManager dataManager = AppContext.getDataManager();

        // try to get an existing reference
        Lobby lobby = null;
        try {
            lobby = dataManager.getBinding(IDENTIFIER, Lobby.class);
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
        // FIXME: this was going to be a queued task, but that tripped the
        // classloading bug that has now been fixed...should we go back to
        // the queue model?
        GameMembershipDetail detail =
            new GameMembershipDetail(IDENTIFIER, numPlayers() + 1);
        gcmRef.get(GameChangeManager.class).notifyMembershipChanged(detail);

        // update all existing members about the new uid's name
        ClientSession session = player.getCurrentSession();
        String playerName = player.getName();
        Messages.sendUidMap(session, playerName, channel(), getCurrentUsers());

        // add the player to the lobby channel and the player map
        channel().join(session, null);
        playerMap.put(session, playerName);
        player.userJoinedChannel(channel());

        // update the player about all uid to name mappings on the channel
        Messages.sendUidMap(playerMap, channel(), session);

        Messages.sendPlayerJoined(player.getCurrentSession(), channel());

        // finally, send the player a welcome message...we need to create
        // a new set around the details because the backing collection
        // provided by Map.values() isn't serializable
        HashSet<GameMembershipDetail> set =
            new HashSet<GameMembershipDetail>(countMap.values());
        Messages.sendLobbyWelcome(set, channel(), session);
        HashSet<CharacterStats> characters = new HashSet<CharacterStats>();
        for (Character character :
                 player.getCharacterManager().getCharacters())
            characters.add(character.getStatistics());
        Messages.sendPlayerCharacters(characters, channel(), session);
    }

    /**
     * Removes the player from the lobby.
     *
     * @param player the <code>Player</code> leaving the lobby
     */
    public void leave(Player player) {
        AppContext.getDataManager().markForUpdate(this);

        Messages.sendPlayerLeft(player.getCurrentSession(), channel());

        // remove the player from the lobby channel and the local map
        ClientSession session = player.getCurrentSession();
        channel().leave(session);
        playerMap.remove(session);

        // send an update about the new lobby membership count
        // FIXME: this was going to be a queued task, but that tripped the
        // classloading bug that has now been fixed...should we go back to
        // the queue model?
        GameMembershipDetail detail =
            new GameMembershipDetail(IDENTIFIER, numPlayers());
        gcmRef.get(GameChangeManager.class).notifyMembershipChanged(detail);
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
        return playerMap.keySet().toArray(new ClientSession[playerMap.size()]);
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
            Messages.sendGameAdded(game, channel(), users);
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
            Messages.sendGameRemoved(game, channel(), users);
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
        // FIXME: should we just send the collection, instead of sending
        // each change separately?
        for (GameMembershipDetail detail : details) {
            countMap.put(detail.getGame(), detail);
            Messages.sendGameCountChanged(detail.getGame(), detail.getCount(),
                                          channel(), users);
        }
    }

}
