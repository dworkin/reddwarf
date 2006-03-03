/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class BattleBoard implements Serializable {

    public class BattleBoardLocation {
	private final int x;
	private final int y;

	public BattleBoardLocation(int x, int y) {
	    this.x = x;
	    this.y = y;
	}

	public int getX() {
	    return x;
	}

	public int getY() {
	    return y;
	}
    }

    private String playerName;
    private int boardHeight;
    private int boardWidth;
    private PositionValue board[][];
    private int startCities;
    private int survivingCities;

    public enum PositionValue {

	/**
	 * Indicates an empty (unoccupied, unbombed) board position.
	 */
	VACANT,

	/**
	 * Indicates a board position that is occupied by a city.
	 */
	CITY,

	/**
	 * Indicates a board position whose contents are unknown.
	 */
	UNKNOWN,

	/**
	 * Indicates a board position that has been bombed and is near
	 * (adjacent) to a city (bombed or unbombed) but not a hit.
	 */
	NEAR,

	/**
	 * Indicates a board position that has been bombed and is not
	 * near (adjacent) to a city (bombed or unbombed).
	 */
	MISS,

	/**
	 * Indicates a board position that contains a bombed city.
	 */
	HIT
    }

    /**
     * Default constructor, not for public use.
     */
    protected BattleBoard() { }

    /**
     * Creates a BattleBoard with the given width and height and with
     * "unknown" contents.  <p>
     *
     * @param width the width of the board
     *
     * @param height the height of the board
     *
     * @param numCities the number of cities that will be place on the
     * board
     *
     * @throws IllegalArgumentException if the board is an invalid
     * size (either width or height less than 1), if the number of
     * cities is less than one, or if the number of cities is more
     * than will fit onto the board
     */
    public BattleBoard(String player, int width, int height, int numCities) {

	if ((width <= 0) || (height <= 0)) {
	    throw new IllegalArgumentException("width and height must be > 0");
	}

	if (numCities < 1) {
	    throw new IllegalArgumentException("numCities must be > 0");
	}

	if (numCities > (width * height)) {
	    throw new IllegalArgumentException("numCities is too large");
	}

	playerName = player;
	boardWidth = width;
	boardHeight = height;
	survivingCities = numCities;
	startCities = numCities;
	board = new PositionValue[boardWidth][boardHeight];

	for (int x = 0; x < boardWidth; x++) {
	    for (int y = 0; y < boardHeight; y++) {
		board[x][y] = PositionValue.VACANT;
	    }
	}
    }

    /**
     * Places cities on an empty battle board. <p>
     *
     * For the sake of simplicity, this method always places the
     * cities in the same places.  This makes the game very boring to
     * play, but makes the example somewhat simpler.
     *
     * @return a list of BattleBoardLocations containing the location
     * of each city
     */
    public List<BattleBoardLocation>  populate() {

	List<BattleBoardLocation> cityLocations =
		new LinkedList<BattleBoardLocation>();

	/*
	 * Note that the city locations are chosen in a completely
	 * non-random manner...
	 */

	int count = startCities;
	for (int y = 0; (y < boardHeight) && (count > 0); y++) {
	    for (int x = 0; (x < boardWidth) && (count > 0); x++) {
		board[x][y] = PositionValue.CITY;
		count--;

		cityLocations.add(new BattleBoardLocation(x, y));
	    }
	}

	return cityLocations;
    }

    /**
     * Returns the name of the player for this board.
     *
     * @return the name of the player for this board
     */
    public String getPlayerName() {
	return playerName;
    }

    /**
     * Returns the height of the board.
     *
     * @return the height of the board
     */
    public int getHeight() {
	return boardHeight;
    }

    /**
     * Returns the width of the board.
     *
     * @return the width of the board
     */
    public int getWidth() {
	return boardWidth;
    }

    /**
     * Returns the number of cities on the board at the start of the
     * game.
     *
     * @return the number of cities on the board at the start of the
     * game
     */
    public int getStartCities() {
	return startCities;
    }

    /**
     * Returns the number of cities still surviving on the the board.
     *
     * @return the number of cities still surviving on the the board
     */
    public int getSurvivingCities() {
	return survivingCities;
    }

    /**
     * Displays the board using a simple text format.
     */
    public void display() {

	System.out.println("-- " + playerName + " -- " + survivingCities +
		" surviving cities");

	for (int j = getHeight() - 1; j >= 0; j--) {

	    System.out.print(j);

	    for (int i = 0; i < getWidth(); i++) {
		String b;

		switch (getBoardPosition(i, j)) {
		    case VACANT   : b = "   "; break;
		    case CITY     : b = " C "; break;
		    case UNKNOWN  : b = " . "; break;
		    case MISS     : b = " - "; break;
		    case NEAR     : b = " + "; break;
		    case HIT      : b = " # "; break;
		    default       : b = "???"; break;
		}
		System.out.print(b);
	    }
	    System.out.println();
	}
	System.out.print(" ");

	for (int i = 0; i < getWidth(); i++) {
	    System.out.print(" " + i + " ");
	}
	System.out.println();
    }

    /**
     * Returns the value at the given (x,y) position on the board.
     *
     * @param x the <em>x</em> coordinate
     *
     * @param y the <em>y</em> coordinate
     *
     * @return the value at the given position in the board
     *
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public PositionValue getBoardPosition(int x, int y) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}
	return board[x][y];
    }

    /**
     * Drops a bomb on the given board position (which changes the
     * current contents of the given board position to {@link
     * PositionValue#HIT}, {@link PositionValue#NEAR}, or {@link
     * PositionValue#MISS}, and returns the result.
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return {@link PositionValue#HIT} if the given position
     * contains a city, {@link PositionValue#NEAR} if the given
     * position is adjacent to a city (and not a city itself), or
     * {@link PositionValue#MISS} if the position is does not contain
     * nor is adjacent to a city
     *
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public PositionValue bombBoardPosition(int x, int y) {
	PositionValue  rc;

	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}

	if (isHit(x, y)) {
	    rc = PositionValue.HIT;
	    hit();
	} else if (isNearMiss(x, y)) {
	    rc = PositionValue.NEAR;
	} else {
	    rc = PositionValue.MISS;
	}

	update(x, y, rc);
	return rc;
    }

    /**
     * Updates the given board position to the given state.  <p>
     *
     * Does not verify that the given state change is actually legal
     * in terms of actual game-play.  For example, it is possible to
     * use this method to change a {@link PositionValue#NEAR} to a
     * {@link PositionValue#HIT} or vice versa.  "Illegal" state
     * changes are permitted in order to allow this method to be used
     * by a player to keep track of their <em>guesses</em> about the
     * contents of the boards of the other players.
     *
     * @param state one of {@link PositionValue#HIT}, {@link
     * PositionValue#NEAR}, or {@link PositionValue#MISS}.
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public PositionValue update(int x, int y, PositionValue state) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}

	PositionValue rc = getBoardPosition(x, y);
	board[x][y] = state;
	return rc;
    }
 
    /**
     * Updates the count of surviving cities when a city is hit.
     */
    public void hit() {
	survivingCities--;
    }

    /**
     * Tests whether or not the given board has been "lost".  A board
     * is lost when it contains no cities.
     *
     * @return <code>true</code> if the board contains zero un-bombed
     * cities, <code>false</code> otherwise
     */
    public boolean lost() {
	return (survivingCities == 0);
    }

    /**
     * Tests whether a given position is a hit (currently occupied by
     * a city).
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return <code>true</code> if the given position contains a
     * city, <code>false</code> otherwise
     */
    public boolean isHit(int x, int y) {
	return (getBoardPosition(x, y) == PositionValue.CITY);
    }

    /**
     * Tests whether a given position is a near miss (not currently
     * occupied by a city, but adjacent to a position that is).
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return <code>true</code> if the given position is a near miss,
     * <code>false</code> otherwise
     */
    public boolean isNearMiss(int x, int y) {

	// Double-check for off-by-one errors!

	int min_x = (x <= 0) ? x : x - 1;
	int min_y = (y <= 0) ? y : y - 1;
	int max_x = (x >= (getWidth() - 1)) ? x : x + 1;
	int max_y = (y >= (getHeight() - 1)) ? y : y + 1;

	if (isHit(x, y)) {
	    return false;
	}

	for (int i = min_x; i <= max_x; i++) {
	    for (int j = min_y; j <= max_y; j++) {
		if ((i == x) && (j == y)) {
		    continue;
		} else if (isHit(i, j)) {
		    return true;
		}
	    }
	}
	return false;
    }
}
