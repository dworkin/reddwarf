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

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Game;
import com.sun.sgs.example.hack.server.Item;
import com.sun.sgs.example.hack.server.NSidedDie;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.SnapshotBoard;
import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.KeyMessages;

import java.io.Serializable;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;


/**
 * This is a simple implementation of <code>Level</code> that doesn't try to
 * do anything fancy with managing the internal state. It uses a
 * <code>LevelBoard</code> to track eveything on the level,
 */
public class SimpleLevel implements Level, Serializable {

    private static final long serialVersionUID = 1;

    // our name
    private String name;

    // the name of the game that owns this level
    private String game;

    // the characters currently in this level
    private HashSet<ManagedReference<CharacterManager>> characterRefs;

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
        characterRefs = new HashSet<ManagedReference<CharacterManager>>();
    }

    /**
     * Sets the <code>Board</code> that maintains state for this level.
     * Typically this only called once, shortly after the <code>Level</code>
     * is created.
     *
     * @param board the <code>Board</code> used by this <code>Level</code>
     */
    public void setBoard(LevelBoard board) {
        AppContext.getDataManager().markForUpdate(this);

        this.board = board;

        levelWidth = board.getWidth();
        levelHeight = board.getHeight();
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
    public void addCharacter(CharacterManager mgr) {
        int x, y;

        do {
            // find a legal space to place this character
            x = NSidedDie.rollNSided(levelWidth) - 1;
            y = NSidedDie.rollNSided(levelHeight) - 1;

            // loop until we find a space to successfully add the character
        } while (! (board.testMove(x, y, mgr)));

        addCharacter(mgr, x, y);
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
    public boolean addCharacter(CharacterManager mgr, int startX, int startY) {
        // let the manager know what level it's on, and where on that
        // level it starts
        mgr.setCurrentLevel(this);
        mgr.setLevelPosition(startX, startY);

        if (! board.addCharacterAt(startX, startY, mgr)) {
            mgr.setCurrentLevel(null);
            mgr.setLevelPosition(-1, -1);
            return false;
        }

        AppContext.getDataManager().markForUpdate(this);

        // keep track of the character
        characterRefs.add(AppContext.getDataManager().createReference(mgr));

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
    public void removeCharacter(CharacterManager mgr) {
        // figure out where the character is now
        int x = mgr.getLevelXPos();
        int y = mgr.getLevelYPos();

        // make sure that the character is actually on this level...it might
        // not be, for instance, if we're doing a sanity-check of someone
        // who has already left (eg, a player who logged out)
        if (characterRefs.remove(AppContext.getDataManager().
                                 createReference(mgr))) {
            AppContext.getDataManager().markForUpdate(this);
            // remove them from the board, and notify everyone
            if (board.removeCharacterAt(x, y, mgr))
                sendUpdate(new BoardSpace(x, y, board.getAt(x, y)));
        }
    }

    /**
     * Adds an item to this level at some random position.
     *
     * @param itemRef a reference to the <code>Item</code>
     */
    public void addItem(Item item) {
        // FIXME: how should I actually pick this spot?
        int x = NSidedDie.rollNSided(levelWidth) - 1;
        int y = NSidedDie.rollNSided(levelHeight) - 1;
        addItem(item, x, y);
    }

    /**
     * Adds an item to this level at the given position.
     *
     * @param itemRef a reference to the <code>Item</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addItem(Item item, int startX, int startY) {
        board.addItemAt(startX, startY, item);
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
            leaveLevel(mgr, origX, origY);
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
            leaveLevel(mgr, x, y);
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
        for (ManagedReference<CharacterManager> mgrRef : characterRefs)
            mgrRef.get().sendUpdate(updates);
    }

    /**
     * Removes a player from this level and sends an update to all other
     * members of this level
     *
     * @param mgr the character that is leaving
     */
    private void leaveLevel(CharacterManager mgr, int x, int y) {
        // make sure we have this player by trying to remove it
        if (characterRefs.remove(AppContext.getDataManager().
                                 createReference(mgr))) {
            AppContext.getDataManager().markForUpdate(this);
            sendUpdate(new BoardSpace(x, y, board.getAt(x, y)));
        }
    }

}
