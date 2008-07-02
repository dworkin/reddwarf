/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;


/**
 * This is a simple implementation of <code>Board</code> that is used to
 * capture state at some point in time.
 */
public class SnapshotBoard implements Board {

    private static final long serialVersionUID = 1;

    // the state is just a 3-dimensional array of ints
    int [][][] boardData;

    // whether it's dark
    private boolean isDark;

    /**
     * Creates a new <code>SnapshotBoard</code> based on the state of the
     * given <code>Board</code>.
     *
     * @param board the <code>Board</code> to snapshot
     */
    public SnapshotBoard(Board board) {
        this.isDark = board.isDark();

        // iterate through the given board, and capture the identifier
        // stack at each position
        boardData = new int[board.getWidth()][][];
        for (int x = 0; x < board.getWidth(); x++) {
            boardData[x] = new int[board.getHeight()][];
            for (int y = 0; y < board.getHeight(); y++) {
                boardData[x][y] = board.getAt(x, y);
            }
        }
    }

    /**
     * Returns the width of this board.
     *
     * @return the board's width
     */
    public int getWidth() {
        return boardData.length;
    }

    /**
     * Returns the height of this board.
     *
     * @return the board's height
     */
    public int getHeight() {
        return boardData[0].length;
    }

    /**
     * Returns the identifier stack at the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     *
     * @return the stack of isentifiers at the given location
     */
    public int [] getAt(int x, int y) {
        return boardData[x][y];
    }

    /**
     * Returns whether or not the level is dark.
     *
     * @return whether the level is dark
     */
    public boolean isDark() {
        return isDark;
    }

}
