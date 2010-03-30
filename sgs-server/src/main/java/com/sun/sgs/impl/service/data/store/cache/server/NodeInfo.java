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

package com.sun.sgs.impl.service.data.store.cache.server;

import com.sun.sgs.impl.service.data.store.cache.CallbackServer;
import com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueServer;
import com.sun.sgs.impl.service.data.store.cache.queue.UpdateQueueRequest;
import com.sun.sgs.impl.util.lock.LockManager;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.Locker;
import com.sun.sgs.impl.util.lock.MultiLockManager;
import com.sun.sgs.impl.util.lock.MultiLocker;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link Locker} that stores information about a node, including storing
 * information about locks held, the associated update queue server, whether
 * the node has been shutdown, and the number of currently active calls being
 * made by the node.
 */
class NodeInfo extends MultiLocker<Object> {

    /** The node ID. */
    final long nodeId;

    /** The callback server for the node. */
    final CallbackServer callbackServer;

    /**
     * The keys of locks held by this node.  Synchronize on this set when
     * accessing it.
     */
    private final Set<Object> locksHeld = new HashSet<Object>();

    /** The update queue server for the node. */
    final RequestQueueServer<UpdateQueueRequest> updateQueueServer;

    /**
     * Whether the node has been requested to shutdown.  Synchronize on this
     * instance when accessing this field.
     */
    private boolean shutdown = false;

    /**
     * The number of active calls currently being made on behalf of this node.
     * Synchronize on this instance when accessing this field.
     */
    private int activeCalls = 0;

    /**
     * Creates an instance of this class.
     *
     * @param	lockManager the lock manager
     * @param	nodeId the node ID
     * @param	callbackServer the callback server
     * @param	updateQueueServer the update queue server
     */
    NodeInfo(MultiLockManager<Object> lockManager,
	     long nodeId,
	     CallbackServer callbackServer,
	     RequestQueueServer<UpdateQueueRequest> updateQueueServer)
    {
	super(lockManager);
	this.nodeId = nodeId;
	this.callbackServer = callbackServer;
	this.updateQueueServer = updateQueueServer;
    }

    @Override
    public String toString() {
	return "NodeInfo[nodeId:" + nodeId + "]";
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns an instance of {@link NodeRequest}.
     */
    @Override
    protected LockRequest<Object> newLockRequest(
	Object key, boolean forWrite, boolean upgrade)
    {
	return new NodeRequest(this, key, forWrite, upgrade);
    }

    /**
     * Notifies this locker that the associated node has locked a key.
     *
     * @param	key the key
     */
    void noteLocked(Object key) {
	synchronized (locksHeld) {
	    locksHeld.add(key);
	}
    }

    /**
     * Notifies this locker that the associated node has released the lock for
     * a key.
     *
     * @param	key the key
     */
    void noteUnlocked(Object key) {
	synchronized (locksHeld) {
	    locksHeld.remove(key);
	}
    }

    /**
     * Releases all locks held by this node.
     *
     * @param	debug whether to print debugging output
     */
    void releaseAllLocks(boolean debug) {
	LockManager<Object> lockManager = getLockManager();
	synchronized (locksHeld) {
	    for (Object key : locksHeld) {
		if (debug) {
		    CachingDataStoreServerImpl.debugOutput(
			this, "e ", key, "shutdown", null);
		}
		lockManager.releaseLock(this, key);
	    }
	}
    }

    /**
     * Marks this node as shutdown, which will cause all future calls to {@link
     * #nodeCallStarted} to fail.  Waits for all currently active calls to
     * complete, and for the update queue server to disconnect before
     * returning.  Returns {@code true} if this was the first call to shutdown,
     * else {@code false}.
     *
     * @return	whether this was the first shutdown call
     */
    synchronized boolean shutdown() {
	boolean didShutdown;
	if (shutdown) {
	    didShutdown = false;
	} else {
	    shutdown = true;
	    didShutdown = true;
	}
	while (activeCalls > 0) {
	    try {
		wait();
	    } catch (InterruptedException e) {
	    }
	}
	if (didShutdown) {
	    updateQueueServer.disconnect();
	}
	return didShutdown;
    }

    /**
     * Notes the start of a call made on behalf of the associated node.
     *
     * @throws	IllegalStateException if the node has failed
     */
    synchronized void nodeCallStarted() {
	if (shutdown) {
	    throw new IllegalStateException(
		"Node " + nodeId + " has been shut down");
	}
	activeCalls++;
    }

    /** Notes the end of a call made on behalf of the associated node. */
    synchronized void nodeCallFinished() {
	activeCalls--;
	if (shutdown && activeCalls == 0) {
	    notifyAll();
	}
    }
}
