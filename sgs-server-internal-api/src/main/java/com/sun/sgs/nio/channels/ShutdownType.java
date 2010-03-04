/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
