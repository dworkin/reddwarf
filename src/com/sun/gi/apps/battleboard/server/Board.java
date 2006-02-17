package com.sun.gi.apps.battleboard.server;
import com.sun.gi.apps.battleboard.client.BattleBoard;
import com.sun.gi.logic.GLO;

public class Board extends BattleBoard implements GLO {

    private static final long serialVersionUID = 1L;

    public Board(String player, int width, int height, int numCities) {
	super(player, width, height, numCities);
    }
}
