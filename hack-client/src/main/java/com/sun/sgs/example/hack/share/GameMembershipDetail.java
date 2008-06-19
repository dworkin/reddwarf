/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
