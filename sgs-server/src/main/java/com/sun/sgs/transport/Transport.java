package com.sun.sgs.transport;

/**
 * IO transport.
 * 
 */
public interface Transport {
    
    /**
     * Shutdown the transport. The actions of this method are implementation
     * dependent, but typlicaly involve closing open network connections,
     * releasing system resources, etc..
     */
    void shutdown();
}