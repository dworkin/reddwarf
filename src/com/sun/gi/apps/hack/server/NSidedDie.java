
/*
 * NSidedDie.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Wed Mar  1, 2006	 3:14:29 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import java.util.Random;


/**
 * This is a simple utility class that simulates rolling common dies. Note
 * that this is not a <code>GLO</code> since there is no persistant state
 * and no need to lock any accesses.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class NSidedDie
{

    // the source of random data
    private static Random random = new Random();

    /**
     * You never need to create a <code>NSidedDie</code> instance, so there
     * is no public constructor.
     */
    private NSidedDie() {

    }

    /**
     * Provides the result of rolling a 4-sided die.
     *
     * @return the result, from 1 to 4
     */
    public static int roll4Sided() {
        return random.nextInt(4) + 1;
    }

    /**
     * Provides the result of rolling a 6-sided die.
     *
     * @return the result, from 1 to 6
     */
    public static int roll6Sided() {
        return random.nextInt(6) + 1;
    }

    /**
     * Provides the result of rolling an 8-sided die.
     *
     * @return the result, from 1 to 8
     */
    public static int roll8Sided() {
        return random.nextInt(8) + 1;
    }

    /**
     * Provides the result of rolling a 10-sided die.
     *
     * @return the result, from 1 to 10
     */
    public static int roll10Sided() {
        return random.nextInt(10) + 1;
    }

    /**
     * Provides the result of rolling a 12-sided die.
     *
     * @return the result, from 1 to 12
     */
    public static int roll12Sided() {
        return random.nextInt(12) + 1;
    }

    /**
     * Provides the result of rolling a 20-sided die.
     *
     * @return the result, from 1 to 20
     */
    public static int roll20Sided() {
        return random.nextInt(20) + 1;
    }

    /**
     * Provides the results of rolling an N-sided die.
     *
     * @return the result, from 1 to N
     */
    public static int rollNSided(int n) {
        return random.nextInt(n) + 1;
    }

}
