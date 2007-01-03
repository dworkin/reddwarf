package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;

/**
 * A GLO representing the "history" of a BattleBoard player: their name,
 * and how many games he or she has has won and lost.
 */
public class PlayerHistory implements ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    private final String playerName;
    private long gamesWon;
    private long gamesLost;

    /**
     * Create a PlayerHistory object for the player of the given name.
     * <p>
     * 
     * Each player should have exactly one PlayerHistory object created
     * for their name. This is not enforced here; it is enforced in
     * {@link Player#gameStarted gameStarted}, which only creates new
     * PlayerHistory instances for players for which it cannot find a
     * PlayerHistory GLO.
     * 
     * @param playerName the player name
     */
    public PlayerHistory(String playerName) {
        this.playerName = playerName;
        this.gamesWon = 0;
        this.gamesLost = 0;
    }
    
    public String getPlayerName() {
        return playerName;
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
        return "playerName: " + playerName + " won: " + gamesWon +
		" lost: " + gamesLost;
    }
}
