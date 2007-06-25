/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for contacting the Watchdog server.
 */
public interface WatchdogServer extends Remote {

    /**
     * Registers a node with the corresponding {@code nodeId} and
     * {@code hostname}, and returns an interval (in milliseconds)
     * that this watchdog must be notified, via the {@link #ping ping}
     * method, in order for the specified node to be considered alive.
     *
     * @param	nodeId	a node ID
     * @param	hostname  a hostname
     *
     * @return	an interval (in milliseconds) that this watchdog
     * 		expects to receive pings from the specified node
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     * @throws	NodeExistsException if a node with the specified
     *		{@code nodeId} has already been registered
     */
    long registerNode(long nodeId, String hostname) throws IOException;

    /**
     * Notifies this watchdog that the node with the specified {@code
     * nodeId} is alive.  This method returns {@code true} if this
     * watchdog still considers the node alive, and returns {@code
     * false} otherwise.  This watchdog considers the node to have
     * failed if a ping is not received from the node before the
     * assigned interval, returned from {@link #registerNode
     * registerNode}, expires.
     *
     * @param	nodeId	a node ID
     *
     * @return	{@code true} if the node is considered alive,
     *		{@code false} otherwise
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean ping(long nodeId) throws IOException;

    /**
     * Returns {@code true} if this watchdog considers the node with
     * the specified (@code nodeId} to be alive, otherwise returns
     * {@code false}.
     *
     * @param	nodeId	a node ID
     *
     * @return	{@code true} if the node is considered alive,
     *		{@code false} otherwise
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean isAlive(long nodeId) throws IOException;

}
