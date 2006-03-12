
/*
 * BoardSpace.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 9:56:05 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.share;

import java.io.Serializable;


/**
 * This represents a single space on a level. It encodes location and
 * identifiers at that location. It is used primarily for shipping data
 * between the client and server, and between specific elements of the
 * server.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class BoardSpace implements Serializable
{

    // the location
    private int x;
    private int y;

    // the identifiers at the location
    private int [] identifiers;

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

}
