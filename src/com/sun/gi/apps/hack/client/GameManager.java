/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */


/*
 * GameManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	10:22:26 PM
 * Desc: 
 *
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
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameManager implements BoardListener, PlayerListener,
                                    CommandListener
{

    //
    private HashSet<BoardListener> boardListeners;
    private HashSet<PlayerListener> playerListeners;

    //
    private ClientConnectionManager connManager = null;

    /**
     *
     */
    public GameManager() {
        boardListeners = new HashSet<BoardListener>();
        playerListeners = new HashSet<PlayerListener>();
    }

    /**
     *
     */
    public void addBoardListener(BoardListener listener) {
        boardListeners.add(listener);
    }

    /**
     *
     */
    public void addPlayerListener(PlayerListener listener) {
        playerListeners.add(listener);
    }

    /**
     *
     */
    public void setConnectionManager(ClientConnectionManager connManager) {
        if (this.connManager == null)
            this.connManager = connManager;
    }

    /**
     * this is called by the gui code ... an action is a key press to move
     * us in some direction (which may be illegal, may be an attack, etc.),
     * a request to take some item, equipping/using some item ... and is
     * there anything else?
     * FIXME: define what this looks like, and what parameters it can take
     * (the current approach of taking a key is temporary)
     */
    public void action(int key) {
        // make sure that this is a key we care about
        int messageType;
        short message = KeyMessages.NONE;

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
            // if we got here, this is a key we don't know how to handle,
            // so just ignore it
            return;
        }

        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put((byte)messageType);
        bb.putShort(message);
        connManager.sendToServer(bb, true);
    }

    /**
     *
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        for (BoardListener listener : boardListeners)
            listener.setSpriteMap(spriteSize, spriteMap);
    }

    /**
     *
     */
    public void changeBoard(Board board) {
        for (BoardListener listener : boardListeners)
            listener.changeBoard(board);
    }

    /**
     *
     */
    public void updateSpaces(BoardSpace [] spaces) {
        for (BoardListener listener : boardListeners)
            listener.updateSpaces(spaces);
    }

    /**
     *
     */
    public void hearMessage(String message) {
        for (BoardListener listener : boardListeners)
            listener.hearMessage(message);
    }

    /**
     *
     */
    public void setCharacter(int id, CharacterStats stats) {
        for (PlayerListener listener : playerListeners)
            listener.setCharacter(id, stats);
    }

    /**
     *
     */
    public void updateCharacter(/*FIXME: define this type*/) {
        for (PlayerListener listener : playerListeners)
            listener.updateCharacter();
    }


}
