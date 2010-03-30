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

import com.sun.sgs.app.Task;
import static com.sun.sgs.impl.util.AbstractBasicService.isRetryableException;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import static org.junit.Assert.assertTrue;

/**
 * A task for tests that run several repeated tasks concurrently.  Note that
 * only one set of these tasks can be run at a time because this class uses
 * statics to track running tasks.  If we needed to support simultaneous,
 * separate sets of tasks in the future, each could by identified by a unique
 * key.
 */
public abstract class RepeatingConcurrentTask implements Serializable, Task {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** How many tasks are currently running. */
    private static int tasksRunning = 0;

    /** Any exception thrown by any task, or null. */
    private static Throwable exception = null;

    /** The transaction proxy or null. */
    private static TransactionProxy txnProxy;

    /** The index currently associated with this task. */
    protected int index;

    /** The offset for computing this task's next index. */
    protected final int nextIndexOffset;

    /** If index reaches or exceeds this value, then this task is done. */
    private final int maxIndex;

    /**
     * Creates an instance of this class.
     *
     * @param	txnProxy the transaction proxy
     * @param	index the first index for this class
     * @param	nextIndexOffset the increment for computing the next index
     * @param	maxIndex the index to reach for completion
     */
    public RepeatingConcurrentTask(
	TransactionProxy txnProxy, int index, int nextIndexOffset, int maxIndex)
    {
	maybeStoreTxnProxy(txnProxy);
	this.index = index;
	this.nextIndexOffset = nextIndexOffset;
	this.maxIndex = maxIndex;
	if (index < nextIndexOffset) {
	    noteStart();
	}		    
    }

    /** Updates the transaction proxy field, if needed. */
    private static synchronized void maybeStoreTxnProxy(
	TransactionProxy txnProxy)
    {
	if (RepeatingConcurrentTask.txnProxy == null) {
	    RepeatingConcurrentTask.txnProxy = txnProxy;
	}
    }

    /** Returns the task service. */
    private static synchronized TaskService getTaskService() {
	return txnProxy.getService(TaskService.class);
    }

    /**
     * Calls {@link #runInternal} and reschedules this task if the final index
     * has not been reached, handling any exceptions.
     */
    public final void run() {
	if (!checkDone()) {
	    try {
		runInternal();
		getTaskService().scheduleTask(this);
	    } catch (RuntimeException e) {
		if (isRetryableException(e)) {
		    throw e;
		} else {
		    unexpectedException(e);
		}
	    } catch (Error e) {
		unexpectedException(e);
	    }
	}
    }

    /** Performs one iteration of this task. */
    protected abstract void runInternal();

    /**
     * Waits the specified number of milliseconds for all outstanding instances
     * of this task to complete, throwing an exception if the tasks do not
     * complete in time or if any of them fail.
     *
     * @param	maxWait the number of milliseconds to wait
     */
    public static synchronized void awaitDone(long maxWait) {
	long stop = System.currentTimeMillis() + maxWait;
	while (tasksRunning > 0) {
	    long wait = stop - System.currentTimeMillis();
	    assertTrue("Timed out", wait > 0);
	    try {
		RepeatingConcurrentTask.class.wait(wait);
	    } catch (InterruptedException e) {
	    }
	}
	if (exception != null) {
	    throw new RuntimeException(
		"Unexpected exception: " + exception, exception);
	}
    }

    /** Checks if the task should be run and rescheduled. */
    private boolean checkDone() {
	synchronized (RepeatingConcurrentTask.class) {
	    if (exception != null || index >= maxIndex) {
		noteFinish();
		return true;
	    } else {
		return false;
	    }
	}
    }

    /** Notes an unexpected exception and marks the task as finished. */
    private static synchronized void unexpectedException(Throwable e) {
	exception = e;
	noteFinish();
    }

    /** Notes that a task has started. */
    private static synchronized void noteStart() {
	tasksRunning++;
    }

    /** Notes that a task has finished. */
    private static synchronized void noteFinish() {
	tasksRunning--;
	if (tasksRunning == 0) {
	    RepeatingConcurrentTask.class.notifyAll();
	}
    }
}
