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

package com.sun.sgs.impl.service.data.store.cache.queue;

import com.sun.sgs.app.ResourceUnavailableException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import com.sun.sgs.impl.service.data.store.cache.FailureReporter;
import com.sun.sgs.impl.service.data.store.cache.queue.
    UpdateQueueRequest.Commit;
import com.sun.sgs.impl.service.data.store.cache.queue.
    UpdateQueueRequest.DowngradeBinding;
import com.sun.sgs.impl.service.data.store.cache.queue.
    UpdateQueueRequest.DowngradeObject;
import com.sun.sgs.impl.service.data.store.cache.queue.
    UpdateQueueRequest.EvictBinding;
import com.sun.sgs.impl.service.data.store.cache.queue.
    UpdateQueueRequest.EvictObject;
import com.sun.sgs.service.TransactionInterruptedException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The facility that data store nodes use to send updates to the data server.
 * The implementation uses a {@link RequestQueueClient} to communicate requests
 * to the server in order.  It makes sure that transactions are committed in
 * order, and that locks are not released until the transactions in which they
 * were used have committed.  It limits the number of unacknowledged commit
 * requests that can appear in the queue to make sure that the node does not
 * outstrip the capacity of the server.  Eviction and downgrade requests are
 * not limited in the queue, though, since they represent a response to
 * requests already being processed on the server. <p>
 *
 * This class is part of the implementation of {@link CachingDataStore}.
 */
public class UpdateQueue {

    /**
     * The ratio between the maximum number of commit requests permitted to be
     * waiting in the queue and the maximum total number of waiting requests,
     * which includes downgrades and evictions.
     */
    private static final int REQUEST_QUEUE_PROPORTION = 2;

    /** The queue for sending requests in order to the server. */
    private final RequestQueueClient queue;

    /**
     * The number of slots available in the request queue for commit requests.
     */
    private final Semaphore commitAvailable;

    /**
     * Maps transaction context IDs to information about the associated
     * transaction for transactions that are either active or waiting to be
     * submitted to the request queue.  The entries are ordered with the lowest
     * context ID first in order to find the items that are no longer waiting
     * for earlier transactions to finish.  Synchronize on this object when
     * adding new entries to insure that they are added in order.
     */
    private final NavigableMap<Long, PendingTxnInfo> pendingSubmitMap =
	new ConcurrentSkipListMap<Long, PendingTxnInfo>();

    /**
     * Stores the context IDs of transactions that have had their commit
     * requests submitted to the request queue but that have not been
     * acknowledged by the server yet.
     */
    private final NavigableSet<Long> pendingAcknowledgeSet =
	new ConcurrentSkipListSet<Long>();

    /**
     * The context ID to use for the next new transaction.  Synchronize on
     * {@code pendingSubmitMap} when accessing this field.
     */
    private long nextContextId = 1;

    /**
     * Creates an instance of this class.
     *
     * @param	host the server host name
     * @param	port the server port
     * @param	updateQueueSize the maximum number of commit requests
     *		permitted to be waiting in the queue
     * @param	nodeId the local node ID
     * @param	maxRetry the maximum retry time for I/O operations
     * @param	retryWait the retry wait for failed I/O operations
     * @param	failureReporter the failure reporter
     * @throws	IOException if there is an I/O failure
     */
    public UpdateQueue(String host,
		       int port,
		       int updateQueueSize,
		       long nodeId,
		       long maxRetry,
		       long retryWait,
		       FailureReporter failureReporter)
	throws IOException
    {
	queue = new RequestQueueClient(
	    nodeId, new RequestQueueClient.BasicSocketFactory(host, port),
	    failureReporter, maxRetry, retryWait,
	    REQUEST_QUEUE_PROPORTION * updateQueueSize);
	commitAvailable = new Semaphore(updateQueueSize);
    }

    /* -- Public methods -- */

    /**
     * Notes the beginning of a new transaction and returns the transaction
     * context ID that should be associated with it.
     *
     * @return	the context ID for the new transaction
     */
    public long beginTxn() {
	PendingTxnInfo info = new PendingTxnInfo();
	long contextId;
	synchronized (pendingSubmitMap) {
	    contextId = nextContextId++;
	    pendingSubmitMap.put(contextId, info);
	}
	return contextId;
    }

