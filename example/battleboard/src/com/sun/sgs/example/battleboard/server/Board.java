package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.example.battleboard.BattleBoard;

/**
 * A Board represents the state of a single Players board in a
 * particular Game of BattleBoard.  <p>
 * 
 * This is a trivial extension of {@link BattleBoard} to add the
 * {@link GLO} interface, which permits instances of this class to be
 * made persistant and to be managed by the server.
 */
public class Board extends BattleBoard implements ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * Default constructor, not for public use.
     */
    protected Board() {
        super();
    }

    /**
     * Construct a blank Board with the given initial parameters.
     */
    public Board(String player, int width, int height, int numCities) {
        super(player, width, height, numCities);
    }
}
