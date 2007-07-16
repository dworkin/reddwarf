/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;

import java.awt.Image;

import java.util.Map;


/**
 * This interface defines a class that listenens for updates to the game
 * board and associated state.
 */
public interface BoardListener
{

    /**
     * Notifies the listener of the sprite map that should be used. This is
     * typically called each time the player enters a new dungeon.
     *
     * @param spriteSize the size, in pixels, of the sprites
     * @param spriteMap a map from sprite identifier to sprite image
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap);

    /**
     * Notifies the listener that the board has changed.
     *
     * @param board the new board where the player is playing
     */
    public void changeBoard(Board board);

    /**
     * Notifies the listener that some set of spaces on the board have changed.
     *
     * @param spaces the changed space detail
     */
    public void updateSpaces(BoardSpace [] spaces);

    /**
     * Notifies the listener of a message. Like a chat message, this is a
     * simple string that should be displayed to the user. Unlike a chat
     * message, this message comes directly from the server, not another
     * player.
     *
     * @param message the message that the player should "hear"
     */
    public void hearMessage(String message);

}
