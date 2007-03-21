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

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;
import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.KeyMessages;

import java.awt.Image;

import java.awt.event.KeyEvent;

import java.nio.ByteBuffer;

import java.util.HashSet;
import java.util.Map;


/**
 * This class manages all interaction with an interactive game. It is used
 * to listen for incoming messages and aggregate them to listeners, and to
 * send messages to the game server for use in the current game.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameManager implements BoardListener, PlayerListener,
                                    CommandListener
{

    // the registered listeners
    private HashSet<BoardListener> boardListeners;
    private HashSet<PlayerListener> playerListeners;

    // the connection manager, used for sending messages to the server
    private ClientConnectionManager connManager = null;

    /**
     * Creates an instance of <code>GameManager</code>.
     */
    public GameManager() {
        boardListeners = new HashSet<BoardListener>();
        playerListeners = new HashSet<PlayerListener>();
    }

    /**
     * Registers a listener for board-related events.
     *
     * @param listener the listener
     */
    public void addBoardListener(BoardListener listener) {
        boardListeners.add(listener);
    }

    /**
     * Registers a listener for player-related events.
     *
     * @param listener the listener
     */
    public void addPlayerListener(PlayerListener listener) {
        playerListeners.add(listener);
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
     * This method notifies the game server that the client has pressed some
     * key that signifies an action in the game. The handling of the key
     * is asynchronous, in that this method returns immediately, and at some
     * point in the future the server may come back with updates based on
     * the requested action.
     *
     * @param key the key, as defined in <code>java.awt.event.KeyEvent</code>
     */
    public void action(int key) {
        int messageType;
        short message = KeyMessages.NONE;

        // figure out what key was pressed and whether we care about it, and
        // also what kind of message this requires (move, take, etc.)
        // Note: this could be done on the server, so that each game could
        // define what keys it uses, but then it would be harder to map
        // custom key bindings to each action
        // FIXME: what's the right approach here?
        switch (key) {
        case KeyEvent.VK_J: message = KeyMessages.LEFT;
            messageType = 1;
            break;
        case KeyEvent.VK_K: message = KeyMessages.DOWN;
            messageType = 1;
            break;
        case KeyEvent.VK_L: message = KeyMessages.RIGHT;
            messageType = 1;
            break;
        case KeyEvent.VK_I: message = KeyMessages.UP;
            messageType = 1;
            break;
        case KeyEvent.VK_SEMICOLON: message = KeyMessages.TAKE;
            messageType = 2;
            break;
        default:
            // if we got here, this is a key we don't handle, so ignore it
            return;
        }

        // FIXME: the message code should be enumerated
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put((byte)messageType);
        bb.putShort(message);

        connManager.sendToServer(bb, true);
    }

    /**
     * Notifies the manager of the sprite map that should be used. This
     * notifies all of the registered listeners.
     *
     * @param spriteSize the size, in pixels, of the sprites
     * @param spriteMap a map from sprite identifier to sprite image
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        for (BoardListener listener : boardListeners)
            listener.setSpriteMap(spriteSize, spriteMap);
    }

    /**
     * Notifies the manager that the board has changed. This notifies all
     * of the registered listeners.
     *
     * @param board the new board where the player is playing
     */
    public void changeBoard(Board board) {
        for (BoardListener listener : boardListeners)
            listener.changeBoard(board);
    }

    /**
     * Notifies the manager that some set of spaces on the board have changed.
     * This notifies all of the registered listeners.
     *
     * @param spaces the changed space detail
     */
    public void updateSpaces(BoardSpace [] spaces) {
        for (BoardListener listener : boardListeners)
            listener.updateSpaces(spaces);
    }

    /**
     * Notifies the manager of a message. This notifies all of the registered
     * listeners
     *
     * @param message the message that the player should "hear"
     */
    public void hearMessage(String message) {
        for (BoardListener listener : boardListeners)
            listener.hearMessage(message);
    }

    /**
     * Called to tell the manager about the character that the client is
     * currently using. This notifies all of the registered listeners.
     *
     * @param id the character's identifier, which specifies their sprite
     * @param stats the characters's statistics
     */
    public void setCharacter(int id, CharacterStats stats) {
        for (PlayerListener listener : playerListeners)
            listener.setCharacter(id, stats);
    }

    /**
     * Called to update aspects of the player's currrent character. This
     * notifies all of the registered listeners.
     */
    public void updateCharacter(/*FIXME: define this type*/) {
        for (PlayerListener listener : playerListeners)
            listener.updateCharacter();
    }


}
