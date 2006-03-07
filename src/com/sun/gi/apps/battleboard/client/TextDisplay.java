/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard.PositionValue;
import com.sun.gi.apps.battleboard.BattleBoard;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class TextDisplay implements Display {

    private final List<BattleBoard> boards;
    private final List<String> players;
    private final int boardWidth;
    private final int boardHeight;

    /**
     * Creates a text-based display for the given list of boards.
     */
    public TextDisplay(List<BattleBoard> boards) {
	if (boards == null) {
	    throw new NullPointerException("boards must not be null");
	}
	if (boards.size() == 0) {
	    throw new IllegalArgumentException("boards must not be empty");
	}

	this.boards = new LinkedList<BattleBoard>(boards);
	this.players = new LinkedList<String>();

	for (BattleBoard board : boards) {
	    this.players.add(board.getPlayerName());
	}

	BattleBoard firstBoard = this.boards.get(0);

	this.boardWidth = firstBoard.getWidth();
	this.boardHeight = firstBoard.getHeight();
    }

    public void removePlayer(String playerName) {
	if (playerName == null) {
	    return;
	}

	players.remove(playerName);

	BattleBoard victim = null;
	for (BattleBoard board : boards) {
	    if (board.getPlayerName().equals(playerName)) {
		victim = board;
		break;
	    }
	}
	if (victim != null) {
	    boards.remove(victim);
	}
    }

    /**
     * {@inheritDoc}
     */
    public void showBoards(String activePlayer) {
	if (boards == null) {
	    throw new NullPointerException("boards is null");
	}

	if (boards.size() == 0) {
	    throw new IllegalArgumentException("no boards to display");
	}

	if (boards.size() > 3) {
	    throw new IllegalArgumentException("too many boards to display");
	}

	System.out.println();
	int index = 1;
	for (BattleBoard currBoard : boards) {
	    int remaining = currBoard.getSurvivingCities();
	    String survivors;

	    if (remaining > 1) {
		survivors = remaining + " surviving cities";
	    } else {
		survivors = "1 surviving city";
	    }

	    System.out.println("player " + index++ + ": " +
		    survivors + ", name: " + currBoard.getPlayerName());
	}

	System.out.println("active: " + activePlayer);
	System.out.println();

	printActiveLine(boards, activePlayer);

	BattleBoard firstBoard = boards.get(0);
	for (int j = firstBoard.getHeight() - 1; j >= 0; j--) {
	    for (BattleBoard currBoard : boards) {

		System.out.print(j);
		if (currBoard.getPlayerName().equals(activePlayer)) {
		    System.out.print(" *");
		} else {
		    System.out.print(" |");
		}

		for (int i = 0; i < currBoard.getWidth(); i++) {
		    PositionValue value = currBoard.getBoardPosition(i, j);
		    System.out.print(valueToString(value));
		}

		if (currBoard.getPlayerName().equals(activePlayer)) {
		    System.out.print("* ");
		} else {
		    System.out.print("| ");
		}
	    }
	    System.out.println();
	}

	printActiveLine(boards, activePlayer);

	for (int i = 0; i < boards.size(); i++) {
	    System.out.print("   ");
	    for (int j = 0; j < firstBoard.getWidth(); j++) {
		System.out.print(" " + j + " ");
	    }
	    System.out.print("  ");
	}
	System.out.println();
	System.out.flush();
    }

    /**
     * Displays a single board. <p>
     *
     * Intended for debugging purposes.  Not part of the {@link Display}
     * interface.
     *
     * @param board the board to display
     */
    public void showBoard(BattleBoard board) {

	System.out.println("-- " + board.getPlayerName() +
		" -- " + board.getSurvivingCities() +
		" surviving cities");

	for (int j = board.getHeight() - 1; j >= 0; j--) {

	    System.out.print(j);

	    for (int i = 0; i < board.getWidth(); i++) {
		PositionValue value = board.getBoardPosition(i, j);
		System.out.print(valueToString(value));
	    }
	    System.out.println();
	}
	System.out.print(" ");

	for (int i = 0; i < board.getWidth(); i++) {
	    System.out.print(" " + i + " ");
	}
	System.out.println();
	System.out.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void message(String message) {
	if (message == null) {
	    throw new NullPointerException("message is null");
	}
	System.out.print(message);
	System.out.println();
	System.out.flush();
    }

    /**
     * {@inheritDoc}
     */
    public String[] getMove() {
	String[] move;
	
	for (;;) {
	    move = getKeyboardInputTokens("player x y, or pass ");

	    if ((move.length == 1) && "pass".equals(move[0])) {
		return move;
	    } else if (move.length == 3) {
		if (!players.contains(move[0])) {
		    message("Player " + move[0] + " is not in the game.");
		} else {
		    int x = Integer.parseInt(move[1]);
		    int y = Integer.parseInt(move[2]);

		    if ((x < 0) || (x >= boardWidth) &&
			    (y < 0) && (y >= boardHeight)) {
			message("Illegal position.");
		    } else {
			return move;
		    }
		}
	    } else {
		message("Illegal move.");
	    }

	    message("  Please try again.\n");
	}
    }

    /**
     * Prints the lines at the top and bottom of the boards display
     * that show which board is "highlighted".  Used by showBoards.
     *
     * If <code>activePlayer</code> is <code>null</code> then no
     * board is highlighted.
     *
     * @param boardList the list of boards being displayed
     *
     * @param activePlayer the name of the player (if any) whose board
     * is active and therefore highlighted
     */
    private static void printActiveLine(List<BattleBoard> boardList,
	    String activePlayer)
    {

	System.out.print("   ");
	for (BattleBoard currBoard : boardList) {
	    String str = (currBoard.getPlayerName().equals(activePlayer)) ?
		    "***" : "---";

	    for (int i = 0; i < currBoard.getWidth(); i++) {
		System.out.print(str);
	    }

	    System.out.print("     ");
	}
	System.out.println();
	System.out.flush();
    }

    /**
     * Returns a String that represents a {@link PositionValue}.
     *
     * @param value the PositionValue
     *
     * @return a String to display for that value
     */
    private String valueToString(PositionValue value) {
	switch (value) {
	    case VACANT   : return "   ";
	    case CITY     : return " C ";
	    case UNKNOWN  : return " ? ";
	    case MISS     : return " m ";
	    case NEAR     : return " N ";
	    case HIT      : return " H ";
	    default       : return "???";
	}
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
    private String[] getKeyboardInputTokens(String prompt) {
	String commandline = "";

	if (prompt == null) {
	    prompt = ">> ";
	}

	for (;;) {
	    System.out.print(prompt);
	    System.out.println();
	    System.out.flush();

	    try {
		commandline = getKeyboardLine();
	    } catch (IOException e) {
		System.out.println("Unexpected exception: " + e);
		System.out.flush();
		return new String[0];
	    }

	    if (commandline == null) {
		return new String[0];
	    } else if (commandline.length() > 0) {
		return commandline.split("\\s+");
	    }
	}
    }

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
    private String getKeyboardLine() throws IOException {
	BufferedReader input = new BufferedReader(
		new InputStreamReader(System.in));
	return input.readLine();
    }
}
