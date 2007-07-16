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

package com.sun.sgs.example.chat.app;

/**
 * Defines the commands understood by the {@link ChatApp} server.
 */
public enum ChatCommand {

    /**
     * Joins this session to the named channel.
     * <pre>
     *    /join channelName
     * </pre>
     */
    JOIN,

    /**
     * Removes this session from the named channel.
     * <pre>
     *    /leave channelName
     * </pre>
     */
    LEAVE,

    /**
     * Echos the given message back to the sender.
     * <pre>
     *    /ping message
     * </pre>
     */
    PING,

    /**
     * Forcibly disconnects this session to the named channel.
     * <pre>
     *    /disconnect
     * </pre>
     */
    DISCONNECT,

    /**
     * Join this session to the named channel.
     * <pre>
     *    /shutdown
     * </pre>
     */
    SHUTDOWN;
}
