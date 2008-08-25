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

import com.sun.sgs.app.util.ScalableHashMap;

import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.Item;

import java.awt.Point;

import java.io.IOException;
import java.io.StreamTokenizer;

import java.util.Set;
import java.util.Map;

/**
 * A server-side {@link LevelBoard} implementation that utilizes
 * individual tile-locking to achieve maximum concurrency.
 * Internally, the board is represented as a 2D gride of tiles, each
 * of which are {@code ManagedObject} instances.  Accesses and updates
 * only acquire the minimum number of tiles, which results in better
 * througput for a board with many active players.
 *
 * This implementation uses a {@link ScalableHashMap} of points
 * instead of a {@code ManagedReference<Tile>[][]} to store its tiles
 * in order to reduce the amount of data that needs to be loaded for
 * each board access.  For large boards, the {@code ManagedReference}
 * array would have imposed a significant data bottleneck.
 */
public class SimpleBoard implements LevelBoard {

    private static final long serialVersionUID = 1;

    /** 
     * the width dimension of this board 
     */
    private final int width;

    /** 
     * the width dimension of this board 
     */
    private final int height;

    /**
     * Whether or not this board is dark
     */
    private boolean isDark;

    /**
     * A reference to the map that contains the board; tiles are
     * accessed by creating a new {@link Point} with the tile's
     * coordinates.  A {@link ScalableHashMap} of points is used
     * instead of a {@code ManagedReference[][]} to reduce the amount
     * of data that needs to be loaded for each board access.
     */
    private ManagedReference<? extends Map<Point,Tile>> boardGridRef;

    /**
     * Creates a new instance of {@code SimpleBoard} with an empty
     * tile set
     *
     * @param width the width of the board
     * @param height the height of the board
     *
     * @throws IllegalArgumentException if height or width is
     *         non-positive
     */
    public SimpleBoard(int width, int height) {
	if (width <= 0)
	    throw new IllegalArgumentException("width must be positive");
	if (height <= 0)
	    throw new IllegalArgumentException("height must be positive");

	this.width = width;
	this.height = height;
	isDark = false;
	ScalableHashMap<Point,Tile> boardGrid = 
	    new ScalableHashMap<Point,Tile>();
	
	boardGridRef = AppContext.getDataManager().createReference(boardGrid);
    }

    /**
     * Creates a new instance of <code>SimpleBoard</code> from the given
     * tokenized stream.
     *
     * @param stok a <code>StreamTokenizer</code> that provides the board
     * @param impassableSprites the set of identifiers that are impassable
     *
     * @throws IOException if the stream isn't formatted correctly
     */
    public static SimpleBoard parse(StreamTokenizer stok)
	throws IOException {

        // get the width and height
        stok.nextToken();
        int width = (int)(stok.nval);
        stok.nextToken();
        int height = (int)(stok.nval);

        // get whether it's dark
        stok.nextToken();
        boolean isDark = stok.sval.equals("true");

        // create the grid for our tiles
	SimpleBoard board = new SimpleBoard(width, height);
        
        // loop through the remaining data, creating the tiles based
        // on their type and setting them on the level
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                stok.nextToken();
                int id = (int)(stok.nval);
		board.setGridSpace(x, y, 
				   (isImpassible(id)
				    ? new ImpassableTile(id)
				    : new PassableTile(id)));
            }
        }
	return board;
    }

    /**
     * A temporary placeholder method for determing whether a tileId
     * is impassible.  This method will be replaced in a forthcoming
     * patch to replace integer tile Ids with enums.
     */
    private static boolean isImpassible(int tileId) {
	// set of impassible sprites ids:
	// 5 6 7 8 9 10 11 12 15 16 19 20
	return 
	    (tileId>= 5 && tileId<= 12) ||
	    tileId== 15 ||
	    tileId== 16 ||
	    tileId== 19 ||
	    tileId== 20;
    }

    private void checkBounds(int x, int y) {
	if (x < 0 || x >= width)
	    throw new IllegalArgumentException("x coordinate " + x +
					       " is outside the range of " +
					       "this level's width: " + width);
	if (y < 0 || y >= height)
	    throw new IllegalArgumentException("y coordinate " + y +
					       " is outside the range of " +
					       "this level's height: " + 
					       height);
    }

    /**
     * Updates the grid space of the level with the provided {@code
     * Tile}.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param tile the new tile at (x,y)
     */
    private void setGridSpace(int x, int y, Tile tile) {
	checkBounds(x, y);

	Tile old = boardGridRef.get().put(new Point(x, y), tile);

	// REMINDER: if we want to support having items or gold
	// embedded in walls, then updating a wall to a floor space
	// should transfer the contents between the two tiles.  This
	// is the point to do that operation.

	// if we are replacing a tile, which could happen if the
	// player has tunneled into a wall, we need to remove the old
	// tile from the data store
	if (old != null) {
	    AppContext.getDataManager().removeObject(old);
	}
    }

    private Tile getGridSpace(int x, int y) {
	checkBounds(x, y);
	return boardGridRef.get().get(new Point(x,y));
    }

    /**
     * Sets the given space as a connector.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param connector the <code>Connector</code>
     */
    public void setAsConnector(int x, int y, Connector connector) {
        Tile tile = getGridSpace(x, y);
        setGridSpace(x, y, new ConnectorTile(tile.getID(), connector));
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
        return getGridSpace(x, y).getIdStack();
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
     * @param mgr the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addCharacterAt(int x, int y, CharacterManager mgr) {
	return getGridSpace(x, y).addCharacter(mgr);
    }

    /**
     * Tries to remove a character from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgr the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeCharacterAt(int x, int y, CharacterManager mgr) {
        return getGridSpace(x, y).removeCharacter(mgr);
    }

    /**
     * Tries to add an item at the given location. This board only allows
     * one item per space.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param item the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addItemAt(int x, int y, Item item) {
        return getGridSpace(x, y).addItem(item);
    }

    /**
     * Tries to remove an item from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param item the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeItemAt(int x, int y, Item item) {
        return getGridSpace(x, y).removeItem(item);
    }

    /**
     * Tests to see if a move would be possible to the given location for
     * the given character.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgr the character's manager
     *
     * @return true if the operation would succeed, false otherwise
     */
    public boolean testMove(int x, int y, CharacterManager mgr) {
        return getGridSpace(x, y).canOccupy(mgr);
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
        if (! getGridSpace(x, y).isPassable(mgr))
            return ActionResult.FAIL;

        // try to move onto that space, but remember the previous position
        // since we'll need it if the player left
        int oldX = mgr.getLevelXPos();
        int oldY = mgr.getLevelYPos();
        ActionResult result = getGridSpace(x, y).moveTo(mgr);

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
                getGridSpace(oldX, oldY).removeCharacter(mgr);

            return result;
        }

        // ...but if we succeeded, then move the character...
	getGridSpace(oldX, oldY).removeCharacter(mgr);
        getGridSpace(x, y).addCharacter(mgr);

        // ...and return success
        return ActionResult.SUCCESS;
    }

    /**
     * Tries to take an item on this tile. Unlike <code>moveTo</code>,
     * this actually will remove items from the tile if they are
     * successfully taken.
     *
     * @param mgr the manager for a character
     *
     * @return the result of getting an item
     */
    public ActionResult getItem(int x, int y, CharacterManager mgr) {
        ActionResult result = getGridSpace(x, y).getItem(mgr);

        if (result == ActionResult.CHARACTER_LEFT)
            getGridSpace(x, y).removeCharacter(mgr);

        return result;
    }

}
