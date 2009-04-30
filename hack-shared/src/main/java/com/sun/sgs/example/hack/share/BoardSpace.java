/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import java.io.Serializable;

import java.util.Arrays;

/**
 * This represents a single space on a level. It encodes location and
 * identifiers at that location. It is used primarily for shipping data
 * between the client and server, and between specific elements of the
 * server.
 */
public class BoardSpace implements Serializable {

    private static final long serialVersionUID = 1;

    // the location
    private final int x;
    private final int y;

    // the identifiers at the location
    private final int [] identifiers;

    /**
     * Creates an instance of <code>BoardSpace</code>.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param identifiers the identifier stack
     */
    public BoardSpace(int x, int y, int [] identifiers) {
        this.x = x;
        this.y = y;
        this.identifiers = identifiers;
    }

    /**
     * Returns the x-coordinate for this space.
     *
     * @return the x-coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the y-coordinate for this space.
     *
     * @return the y-coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Returns the stack of identifiers at this space.
     *
     * @return the identifier stack
     */
    public int [] getIdentifiers() {
        return identifiers;
    }

    public String toString() {
	return String.format("(%d,%d) = %s", x, y, Arrays.toString(identifiers));
    }

}
