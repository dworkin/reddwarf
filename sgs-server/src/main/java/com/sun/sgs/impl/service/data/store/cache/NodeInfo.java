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
 */

package com.sun.sgs.impl.service.data.store.cache;

import com.sun.sgs.impl.util.lock.LockManager;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.Locker;
import com.sun.sgs.impl.util.lock.MultiLockManager;
import com.sun.sgs.impl.util.lock.MultiLocker;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link Locker} that stores information about a node, including storing
 * information about locks held and whether the node has been shutdown.  This
 * class is part of the implementation of {@link CachingDataStoreServerImpl}.
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

    /** Whether the node has been shutdown. */
    private boolean shutdown = false;

    /** The number of active calls on behalf of this node. */
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
     * Notes that this node has locked a key.
     *
     * @param	key the key
     */
    void noteLocked(Object key) {
	synchronized (locksHeld) {
	    locksHeld.add(key);
	}
    }

    /**
     * Notes that this node has released the lock for a key.
     *
     * @param	key the key
     */
    void noteUnlocked(Object key) {
	synchronized (locksHeld) {
	    locksHeld.remove(key);
	}
    }

    /** Releases all locks held by this node. */
    void releaseAllLocks() {
	LockManager<Object> lockManager = getLockManager();
	synchronized (locksHeld) {
	    for (Object key : locksHeld) {
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
     * Note the start of a call made on behalf of this node.
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

    /** Note the end of a call made on behalf of this node. */
    synchronized void nodeCallFinished() {
	activeCalls--;
	if (shutdown && activeCalls == 0) {
	    notifyAll();
	}
    }
}
