
/*
 * GameMembershipDetail.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Tue Feb 28, 2006	 6:29:09 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.share;

import java.io.Serializable;


/**
 * This class provides basic detail about the membership of a game.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameMembershipDetail implements Serializable
{

    // the name
    private String game;

    // the membership count
    private int count;

    /**
     * Creates an instance of <code>GameMembershipDetail</code>.
     *
     * @param game the name of the game
     * @param count the membership count
     */
    public GameMembershipDetail(String game, int count) {
        this.game = game;
        this.count = count;
    }

    /**
     * Returns the name of the game.
     *
     * @return the game's name
     */
    public String getGame() {
        return game;
    }

    /**
     * Returns the membership count for this game.
     *
     * @return the membership count
     */
    public int getCount() {
        return count;
    }

}
