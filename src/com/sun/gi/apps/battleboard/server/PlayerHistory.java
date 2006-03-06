/*
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.apps.battleboard.server;

import com.sun.gi.logic.GLO;

/**
 * A GLO representing the "history" of a BattleBoard player:  their
 * name, and how many games he or she has has won and lost.  <p>
 */
public class PlayerHistory implements GLO {
    private static final long serialVersionUID = 1L;

    private final String playerName;
    private long gamesWon;
    private long gamesLost;

    /**
     * Create a PlayerHistory object for the player of the given name. 
     * <p>
     *
     * Each player should have exactly one PlayerHistory object
     * created for their name.  This is not enforced here; it is
     * enforced in {@link Player.gameStarted gameStarted}, which only
     * creates new PlayerHistory instances for players for which it
     * cannot find a PlayerHistory GLO.
     *
     * {@see Player.gameStarted}
     *
     * @param name the player name
     */
    public PlayerHistory(String name) {
	this.playerName = name;
	this.gamesWon = 0;
	this.gamesLost = 0;
    }

    /**
     * Returns the number of games won by this player.
     *
     * @return the number of games won by this player
     */
    public long getWon() {
	return gamesWon;
    }

    /**
     * Returns the number of games lost by this player.
     *
     * @return the number of games lost by this player
     */
    public long getLost() {
	return gamesLost;
    }

    /**
     * Returns the number of games played by this player.
     *
     * @return the number of games played by this player
     */
    public long getTotalGames() {
	return gamesWon + gamesLost;
    }

    /**
     * Increment the number of wins by this player.
     */
    public void win() {
	gamesWon++;
    }

    /**
     * Increment the number of losses by this player.
     */
    public void lose() {
	gamesLost++;
    }

    /**
     * Returns a string representing known about this player.
     *
     * @return a string representing known about this player.
     */
    public String toString() {
	return "playerName: " + playerName +
		" won: " + gamesWon + " lost: " + gamesLost;
    }
}
