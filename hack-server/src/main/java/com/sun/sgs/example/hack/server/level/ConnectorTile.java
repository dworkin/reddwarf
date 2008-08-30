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
import com.sun.sgs.example.hack.server.ServerItem;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.RoomInfo.FloorType;

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

    /**
     * The type of this tile
     */
    private FloorType floorType;


    // a reference to the connector
    private ManagedReference<Connector> connectorRef;

    // the characters currently on this tile
    //private ArrayList<ManagedReference<CharacterManager>> characterRefs;
    private ManagedReference<CharacterManager> characterRef;

    /**
     * Creates an instance of <code>ConnectorTile</code>.
     *
     * @param id the tile's identifier
     * @param connectorRef a reference to the <code>Connector</code>
     */
    public ConnectorTile(FloorType floorType, Connector connector) {
        this.floorType = floorType;

        connectorRef = AppContext.getDataManager().createReference(connector);

        //characterRefs = new ArrayList<ManagedReference<CharacterManager>>();
	characterRef = null;
    }

    /**
     * Returns the identifier for this tile.
     *
     * @return the tile's identifier
     */
    public FloorType getFloorType() {
        return floorType;
    }

    public CharacterManager getCharacterManager() {
	return (characterRef == null) ? null : characterRef.get();
    }

    public ServerItem getItem() {
	return null;
    }

    /**
     * Always returns true, because connection points are, by definition,
     * passable.
     *
     * @param mgr the manager for a character
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
     * @param mgr the manager for a character
     *
     * @return true
     */
    public boolean canOccupy(CharacterManager mgr) {
        return true;
    }

//     /**
//      * Returns a stack of identifiers, specifying everything on this
//      * <code>Tile</code>.
//      *
//      * @return the set of identifiers for the things at this space
//      */
//     public int [] getIdStack() {
//         int [] ids = new int[characterRefs.size() + 1];
//         int i = 1;

//         // this tile can't have items, so we just make a stack of all
//         // the characters, where the top-most will probably obscure all
//         // other characters
//         for (ManagedReference<CharacterManager> mgrRef : characterRefs)
//             ids[i++] = mgrRef.get().getCurrentCharacter().getID();

//         // the first element must always be the tile itself
//         ids[0] = getID();

//         return ids;
//     }

    /**
     * Adds the given character to this tile if possible. This should always
     * succeed.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(CharacterManager mgr) {
	if (characterRef != null)
	    return false;

	DataManager dm = AppContext.getDataManager();
	characterRef = dm.createReference(mgr);
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
        if (this.characterRef == null) {
            return false;
        }

	DataManager dm = AppContext.getDataManager();

        // make sure that the character here is the one being removed
        if (! this.characterRef.equals(dm.createReference(mgr))) {
	    // debugging output
            System.out.println("not equal on removal: " +
                               characterRef.get().toString() +
                               " != " + mgr.toString());
            return false;
        }

        this.characterRef = null;
	dm.markForUpdate(this);

        return true;
    }

    /**
     * Always returns false, since you can't have items on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return false
     */
    public boolean addItem(ServerItem item) {
        return false;
    }

    /**
     * Always returns false, since you can't have items on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return false
     */
    public boolean removeItem(ServerItem item) {
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
