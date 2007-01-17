package com.sun.sgs.impl.io;

/**
 * A convenience class for defining common constants.
 * 
 * @author Sten Anderson
 * @since  1.0
 */
public class IOConstants {

    // TODO move this concept elsewhere? -JM
    
    // cannot instantiate
    private IOConstants() {}
    
    /**
     * The type of IO transport: TCP, or UDP
     */
    public enum TransportType {
        RELIABLE,
        UNRELIABLE
    }
}
