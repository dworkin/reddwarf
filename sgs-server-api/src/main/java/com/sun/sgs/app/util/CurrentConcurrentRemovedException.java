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
package com.sun.sgs.app.util;

/**
 * The {@code CurrentConcurrentRemovedException} is thrown by an iterator
 * when the current element it is on has been removed concurrently by something
 * else. It will not be thrown when the iterator itself has removed the element.
 * */
public class CurrentConcurrentRemovedException
        extends RuntimeException {

    /** Constructs a new {@code CurrentConcurrentRemovedException}
     */
    CurrentConcurrentRemovedException() {
    }

    /** Constructs a new {@code CurrentConcurrentRemovedException}
     * with the specified detail message.
     *
     * @param   message   the message with additional details as to the nature
     *                    of the {@code CurrentConcurrentRemovedException}
     */
    CurrentConcurrentRemovedException(String message) {
        super(message);
    }
}
