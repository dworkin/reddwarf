package com.sun.sgs.example.battleboard;

/**
 * BoardListeners are notified whenever a square on a {@link
 * BattleBoard} changes.
 */
public interface BoardListener {

    /**
     * Notifies the listener that a particular position on a {@link
     * BattleBoard} has changed.
     *
     * @param board the BattleBoard referenced
     *
     * @param x the x location of the changed square
     *
     * @param y the y location of the changed square
     */
    void boardChanged(BattleBoard board, int x, int y);
}
