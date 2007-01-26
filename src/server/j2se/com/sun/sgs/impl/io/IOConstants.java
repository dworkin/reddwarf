package com.sun.sgs.impl.io;

/**
 * Definition of transport constants.
 */
public class IOConstants {

    // TODO move this concept elsewhere? -JM
    
    // cannot instantiate
    private IOConstants() {
        // empty
    }
    
    /**
     * The type of IO transport: TCP, or UDP.
     */
    public enum TransportType {
        /** Reliable transport, such as TCP. */
        RELIABLE,
        
        /** Unreliable transport, such as UDP. */
        UNRELIABLE
    }
}
