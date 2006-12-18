package com.sun.sgs.impl.io;

import com.sun.sgs.io.IOFilter;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;

/**
 * A filter that doesn't do anything except simply pass the messages through.
 * This is the default filter.
 */
public class PassthroughFilter implements IOFilter {

    /**
     * {@inheritDoc}
     * 
     * This implementation simply forwards the byte message on to the
     * {@code IOHandle}'s associated {@code IOHandler.bytesReceived} call back. 
     */
    public void filterReceive(IOHandle handle, byte[] message) {
        SocketHandle socketHandle = (SocketHandle) handle;
        socketHandle.getIOHandler().bytesReceived(message, handle);
    }

    /**
     * {@inheritDoc}
     * 
     * This implementation simply sends out each byte message without
     * modification.
     * 
     */
    public void filterSend(IOHandle handle, byte[] message) {
        ((SocketHandle) handle).doSend(message);
    }

}
