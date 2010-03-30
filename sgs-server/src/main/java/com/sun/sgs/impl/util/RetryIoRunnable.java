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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.util.AbstractBasicService.isRetryableException;
import java.io.IOException;
import static java.util.logging.Level.FINEST;
import java.util.logging.Logger;

/**
 * A {@code Runnable} that supports performing an I/O operation that returns a
 * value and that will be retried in case of an I/O failure.  Retries will be
 * performed so long as the local node has not been shutdown, the maximum retry
 * time has not been exceeded, and the I/O operation has not thrown a
 * non-I/O-related exception that is not retryable.  Retries after an I/O
 * failure are performed after a specified delay to limit wasted effort during
 * a network outage.  If the I/O operation succeeds, it's result is supplied
 * for further processing before the {@code Runnable} exits.  If the I/O
 * operation fails, the failure is reported.
 *
 * @param	<R> the type of result of the I/O operation
 */
public abstract class RetryIoRunnable<R> extends ShouldRetryIo
    implements Runnable
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(RetryIoRunnable.class.getName()));

    /** The local node ID. */
    private final long nodeId;

    /**
     * Creates an instance of this class.
     *
     * @param	nodeId the local node ID
     * @param	maxRetry the maximum number of milliseconds to retry failing
     *		I/O operations
     * @param	retryWait the number of milliseconds to wait between retries
     * @throws	IllegalArgumentException if either of {@code maxRetry} or
     *		{@code retryWait} is negative
     */
    public RetryIoRunnable(long nodeId, long maxRetry, long retryWait) {
	super(maxRetry, retryWait);
	this.nodeId = nodeId;
    }

    /**
     * Performs the I/O operation.  If the operation throws an {@code
     * IOException}, or an exception that implements {@link
     * ExceptionRetryStatus} and whose {@link ExceptionRetryStatus#shouldRetry
     * shouldRetry} method returns {@code true}, then it will be retried until
     * the retry timeout is reached.  Other exceptions will prevent further
     * retries and will be passed in a call to {@link #reportFailure}.
     *
     * @return	the result of the I/O operation
     * @throws	IOException if the I/O operation fails
     * @throws	Exception if the operation's failure should be reported
     */
    protected abstract R callOnce() throws Exception;

    /**
     * Checks if the I/O operation should be abandoned due to a request to
     * shutdown the local node.
     *
     * @return	whether a shutdown has been requested
     */
    protected abstract boolean shutdownRequested();

    /**
     * Performs further processing on the results of the I/O operation as
     * returned by {@link #callOnce}.
     *
     * @param	result the return value of {@code callOnce}
     */
    protected abstract void runWithResult(R result);

    /**
     * Reports that an operation failed with an exception other than an {@link
     * IOException}.  Implementations may chose to report that the node has
     * failed.
     *
     * @param	exception the exception produced by the operation's failure
     */
    protected abstract void reportFailure(Throwable exception);

    /**
     * Calls {@link #callOnce} until it either succeeds, fails with an {@link
     * IOException} for more the maximum amount of time allowed for I/O
     * retries, fails with some other exception that is not retryable, or until
     * {@link #shutdownRequested} returns {@code true}.  If {@code callOnce}
     * succeeds, calls {@link #runWithResult} with the resulting value and then
     * returns.  If the I/O operation fails for more than the maximum time, or
     * if it fails with an exception other than an {@link IOException} that is
     * not retryable, calls {@link #reportFailure} and exits.
     */
    public void run() {
	String operation = logger.isLoggable(FINEST)
	    ? "Calling nodeId:" + nodeId + " " + this : null;
	try {
	    R result;
	    while (true) {
		if (shutdownRequested()) {
		    return;
		}
		logger.log(FINEST, operation);
		try {
		    result = callOnce();
		    break;
		} catch (RuntimeException e) {
		    ioSucceeded();
		    if (isRetryableException(e)) {
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
		logger.log(FINEST, operation + " returns " + result);
	    }
	    runWithResult(result);
	} catch (Throwable e) {
	    if (logger.isLoggable(FINEST)) {
		logger.logThrow(FINEST, e, operation + " throws");
	    }
	    reportFailure(e);
	    return;
	}
    }
}
