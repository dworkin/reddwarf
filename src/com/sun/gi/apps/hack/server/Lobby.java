/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/


/*
 * Lobby.java
 *
 * Created by: seth proctor (stp)
 * Created on: Mon Feb 20, 2006	 4:41:24 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.GameMembershipDetail;

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
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Lobby implements Game, GameChangeListener
{

    /**
     * The identifier for the lobby
     */
    public static final String IDENTIFIER = NAME_PREFIX + "lobby";

    // a reference to the game change manager
    private GLOReference<GameChangeManager> gcmRef;

    // the channel used for all players currently in the lobby
    private ChannelID channel;

    // the set of players in the lobby, mapping from uid to account name
    private HashMap<UserID,String> playerMap;

    // the map for player counts in each game
    private HashMap<String,GameMembershipDetail> countMap;

    /**
     * Creates an instance of <code>Lobby</code>. In practice there should
     * only ever be one of these, so we don't all direct access to the
     * constructor. Instead, you get access through <code>getInstance</code>
     * and that enforces the singleton.
     *
     * @param task the task this is running in
     * @param mcmRef a reference to the manager we'll notify when lobby
     *               membership counts change
     */
    private Lobby(SimTask task, GLOReference<GameChangeManager> gcmRef) {
        // create a channel for all clients in the lobby, but lock it so
        // that we control who can enter and leave the channel
        channel = task.openChannel(IDENTIFIER);
        task.lock(channel, true);

        // keep track of the MembershipChangeManager ref
        this.gcmRef = gcmRef;

        // initialize the player list
        playerMap = new HashMap<UserID,String>();

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
     * @param task the task this is running in
     * @param mcmRef a reference to the manager we'll notify when lobby
     *               membership counts change
     *
     * @return a reference to the single <code>Lobby</code>
     */
    public static GLOReference<Lobby> getInstance(
            GLOReference<GameChangeManager> gcmRef) {
        SimTask task = SimTask.getCurrent();

        // try to get an existing reference
        GLOReference<Lobby> lobbyRef = task.findGLO(IDENTIFIER);

        // if we couldn't find a reference, then create it
        if (lobbyRef == null) {
            lobbyRef = task.createGLO(new Lobby(task, gcmRef), IDENTIFIER);

            // if doing the create returned null then someone beat us to
            // it, so get their already-registered reference
            if (lobbyRef == null)
                lobbyRef = task.findGLO(IDENTIFIER);
        }

        // return the reference
        return lobbyRef;
    }

    /**
     * Joins a player to the lobby. This is done when a player first connects,
     * and whenever they leave an active game.
     *
     * @param player the <code>Player</code> joining the lobby
     */
    public void join(Player player) {
        SimTask task = SimTask.getCurrent();

        // send an update about the new lobby membership count
        // FIXME: this was going to be a queued task, but that tripped the
        // classloading bug that has now been fixed...should we go back to
        // the queue model?
        GameMembershipDetail detail =
            new GameMembershipDetail(IDENTIFIER, numPlayers() + 1);
        gcmRef.get(task).notifyMembershipChanged(detail);

        // update all existing members about the new uid's name
        UserID uid = player.getCurrentUid();
        String playerName = player.getName();
        Messages.sendUidMap(task, uid, playerName, channel, getCurrentUsers());

        // add the player to the lobby channel and the player map
        task.join(uid, channel);
        playerMap.put(uid, playerName);

        // update the player about all uid to name mappings on the channel
        Messages.sendUidMap(task, playerMap, channel, uid);

        // finally, send the player a welcome message...we need to create
        // a new set around the details because the backing collection
        // provided by Map.values() isn't serializable
        HashSet<GameMembershipDetail> set =
            new HashSet<GameMembershipDetail>(countMap.values());
        Messages.sendLobbyWelcome(task, set, channel, uid);
        HashSet<CharacterStats> characters = new HashSet<CharacterStats>();
        for (Character character : player.getCharacterManager().
                 peek(task).getCharacters())
            characters.add(character.getStatistics());
        Messages.sendPlayerCharacters(task, characters, channel, uid);
    }

    /**
     * Removes the player from the lobby.
     *
     * @param player the <code>Player</code> leaving the lobby
     */
    public void leave(Player player) {
        SimTask task = SimTask.getCurrent();

        // remove the player from the lobby channel and the local map
        UserID uid = player.getCurrentUid();
        task.leave(uid, channel);
        playerMap.remove(uid);

        // send an update about the new lobby membership count
        // FIXME: this was going to be a queued task, but that tripped the
        // classloading bug that has now been fixed...should we go back to
        // the queue model?
        GameMembershipDetail detail =
            new GameMembershipDetail(IDENTIFIER, numPlayers());
        gcmRef.get(task).notifyMembershipChanged(detail);
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
    private UserID [] getCurrentUsers() {
        return playerMap.keySet().toArray(new UserID[playerMap.size()]);
    }

    /**
     * Notifies the listener that some games were added to the app.
     *
     * @param games the games that were added
     */
    public void gameAdded(Collection<String> games) {
        UserID [] users = getCurrentUsers();

        // send out notice of the new games
        for (String game : games) {
            countMap.put(game, new GameMembershipDetail(game, 0));
            Messages.sendGameAdded(SimTask.getCurrent(), game, channel,
                                   users);
        }
    }

    /**
     * Notifies the listener that some games were removed from the app.
     *
     * @param games the games that were removed
     */
    public void gameRemoved(Collection<String> games) {
        UserID [] users = getCurrentUsers();

        // send out notice of the removed games
        for (String game : games) {
            Messages.sendGameRemoved(SimTask.getCurrent(), game, channel,
                                     users);
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
        UserID [] users = getCurrentUsers();

        // for each change, track the detail locally (to send in welcome
        // messages when players first join) and send a message to all
        // current lobby members
        // FIXME: should we just send the collection, instead of sending
        // each change separately?
        for (GameMembershipDetail detail : details) {
            countMap.put(detail.getGame(), detail);
            Messages.sendGameCountChanged(SimTask.getCurrent(),
                                          detail.getGame(), detail.getCount(),
                                          channel, users);
        }
    }

}
