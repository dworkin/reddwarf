/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
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
    public BoardSpace getAt(int x, int y);

    /**
     * Returns whether or not the level is dark. A dark board is one where
     * some souce of light is needed to see anything but the space where
     * a character is currently standing.
     *
     * @return whether the level is dark
     */
    //public boolean isDark();

}
