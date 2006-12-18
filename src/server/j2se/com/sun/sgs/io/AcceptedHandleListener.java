package com.sun.sgs.io;

/**
 * Listens for events on an associated <code>IOAcceptor</code>.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface AcceptedHandleListener {
    
    /**
     * Called when a new incoming <code>IOHandle</code> is established. 
     * 
     * @param handle         the incoming <code>IOHandle</code>
     * 
     * @return the {@code IOHandler} which will receive events for this 
     *         {@code IOHandle}
     */
    public IOHandler newHandle(IOHandle handle);

    

}
