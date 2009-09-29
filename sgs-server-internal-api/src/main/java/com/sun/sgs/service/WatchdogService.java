/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.service;

import java.util.Iterator;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.TransactionException;

/**
 * The {@code WatchdogService} monitors the health of server nodes and
 * notifies registered listeners of node status change events.  It
 * also provides information and notification services about node
 * backup and recovery.
 */
public interface WatchdogService extends Service {

    /**
     * Returns {@code true} if the local node is considered alive,
     * otherwise returns {@code false}.  This method should only be
     * called from within a transaction.
     *
     * @return	{@code true} if the local node is considered alive, and
     * 		{@code false} otherwise
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    boolean isLocalNodeAlive();

    /**
     * Returns {@code true} if the local node is considered alive,
     * otherwise returns {@code false}.  This method returns the most
     * recent information known to this service and may not be
     * definitive.  For definitive information, use the {@link
     * #isLocalNodeAlive isLocalNodeAlive} method. <p>
     *
     * This method may be invoked any time after this service is
     * intialized, whether or not the calling context is inside or outside
     * of a transaction.
     *
     * @return	{@code true} if the local node is considered alive, and
     * 		{@code false} otherwise
     */
    boolean isLocalNodeAliveNonTransactional();

    /**
     * Returns an iterator for the set of nodes that this service
     * monitors.  The {@code remove} operation of the returned
     * iterator is not supported and will throw {@code
     * UnsupportedOperationException} if invoked.  This method must
     * be called within a transaction, and the returned iterator
     * must only be used within that transaction.
     *
     * @return	an iterator for the set of nodes that this service
     *		monitors
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    Iterator<Node> getNodes();

    /**
     * Returns node status information for the node with the specified
     * {@code nodeId}, or {@code null} if the node is unknown.  This
     * method should only be called within a transaction.
     *
     * @param	nodeId	a node ID
     * @return	node status information for the specified {@code nodeId},
     *		or {@code null}
     * @throws	IllegalArgumentException if the specified {@code nodeId}
     *		is not within the range of valid IDs
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    Node getNode(long nodeId);

    /**
     * Returns the node that is designated as the backup for the node
     * with the specified {@code nodeId}, or {@code null} if no backup
     * is currently designated.  This method must be called within a
     * transaction.
     *
     * <p><b>Note: this method should probably be moved to the Node
     * interface, or it should throw an exception in the case where
     * there is no existing node corresponding to {@code nodeId}.</b>
     *
     * @param	nodeId a node ID
     * @return	a backup node, or {@code null}
     * @throws	IllegalArgumentException if the specified {@code nodeId}
     *		is not within the range of valid IDs
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    Node getBackup(long nodeId);

    /**
     * Registers a {@code listener} to be notified when any node that
     * this service monitors starts or fails.  Registered listeners
     * are notified outside of a transaction. <p>
     *
     * This method must be invoked outside of a transaction.
     *
     * @param	listener a node listener
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    void addNodeListener(NodeListener listener);

    /**
     * Adds the specified recovery {@code listener} for the local
     * node.  If the local node is designated as a backup for a node
     * that fails, the specified {@code listener} will be notified
     * (outside of a transaction) by having its {@link
     * RecoveryListener#recover recover} method invoked, passing the
     * failed node and a {@link SimpleCompletionHandler} whose {@link
     * SimpleCompletionHandler#completed completed} method must be invoked
     * when the recovery operations initiated by the {@code listener} are
     * complete. <p>
     *
     * This method must be invoked outside of a transaction.
     *
     * @param	listener a recovery listener
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    void addRecoveryListener(RecoveryListener listener);

    /**
     * Informs the watchdog that a problem has occured in a service or
     * component. The watchdog will notify the server of the failure and
     * then proceeed to shutting down the node. The node specified as the
     * {@code nodeId} can be a local node or a remote node. <p>
     *
     * This method must be invoked outside of a transaction.
     * 
     * @param nodeId the id of the node to shutdown
     * @param className the class name of the service that failed
     * @throws	IllegalStateException if this method is invoked from a
     *		transactional context
     */
    void reportFailure(long nodeId, String className);

    /**
     * Returns the current global application time in milliseconds. This
     * method returns the amount of time in milliseconds that the current
     * application has been running since the
     * {@link AppListener#initialize(java.util.Properties) initialize} method
     * was called on the application's {@code AppListener} object. Any time
     * that passes while the application is not running does not affect this
     * value. This means that this method can effectively be used to
     * measure the global wall clock time of the current application's
     * running state.
     * <p>
     * Note: This method cannot be reliably used to make fine-grained time
     * comparisons across task boundaries.  Unpredictable conditions such as
     * network latency mean that the resulting value may be subject to some
     * skew.  Additionally, the <em>global</em> accuracy of the clock time
     * may drift as much as a few seconds in either direction due to inherent
     * inaccuracies recovering application time after a system crash.
     *
     * @return the current global application time in milliseconds
     */
    long currentAppTimeMillis();

    /**
     * Converts a system time value representing the total elapsed time in
     * milliseconds since midnight, January 1, 1970 UTC to an "application time"
     * value.  An application time value is the total amount of  time in
     * milliseconds that the current application has been running since the
     * {@link AppListener#initialize(java.util.Properties) initialize} method
     * was called on its {@code AppListener} object.
     *
     * @param systemTimeMillis a system time value
     * @return the given system time converted into application time
     */
    long getAppTimeMillis(long systemTimeMillis);

    /**
     * Converts an "application time" value representing the total amount of
     * time in milliseconds that the current application has been running since
     * the {@link AppListener#initialize(java.util.Properties) initialize}
     * method was called on its {@code AppListener} object to a system time
     * value.  A system time value is the total elapsed time in milliseconds
     * since midnight, January 1, 1970 UTC.
     *
     * @param appTimeMillis an application time value
     * @return the given application time converted into system time
     */
    long getSystemTimeMillis(long appTimeMillis);
}
