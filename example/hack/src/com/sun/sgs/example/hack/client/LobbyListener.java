/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.util.Collection;


/**
 * This interface defines a class that listens for events from the lobby.
 */
public interface LobbyListener
{

    /**
     * Notifies the listener that a game was added.
     *
     * @param game the name of the game
     */
    public void gameAdded(String game);

    /**
     * Notifies the listener that a game was removed.
     *
     * @param game the name of the game
     */
    public void gameRemoved(String game);

    /**
     * Notifies the listener that the membership count of the lobby has
     * changed.
     *
     * @param count the number of players
     */
    public void playerCountUpdated(int count);

    /**
     * Notifies the listener that the membership count of some game has
     * changed.
     *
     * @param game the name of the game where the count changed
     * @param count the number of players
     */
    public void playerCountUpdated(String game, int count);

    /**
     * Notifies the listener of the characters available for the player.
     *
     * @param characters the characters available to play
     */
    public void setCharacters(Collection<CharacterStats> characters);

}
