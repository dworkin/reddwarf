package com.sun.sgs.example.battleboard.client;

import com.sun.sgs.example.battleboard.BattleBoard;

public interface BattleBrain {

    /**
     * Gets a move from the user and returns the Strings corresponding
     * to the tokens in that move.  <p>
     *
     * This method is responsible for validating the input provided by
     * the user; it should not return until it has valid input.
     *
     * @param boards an array of the current BattleBoard states.  CAUTION:
     * Some or all of the boards may be null!
     * @return an array of Strings representing the operator ("pass"
     * or the name of the player to bomb) and the operands.
     */
    String[] getMove(BattleBoard[] boards);
}
