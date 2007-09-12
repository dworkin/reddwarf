/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.CharacterStats;


/**
 * This interface is used to listen for player character events. Examples
 * are character statistics or inventory changes.
 */
public interface PlayerListener
{

    /**
     * Called to tell listeners about the character that the client is
     * currently using. In this game, a player may only play one character
     * at a time.
     *
     * @param id the character's identifier, which specifies their sprite
     * @param stats the characters's statistics
     */
    public void setCharacter(int id, CharacterStats stats);

    /**
     * Called to update aspects of the player's currrent character.
     */
    public void updateCharacter(/*FIXME: define this type*/);

    /**
     * FIXME: we also need some inventory methods
     */

}
