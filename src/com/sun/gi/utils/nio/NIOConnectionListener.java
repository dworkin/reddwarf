package com.sun.gi.utils.nio;

import java.nio.ByteBuffer;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface NIOConnectionListener {

    /**
     * Callback to process a packet that has been received from
     * a connection.
     *
     * @param conn   the NIOConnection that has received the new data
     * @param inBuf  the readonly ByteBuffer containing a contiguous
     *		     packet of data to process, ready for reading.
     */
    public void packetReceived(NIOConnection conn, ByteBuffer inBuf);

    /**
     * Callback to indicate a connection has closed.
     *
     * @param conn the NIOConnection that has disconnected
     */
    public void disconnected(NIOConnection conn);
}
