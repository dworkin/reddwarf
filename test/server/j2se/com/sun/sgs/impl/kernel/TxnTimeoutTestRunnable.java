/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TransactionTimeoutException;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * Runnable that waits for some time and then tries to access the current
 * transaction state. Used to test transaction timeout.
 */
public class TxnTimeoutTestRunnable implements Runnable {

    private final long delay;
    private final boolean unbounded;
    public boolean timedOut = false;

    /**
     * Creates an instance that waits the specified time during a bounded
     * or unbounded transaction.
     */
    public TxnTimeoutTestRunnable(long delay, boolean unbounded) {
	this.delay = delay;
	this.unbounded = unbounded;
    }

    public void run() {
	try {
	    TaskHandler.runTransactionalTask(new TxnTask(), unbounded);
	} catch (Exception e) {}
    }

    private class TxnTask implements KernelRunnable {
	public void run() throws Exception {
	    Thread.sleep(delay);

	    try {
		((TransactionalTaskThread)(Thread.currentThread())).
		    getCurrentTransaction();
	    } catch (TransactionTimeoutException tte) {
		timedOut = true;
	    }
	}
	public String getBaseTaskType() {
	    return TxnTimeoutTestRunnable.class.getName();
	}
    }

}
