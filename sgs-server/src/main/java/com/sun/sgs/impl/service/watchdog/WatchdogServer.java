/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.service.WatchdogService;

/**
 * A remote interface for contacting the Watchdog server.
 */
public interface WatchdogServer extends Remote {

    /**
     * Registers a node with the corresponding {@code host}, {@code port},
     * and {@code client}, and returns and array containing two {@code long}
     * values consisting of:
     * <ul>
     * <li>a unique node ID for the node, and
     * <li>an interval (in milliseconds) that this watchdog must be notified,
     * via the {@link #renewNode renewNode} method, in order for the specified
     * node to be considered alive.
     * </ul>
     * <p>
     * If this method throws {@code NodeRegistrationFailedException}, the
     * caller should not retry as this indicates a fatal error.
     * <p>
     * When a node fails or a new node starts, the given {@code client} will
     * be notified of these status changes via its {@link
     * WatchdogClient#nodeStatusChanges nodeStatusChanges} method.
     * 
     * @param host a host name
     * @param port a port number
     * @param client a watchdog client
     * @return an array containing two {@code long} values consisting of a
     * unique node ID and a renew interval (in milliseconds)
     * @throws IOException if a communication problem occurs while invoking
     * this method
     * @throws NodeRegistrationFailedException if there is a problem
     * registering the node
     */
    long[] registerNode(String host, int port, WatchdogClient client)
	    throws NodeRegistrationFailedException, IOException;

    /**
     * Notifies this watchdog that the node with the specified {@code nodeId}
     * is alive. This method returns {@code true} if this watchdog still
     * considers the node alive, and returns {@code false} otherwise. This
     * watchdog considers the node to have failed if a renew request is not
     * received from the node before the assigned interval, returned from
     * {@link #registerNode registerNode}, expires. If this method returns
     * {@code false} for a given {@code nodeId}, the caller should not retry
     * this method because the node is considered to have failed.
     * 
     * @param nodeId a node ID
     * @return {@code true} if the node is considered alive, {@code false}
     * otherwise
     * @throws IOException if a communication problem occurs while invoking
     * this method
     */
    boolean renewNode(long nodeId) throws IOException;

    /**
     * Notifies this watchdog that the node with the specified {@code nodeId}
     * has been recovered by the node with the specified {@code backupId}.
     * 
     * @param nodeId the recovered node's ID
     * @param backupId the backup node's ID
     * @throws IOException if a communication problem occurs while invoking
     * this method
     */
    void recoveredNode(long nodeId, long backupId) throws IOException;

    /**
     * Notifies the node with the given ID that it has failed and should
     * shutdown. This notification is a result of a server running into
     * difficulty communicating with a remote node, so the server's watchdog
     * service is responsible for notifying the watchdog server in order to
     * issue the shutdown. If the method returns {@code false}, then it means
     * that another thread has already issued the node to shutdown.
     * 
     * @param nodeId the failed node's ID
     * @param className the class issuing the failure
     * @param severity the severity of the failure
     * @param maxNumberOfAttempts the maximum number of attempts to try and
     * resolve an {@code IOException}
     */
    void setNodeAsFailed(long nodeId, String className,
	    WatchdogService.FailureLevel severity, int maxNumberOfAttempts)
	    throws IOException;

    /**
     * Notifies the data store that the node has failed. This method is called
     * if the watchdog service has already dealt with the shutdown procedure.
     * If the node is known to be a remote node, then the three-argument
     * method should be called instead. If the method returns {@code false},
     * then it means that another thread has already issued the node to
     * shutdown.
     * 
     * @param nodeId the failed node's ID
     */
    void setNodeAsFailed(long nodeId);
}
