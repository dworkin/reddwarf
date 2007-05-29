/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.share;

import java.io.Serializable;


/**
 * This represents a single space on a level. It encodes location and
 * identifiers at that location. It is used primarily for shipping data
 * between the client and server, and between specific elements of the
 * server.
 */
public class BoardSpace implements Serializable {

    private static final long serialVersionUID = 1;

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
