package com.sun.sgs.io;

/**
 * An IOFilter provides hooks into modifying messages before they are sent, and
 * as they are received.
 * <p>
 * A filter's {@code filterSend} method is called after a client has requested
 * data be sent on the given {@code IOHandle}.  The filter is free to modify 
 * the data in any way, and sent it out on the {@code IOHandle} as a whole,
 * in pieces, or not at all. 
 * <p>
 * A filter's {@code filterReceived} is called as messages are received by the
 * framework on the given {@code IOHandle}, but before the client's 
 * {@code IOHandler} is notified.  The filter is free to modify the data in any
 * way, and call the handle's associated {@code IOHandler.messageReceived} 
 * callback once, several times, or not at all.
 * <p>
 * Potential uses for {@code IOFilter}s are:
 * <ul>
 * <li>Guaranteeing that only complete messages are forwarded to the client</li>
 * <li>Performing some low-level byte logging information</li>
 * <li>Filtering out unwanted information, for example, a dirty word filter
 * on a channel</li>
 * <li>Acting as a chain of filters for all of the above</li>
 * </ul> 
 */
public interface IOFilter {
    

    /**
     * Called after a client has requested data be sent on the given 
     * {@code IOHandle}.  The filter is free to modify the data in any way, 
     * and sent it out on the {@code IOHandle} as a whole,in pieces, or not 
     * at all. 
     * 
     * @param handle            the handle on which to send the data
     * @param message           the data to filter, and send
     */
    public void filterSend(IOHandle handle, byte[] message);
    
    
    /**
     * Called as messages are received by the framework on the given 
     * {@code IOHandle}, but before the client's {@code IOHandler} is notified.  
     * The filter is free to modify the data in any way, and call the handle's 
     * associated {@code IOHandler.messageReceived} callback once, several 
     * times, or not at all.
     * 
     * @param handle            the handle on which the data was received
     * @param message           the data to filter and forward on to the 
     *                          associated call back
     */
    public void filterReceive(IOHandle handle, byte[] message);

}
