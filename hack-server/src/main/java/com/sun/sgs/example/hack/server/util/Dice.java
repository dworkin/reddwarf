/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.util;

import java.io.Serializable;

/**
 * A representation of a N dice with M sides (NdM for short).
 */
public final class Dice implements Serializable {
       
    private static final long serialVersionUID = 4L;

    private final int numDice;
    private final int numSides;

    /**
     * Creates a {@code Dice} instance with the provided number of
     * dice each with provided number of sides.
     */
    public Dice(int numDice, int numSides) {
	this.numDice = numDice;
	this.numSides = numSides;
    }

    public int numDice() {
	return numDice;
    }

    public int numSides() {
	return numSides;
    }

    /**
     * Rolls all the dice contained within this class and returns
     * their sum.
     */
    public int roll() {
	int sum = 0;
	for (int i = 0; i < numDice; ++i)
	    sum += (int)(Math.random() * numSides) + 1;
	return sum;
    }

    public String toString() {
	return numDice + "d" + numSides;
    }
}
