/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.apps.battleboard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * A simple representation of a BattleBoard game board.
 */
public class BattleBoard implements Serializable {

    private static final long serialVersionUID = 1;

    public static final int DEFAULT_BOARD_WIDTH = 4;
    public static final int DEFAULT_BOARD_HEIGHT = 4;
    public static final int DEFAULT_NUM_CITIES = 2;

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

    private transient Random random = new Random();

    private String playerName;
    private int boardHeight;
    private int boardWidth;
    private PositionValue board[][];
    private int startCities;
    private int survivingCities;
    private transient ArrayList<BoardListener> listeners;

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
    protected BattleBoard() {
    // no-op
    }

    /**
     * Creates a BattleBoard with the given width and height and with
     * "unknown" contents.
     * <p>
     * 
     * @param width the width of the board
     * 
     * @param height the height of the board
     * 
     * @param numCities the number of cities that will be place on the
     * board
     * 
     * @throws IllegalArgumentException if the board is an invalid size
     * (either width or height less than 1), if the number of cities is
     * less than one, or if the number of cities is more than will fit
     * onto the board
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

        if (player == null) {
            throw new NullPointerException("player must not be null");
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
     * Places cities at random on an empty battle board.
     * <p>
     * 
     * @return a list of BattleBoardLocations containing the location of
     * each city
     */
    public List<BattleBoardLocation> populate() {
        List<BattleBoardLocation> cityLocations = new LinkedList<BattleBoardLocation>();
        int count = startCities;

        /*
         * Randomly picks a location and, if there isn't already a city
         * there, places a city there and then repeats.
         * 
         * This method is not efficient if the number of cities is more
         * than half the total number of positions -- but usually the
         * number of cities is a small fraction of the number of
         * possible positions and therefore this is not a worry.
         */

        while (count > 0) {
            int x = random.nextInt(boardWidth);
            int y = random.nextInt(boardHeight);

            if (board[x][y] != PositionValue.CITY) {
                board[x][y] = PositionValue.CITY;
                cityLocations.add(new BattleBoardLocation(x, y));
                count--;
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
     * Adds a <code>BoardListener</code> to the list of listeners.
     *
     * @param listener the BoardListener to add
     */
    public synchronized void addBoardListener(BoardListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<BoardListener>();
        }
        listeners.add(listener);
    }
    
    /**
     * Removes a <code>BoardListener</code> from the list of listeners.
     *
     * @param listener the BoardListener to remove.
     */
    public synchronized void removeBoardListener(BoardListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }
    
    /**
     * Fires off a boardChanged event to all BoardListeners.
     *
     * @param x the x location of the square that changed.
     * @param y the y location of the square that changed.
     */
    private synchronized void fireBoardEvent(int x, int y) {
        if (listeners != null) {
            for (BoardListener listener : listeners) {
                listener.boardChanged(this, x, y);
            }
        }
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
     * Returns the number of cities still surviving on the board.
     * 
     * @return the number of cities still surviving on the board
     */
    public int getSurvivingCities() {
        return survivingCities;
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
     * @return {@link PositionValue#HIT} if the given position contains
     * a city, {@link PositionValue#NEAR} if the given position is
     * adjacent to a city (and not a city itself), or
     * {@link PositionValue#MISS} if the position is does not contain
     * nor is adjacent to a city
     * 
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public PositionValue bombBoardPosition(int x, int y) {
        PositionValue rc;

        if ((x < 0) || (x >= boardWidth)) {
            throw new IllegalArgumentException("illegal x: " + x);
        }
        if ((y < 0) || (y >= boardHeight)) {
            throw new IllegalArgumentException("illegal y: " + y);
        }

        if (isHit(x, y)) {
            rc = PositionValue.HIT;
            hit();
	} else if (isBombedCity(x, y)) {
	    rc = PositionValue.HIT;
        } else if (isNearMiss(x, y)) {
            rc = PositionValue.NEAR;
	} else {
            rc = PositionValue.MISS;
        }

        update(x, y, rc);
        return rc;
    }

    /**
     * Updates the given board position to the given state.
     * <p>
     * 
     * Does not verify that the given state change is actually legal in
     * terms of actual game-play. For example, it is possible to use
     * this method to change a {@link PositionValue#NEAR} to a
     * {@link PositionValue#HIT} or vice versa. "Illegal" state changes
     * are permitted in order to allow this method to be used by a
     * player to keep track of their <em>guesses</em> about the
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
	fireBoardEvent(x, y);
        return rc;
    }

    /**
     * Updates the count of surviving cities when a city is hit.
     */
    public void hit() {
        survivingCities--;
    }

    /**
     * Tests whether or not the given board has been "lost". A board is
     * lost when it contains no cities.
     * 
     * @return <code>true</code> if the board contains zero un-bombed
     * cities, <code>false</code> otherwise
     */
    public boolean lost() {
        return (survivingCities == 0);
    }

    /**
     * Tests whether a given position is a hit (currently occupied by a
     * city).
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
     * Tests whether a given position is a previously bombed city
     * (i.e. currently occupied by a hit)
     *
     * @param x the <em>x</em> coordinate of the bomb
     *
     * @param y the <em>y</em> coordinate of the bomb
     *
     * @return <code>true</code> if the given position contains a
     * hit, <code>false</code> otherwise
     */
    public boolean isBombedCity(int x, int y) {
	return getBoardPosition(x, y) == PositionValue.HIT;
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
     * 
     * @throws IllegalArgumentException if either of <em>x</em> or
     * <em>y</em> is outside the board
     */
    public boolean isNearMiss(int x, int y) {
        if ((x < 0) || (x >= boardWidth)) {
            throw new IllegalArgumentException("illegal x: " + x);
        }
        if ((y < 0) || (y >= boardHeight)) {
            throw new IllegalArgumentException("illegal y: " + y);
        }

	if (isHit(x, y) || isBombedCity(x, y)) {
            return false;
        }

        int min_x = (x == 0) ? x : x - 1;
        int min_y = (y == 0) ? y : y - 1;
        int max_x = (x == (getWidth() - 1)) ? x : x + 1;
        int max_y = (y == (getHeight() - 1)) ? y : y + 1;

        for (int i = min_x; i <= max_x; i++) {
            for (int j = min_y; j <= max_y; j++) {
                if ((i == x) && (j == y)) {
                    continue;
		} else if (isHit(i, j) || isBombedCity(i, j)) {
                    return true;
                }
            }
        }
        return false;
    }
}
