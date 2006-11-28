package com.sun.sgs.io;

import java.io.IOException;

/**
 * Represents a handle for sending and receiving byte data.
 * <code>IOHandle</code>s typically have an associated <code>IOHandler</code>
 * for receiving events on the handle.  A handle may be a reliable
 * connection, such as a TCP connection, or unreliable, such as UDP.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOHandle {
    
    /**
     * Sends a byte message on this handle.  The caller should relinquish 
     * ownship of the given byte array, and should not attempt to manipulate
     * its contents after calling this method.
     * 
     * @param message           the message to send
     * 
     * @throws IOException if there was a problem sending the message
     */
    public void sendMessage(byte[] message) throws IOException;
    
    /**
     * Attempts to close the underlying <code>Connection</code>.  Note that
     * the <code>Connection</code> should not be considered closed until
     * <code>ConnectionListener.disconnected()</code> is called.
     * 
     * @throws IOException if there was a problem closing the handle
     */ 
    public void close() throws IOException;
    
    /**
     * Sets the associated <code>IOHandler</code> which will 
     * receive events for this <code>IOHandle</code>.
     * 
     * @param handler           the handler on which to receive events 
     */
    public void setIOHandler(IOHandler handler);
    

}
