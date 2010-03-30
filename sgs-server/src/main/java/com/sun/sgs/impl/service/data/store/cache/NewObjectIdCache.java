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

package com.sun.sgs.impl.service.data.store.cache;

import com.sun.sgs.impl.service.data.store.cache.server.CachingDataStoreServer;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import static java.util.logging.Level.FINER;
import java.util.logging.Logger;

/**
 * Requests and caches new object IDs from the {@link CachingDataStoreServer}.
 * This class allocates new object IDs in batches, allocating the first batch
 * in the constructor.  When half of the current batch of IDs is used up, it
 * allocates another batch in a separate thread, in hopes that the next batch
 * of IDs will be available by the time the current batch is completely
 * used.
 */
class NewObjectIdCache {

    /** The name of this class. */
    private static final String CLASSNAME = NewObjectIdCache.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The data store */
    private final CachingDataStore store;

    /** The number of new object IDs to allocate at a time. */
    private final int batchSize;

    /**
     * A thread for obtaining new object IDs, which may be dead, or {@code
     * null} if this instance is being constructed.  Synchronize on this
     * instance when accessing this field.
     */
    private Thread newObjectsThread;

    /**
     * The current range for allocating IDs, or {@code null} if this instance
     * is being constructed.  Synchronize on this instance when accessing this
     * field.
     */
    private Range currentRange = null;

    /**
     * The next range for allocating IDs, or {@code null} if the next range of
     * IDs has not been obtained yet.  Synchronize on this instance when
     * accessing this field.
     */
    private Range nextRange = null;

    /**
     * Creates an instance of this class, starts a thread that allocates IDs,
     * and waits for the first range to be allocated.
     *
     * @param	store the data store
     * @param	batchSize the number of new object IDs to allocate at a time
     * @throws	IllegalArgumentException if {@code batchSize} is less than
     *		{@code 2}
     */
    NewObjectIdCache(CachingDataStore store, int batchSize) {
	if (batchSize < 2) {
	    throw new IllegalArgumentException(
		"The batchSize must be at least 2");
	}
	this.store = store;
	this.batchSize = batchSize;
	synchronized (this) {
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
     * Returns a new object ID.
     *
     * @return	the new object ID
     * @throws	IllegalStateException if the data store has started shutting
     *		down
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
	    /*
	     * The thread will find out that a shutdown is underway by checking
	     * with the data store, so no need to set a flag here.
	     */
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
	    new NewObjectsRunnable(), CLASSNAME + ".newObjects");
    }

    /** A {@code Runnable} that obtains new object IDs. */
    private class NewObjectsRunnable
	extends CachingDataStore.BasicRetryIoRunnable<Long>
    {
	NewObjectsRunnable() {
	    super(store);
	}
	@Override
	public String toString() {
	    return "NewObjectsRunnable[]";
	}
	protected Long callOnce() throws IOException {
	    logger.log(FINER, "Requesting new object IDs");
	    return store.getServer().newObjectIds(batchSize);
	}
	protected void runWithResult(Long result) {
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

	/** Checks if the range has no available IDs. */
	boolean isEmpty() {
	    return first > last;
	}

	/** Checks if the range has no more than half of its IDs available. */
	boolean isHalfEmpty() {
	    return first > half;
	}

	/** Allocates and returns the next available ID. */
	long next() {
	    assert !isEmpty();
	    return first++;
	}
    }
}
