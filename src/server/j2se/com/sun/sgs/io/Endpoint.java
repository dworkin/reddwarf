package com.sun.sgs.io;

/**
 * Represents a remote connection endpoint.  Endpoints are used to obtain
 * {@code IOConnector}s, which can be used to connect to the Endpoint.
 * 
 * @author      Sten Anderson
 */
public interface Endpoint {
    
    /**
     * Creates a non-reusable connector for connecting to the remote
     * Endpoint.
     * 
     * @return  a connector configured to connect to this remote Endpoint.
     */
    IOConnector createConnector();

}
