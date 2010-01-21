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

import java.util.concurrent.Callable;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.TransactionScheduler;

/**
 * An abstract utility class to run a transactional task that returns a
 * value.  A subclass of this class must implement the abstract {@code
 * call} method which is invoked in a transaction.<p>
 *
 * Here's an example of running a {@code KernelCallable} that returns a
 * boolean value:<p>
 *
 * <pre>
 * boolean result = KernelCallable.call(
 *	new KernelCallable&lt;Boolean&gt;("MyKernelCallable") {
 *	    public Boolean call() {
 *		return ...;
 *	    }
 *	},
 *	txnScheduler, taskOwner);
 * </pre>
 * @param <R> the type of the result (the return value of the {@code call}
 *	      method)
 */
public abstract class KernelCallable<R>
    extends AbstractKernelRunnable
    implements Callable<R>
{
    /** The result of invoking the {@code call} method. */
    private R result;
    /** The flag to indicate whether the {@code call} method is complete. */
    private boolean done;

    /**
     * Constructs an instance with the specified {@code name}.
     *
     * @param	name a descriptive name (or {@code null}) for use in the
     *		{@code toString} method
     */
    public KernelCallable(String name) {
	super(name);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation invokes the {@code call} method of this
     * instance and sets the result.
     *
     * @throws IllegalStateException if this task has already been successfully
     *         run via an invocation of the {@link
     *         #call(com.sun.sgs.impl.util.KernelCallable,
     *         com.sun.sgs.kernel.TransactionScheduler,
     *         com.sun.sgs.auth.Identity) call} method
     */
    @Override
    public synchronized void run() throws Exception {
	if (done) {
	    throw new IllegalStateException("already completed");
	}
	result = call();
    }

    /**
     * This method should be called to indicate that the {@code call} method
     * has been invoked for this {@code KernelCallable} and the internal result
     * is available for retrieval.  After this method has been invoked, further
     * attempts to execute this task (either via the {@link
     * #call(com.sun.sgs.impl.util.KernelCallable,
     * com.sun.sgs.kernel.TransactionScheduler, com.sun.sgs.auth.Identity) call}
     * method or through the {@code TransactionScheduler}) will throw
     * {@code IllegalStateException}.
     */
    private synchronized void setDone() {
        done = true;
    }

    /**
     * Returns the result, previously set by invoking the {@link #run}
     * method.
     *
     * @return	the result
     * @throws	IllegalStateException if the {@link #run} method has not
     *		completed
     */
    private synchronized R getResult() {
	if (!done) {
	    throw new IllegalStateException("not done");
	}
	return result;
    }

    /**
     * Runs the specified {@code callable} (by invoking its {@code call}
     * method) in a transaction using the specified {@code txnScheduler}
     * and {@code taskOwner} and returns the result.  The specified
     * {@code callable} can only be used once.
     *
     * @param	<R> the return type of the {@code KernelCallable}
     * @param	callable a callable to invoke
     * @param	txnScheduler a transaction scheduler
     * @param	taskOwner an identity for the task's owner
     * @return	the result of executing the {@code callable}
     * @throws	Exception if running the specified {@code callable} throws
     *		an {@code Exception} 
     */
    public static <R> R call(KernelCallable<R> callable,
			     TransactionScheduler txnScheduler,
			     Identity taskOwner)
	throws Exception
    {
	txnScheduler.runTask(callable, taskOwner);
        callable.setDone();
	return callable.getResult();
    }
}
