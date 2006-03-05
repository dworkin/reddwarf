/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.apps.battleboard.BattleBoard.PositionValue;
import java.util.List;

import static com.sun.gi.apps.battleboard.BattleBoard.PositionValue.*;

public class TextDisplay {
    private final String myName;

    public TextDisplay(String myName) {
	if (myName == null) {
	    throw new NullPointerException("myName must not be null");
	}

	this.myName = myName;
    }

    public void showBoards(List<BattleBoard> boards, String activePlayer) {
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
    }

    /**
     * Displays a single board using a simple text format.
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
    }

    public void message(String message) {
	if (message == null) {
	    throw new NullPointerException("message is null");
	}
	System.out.print(message);
	System.out.flush();
    }

    private void printActiveLine(List<BattleBoard> boards,
	    String activePlayer)
    {

	System.out.print("   ");
	for (BattleBoard currBoard : boards) {
	    String str = (currBoard.getPlayerName().equals(activePlayer)) ?
		    "***" : "---";

	    for (int i = 0; i < currBoard.getWidth(); i++) {
		System.out.print(str);
	    }

	    System.out.print("     ");
	}
	System.out.println();
    }

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
}

