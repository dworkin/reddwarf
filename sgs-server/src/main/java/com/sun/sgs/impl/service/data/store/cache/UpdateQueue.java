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

import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.impl.service.data.store.cache.
    UpdateQueueRequest.DowngradeBinding;
import com.sun.sgs.impl.service.data.store.cache.
    UpdateQueueRequest.DowngradeObject;
import com.sun.sgs.impl.service.data.store.cache.
    UpdateQueueRequest.EvictBinding;
import com.sun.sgs.impl.service.data.store.cache.
    UpdateQueueRequest.EvictObject;
import com.sun.sgs.impl.service.data.store.cache.UpdateQueueRequest.Commit;
import com.sun.sgs.service.TransactionInterruptedException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The facility that data store nodes use to send updates to the data server.
 */
class UpdateQueue {

    /** The data store. */
    private final CachingDataStore store;

    /** The queue for sending requests in order to the server. */
    private final RequestQueueClient queue;

    /**
     * The number of slots available in the request queue for commit requests.
     */
    private final Semaphore commitAvailable;

    /**
     * Maps transaction ticks to information about the associated transaction.
     * The entries are ordered with the lowest transaction tick first in order
     * to find the items that are no longer waiting for earlier transactions to
     * complete.  Synchronize on this object when adding new entries to insure
     * that they are added in order.
     */
    private final NavigableMap<Long, PendingTxnInfo> pendingMap =
	new ConcurrentSkipListMap<Long, PendingTxnInfo>();

    /**
     * The context ID to use for the next new transaction.  Synchronize on
     * {@code pendingMap} when accessing this field.
     */
    private long nextContextId = 1;

    /**
     * Creates an instance of this class.
     *
     * @param	store the data store
     * @param	host the server host name
     * @param	port the server port
     * @param	updateQueueSize the size of the update queue
     * @throws	IOException if there is an I/O failure
     */
    UpdateQueue(final CachingDataStore store,
		String host,
		int port,
		int updateQueueSize)
	throws IOException
    {
	this.store = store;
	queue = new RequestQueueClient(
	    store.getNodeId(),
	    new RequestQueueClient.BasicSocketFactory(host, port),
	    store,
	    /* FIXME: Pass properties? */
	    new Properties());
	commitAvailable = new Semaphore(updateQueueSize);
    }

    /* -- Package access methods -- */

    /**
     * Notes the beginning of a new transaction and returns the transaction
     * context ID that should be associated with it.
     *
     * @return	the context ID for the new transaction
     */
    long beginTxn() {
	PendingTxnInfo info = new PendingTxnInfo();
	long contextId;
	synchronized (pendingMap) {
	    contextId = nextContextId++;
	    pendingMap.put(contextId, info);
	}
	return contextId;
    }

    /**
     * Prepares for commit by reserving a slot in the update queue for the
     * commit request.  Returns if successful, else throws an exception.
     *
     * @param	stop the time in milliseconds when waiting should fail
     * @throws	ResourceUnavailableException if no space becomes available in
     *		the update queue before the transaction times out
     * @throws	TransactionInterruptedException if the thread is interrupted
     *		while waiting for space in the update queue
     */
    void prepare(long stop) {
	try {
	    if (!commitAvailable.tryAcquire(stop, MILLISECONDS)) {
		throw new ResourceUnavailableException("Update queue full");
	    }
	} catch (InterruptedException e) {
	    throw new TransactionInterruptedException("Task interrupted", e);
	}
    }

    /**
     * Commits changes to the server.  The {@code oids} parameter contains the
     * object IDs of the objects that have been changed.  For each element of
     * that array, the element of the {@code oidValues} array in the same
     * position contains the new value for the object ID, or {@code null} if
     * the object should be removed.  If any of the object IDs are for newly
     * created objects, those IDs will be listed first and the {@code newOids}
     * parameter specifies how many of them there are.  The {@code names}
     * parameter contains the names whose bindings have been changed.  For each
     * element of that array, the element of the {@code nameValues} array in
     * the same position contains the new value for the name binding, or {@code
     * -1} if the name binding should be removed.  If any of the names are
     * being newly bound, those names will be listed first and the {@code
     * newNames} parameter specifies how many of them there are.
     *
     * @param	contextId the transaction context ID
     * @param	oids the object IDs
     * @param	oidValues the associated data values
     * @param	newOids the number of object IDs that are new
     * @param	names the names
     * @param	nameValues the associated name bindings
     * @param	newNames the number of names that are newly bound
     * @throws	IllegalArgumentException if {@code oids} and {@code oidValues}
     *		are not the same length, if {@code oids} contains a negative
     *		value, if {@code newOids} is negative or greater than the
     *		length of {@code oids}, if {@code names} and {@code nameValues}
     *		are not the same length, if {@code nameValues} contains a
     *		negative value, or if {@code newNames} is negative or greater
     *		than the length of {@code names}
     */
    void commit(long contextId,
		long[] oids,
		byte[][] oidValues,
		int newOids,
		String[] names,
		long[] nameValues,
		int newNames)
    {
	completed(contextId);
	addRequest(
	    contextId,
	    new Commit(oids, oidValues, newOids, names, nameValues, newNames,
		       store.new FailingCompletionHandler() {
			   @Override
		           public void completed() {
			       commitAvailable.release();
			   }
		       }));
    }

    /**
     * Notes that a transaction has been aborted.
     *
     * @param	contextId the transaction context ID
     * @param	prepared whether the transaction had been prepared
     */
    void abort(long contextId, boolean prepared) {
	if (prepared) {
	    commitAvailable.release();
	}
	completed(contextId);
    }
	

