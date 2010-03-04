/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap.affinity.dlpa;

import java.io.IOException;
import java.rmi.Remote;

/**
 * The label propagation algorithm server.  These methods can all be
 * called by LPA client nodes.
 */
public interface LPAServer extends Remote {
    /**
     * Indicates that the given {@code nodeId} is ready to begin the label
     * propagation algorithm.
     *
     * @param nodeId the node that is ready to begin
     * @param failed {@code true} if there was a problem while setting up
     * @throws IOException if there is a communication problem
     */
    void readyToBegin(long nodeId, boolean failed) throws IOException;

    /**
     * Indicates that the given {@code nodeId} has completed an iteration
     * of the label propagation algorithm.
     *
     * @param nodeId the node that has finished an iteration
     * @param converged {@code true} if the node believes the algorithm has
     *     converged and can be stopped
     * @param failed {@code true} if there was a problem while running
     * @param iteration the iteration that has finished
     * @throws IOException if there is a communication problem
     */
    void finishedIteration(long nodeId, boolean converged, boolean failed,
                           int iteration)
        throws IOException;

    /**
     * Registers a proxy for the node. If a client has already
     * been registered for the node, it is replaced.
     *
     * @param nodeId the node the proxy represents
     * @param client the client proxy, which this server and other nodes
     *        can call
     * @throws IOException if there is a communication problem
     */
    void register(long nodeId, LPAClient client) throws IOException;

    /**
     * Returns the {@code LPAClient} for the given {@code nodeId}.
     * If {@code null} is returned, the node should be considered failed.
     * 
     * @param nodeId the node we need the proxy for
     * @return the LPA client proxy for the given node, or {@code null}
     *        if the node has failed
     * @throws IOException if there is a communication problem
     */
    LPAClient getLPAClientProxy(long nodeId) throws IOException;
}
