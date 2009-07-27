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

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import static java.util.logging.Level.FINER;
import java.util.logging.Logger;

/**
 * Requests and caches new object IDs from the {@link CachingDataStoreServer}.
 */
class NewObjectIdCache {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(NewObjectIdCache.class.getName()));

    /** The data store */
    private final CachingDataStore store;

    /** The number of new object IDs to allocate at a time. */
    private final int batchSize;

    /** A thread for obtaining new object IDs.  This thread may be dead. */
    private Thread newObjectsThread;

    /**
     * The current range for allocating IDs, or {@code null} if this instance
     * is being constructed.
     */
    private Range currentRange = null;

    /**
     * The next range for allocating IDs, or {@code null} if the next range of
     * IDs has not been obtained yet.
     */
    private Range nextRange = null;

    /**
     * Creates an instance of this class.
     *
     * @param	store the data store
     * @param	batchSize the number of new object IDs to allocate at a time 
     */
    NewObjectIdCache(CachingDataStore store, int batchSize) {
	synchronized (this) {
	    this.store = store;
	    this.batchSize = batchSize;
	    newObjectsThread = createNewObjectsThread();
	    newObjectsThread.start();
	    while (currentRange == null && !store.getShutdownRequested()) {
		try {
		    wait();
		} catch (InterruptedException e) {
		}
	    }
	}
    }

    /**
     * Returns a new object ID or {@code -1} if it is unable to obtain the ID
     * before the client started shutting down.
     */
    synchronized long getNewObjectId() {
	if (!currentRange.isEmpty()) {
	    long result = currentRange.next();
	    if (currentRange.isHalfEmpty() &&
		nextRange == null &&
		!newObjectsThread.isAlive() &&
		!store.getShutdownTxnsCompleted())
	    {
		newObjectsThread = createNewObjectsThread();
		newObjectsThread.start();
	    }
	    return result;
	}
	while (nextRange == null) {
	    if (store.getShutdownTxnsCompleted()) {
		throw new IllegalArgumentException(
		    "DataStoreCache is shutting down");
	    }
	    try {
		if (logger.isLoggable(FINER)) {
		    logger.log(FINER, "Blocked waiting for new object IDs");
		}
		wait();
	    } catch (InterruptedException e) {
		continue;
	    }
	}
	if (logger.isLoggable(FINER)) {
	    logger.log(FINER,
		       "Switching new object ID blocks from " + currentRange +
		       " to " + nextRange);
	}
	currentRange = nextRange;
	nextRange = null;
	return currentRange.next();
    }

    /** Shuts down this instance. */
    synchronized void shutdown() {
	if (newObjectsThread != null && newObjectsThread.isAlive()) {
	    newObjectsThread.interrupt();
	    while (true) {
		try {
		    newObjectsThread.join();
		    break;
		} catch (InterruptedException e) {
		}
	    }
	}
    }

    /** Creates a thread for obtaining new object IDs. */
    private Thread createNewObjectsThread() {
	return new Thread(
	    new NewObjectsRunnable(store), "CachingDataStore newObjects");
    }

    /** A {@code Runnable} that obtains new object IDs. */
    private class NewObjectsRunnable extends RetryIoRunnable<Long> {
	NewObjectsRunnable(CachingDataStore store) {
	    super(store);
	}
	Long callOnce() throws IOException {
	    logger.log(FINER, "Requesting new object IDs");
	    return store.getServer().newObjectIds(batchSize);
	}
	void runWithResult(Long result) {
	    Range range = new Range(result, result + batchSize - 1);
	    synchronized (NewObjectIdCache.this) {
		if (currentRange == null) {
		    currentRange = range;
		} else {
		    assert nextRange == null;
		    nextRange = range;
		}
		if (logger.isLoggable(FINER)) {
		    logger.log(FINER, "Obtained new object IDs: " + range);
		}
		NewObjectIdCache.this.notifyAll();
	    }
	}
    }

    /** Represents a range of new object IDs. */
    private static class Range {

	/** The first available ID. */
	private long first;

	/** The last available ID. */
	private final long last;

	/** The ID halfway through the original range. */
	private final long half;

	/** Creates a new range. */
	Range(long first, long last) {
	    this.first = first;
	    this.last = last;
	    half = (first + last) / 2;
	}

	@Override
	public String toString() {
	    return "Range[first:" + first + ", last:" + last + "]";
	}

	/** Checks if the range is empty */
	boolean isEmpty() {
	    return first > last;
	}

	/** Checks if the range is at last half empty. */
	boolean isHalfEmpty() {
	    return first > half;
	}

	/** Allocates and returns the next available ID. */
	long next() {
	    assert first <= last;
	    return first++;
	}
    }
}
