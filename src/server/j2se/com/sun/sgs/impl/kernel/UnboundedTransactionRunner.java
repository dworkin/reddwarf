/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * Package-private utility class that runs a task in a transactional
 * context with unbounded timeout.
 */
class UnboundedTransactionRunner implements KernelRunnable {

    // the kernel runnable
    private final KernelRunnable transactionalTask;

    /**
     * Creates an instance of <code>UnboundedTransactionRunner</code> that
     * accepts a <code>KernelRunnable</code> to run in a transactional context
     * with unbounded timeout.
     *
     * @param transactionalTask the <code>KernelRunnable</code> to run
     */
    UnboundedTransactionRunner(KernelRunnable transactionalTask) {
	if (transactionalTask == null)
            throw new NullPointerException("null task not allowed");
        this.transactionalTask = transactionalTask;
    }

    /**
     * Returns the base type of the task that is run transactionally by
     * this <code>TransactionRunner</code>.
     *
     * @return the base type of the wrapped task
     */
    public String getBaseTaskType() {
        return transactionalTask.getBaseTaskType();
    }

    /**
     * Runs this <code>TransactionRunner</code>, which in turn runs its
     * <code>KernelRunnable</code> in a transactional context with unbounded
     * timeout.
     *
     * @throws Exception if an error occurs with the
     *                   <code>KernelRunnable</code> or with the transaction
     */
    public void run() throws Exception {
        TaskHandler.runTransactionalTask(transactionalTask, true);
    }

}
