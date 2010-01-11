/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.nio.channels;

import java.net.Socket;

/**
 * A typesafe enumeration used when shutting down a connection on a
 * stream-oriented connecting socket.
 *
 * @see Socket#shutdownInput()
 * @see Socket#shutdownOutput()
 * @see AsynchronousSocketChannel#shutdown(ShutdownType)
 */
public enum ShutdownType {

    /**
     * Further reads are disallowed.
     */
    READ,

    /**
     * Further writes are disallowed.
     */
    WRITE,

    /**
     * Further reads and writes are disallowed.
     */
    BOTH
}
