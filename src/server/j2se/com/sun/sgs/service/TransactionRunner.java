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

package com.sun.sgs.service;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.kernel.KernelRunnable;


/**
 * This is the basic <code>KernelRunnable</code> that is used to run any
 * task in a transactional context. This class handles creating the
 * transaction state, running a task, and committing the transaction
 * once the task has finished.
 */
public class TransactionRunner implements KernelRunnable {

    // the kernel runnable
    private final KernelRunnable transactionalTask;

    /**
     * Creates an instance of <code>TransactionRunner</code> that accepts
     * a <code>KernelRunnable</code> to run in a transactional context.
     *
     * @param transactionalTask the <code>KernelRunnable</code> to run
     */
    public TransactionRunner(KernelRunnable transactionalTask) {
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
     * <code>KernelRunnable</code> in a transactional context.
     *
     * @throws Exception if an error occurs with the
     *                   <code>KernelRunnable</code> or with the transaction
     */
    public void run() throws Exception {
        TaskHandler.runTransactionalTask(transactionalTask);
    }

}
