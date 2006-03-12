
/*
 * SimpleBoard.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	10:51:54 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import java.io.IOException;
import java.io.StreamTokenizer;

import java.util.Set;


/**
 * This is a simple implementation of <code>LevelBoard</code> that is used
 * as the default mechanism to store level state. Note that this class is
 * not a <code>GLO</code>. It must be managed as private state with something
 * that is a <code>GLO</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class SimpleBoard implements LevelBoard
{

    // the dimension of this board
    private int width;
    private int height;

    // whether or not this board is dark
    private boolean isDark;

    // an array of Tiles, which is where we store the state
    private Tile [][] tiles;

    /**
     * Creates a new instance of <code>SimpleBoard</code> from the given
     * tokenized stream.
     *
     * @param stok a <code>StreamTokenizer</code> that provides the board
     * @param impassableSprites the set of identifiers that are impassable
     *
     * @throws IOException if the stream isn't formatted correctly
     */
    public SimpleBoard(StreamTokenizer stok, Set<Integer> impassableSprites)
        throws IOException
    {
        // get the width and height
        stok.nextToken();
        width = (int)(stok.nval);
        stok.nextToken();
        height = (int)(stok.nval);

        // get whether it's dark
        stok.nextToken();
        isDark = stok.sval.equals("true");

        // create the grid for our tiles
        tiles = new Tile[width][height];
        
        // loop through the remaining data, creating the tiles
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                stok.nextToken();
                int id = (int)(stok.nval);
                if (impassableSprites.contains(id))
                    tiles[x][y] = new ImpassableTile(id);
                else
                    tiles[x][y] = new PassableTile(id);
            }
        }
    }

    /**
     * Sets the given space as a connector.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param connectorRef a reference to the <code>Connector</code>
     */
    public void setAsConnector(int x, int y,
                               GLOReference<? extends Connector>
                               connectorRef) {
        Tile tile = tiles[x][y];
        tiles[x][y] = new ConnectorTile(tile.getID(), connectorRef);
    }

    /**
     * Returns the width of this board.
     *
     * @return the board's width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of this board.
     *
     * @return the board's height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the stack of identifiers at the given position.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     *
     * @return the set of identifiers at this space
     */
    public int [] getAt(int x, int y) {
        return tiles[x][y].getIdStack();
    }

    /**
     * Returns whether or not this board is dark.
     *
     * @return true if this board is dark, false otherwise
     */
    public boolean isDark() {
        return isDark;
    }

    /**
     * Tries to add a character at the given location. This board only
     * allows one character per space.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addCharacterAt(int x, int y,
                                  GLOReference<? extends CharacterManager>
                                  mgrRef) {
        
        return tiles[x][y].addCharacter(mgrRef);
    }

    /**
     * Tries to remove a character from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeCharacterAt(int x, int y,
                                     GLOReference<? extends CharacterManager>
                                     mgrRef) {
        return tiles[x][y].removeCharacter(mgrRef);
    }

    /**
     * Tries to add an item at the given location. This board only allows
     * one item per space.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addItemAt(int x, int y,
                             GLOReference<? extends Item> itemRef) {
        return tiles[x][y].addItem(itemRef);
    }

    /**
     * Tries to remove an item from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeItemAt(int x, int y,
                                GLOReference<? extends Item> itemRef) {
        return tiles[x][y].removeItem(itemRef);
    }

    /**
     * Tests to see if a move would be possible to the given location for
     * the given character.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation would succeed, false otherwise
     */
    public boolean testMove(int x, int y,
                            GLOReference<? extends CharacterManager> mgrRef) {
        return tiles[x][y].isPassable(mgrRef);
    }

    /**
     * Moves the given character to the given location. The character must
     * alredy be on the board through a call to <code>addCharacterAt</code>.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgr the character's manager
     *
     * @return the result of attempting the move
     */
    public ActionResult moveTo(int x, int y, CharacterManager mgr) {
        // see if the space is passable
        if (! testMove(x, y, mgr.getReference()))
            return ActionResult.FAIL;

        // try to move onto that space, but remember the previous position
        // since we'll need it if the player left
        int oldX = mgr.getLevelXPos();
        int oldY = mgr.getLevelYPos();
        ActionResult result = tiles[x][y].moveTo(mgr);

        // if this didn't result in sucess, then return the result (note
        // that this could still have been a "successful" move, since
        // this could have been an attack, a connection somewhere else,
        // etc.) ...
        if (result != ActionResult.SUCCESS) {
            // before returning, check if the result was CHARACTER_LEFT,
            // in which case we remove them from the board (and we'll use
            // the pre-move location, just in case the character now
            // thinks it has a new location somewhere)
            if (result == ActionResult.CHARACTER_LEFT)
                tiles[oldX][oldY].removeCharacter(mgr.getReference());

            return result;
        }

        // ...but if we succeeded, then move the character...
        tiles[oldX][oldY].removeCharacter(mgr.getReference());
        tiles[x][y].addCharacter(mgr.getReference());

        // ...and return success
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
    public ActionResult getItem(int x, int y, CharacterManager mgr) {
        ActionResult result = tiles[x][y].getItem(mgr);

        if (result == ActionResult.CHARACTER_LEFT)
            tiles[x][y].removeCharacter(mgr.getReference());

        return result;
    }

}
