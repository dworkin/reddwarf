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
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

/**
 * A {@code Runnable} that supports retrying an I/O operation so long as the
 * data store has not been shutdown, and exits and reports failure if the I/O
 * operation fails for too long.
 *
 * @param	<R> the type of result of the I/O operation
 */
abstract class RetryIoRunnable<R> implements Runnable {

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(RetryIoRunnable.class.getName()));

    /** Data store, for managing shutdown. */
    final CachingDataStore store;

    /**
     * The time of the first I/O failure, or {@code -1} if no failures have
     * been seen.
     */
    private long failureStarted = -1;

    /** Creates an instance of this class. */
    RetryIoRunnable(CachingDataStore store) {
	this.store = store;
    }

    /**
     * Performs the I/O operation.
     *
     * @return	the result of the I/O operation
     * @throws	IOException if the I/O operation fails
     */
    abstract R callOnce() throws IOException;

    /**
     * Performs remaining operations using the result of a successful call to
     * {@link #callOnce}.
     *
     * @param	value the result of the successful call to {@code
     *		callOnce} 
     */
    abstract void callWithResult(R result);

    /**
     * Checks if the I/O operation should be abandoned due to shutdown of the
     * data store. <p>
     *
     * This implementation checks if all active transactions have completed.
     */
    boolean shouldShutdown() {
	return store.getShutdownTxnsCompleted();
    }

    /**
     * Calls {@link #callOnce} until it succeeds, until it fails for the
     * maximum amount of time allowed for I/O retries, or until {@link
     * #shutdown} is set to {@code true}.  If {@code callOnce} succeeds, calls
     * {@link run(V)} with the resulting value and then returns.  If the I/O
     * operation fails for more than the maximum time, calls {@link
     * CachingDataStore#reportFailure}.
     */
    public void run() {
	R result;
	while (true) {
	    if (shouldShutdown()) {
		return;
	    }
	    try {
		result = callOnce();
		break;
	    } catch (IOException e) {
		if (shouldShutdown()) {
		    return;
		}
		long now = System.currentTimeMillis();
		if (failureStarted == -1) {
		    failureStarted = now;
		} else if (now - failureStarted > store.getMaxRetry()) {
		    logger.logThrow(WARNING, e,
				    "Requesting shutdown due to" +
				    " continued I/O failure");
		    store.reportFailure();
		    return;
		} else if (logger.isLoggable(FINER)) {
		    logger.logThrow(FINER, e, "I/O failure");
		}
	    }
	}
	callWithResult(result);
    }
}
