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

package com.sun.sgs.test.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Define an object to use for waiting for the completion of a set of
 * operations.
 */
public class AwaitDone {

    /** The number of operations that have not completed. */
    private final CountDownLatch done;

    /** The exception from a failed operation, or null. */
    private final AtomicReference<Throwable> failure =
	new AtomicReference<Throwable>();

    /**
     * Creates an instance that waits for the specified number of operations.
     *
     * @param	count the number of operations
     */
    public AwaitDone(int count) {
	done = new CountDownLatch(count);
    }

    /** Notes that an operation completed successfully. */
    public void taskSucceeded() {
	done.countDown();
    }

    /**
     * Notes that an operation failed with the specified exception.
     *
     * @param	exception the exception
     */
    public void taskFailed(Throwable exception) {
	failure.set(exception);
	while (done.getCount() > 0) {
	    done.countDown();
	}
    }

    /**
     * Returns whether all operations are done.
     *
     * @return	whether all operations are done
     */
    public boolean getDone() {
	return done.getCount() == 0;
    }

    /**
     * Waits for the operations to complete, throwing an exception if any
     * operation fails or does not complete in the specified amount of time.
     *
     * @param	timeout the maximum time to wait
     * @param	unit the time unit of the {@code timeout} argument 
     * @throws	InterruptedException if the thread is interrupted
     */
    public void await(long timeout, TimeUnit unit) throws InterruptedException {
	if (!done.await(timeout, unit)) {
	    taskFailed(new RuntimeException("Tasks did not complete"));
	}
	Throwable exception = failure.get();
	if (exception instanceof RuntimeException) {
	    throw (RuntimeException) exception;
	} else if (exception instanceof Error) {
	    throw (Error) exception;
	} else if (exception != null) {
	    throw new RuntimeException("Unexpected exception", exception);
	}
    }
}
