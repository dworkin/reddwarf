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

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import static java.util.logging.Level.FINEST;
import java.util.logging.Logger;

/**
 * A {@code Runnable} that supports retrying an I/O operation so long as the
 * data store has not been shutdown, and exits and reports failure if the I/O
 * operation fails for too long. <p>
 *
 * This class is part of the implementation of {@link CachingDataStore}.
 *
 * @param	<R> the type of result of the I/O operation
 */
abstract class RetryIoRunnable<R> extends ShouldRetryIo implements Runnable {

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(RetryIoRunnable.class.getName()));

    /** The data store. */
    final CachingDataStore store;

    /**
     * Creates an instance of this class.
     *
     * @param	store the data store, for handling shutdown and failure
     */
    RetryIoRunnable(CachingDataStore store) {
	super(store.getMaxRetry(), store.getRetryWait());
	this.store = store;
    }

    /**
     * Performs the I/O operation.  If the operation throws an {@code
     * IOException}, or an exception that implements {@link
     * ExceptionRetryStatus} and whose {@link ExceptionRetryStatus#shouldRetry
     * shouldRetry} method returns {@code true}, then it will be retried until
     * the retry timeout is reached.  Other exceptions will be treated as
     * permanent failures.
     *
     * @return	the result of the I/O operation
     * @throws	IOException if the I/O operation fails
     * @throws	Exception if the operation fails permanently
     */
    abstract R callOnce() throws Exception;

    /** 
     * Checks if the I/O operation should be abandoned due to a shutdown
     * request. <p>
     *
     * The default implementation returns {@code true} if the data store has
     * completed shutting down transactions.
     */
    boolean shutdownRequested() {
	return store.getShutdownTxnsCompleted();
    }

    /**
     * Performs remaining operations using the return value of {@link
     * #callOnce}.
     *
     * @param	result the return value of {@code callOnce}
     */
    abstract void runWithResult(R result);

    /**
     * Calls {@link #callOnce} until it either succeeds, fails for the maximum
     * amount of time allowed for I/O retries, or until {@link
     * #shutdownRequested} returns {@code true}.  If {@code callOnce} succeeds,
     * calls {@link #runWithResult} with the resulting value and then returns.
     * If the I/O operation fails for more than the maximum time, calls {@link
     * CachingDataStore#reportFailure}.
     */
    public void run() {
	try {
	    R result;
	    while (true) {
		if (shutdownRequested()) {
		    return;
		}
		try {
		    if (logger.isLoggable(FINEST)) {
			logger.log(FINEST,
				   "Calling nodeId:" + store.nodeId +
				   " " + this);
		    }
		    result = callOnce();
		    break;
		} catch (RuntimeException e) {
		    ioSucceeded();
		    if (e instanceof ExceptionRetryStatus &&
			((ExceptionRetryStatus) e).shouldRetry())
		    {
			logger.logThrow(FINEST, e, "Retrying: {0}", this);
		    } else {
			throw e;
		    }
		} catch (IOException e) {
		    if (shouldRetry()) {
			logger.logThrow(
			    FINEST, e, "Retrying I/O failure: {0}", this);
		    } else {
			throw e;
		    }
		}
	    }
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST,
			   "Calling nodeId:" + store.nodeId + " " + this +
			   " returns " + result);
	    }
	    runWithResult(result);
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(
		    FINEST, e,
		    "Calling nodeId:" + store.nodeId + " " + this + " throws");
	    }
	    store.reportFailure(
		new Exception("Operation " + this + " failed: " + e, e));
	    return;
	}
    }
}
