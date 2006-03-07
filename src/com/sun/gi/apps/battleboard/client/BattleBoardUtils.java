/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Utility methods for the BattleBoard example program.
 */
public class BattleBoardUtils {

    /**
     * Reads a line of input from System.in (which for the purpose of
     * this game, we assume is a players keyboard) and returns it as a
     * String.
     *
     * @return the next line of text read from <code>System.in</code>.
     *
     * @throws IOException if an exception occurs accessing
     * <code>System.in</code>.
     */
    public static String getKeyboardLine() throws IOException {
	BufferedReader input = new BufferedReader(
		new InputStreamReader(System.in));
	return input.readLine();
    }
}
