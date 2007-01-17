package com.sun.sgs.io;

import java.util.concurrent.Executor;

import com.sun.sgs.impl.io.IOConstants.TransportType;

/**
 * Represents a remote connection endpoint. Endpoints are used to obtain
 * {@link IOConnector}s and {@link IOAcceptor}s, which can be used to
 * connect to the Endpoint.
 * 
 * @author Sten Anderson
 */
public interface Endpoint<T> {

    /**
     * Creates a non-reusable connector for connecting to the remote
     * Endpoint.
     * 
     * @return a connector configured to connect to the remote Endpoint.
     */
    IOConnector<T> createConnector();

    /**
     * Creates a non-reusable acceptor to listen to connections on the local
     * Endpoint.
     * 
     * @return an acceptor configured to listen on the given local Endpoint.
     */
    IOAcceptor<T> createAcceptor();

    /**
     * Return the address encapsulated by this Endpoint.
     * 
     * @return the address encapsulated by this Endpoint.
     */
    T getAddress();
}