/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
