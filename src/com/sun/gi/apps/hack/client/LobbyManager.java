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
 * LobbyManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 9:51:41 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashSet;


/**
 * This class manages interaction with the lobby. It listens for incoming
 * messages and aggregates them to all other listeners, and it also accepts
 * and sends all outgoing messages.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyManager implements LobbyListener
{

    // the set of listeners subscribed for lobby messages
    private HashSet<LobbyListener> listeners;

    // the connection manager, used to send messages to the server
    private ClientConnectionManager connManager = null;

    /**
     * Creates a new instance of <code>LobbyManager</code>.
     */
    public LobbyManager() {
        listeners = new HashSet<LobbyListener>();
    }

    /**
     * Sets the connection manager that this class uses for all communication
     * with the game server. This method may only be called once during
     * the lifetime of the client.
     *
     * @param connManager the connection manager
     */
    public void setConnectionManager(ClientConnectionManager connManager) {
        if (this.connManager == null)
            this.connManager = connManager;
    }

    /**
     * Adds a listener for lobby events.
     *
     * @param listener the listener to add
     */
    public void addLobbyListener(LobbyListener listener) {
        listeners.add(listener);
    }

    /**
     * This method is used to tell the server that the player wants to
     * join the given game as the given player.
     *
     * @param gameName the name of the game to join
     * @param characterName the name of the character to join as
     */
    public void joinGame(String gameName, String characterName) {
        ByteBuffer bb = ByteBuffer.allocate(5 + gameName.length() +
                                            characterName.length());

        // FIXME: the message codes should be enumerated somewhere
        // the message format is: 1 GameNameLength GameName CharacterName
        bb.put((byte)1);
        bb.putInt(gameName.length());
        bb.put(gameName.getBytes());
        bb.put(characterName.getBytes());

        connManager.sendToServer(bb, true);
    }

    /**
     * Notifies the manager that a game was added. This causes the manager
     * to notify all installed listers.
     *
     * @param game the name of the game
     */
    public void gameAdded(String game) {
        for (LobbyListener listener : listeners)
            listener.gameAdded(game);
    }

    /**
     * Notifies the manager that a game was removed. This causes the manager
     * to notify all installed listers.
     *
     * @param game the name of the game
     */
    public void gameRemoved(String game) {
        for (LobbyListener listener : listeners)
            listener.gameRemoved(game);
    }

    /**
     * Notifies the manager that the membership count of the lobby has
     * changed. This causes the manager to notify all installed listeners.
     *
     * @param count the number of players
     */
    public void playerCountUpdated(int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(count);
    }

    /**
     * Notifies the manager that the membership count of some game has
     * changed. This causes the manager to notify all installed listeners.
     *
     * @param game the name of the game where the count changed
     * @param count the number of players
     */
    public void playerCountUpdated(String game, int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(game, count);
    }

    /**
     * Notifies the manager of the characters available for the player. This
     * causes the manager to notify all installed listeners.
     *
     * @param characters the characters available to play
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        for (LobbyListener listener : listeners)
            listener.setCharacters(characters);
    }

}
