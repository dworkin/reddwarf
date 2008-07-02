/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.share;

import java.io.Serializable;


/**
 * This class provides basic detail about the membership of a game.
 */
public class GameMembershipDetail implements Serializable {

    private static final long serialVersionUID = 1;

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
