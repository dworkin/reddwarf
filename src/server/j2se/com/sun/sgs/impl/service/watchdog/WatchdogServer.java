/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.watchdog;

import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for contacting the Watchdog server.
 */
public interface WatchdogServer extends Remote {

    /**
     * Registers a node with the corresponding {@code hostname} and
     * {@code client}, and returns and array containing two {@code
     * long} values consisting of:
     *
     * <ul>
     * <li>a unique node ID for the node, and
     *
     * <li>an interval (in milliseconds) that this watchdog must be
     * notified, via the {@link #renewNode renewNode} method, in order
     * for the specified node to be considered alive.
     * </ul>
     *
     * <p>If this method throws {@code NodeRegistrationFailedException},
     * the caller should not retry as this indicates a fatal error.
     *
     * <p>When a node fails or a new node starts, the given {@code
     * client} will be notified of these status changes via its {@link
     * WatchdogClient#nodeStatusChanges nodeStatusChanges} method.
     *
     * @param	hostname  a hostname
     * @param	client a watchdog client
     *
     * @return 	an array containing two {@code long} values consisting of
     *		a unique node ID and a renew interval (in milliseconds)
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     * @throws	NodeRegistrationFailedException if there is a problem
     * 		registering the node
     */
    long[] registerNode(String hostname, WatchdogClient client)
	throws NodeRegistrationFailedException, IOException;

    /**
     * Notifies this watchdog that the node with the specified {@code
     * nodeId} is alive.  This method returns {@code true} if this
     * watchdog still considers the node alive, and returns {@code
     * false} otherwise.  This watchdog considers the node to have
     * failed if a renew request is not received from the node before
     * the assigned interval, returned from {@link #registerNode
     * registerNode}, expires.  If this method returns {@code false}
     * for a given {@code nodeId}, the caller should not retry this
     * method because the node is considered to have failed.
     *
     * @param	nodeId	a node ID
     *
     * @return	{@code true} if the node is considered alive,
     *		{@code false} otherwise
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    boolean renewNode(long nodeId) throws IOException;

    /**
     * Returns {@code true} if this watchdog considers the node with
     * the specified {@code nodeId} to be alive, otherwise returns
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

    /**
     * Notifies this watchdog that the node with the specified (@code
     * nodeId} has been recovered by the node with the specified
     * {@code backupId}.
     *
     * @param	nodeId the recovered node's ID
     * @param	backupId the backup node's ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void recoveredNode(long nodeId, long backupId) throws IOException;
}
