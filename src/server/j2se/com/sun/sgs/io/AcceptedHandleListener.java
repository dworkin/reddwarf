package com.sun.sgs.io;

/**
 * Listens for events on an associated <code>IOAcceptor</code>.
 * Right now the only event is a new, incoming <code>IOHandle</code>.
 * 
 * @author Sten Anderson
 * @since 1.0
 */
public interface AcceptedHandleListener {
    
    /**
     * Called when a new incoming <code>IOHandle</code> is established. 
     * 
     * @param handle         the incoming <code>IOHandle</code>
     */
    public void newHandle(IOHandle handle);

    

}