    /**
     * Evicts an object from the cache.  The {@link
     * CompletionHandler#completed} method of {@code handler} will be called
     * with {@code oid} when the eviction has been completed.
     *
     * @param	contextId the transaction context ID
     * @param	oid the ID of the object to evict
     * @param	completionHandler the handler to notify when the eviction has
     *		been completed 
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    void evictObject(
	long contextId, long oid, CompletionHandler completionHandler)
    {
	addRequest(contextId, new EvictObject(oid, completionHandler));
    }

    /**
     * Downgrades access to an object in the cache from write access to read
     * access.  The {@link CompletionHandler#completed} method of {@code
     * handler} will be called with {@code oid} when the downgrade has been
     * completed.
     *
     * @param	contextId the transaction context ID
     * @param	oid the object ID to evict
     * @param	completionHandler the handler to notify when the eviction has
     *		been completed 
     */
    void downgradeObject(
	long contextId, long oid, CompletionHandler completionHandler)
    {
	addRequest(contextId, new DowngradeObject(oid, completionHandler));
    }

    /**
     * Evicts a name binding from the cache.  The {@link
     * CompletionHandler#completed} method of {@code handler} will be called
     * with {@code name} when the eviction has been completed.
     *
     * @param	contextId the transaction context ID
     * @param	name the name
     * @param	completionHandler the handler to notify when the eviction has
     *		been completed 
     */
    void evictBinding(
	long contextId, String name, CompletionHandler completionHandler)
    {
	addRequest(contextId, new EvictBinding(name, completionHandler));
    }

    /**
     * Downgrades access to a name binding in the cache from write access to
     * read access.  The {@link CompletionHandler#completed} method of {@code
     * handler} will be called with {@code name} when the downgrade has been
     * completed.
     *
     * @param	contextId the transaction context ID
     * @param	name the name
     * @param	completionHandler the handler to notify when the downgrade has
     *		been completed 
     */
    void downgradeBinding(
	long contextId, String name, CompletionHandler completionHandler)
    {
	addRequest(contextId, new DowngradeBinding(name, completionHandler));
    }

    void shutdown() {
	queue.shutdown();
    }

    /**
     * Returns the context ID of the oldest transaction that is still pending.
     */
    long highestPendingContextId() {
	try {
	    return pendingMap.firstKey();
	} catch (NoSuchElementException e) {
	    return Long.MAX_VALUE;
	}
    }

    /* -- Private methods -- */

    /**
     * Adds a request to the table if the associated transaction is still
     * pending.  If the request is not added, then the caller should add the
     * request to the update queue directly.
     *
     * @param	contextId the transaction context ID
     * @param	request the request
     */
    private void addRequest(long contextId, Request request) {
	PendingTxnInfo info = pendingMap.get(contextId);
	if (info == null || !info.addRequest(request)) {
	    queue.addRequest(request);
	}
    }

    /**
     * Note that the transaction with the associated context ID has completed,
     * either by committing or aborting.
     */
    private void completed(long contextId) {
	PendingTxnInfo info = pendingMap.get(contextId);
	assert info != null;
	info.setComplete();
	if (contextId == pendingMap.firstKey()) {
	    pendingMap.remove(contextId);
	    List<Request> requests = info.getRequests();
	    assert requests != null;
	    for (Request request : requests) {
		queue.addRequest(request);
	    }
	    while (true) {
		Entry<Long, PendingTxnInfo> entry = pendingMap.firstEntry();
		if (entry == null) {
		    break;
		}
		requests = entry.getValue().getRequests();
		if (requests == null) {
		    break;
		}
		pendingMap.remove(entry.getKey());
		for (Request request : requests) {
		    queue.addRequest(request);
		}
	    }
	}
    }

    /* -- Nested classes -- */

    /** Records information about an active or pending transaction. */
    private static class PendingTxnInfo {

	/** Whether the transaction has been completed. */
	private boolean complete = false;

	/**
	 * Whether the transaction is still present in the pending transaction
	 * map, meaning that it has or could receive, associated requests,
	 * because there are still active transactions with lower context IDs.
	 */
	private boolean pending = true;

	/** The requests associated with this transaction or {@code null}. */
	private List<Request> requests = null;

	/** Creates an instance of this class. */
	PendingTxnInfo() { }

	/** Notes that the associated transaction is complete. */
	synchronized void setComplete() {
	    assert !complete;
	    complete = true;
	}

	/**
	 * Returns the requests for the associated transaction.  Returns {@code
	 * null} if the transaction is not yet complete.  If the transaction is
	 * pending, returns a list containing the associated requests, if any,
	 * and marks the transaction as not pending.  If the transaction is not
	 * pending, returns an empty list.
	 *
	 * @return	the requests or {@code null}
	 */
	synchronized List<Request> getRequests() {
	    if (!complete) {
		return null;
	    } else if (pending) {
		pending = false;
		if (requests != null) {
		    return requests;
		}
	    }
	    return Collections.emptyList();
	}

	/**
	 * Attempts to associate a request with this transaction.  The request
	 * is added, and {@code true} is returned, if this transaction is still
	 * pending, otherwise returns {@code false}.  If the return value is
	 * {@code false}, the caller should add the request directly to the
	 * request queue.
	 *
	 * @param	request the request
	 * @return	whether the request was added
	 */
	synchronized boolean addRequest(Request request) {
	    if (!pending) {
		return false;
	    }
	    if (requests == null) {
		requests = new LinkedList<Request>();
	    }
	    requests.add(request);
	    return true;
	}
    }
}
