/*
 * Copyright 2006 by Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.gi.apps.battleboard.client;

import com.sun.gi.apps.battleboard.BattleBoard;
import java.util.List;

/**
 * An interface defining a very simple display for the BattleBoard
 * game.
 */
public interface Display {

    /**
     * Updates the display to show all of the boards, highlighting the
     * activePlayer (if any).  <p>
     *
     * If <code>activePlayer</code> is <code>null</code>, then no
     * board is highlighted.
     *
     * @param activePlayer the name of the "activePlayer" (the player
     * whose board to highlight.
     */
    void showBoards(String activePlayer);

    /**
     * Removes a player from the display.
     *
     * @param playerName the name of the player to remove
     */
    void removePlayer(String playerName);

    /**
     * Displays a message.
     *
     * @param message the String to display
     */
    void message(String message);

    /**
     * Gets a move from the user and returns the Strings corresponding
     * to the tokens in that move. <p>
     *
     * This method is responsible for validating the input provided by
     * the user; it should not return until it has valid input.
     *
     * @return an array of Strings representing the operator ("pass" or
     * the name of the player to bomb) and the operands.
     *
     * @see BattleBoardPlayer.yourMove
     */
    String[] getMove();
}
