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

package com.sun.sgs.impl.service.channel;

import java.util.concurrent.Callable;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;

public abstract class KernelCallable<R>
    extends AbstractKernelRunnable
    implements Callable<R>
{
    private R result;
    private volatile boolean done;

    public KernelCallable(String name) {
	super(name);
    }
    
    public void run()  throws Exception {
	result = call();
	done = true;
    }

    public R getResult() {
	if (!done) {
	    throw new IllegalStateException("not done");
	}
	return result;
    }

    public static <R> R call(KernelCallable<R> callable,
			   TransactionScheduler txnScheduler,
			   Identity taskOwner)
	throws Exception
    {
	txnScheduler.runTask(callable, taskOwner);
	return callable.getResult();
    }
}
