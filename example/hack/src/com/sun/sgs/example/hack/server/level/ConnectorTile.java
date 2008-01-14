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

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Item;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;

import java.util.ArrayList;


/**
 * This implementation of <code>Tile</code> is used to handle interaction
 * with <code>Connector</code>s. If you walk onto this tile, you are moved
 * based on the <code>Connector</code>. This tile allows any number of
 * characters to occupy it so no one can block other's access. It also
 * ignores all interaction, so you can't collide with (fight) other
 * characters while on a connection point. This means that when you first
 * arrive somewhere, you're safe until you step off the connection point.
 */
public class ConnectorTile implements Tile, Serializable {

    private static final long serialVersionUID = 1;

    // the id of this tile
    private int id;

    // a reference to the connector
    private ManagedReference<Connector> connectorRef;

    // the characters currently on this tile
    private ArrayList<ManagedReference<CharacterManager>> characterRefs;

    /**
     * Creates an instance of <code>ConnectorTile</code>.
     *
     * @param id the tile's identifier
     * @param connectorRef a reference to the <code>Connector</code>
     */
    public ConnectorTile(int id, Connector connector) {
        this.id = id;

        connectorRef = AppContext.getDataManager().createReference(connector);

        characterRefs = new ArrayList<ManagedReference<CharacterManager>>();
    }

    /**
     * Returns the identifier for this tile.
     *
     * @return the tile's identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Always returns true, because connection points are, by definition,
     * passable.
     *
     * @param mgrRef the manager for a character
     *
     * @return true
     */
    public boolean isPassable(CharacterManager mgr) {
        return true;
    }

    /**
     * Always returns true, because connection points are, by definition,
     * spaces you can occupy.
     *
     * @param mgrRef the manager for a character
     *
     * @return true
     */
    public boolean canOccupy(CharacterManager mgr) {
        return true;
    }

    /**
     * Returns a stack of identifiers, specifying everything on this
     * <code>Tile</code>.
     *
     * @return the set of identifiers for the things at this space
     */
    public int [] getIdStack() {
        int [] ids = new int[characterRefs.size() + 1];
        int i = 1;

        // this tile can't have items, so we just make a stack of all
        // the characters, where the top-most will probably obscure all
        // other characters
        for (ManagedReference<CharacterManager> mgrRef : characterRefs)
            ids[i++] = mgrRef.get().getCurrentCharacter().getID();

        // the first element must always be the tile itself
        ids[0] = getID();

        return ids;
    }

    /**
     * Adds the given character to this tile if possible. This should always
     * succeed.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(CharacterManager mgr) {
        return characterRefs.add(AppContext.getDataManager().
                                 createReference(mgr));
    }

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(CharacterManager mgr) {
        return characterRefs.remove(AppContext.getDataManager().
                                    createReference(mgr));
    }

    /**
     * Always returns false, since you can't have items on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return false
     */
    public boolean addItem(Item item) {
        return false;
    }

    /**
     * Always returns false, since you can't have items on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return false
     */
    public boolean removeItem(Item item) {
        return false;
    }

    /**
     * Test to move the given character to this tile. Typically this results
     * in moving the character to a new point in the game based on the
     * <code>Connector</code>, but since <code>Connector</code> can choose
     * whether or not to allow characters to enter (eg, look at
     * <code>PlayerConnector</code>), this isn't always true.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of making the move
     */
    public ActionResult moveTo(CharacterManager characterManager) {
        // this ignores any characters on this space, and simply sends
        // the moving character into the connector
        if (connectorRef.get().enteredConnection(characterManager))
            return ActionResult.CHARACTER_LEFT;
        else
            return ActionResult.FAIL;
    }

    /**
     * This always fails, since no items are allowed on this tile.
     *
     * @param characterManager the manager for a character
     *
     * @return <code>ActionResult.FAIL</code>
     */
    public ActionResult getItem(CharacterManager characterManager) {
        return ActionResult.FAIL;
    }

}
