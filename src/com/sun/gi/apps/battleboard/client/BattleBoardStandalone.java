
package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Test driver for BattleBoardPlayer.
 */
class BattleBoardStandalone {
    private static String playerName = "HamsterBoy";
    private final static int boardWidth = 6;
    private final static int boardHeight = 6;
    private final static int numCities = 10;

    public static void main(String[] args) {
	BattleBoard myBoard = new BattleBoard(playerName,
		boardWidth, boardHeight, numCities);

	myBoard.populate();

	BattleBoardPlayer player = new BattleBoardPlayer(playerName);

	BufferedReader input = new BufferedReader(
		new InputStreamReader(System.in));

	while (!player.lost()) {
	    String commandline;

	    System.out.print(">> ");
	    System.out.flush();
	    try {
		commandline = input.readLine();
	    } catch (IOException e) {
		System.out.println(e);
		return;
	    }

	    String[] tokens = commandline.split("\\s*");
	    player.playGame(tokens);
	}
    }
}
