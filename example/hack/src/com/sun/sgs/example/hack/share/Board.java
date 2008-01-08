/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 * This is the interface used by all classes that store and share data for
 * <code>Level</code>s.
 */
public interface Board extends Serializable {

    /**
     * Returns the width of this board.
     *
     * @return the board's width
     */
    public int getWidth();

    /**
     * Returns the height of this board.
     *
     * @return the board's height
     */
    public int getHeight();

    /**
     * Returns the identifier stack at the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     *
     * @return the stack of isentifiers at the given location
     */
    public int [] getAt(int x, int y);

    /**
     * Returns whether or not the level is dark. A dark board is one where
     * some souce of light is needed to see anything but the space where
     * a character is currently standing.
     *
     * @return whether the level is dark
     */
    public boolean isDark();

}