    /**
     * Prepares for commit by reserving a slot in the update queue for the
     * commit request, waiting for space in the queue if needed.  Returns if
     * successful, else throws an exception.
     *
     * @param	stop the time in milliseconds when the transaction times out
     * @throws	ResourceUnavailableException if no space becomes available in
     *		the update queue before the transaction times out
     * @throws	TransactionInterruptedException if the thread is interrupted
     *		while waiting for space in the update queue
     * @throws	TransactionTimeoutException if the transaction has timed out
     */
    public void prepare(long stop) {
	try {
	    long timeout = stop - System.currentTimeMillis();
	    if (timeout < 0) {
		throw new TransactionTimeoutException("Transaction timed out");
	    } else if (!commitAvailable.tryAcquire(timeout, MILLISECONDS)) {
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
     * -1} if the name binding should be removed.
     *
     * @param	contextId the transaction context ID
     * @param	oids the object IDs
     * @param	oidValues the associated data values
     * @param	newOids the number of object IDs that are new
     * @param	names the names
     * @param	nameValues the associated name bindings
     * @throws	IllegalArgumentException if {@code oids} and {@code oidValues}
     *		are not the same length, if {@code oids} contains a negative
     *		value, if {@code newOids} is negative or greater than the
     *		length of {@code oids}, if {@code names} and {@code nameValues}
     *		are not the same length, if {@code nameValues} contains a
     *		negative value
     */
    public void commit(final long contextId,
		       long[] oids,
		       byte[][] oidValues,
		       int newOids,
		       String[] names,
		       long[] nameValues)
    {
	pendingAcknowledgeSet.add(contextId);
	txnFinished(contextId);
	queue.addRequest(
	    new Commit(contextId, oids, oidValues, newOids, names, nameValues,
		       new CompletionHandler() {
			   @Override
			   public void completed() {
			       pendingAcknowledgeSet.remove(contextId);
			       commitAvailable.release();
			   }
		       }));
    }

    /**
     * Notes that a transaction has been aborted, or that it committed without
     * any modifications.
     *
     * @param	contextId the transaction context ID
     * @param	prepared whether the transaction had been prepared
     */
    public void abort(long contextId, boolean prepared) {
	if (prepared) {
	    commitAvailable.release();
	}
	txnFinished(contextId);
    }

    /**
     * Evicts an object from the cache.  The {@link
     * CompletionHandler#completed} method of {@code handler} will be called
     * when the eviction has been completed.
     *
     * @param	contextId the transaction context ID
     * @param	oid the ID of the object to evict
     * @param	completionHandler the handler to notify when the eviction has
     *		been completed
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    public void evictObject(
	long contextId, long oid, CompletionHandler completionHandler)
    {
	addRequest(contextId, new EvictObject(oid, completionHandler));
    }

    /**
     * Downgrades access to an object in the cache from write access to read
     * access.  The {@link CompletionHandler#completed} method of {@code
     * handler} will be called when the downgrade has been completed.
     *
     * @param	contextId the transaction context ID
     * @param	oid the object ID to evict
     * @param	completionHandler the handler to notify when the downgrade has
     *		been completed
     * @throws	IllegalArgumentException if {@code oid} is negative
     */
    public void downgradeObject(
	long contextId, long oid, CompletionHandler completionHandler)
    {
	addRequest(contextId, new DowngradeObject(oid, completionHandler));
    }

    /**
     * Evicts a name binding from the cache.  The {@link
     * CompletionHandler#completed} method of {@code handler} will be called
     * when the eviction has been completed.
     *
     * @param	contextId the transaction context ID
     * @param	name the name, which may be {@code null}
     * @param	completionHandler the handler to notify when the eviction has
     *		been completed
     */
    public void evictBinding(
	long contextId, String name, CompletionHandler completionHandler)
    {
	addRequest(contextId, new EvictBinding(name, completionHandler));
    }

    /**
     * Downgrades access to a name binding in the cache from write access to
     * read access.  The {@link CompletionHandler#completed} method of {@code
     * handler} will be called when the downgrade has been completed.
     *
     * @param	contextId the transaction context ID
     * @param	name the name, which may be {@code null}
     * @param	completionHandler the handler to notify when the downgrade has
     *		been completed
     */
    public void downgradeBinding(
	long contextId, String name, CompletionHandler completionHandler)
    {
	addRequest(contextId, new DowngradeBinding(name, completionHandler));
    }

    /** Shuts down the queue. */
    public void shutdown() {
	queue.shutdown();
    }

    /**
     * Returns the context ID of the oldest transaction which has not finished,
     * or whose commit request has been sent to the server but not yet
     * acknowledged, or {@link Long#MAX_VALUE Long.MAX_VALUE} if there are no
     * pending transactions.
     *
     * @return	the lowest pending context ID or {@code Long.MAX_VALUE}
     */
    public long lowestPendingContextId() {
	long result;
	try {
	    result = pendingAcknowledgeSet.first();
	} catch (NoSuchElementException e) {
	    result = Long.MAX_VALUE;
	}
	try {
	    long firstPendingSubmit = pendingSubmitMap.firstKey();
	    if (firstPendingSubmit < result) {
		result = firstPendingSubmit;
	    }
	} catch (NoSuchElementException e) {
	}
	return result;
    }

    /* -- Private methods -- */

    /**
     * Adds a request to the pending submission map if the associated
     * transaction is still waiting to be submitted to the server, and
     * otherwise adds the request directly to the update queue.  The context ID
     * should be a value that has been returned by beginTxn.
     *
     * @param	contextId the transaction context ID
     * @param	request the request
     */
    private void addRequest(long contextId, Request request) {
	PendingTxnInfo info = pendingSubmitMap.get(contextId);
	if (info == null || !info.addRequest(request)) {
	    queue.addRequest(request);
	}
    }

    /**
     * Notes that the transaction with the associated context ID has finished,
     * either by committing or aborting.
     */
    private void txnFinished(long contextId) {
	PendingTxnInfo info = pendingSubmitMap.get(contextId);
	/*
	 * Information about the transaction should already be present from
	 * when the transaction started.
	 */
	assert info != null;
	info.setFinished();
	try {
	    /*
	     * TBD: Rather than having a race for who will process the next
	     * batch of transactions, it might be better to use a lock to make
	     * sure only one thread does this.  -tjb@sun.com (01/12/2010)
	     */
	    if (contextId != pendingSubmitMap.firstKey()) {
		/*
		 * Earlier transactions are not yet finished, so let them
		 * handle the processing.
		 */
		return;
	    }
	} catch (NoSuchElementException e) {
	    /*
	     * The entry must have been removed in the meantime by the
	     * processing performed by an earlier transaction.
	     */
	    return;
	}
	/*
	 * This transaction is the earliest pending transaction -- remove it
	 * and process it now.
	 */
	pendingSubmitMap.remove(contextId);
	List<Request> requests = info.getRequests();
	/*
	 * The requests should not be null because we have already marked this
	 * transaction as finished.
	 */
	assert requests != null;
	/* Process any requests associated with this finished transaction */
	for (Request request : requests) {
	    queue.addRequest(request);
	}
	while (true) {
	    Entry<Long, PendingTxnInfo> entry = pendingSubmitMap.firstEntry();
	    if (entry == null) {
		/* Queue is empty */
		break;
	    }
	    requests = entry.getValue().getRequests();
	    if (requests == null) {
		/* The transaction is not finished */
		break;
	    }
	    /*
	     * Remove the transaction from the queue and process its requests
	     */
	    pendingSubmitMap.remove(entry.getKey());
	    for (Request request : requests) {
		queue.addRequest(request);
	    }
	}
    }

    /* -- Nested classes -- */

    /** Records information about an active or pending transaction. */
    private static class PendingTxnInfo {

	/** Whether the transaction has finished. */
	private boolean finished = false;

	/**
	 * Whether the transaction is still present in the pending submit map,
	 * meaning that it has or could receive, associated requests, because
	 * there are still active transactions with lower context IDs.
	 */
	private boolean pendingSubmit = true;

	/** The requests associated with this transaction or {@code null}. */
	private List<Request> requests = null;

	/** Creates an instance of this class. */
	PendingTxnInfo() { }

	/** Notes that the associated transaction has finished. */
	synchronized void setFinished() {
	    assert !finished;
	    finished = true;
	}

	/**
	 * Returns the requests for the associated transaction.  Returns {@code
	 * null} if the transaction has not yet finished.  If the transaction
	 * is waiting to be submitted to the server, returns a list containing
	 * the associated requests, if any, and marks the transaction as not
	 * pending.  If the transaction is not pending, returns an empty list.
	 *
	 * @return	the requests or {@code null}
	 */
	synchronized List<Request> getRequests() {
	    if (!finished) {
		return null;
	    } else if (pendingSubmit) {
		pendingSubmit = false;
		if (requests != null) {
		    return requests;
		}
	    }
	    return Collections.emptyList();
	}

	/**
	 * Attempts to associate a request with this transaction.  The request
	 * is added, and {@code true} is returned, if this transaction is still
	 * waiting to be submitted to the server, otherwise returns {@code
	 * false}.  If the return value is {@code false}, the caller should add
	 * the request directly to the request queue.
	 *
	 * @param	request the request
	 * @return	whether the request was added
	 */
	synchronized boolean addRequest(Request request) {
	    if (!pendingSubmit) {
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
