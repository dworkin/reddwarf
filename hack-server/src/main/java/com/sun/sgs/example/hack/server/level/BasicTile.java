/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.level;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Item;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This is an abstract implementation of <code>Tile</code> that provides the
 * base for <code>PassableTile</code>, <code>ImpassableTile</code>, etc. It
 * maintains the rule that only one character and one item may be on the
 * tile at any time.
 */
public abstract class BasicTile implements Tile {

    private static final long serialVersionUID = 1;

    // the id of this tile
    private int tileTypeId;

    // the character manager for any character that is currently on
    // this space
    private ManagedReference<CharacterManager> mgrRef;

    // the item on this space, if any
    private ManagedReference<Item> itemRef;

    /**
     * Creates an instance of <code>BasicTile</code>.
     *
     * @param id the tile's identifier
     */
    protected BasicTile(int tileTypeId) {
        this.tileTypeId = tileTypeId;
        this.mgrRef = null;
        this.itemRef = null;
    }

    /**
     * Returns the identifier for this tile.
     *
     * @return the tile's identifier
     */
    public int getID() {
        return tileTypeId;
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
            ids = new int [] {tileTypeId};
        else
            ids = new int [] {tileTypeId, itemRef.get().getID()};

        // if there is a character here, create a new array that's 1 index
        // bigger, and put the character at the end
        if (mgrRef != null) {
            int [] tmp = new int [ids.length + 1];
            for (int i = 0; i < ids.length; i++)
                tmp[i] = ids[i];
            tmp[ids.length] = mgrRef.get().getCurrentCharacter().getID();
            ids = tmp;
        }

        return ids;
    }

    /**
     * Checks if the there is anything currently occupying this tile that
     * would keep the character from occupying it.
     *
     * @param mgr the manager for a character
     *
     * @return whether or not the character can occupy this tile
     */
    public boolean canOccupy(CharacterManager mgr) {
        // if the space isn't passible, then it can't be occupied
        if (! isPassable(mgr))
            return false;

        // if there's a character here, then the space can't be occupied
        // NOTE: if we allow passable characters in the future, then this
        // needs to be updated
        if (mgrRef != null)
            return false;

        // NOTE: if we allow items that block movement, then this will need
        // to be updated

        return true;
    }

    /**
     * Adds the given character to this tile if possible. This succeeds
     * if there isn't currently a character on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(CharacterManager mgr) {
        // make sure no is already here
        if (mgrRef != null)
            return false;

	DataManager dm = AppContext.getDataManager();
        mgrRef = dm.createReference(mgr);
	dm.markForUpdate(this);

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
    public boolean removeCharacter(CharacterManager mgr) {
        // make sure that there's a character here
        if (this.mgrRef == null) {
            return false;
        }

	DataManager dm = AppContext.getDataManager();

        // make sure that the character here is the one being removed
        if (! this.mgrRef.equals(dm.createReference(mgr))) {
	    // debugging output
            System.out.println("not equal on removal: " +
                               mgrRef.get().toString() +
                               " != " + mgr.toString());
            return false;
        }

        this.mgrRef = null;
	dm.markForUpdate(this);

        return true;
    }

    /**
     * Adds the given item to this tile if possible. This succeeds
     * if there isn't currently an item on this tile.
     *
     * @param item the item
     *
     * @return whether or not the item was added successfully
     */
    public boolean addItem(Item item) {
        // check that there isn't an item here
        if (itemRef != null)
            return false;

	DataManager dm = AppContext.getDataManager();
        itemRef = dm.createReference(item);
	dm.markForUpdate(this);

        return true;
    }

    /**
     * Removes the given item from this tile, if and only if this item
     * is already on this tile.
     *
     * @param item the item
     *
     * @return whether or not the item was removed successfully
     */
    public boolean removeItem(Item item) {
        // check that there's an item here
        if (itemRef == null)
            return false;

	DataManager dm = AppContext.getDataManager();
        // check that the item here is the once being removed
        if (! itemRef.equals(dm.createReference(item)))
            return false;

        itemRef = null;
	dm.markForUpdate(this);

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
            return mgrRef.get().getCurrentCharacter().
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
        if (itemRef == null)
            return ActionResult.FAIL;

        // give this item the chance to react (most items are passive,
        // but you could immediately affect the user here)
        return itemRef.get().giveTo(characterManager);
    }

}
