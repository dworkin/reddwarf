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

package com.sun.sgs.example.hack.server;


/**
 * This interface is used by classes that consume messages. This is
 * typically used for <code>Game</code> implementations to provide new
 * instances of <code>MessageHandler</code>s to anyone in the game. This
 * pattern lets the game define the logic for messages and how to handle
 * them, but offloads contention to the class (typically <code>Player</code>)
 * that is trying to process a message.
 */
public interface MessageHandler {

    /**
     * Called to handle a message.
     *
     * @param player the player associated with the message
     * @param data the message
     */
    public void handleMessage(Player player, byte [] message);

}
