package com.sun.gi.apps.battleboard;

import java.io.Serializable;

public class BattleBoard implements Serializable {

    private static long serialVersionUID = 1;

    /*
     * Default size, for now.  Use of defaults will be deprecated
     * later.
     */

    static int DEFAULT_BOARD_HEIGHT	= 8;
    static int DEFAULT_BOARD_WIDTH	= 8;

    final String name;
    final int    boardHeight;
    final int    boardWidth;
    final int    startingCityCount;
    final int    board[][];

    int cityCount;

    /*
     * Could use an enum, but that complicates the wire protocol.  So
     * I'm using old-fashioned hard-wired constants here.
     */

    public static final int POS_VACANT	= 0;
    public static final int POS_CITY	= 1;
    public static final int POS_NUKED	= 2;

    public static final int POS_UNKN	= 3;
    public static final int POS_NEAR	= 4;
    public static final int POS_MISS	= 5;

    static final int MISS	= 100;
    static final int NEAR_MISS	= 101;
    static final int HIT	= 102;

    public BattleBoard(String name) {
	this(name, DEFAULT_BOARD_HEIGHT, DEFAULT_BOARD_WIDTH);
    }

    public BattleBoard(String name, int width, int height) {
	this(name, width, height, (width < height) ? width : height);
    }

    public BattleBoard(String name, int width, int height,
	    int startingCities) {

	if ((width <= 0) || (height <= 0)) {
	    throw new IllegalArgumentException("invalid board size");
	}

	this.name = name;
	boardWidth = width;
	boardHeight = height;
	startingCityCount = startingCities;

	cityCount = startingCityCount;

	board = new int[boardWidth][boardHeight];

	for (int i = 0; i < boardWidth; i++) {
	    for (int j = 0; j < boardHeight; j++) {
		board[i][j] = POS_VACANT;
	    }
	}

	// XXX This is completely lame.  I'm just going to put the
	// cities in the same place, every time, for debugging. 
	// -DJE

	for (int i = 0; i < cityCount; i++) {
	    board[i][i] = POS_CITY;
	}
    }

    public int getHeight() {
	return boardHeight;
    }

    public int getWidth() {
	return boardWidth;
    }

    public void display() {
	for (int j = getHeight() - 1; j >= 0; j--) {

	    System.out.print(j);

	    for (int i = 0; i < getWidth(); i++) {
		String b;

		switch (getBoardPosition(i, j)) {
		    case POS_VACANT : b = "   "; break;
		    case POS_NUKED  : b = " # "; break;
		    case POS_CITY   : b = " C "; break;
		    case POS_UNKN   : b = "   "; break;
		    case POS_NEAR   : b = " + "; break;
		    case POS_MISS   : b = " - "; break;
		    default         : b = "???"; break;
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

    public int getBoardPosition(int x, int y) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}
	return board[x][y];
    }

    int nukeBoardPosition(int x, int y) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}

	int rc;

	if (isHit(x, y)) {
	    rc = HIT;
	    cityCount--;
	} else if (isNearMiss(x, y)) {
	    rc = NEAR_MISS;
	} else {
	    rc = MISS;
	}

	board[x][y] = POS_NUKED;
	return rc;
    }

    boolean lost() {
	return (cityCount == 0);
    }

    int updateBoardPosition(int x, int y, int state) {
	if ((x < 0) || (x >= boardWidth)) {
	    throw new IllegalArgumentException("illegal x: " + x);
	}
	if ((y < 0) || (y >= boardHeight)) {
	    throw new IllegalArgumentException("illegal y: " + y);
	}

	// XXX Should make sure that the new state is valid with
	// respect to the previous state.  For example a NEAR_MISS
	// can't become a CITY, etc.

	int rc = getBoardPosition(x, y);
	board[x][y] = state;
	return rc;
    }

    private boolean isHit(int x, int y) {
	return (getBoardPosition(x, y) == POS_CITY);
    }

    private boolean isNearMiss(int x, int y) {

	// Double-check for off-by-one errors!

	int min_x = (x <= 0) ? x : x - 1;
	int min_y = (y <= 0) ? y : y - 1;
	int max_x = (x >= (getWidth() - 1)) ? x : x + 1;
	int max_y = (y >= (getHeight() - 1)) ? y : y + 1;

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
