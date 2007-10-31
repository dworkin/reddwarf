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
