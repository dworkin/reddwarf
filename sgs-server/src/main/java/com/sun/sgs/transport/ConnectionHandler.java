package com.sun.sgs.transport;

import com.sun.sgs.nio.channels.AsynchronousByteChannel;

/**
 * Interface implemented by objects implementing a connection handler.
 */
public interface ConnectionHandler {
    
    /**
     * Notify the handler that a new connetion has been initiated.
     * 
     * @param channel on which the new connection can communicate.
     */
    void newConnection(AsynchronousByteChannel channel);
}