
package com.sun.gi.apps.battleboard.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

/**
 * Utility methods.
 */

public class BattleBoardUtils {

    public static String[] getKeyboardInputTokens(String prompt) {
	String commandline = "";

	if (prompt == null) {
	    prompt = ">> ";
	}

	BufferedReader input = new BufferedReader(
		new InputStreamReader(System.in));

	for (;;) {
	    System.out.print(prompt);
	    System.out.flush();

	    try {
		commandline = input.readLine();
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
