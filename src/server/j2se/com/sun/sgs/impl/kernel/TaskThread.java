/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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

import com.sun.sgs.kernel.TaskOwner;


/**
 * This abstract implementation of <code>Thread</code> represents all of the
 * worker threads in the system. It is used by the
 * <code>ResourceCoordinator</code> to manage the available threads for
 * running long-lived tasks. Note that these are the tasks run through this
 * interface, and not the short-lived tasks queued in the scheduler. The
 * <code>startTask</code> method on <code>ResourceCoordinator</code> should
 * be called infrequently, so <code>TaskThread</code>s will not be given
 * new tasks frequently.
 * <p>
 * This interface is used only to manage the current owner of this
 * thread, which starts as the owner of the initial task, but will be
 * re-set with each task run out of the scheduler. See
 * <code>TaskHandler</code> for details on this process.
 */
abstract class TaskThread extends Thread {

    // the current owner of this thread
    private TaskOwner currentOwner;

    /**
     * Creates a new instance of <code>TaskThread</code>.
     *
     * @param r the root <code>Runnable</code> for this <code>Thread</code>
     */
    protected TaskThread(Runnable r) {
        super(r);
        currentOwner = Kernel.TASK_OWNER;
    }

    /**
     * Returns the current owner of the work being done by this thread.
     * Depending on what is currently running in this thread, the owner may
     * be the owner of the last <code>Runnable</code> provided to
     * <code>runTask</code>, or the owner of a specific
     * <code>KernelRunnable</code> running in this thread (typically
     * consumed from the <code>TaskScheduler</code>).
     *
     * @return the current owner
     *
     * @throws IllegalStateException if <code>getCurrentOwner</code> is not
     *                               called on the current thread
     */
    TaskOwner getCurrentOwner() {
        // make sure that we're looking at the our own thread
        if (Thread.currentThread() != this)
            throw new IllegalStateException("Cannot ask for the owner of a " +
                                            "thread other than the current " +
                                            "thread");

        return currentOwner;
    }

    /**
     * Sets the current owner of the work being done by this thread. The only
     * components who have access to this ability are those in the kernel and
     * the <code>TaskScheduler</code> (via the <code>TaskHandler</code>).
     *
     * @param owner the new owner
     *
     * @throws IllegalStateException if <code>setCurrentOwner</code> is not
     *                               called on the current thread
     */
    void setCurrentOwner(TaskOwner owner) {
        // make sure that we're setting the owner for our thread
        if (Thread.currentThread() != this)
            throw new IllegalStateException("Cannot set the owner of a " +
                                            "thread other than the current " +
                                            "thread");

        currentOwner = owner;
        ContextResolver.
            setContext((AbstractKernelAppContext)(owner.getContext()));
    }

}
