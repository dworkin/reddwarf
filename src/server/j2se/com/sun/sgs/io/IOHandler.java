package com.sun.sgs.io;

import java.nio.ByteBuffer;

/**
 * Attaches to an <code>IOHandle</code> in order to receive connection
 * events, such as data arriving.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface IOHandler {
    
    /**
     * Called when a message is received on the associated handle.  The
     * message is simply a collection of bytes that may or may not contain
     * complete "packets".  It is up to this method to decide how to decode
     * the bytes into a high level message.
     * 
     * @param buffer            the buffer containing the data
     * @param handle            the IOHandle on which the data arrived
     */
    public void messageReceived(ByteBuffer buffer, IOHandle handle);
    
    /**
     * Called when the given <code>IOHandle</code> is disconnected.
     *
     * @param handle            the handle that closed
     */
    public void disconnected(IOHandle handle);
    
    /**
     * Called when an exception is thrown on the given handle.  Exceptions can
     * bubble up to this call for a number of reasons:
     * <p><ul>
     * <li>As the result of the asynchronous work of a requested operation 
     * on the handle (such as "sendMessage")</li>
     * <li>As the result of an unexpected condition (for example, an unplanned
     * disconnect)</li>
     * </ul> 
     * 
     * @param exception         the thrown exception
     * @param handle            the handle on which the exception occured         
     */
    public void exceptionThrown(Throwable exception, IOHandle handle);
    
    /**
     * Called when the given <code>IOHandle</code> is connected
     * and ready to use.  Once this call back is fired, the handle can be
     * considered "live" and data may be sent and received on it.
     *
     */
    public void connected(IOHandle handle);

}
