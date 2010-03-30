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

import com.sun.sgs.auth.Identity;
import static com.sun.sgs.impl.util.AbstractBasicService.isRetryableException;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.util.concurrent.TimeUnit;

/**
 * A kernel runnable to make it easier to break a transactional task into
 * separate transactions.
 */
public abstract class ChunkedTask extends TestAbstractKernelRunnable {

    /** The transaction scheduler. */
    private final TransactionScheduler txnScheduler;

    /** The task owner. */
    private final Identity taskOwner;

    /** For waiting for the task to be done. */
    private AwaitDone done = new AwaitDone(1);

    /**
     * Creates an instance of this class.
     *
     * @param	txnScheduler the transaction scheduler
     * @param	taskOwner the task owner
     */
    protected ChunkedTask(TransactionScheduler txnScheduler,
			  Identity taskOwner)
    {
	this.txnScheduler = txnScheduler;
	this.taskOwner = taskOwner;
    }

    /**
     * Runs this task and waits until it is done, throwing an assertion error
     * if it does not complete in the specified time.
     *
     * @param	wait the time to wait for completion, in milliseconds
     */
    public void runAwaitDone(long wait) throws InterruptedException {
	txnScheduler.scheduleTask(this, taskOwner);
	done.await(wait, TimeUnit.MILLISECONDS);
    }

    /** Runs the task chunks, rescheduling this task until it is done. */
    public final void run() {
	try {
	    if (runChunk()) {
		done.taskSucceeded();
	    } else {
		txnScheduler.scheduleTask(this, taskOwner);
	    }
	} catch (RuntimeException e) {
	    if (isRetryableException(e)) {
		throw e;
	    }
	    done.taskFailed(e);
	} catch (Error e) {
	    done.taskFailed(e);
	}
    }

    /**
     * Runs a portion of the task, returning {@code true} if the entire task is
     * done.
     *
     * @return	whether the task is done
     */
    protected abstract boolean runChunk();
}
