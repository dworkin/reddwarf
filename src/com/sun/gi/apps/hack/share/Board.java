
/*
 * Board.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Feb 16, 2006	 4:46:19 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.share;

import java.io.Serializable;


/**
 * This is the interface used by all classes that store and share data for
 * <code>Level</code>s.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Board extends Serializable
{

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
