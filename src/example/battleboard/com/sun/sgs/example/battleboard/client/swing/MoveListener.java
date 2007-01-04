package com.sun.sgs.example.battleboard.client.swing;

public interface MoveListener {

    /**
     * Notifies the listener that a move has been made.
     *
     * @param move the move, in command format.
     */
    public void moveMade(String[] move);
}
