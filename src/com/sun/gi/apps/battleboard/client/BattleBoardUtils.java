/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

/**
 * Utility methods for the BattleBoard example program.
 */
public class BattleBoardUtils {

    /**
     * Gets a line of input from System.in (which for the purpose of
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

    /**
     * Prompts the user for input (via <code>System.out</code>), reads
     * a line of input from <code>System.in</code>, splits the line
     * into tokens by whitespace and returns the tokens as an array of
     * Strings.  If the prompt is <code>null</code>, then a default
     * prompt of <code>"&gt;&gt;"</code> is used. <p>
     *
     * If an exception occurs, a zero-length array is returned.  <p>
     *
     * @param prompt the prompt to give the user
     *
     * @return an array of Strings containing the tokens in the next
     * line of input from <code>System.in</code>, or an empty array if
     * any errors occur
     */
    public static String[] getKeyboardInputTokens(String prompt) {
	String commandline = "";

	if (prompt == null) {
	    prompt = ">> ";
	}

	for (;;) {
	    System.out.print(prompt);
	    System.out.flush();

	    try {
		commandline = getKeyboardLine();
	    } catch (IOException e) {
		System.out.println("Unexpected exception: " + e);
		return new String[0];
	    }

	    if (commandline == null) {
		return new String[0];
	    } else if (commandline.length() > 0) {
		return commandline.split("\\s+");
	    }
	}
    }
}
