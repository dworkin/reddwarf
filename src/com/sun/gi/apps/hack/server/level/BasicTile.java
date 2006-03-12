
/*
 * BasicTile.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	 8:30:09 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This is an abstract implementation of <code>Tile</code> that provides the
 * base for <code>PassableTile</code>, <code>ImpassableTile</code>, etc. It
 * maintains the rule that only one character and one item may be on the
 * tile at any time.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public abstract class BasicTile implements Tile
{

    // the id of this tile
    private int id;

    // the character that is currently on this space, if any
    private GLOReference<? extends CharacterManager> mgrRef;

    // the item on this space, if any
    private GLOReference<? extends Item> itemRef;

    /**
     * Creates an instance of <code>BasicTile</code>.
     *
     * @param id the tile's identifier
     */
    protected BasicTile(int id) {
        this.id = id;
        this.mgrRef = null;
        this.itemRef = null;
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
     * Returns a stack of identifiers, specifying everything on this
     * <code>Tile</code>.
     *
     * @return the set of identifiers for the things at this space
     */
    public int [] getIdStack() {
        // the array of ids that we will return
        int [] ids = null;

        // NOTE: This is a fairly un-optimized approach to generating this
        // stack, and since this is done reasonably frequently, it would
        // be nice to re-visit this method

        // if there's no item, create an array with just the tile's id,
        // otherwise put both in an array
        if (itemRef == null)
            ids = new int [] {id};
        else
            ids = new int [] {id, itemRef.peek(SimTask.getCurrent()).getID()};

        // if there is a character here, create a new array that's 1 index
        // bigger, and put the character at the end
        if (mgrRef != null) {
            int [] tmp = new int [ids.length + 1];
            for (int i = 0; i < ids.length; i++)
                tmp[i] = ids[i];
            tmp[ids.length] = mgrRef.peek(SimTask.getCurrent()).
                getCurrentCharacter().getID();
            ids = tmp;
        }

        return ids;
    }

    /**
     * Adds the given character to this tile if possible. This succeeds
     * if there isn't currently a character on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(GLOReference<? extends CharacterManager>
                                mgrRef) {
        // make sure no is already here
        if (this.mgrRef != null)
            return false;

        this.mgrRef = mgrRef;

        return true;
    }

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(GLOReference<? extends CharacterManager>
                                   mgrRef) {
        // FIXME: these invariants should be logged

        // make sure that there's a character here
        if (this.mgrRef == null) {
            System.out.println("tried to remove a null char from a tile");
            return false;
        }

        // make sure that the character here is the one being removed
        if (! this.mgrRef.equals(mgrRef)) {
            SimTask task = SimTask.getCurrent();
            System.out.println("not equal on removal: " +
                               mgrRef.peek(task).toString() + " != " +
                               this.mgrRef.peek(task).toString());
            return false;
        }

        this.mgrRef = null;

        return true;
    }

    /**
     * Adds the given item to this tile if possible. This succeeds
     * if there isn't currently an item on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return whether or not the item was added successfully
     */
    public boolean addItem(GLOReference<? extends Item> itemRef) {
        // check that there isn't an item here
        if (this.itemRef != null)
            return false;

        this.itemRef = itemRef;

        return true;
    }

    /**
     * Removes the given item from this tile, if and only if this item
     * is already on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return whether or not the item was removed successfully
     */
    public boolean removeItem(GLOReference<? extends Item> mgrRef) {
        // check that there's an item here
        if (this.itemRef == null)
            return false;

        // check that the item here is the once being removed
        if (! this.itemRef.equals(itemRef))
            return false;

        this.itemRef = null;

        return true;
    }

    /**
     * Test to move the given character to this tile. If a character is
     * on this space, then the two characters interact. This does not
     * actually move the character onto this space.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of making the move
     */
    protected ActionResult charMoveTo(CharacterManager characterManager) {
        // if there is currently a character on this tile, then do the
        // collision
        if (mgrRef != null)
            return mgrRef.get(SimTask.getCurrent()).getCurrentCharacter().
                collidedFrom(characterManager.getCurrentCharacter());

        // if there's no character here, then we always succeed
        return ActionResult.SUCCESS;
    }

    /**
     * Tries to take an item on this tile. Unlike <code>moveTo</code>,
     * this actually will remove items from the tile if they are
     * successfully taken.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of getting an item
     */
    public ActionResult getItem(CharacterManager characterManager) {
        // give this item the chance to react (most items are passive,
        // but you could immediately affect the user here)
        return itemRef.get(SimTask.getCurrent()).giveTo(characterManager);
    }

}
