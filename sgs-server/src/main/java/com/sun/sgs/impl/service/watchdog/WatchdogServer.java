/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.service.Node.Health;
import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for contacting the Watchdog server.
 */
public interface WatchdogServer extends Remote {

    /**
     * Registers a node with the corresponding {@code nodeId}, {@code host},
     * and {@code client}, and returns the interval (in milliseconds) that this
     * watchdog must be notified, via the {@link #renewNode renewNode} method,
     * in order for the specified node to be considered alive.
     *
     * <p>If this method throws {@code NodeRegistrationFailedException},
     * the caller should not retry as this indicates a fatal error.
     *
     * <p>When a node fails or a new node starts, the given {@code
     * client} will be notified of these status changes via its {@link
     * WatchdogClient#nodeStatusChanges nodeStatusChanges} method.
     *
     * @param	nodeId the node ID of the node
     * @param	host  a host name
     * @param	client a watchdog client
     * @param   jmxPort the port JMX is listening on, or -1 if JMX is not
     *                   enabled for remote listening on the node
     *
     * @return 	the renew interval (in milliseconds)
     *
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     * @throws	NodeRegistrationFailedException if there is a problem
     * 		registering the node
     */
    long registerNode(
	long nodeId, String host, WatchdogClient client, int jmxPort)
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
     * Notifies this watchdog that the node with the specified {@code
     * nodeId} has been recovered by the node with the specified
     * {@code backupId}.
     *
     * @param	nodeId the recovered node's ID
     * @param	backupId the backup node's ID
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void recoveredNode(long nodeId, long backupId) throws IOException;

    /**
     * Notifies the node with the given ID that its health has been set. If the
     * {@code health} is {@link Health#RED RED} it indicates that the
     * node has failed and should be shutdown. In this case, if the given node
     * is a remote node, this notification is a
     * result of a server running into difficulty communicating with a remote
     * node, so the server's watchdog service is responsible for notifying the
     * watchdog service in order to issue the shutdown. The {@code component}
     * parameter may be any identifying string, but is typically the class
     * name of the component that reported the specified health.
     *
     * @param nodeId the node's ID
     * @param isLocal specifies if the node is reporting health on itself or
     * a remote node
     * @param health the node's health
     * @param component the component reported the specified health
     * @param maxNumberOfAttempts the maximum number of attempts to try and
     * resolve an {@code IOException}
     * @throws IOException if a communication error occurs while trying to set
     * the node's health
     */
    void setNodeHealth(long nodeId, boolean isLocal,
                       Health health, String component,
                       int maxNumberOfAttempts)
	    throws IOException;

    /**
     * Returns the global application time in milliseconds as it is maintained
     * by the central node.  This method returns the amount of time in
     * milliseconds that the current application has been running since the
     * {@link AppListener#initialize(java.util.Properties) initialize} method
     * was called on the application's {@code AppListener} object.
     * <p>
     * Note: Since this method is typically called as part of a {@code Remote}
     * interface, appropriate measures should be taken to account for possible
     * network latency.
     *
     * @return the global application time in milliseconds according to the
     *         central time server
     * @throws	IOException if a communication error occurs while
     * 		invoking this method
     */
    long currentAppTimeMillis() throws IOException;
}
