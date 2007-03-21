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

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Game;
import com.sun.gi.apps.hack.server.Item;
import com.sun.gi.apps.hack.server.NSidedDie;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import com.sun.gi.apps.hack.share.SnapshotBoard;
import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;
import com.sun.gi.apps.hack.share.KeyMessages;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;


/**
 * This is a simple implementation of <code>Level</code> that doesn't try to
 * do anything fancy with managing the internal state. It uses a
 * <code>LevelBoard</code> to track eveything on the level,
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class SimpleLevel implements Level
{

    // a reference to ourself, which we'll get lazily
    private GLOReference<? extends Level> selfRef = null;

    // our name
    private String name;

    // the name of the game that owns this level
    private String game;

    // the characters currently in this level
    private HashSet<GLOReference<? extends CharacterManager>> characters;

    // the dimentsion of this level
    private int levelWidth;
    private int levelHeight;

    // the board that maintains our state
    private LevelBoard board;

    /**
     * Creates a <code>SimpleLevel</code>.
     *
     * @param levelName the name of this level
     * @param gameName the name of the game where this level exists
     */
    public SimpleLevel(String levelName, String gameName) {
        this.name = levelName;
        this.game = gameName;

        // create a new set for our characters
        characters = new HashSet<GLOReference<? extends CharacterManager>>();
    }

    /**
     * Sets the <code>Board</code> that maintains state for this level.
     * Typically this only called once, shortly after the <code>Level</code>
     * is created.
     *
     * @param board the <code>Board</code> used by this <code>Level</code>
     */
    public void setBoard(LevelBoard board) {
        this.board = board;

        levelWidth = board.getWidth();
        levelHeight = board.getHeight();
    }

    /**
     * Private helper that provides a reference to ourselves. Since we can't
     * get this in the constructor (we're not registered as a GLO at that
     * point), we provide a lazy-assignment accessor here. All code in this
     * class should use this method rather than accessing the reference
     * directly.
     * <p>
     * FIXME: this should actually be handled by a getInstance method
     */
    private GLOReference<? extends Level> getSelfRef() {
        if (selfRef == null)
            selfRef = SimTask.getCurrent().findGLO(Game.NAME_PREFIX + name);

        return selfRef;
    }

    /**
     * Returns the name of this level.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Adds a character to this level at some random point.
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void addCharacter(GLOReference<? extends CharacterManager> mgrRef) {
        int x, y;

        do {
            // find a legal space to place this character
            x = NSidedDie.rollNSided(levelWidth) - 1;
            y = NSidedDie.rollNSided(levelHeight) - 1;

            // loop until we find a space to successfully add the character
        } while (! (board.testMove(x, y, mgrRef)));

        addCharacter(mgrRef, x, y);
    }

    /**
     * Adds a character to this level at the given location.
     * <p>
     * FIXME: the ordering here should probably change, so we send the
     * updates to other characters, and then send the board to the new
     * characters, since the board will have the new character on it already
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     * 
     * @return true upon success, otherwise false.
     */
    public boolean addCharacter(GLOReference<? extends CharacterManager> mgrRef,
                             int startX, int startY) {
        // let the manager know what level it's on, and where on that
        // level it starts
        CharacterManager mgr = mgrRef.get(SimTask.getCurrent());
        mgr.setCurrentLevel(getSelfRef());
        mgr.setLevelPosition(startX, startY);

        if (! board.addCharacterAt(startX, startY, mgrRef)) {
            mgr.setCurrentLevel(null);
            mgr.setLevelPosition(-1, -1);
            return false;
        }

        // keep track of the character
        characters.add(mgrRef);

        // now we need to send the board and position to the character
        mgr.sendBoard(getBoardSnapshot());

        // finally, update everyone about the new charcater
        sendUpdate(new BoardSpace(startX, startY,
                                  board.getAt(startX, startY)));

        return true;
    }

    /**
     * Removes a character from the level. This is typically only called
     * when a character wants to remove itself directly (eg, they were
     * killed, or quit back to the lobby). Otherwise, characters are
     * removed naturally through other actions (like movement).
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void removeCharacter(GLOReference<? extends CharacterManager>
                                mgrRef) {
        // figure out where the character is now
        CharacterManager mgr = mgrRef.peek(SimTask.getCurrent());
        int x = mgr.getLevelXPos();
        int y = mgr.getLevelYPos();

        // make sure that the character is actually on this level...it might
        // not be, for instance, if we're doing a sanity-check of someone
        // who has already left (eg, a player who logged out)
        if (characters.remove(mgrRef)) {
            // remove them from the board, and notify everyone
            if (board.removeCharacterAt(x, y, mgrRef))
                sendUpdate(new BoardSpace(x, y, board.getAt(x, y)));
        }
    }

    /**
     * Adds an item to this level at some random position.
     *
     * @param itemRef a reference to the <code>Item</code>
     */
    public void addItem(GLOReference<? extends Item> itemRef) {
        // FIXME: how should I actually pick this spot?
        int x = NSidedDie.rollNSided(levelWidth) - 1;
        int y = NSidedDie.rollNSided(levelHeight) - 1;
        addItem(itemRef, x, y);
    }

    /**
     * Adds an item to this level at the given position.
     *
     * @param itemRef a reference to the <code>Item</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addItem(GLOReference<? extends Item> itemRef, int startX,
                        int startY) {
        board.addItemAt(startX, startY, itemRef);
    }

    /**
     * Returns a snapshot (ie, static) view of the level. This uses the
     * <code>SnapshotBoard</code> so it can be shared with a client.
     *
     * @return a snapshot of the board
     */
    public Board getBoardSnapshot() {
        return new SnapshotBoard(board);
    }

    /**
     * Tries to move the given character in the given direction
     * <p>
     * FIXME: direction should be an enum
     *
     * @param mgr the manager for the <code>Character</code> that is trying
     *            to move
     * @param direction the direction in which the character wants to move
     *
     * @return true if we moved in the requested direction, false otherwise
     */
    public boolean move(CharacterManager mgr, int direction) {
        // get the current position of the character...
        int x = mgr.getLevelXPos();
        int y = mgr.getLevelYPos();
        int origX = x;
        int origY = y;

        // ...and figure out where they're trying to go
        switch (direction) {
        case KeyMessages.UP: y--;
            break;
        case KeyMessages.DOWN: y++;
            break;
        case KeyMessages.LEFT: x--;
            break;
        case KeyMessages.RIGHT: x++;
            break;
        default:
            // FIXME: when we setup an enum we won't need to check this
            return false;
        }

        // make sure they're moving somewhere on the board
        if ((y < 0) || (y >= levelHeight) || (x < 0) || (x >= levelWidth))
            return false;

        // try to actually make the move
        ActionResult result = board.moveTo(x, y, mgr);

        // if the move failed, we're done
        if (result == ActionResult.FAIL)
            return false;

        // if the move resulted in the character leaving the board, then we
        // remove the character and notify everyone
        if (result == ActionResult.CHARACTER_LEFT) {
            leaveLevel(mgr);
            return false;
        }

        // if we got here then the move succeeded, so let the character know
        // where they are...
        mgr.setLevelPosition(x, y);

        // ...and generate updates for the vacant space and the new position,
        // and broadcast the update to the level
        HashSet<BoardSpace> updates = new HashSet<BoardSpace>();
        updates.add(new BoardSpace(origX, origY, board.getAt(origX, origY)));
        updates.add(new BoardSpace(x, y, board.getAt(x, y)));
        sendUpdates(updates);

        return true;
    }
    
    /**
     * Tries to take items at the character's current location.
     *
     * @param mgr the manager for the <code>Character</code> that is trying
     *            to take the items
     *
     * @return true if we took something, false otherwise
     */
    public boolean take(CharacterManager mgr) {
        int x = mgr.getLevelXPos();
        int y = mgr.getLevelYPos();

        // try to take the item at our position
        ActionResult result = board.getItem(x, y, mgr);

        // if we failed, we're done
        if (result == ActionResult.FAIL)
            return false;

        // if the move resulted in the character leaving the board, then we
        // remove the character and notify everyone
        if (result == ActionResult.CHARACTER_LEFT) {
            leaveLevel(mgr);
            return false;
        }

        // let everyone know that we got the item
        sendUpdate(new BoardSpace(x, y, board.getAt(x, y)));

        // FIXME: we should let the character know that they got the item

        return true;
    }

    /**
     * Sends an update about a single space to all the members of the level.
     *
     * @param update the space being updated
     */
    private void sendUpdate(BoardSpace update) {
        HashSet<BoardSpace> updates = new HashSet<BoardSpace>();
        updates.add(update);
        sendUpdates(updates);
    }

    /**
     * Sends an update about many spaces to all the members of the level.
     *
     * @param updates the spaces being updated
     */
    private void sendUpdates(Set<BoardSpace> updates) {
        SimTask task = SimTask.getCurrent();
        for (GLOReference<? extends CharacterManager> mgrRef : characters)
            mgrRef.peek(task).sendUpdate(updates);
    }

    /**
     * Removes a player from this level and sends an update to all other
     * members of this level
     *
     * @param mgr the character that is leaving
     */
    private void leaveLevel(CharacterManager mgr) {
        // make sure we have this player by trying to remove it
        if (characters.remove(mgr.getReference())) {
            int x = mgr.getLevelXPos();
            int y = mgr.getLevelYPos();
            sendUpdate(new BoardSpace(x, y, board.getAt(x, y)));
        }
    }

}
