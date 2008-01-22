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
     * Broadcasts a message on the named channel.
     * <pre>
     *    /broadcast channelName:message
     * </pre>
     */
    BROADCAST,

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
     * Shuts down the server.
     * <pre>
     *    /shutdown
     * </pre>
     */
    SHUTDOWN;
}
