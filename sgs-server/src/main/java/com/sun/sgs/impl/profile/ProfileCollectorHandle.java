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
package com.sun.sgs.impl.profile;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.TransactionListenerDetail;

/**
 * The object which manages how {@link ProfileCollector}s keep track
 * of which tasks are generating which data.
 */
public interface ProfileCollectorHandle {

    /**
     * Notifies the collector that a thread has been added to the scheduler.
     */
    void notifyThreadAdded();

    /**
     * Notifies the collector that a thread has been removed from the
     * scheduler.
     */
    void notifyThreadRemoved();

    /**
     * Notifies the collector that the node has been assigned its identifier.
     *
     * @param nodeId the identifier for the node
     */
    void notifyNodeIdAssigned(long nodeId);

    /**
     * Tells the collector that a new task is starting in the context of
     * the calling thread. If another task was alrady being profiled in the
     * context of the calling thread then that profiling data is pushed
     * onto a stack until the new task finishes from a call to
     * <code>finishTask</code>.
     *
     * @param task the <code>KernelRunnable</code> that is starting
     * @param owner the <code>Identity</code> of the task owner
     * @param scheduledStartTime the requested starting time for the task
     * @param readyCount the number of ready tasks at the scheduler
     */
    void startTask(KernelRunnable task, Identity owner,
                   long scheduledStartTime, int readyCount);

    /**
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to <code>startTask</code>) is
     * transactional. This does not mean that all operations of the task
     * are transactional, but that at least some of the task is run in a
     * transactional context.
     *
     * @param transactionId the identifier for the transaction
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    void noteTransactional(byte [] transactionId);

    /**
     * Tells the collector about a participant of a transaction when that
     * participant has finished participating (i.e., has committed, has
     * prepared read-only, or has aborted). The transaction must be the
     * current transaction for the current task, and therefore
     * <code>noteTransactional</code> must first have been called in
     * the context of the current thread.
     *
     * @param participantDetail the detail associated with the participant
     *
     * @throws IllegalStateException if no transactional task is bound to
     *                               this thread
     */
    void addParticipant(ProfileParticipantDetail participantDetail);

    /**
     * Tells the collector about a listener of a transaction when that
     * listener is finished with its work (i.e., after its
     * <code>afterCompletion</code>} method has been called). The
     * transaction must be the current transaction for the current task,
     * and therefore <code>noteTransactional</code> must first have been
     * called in the context of the current thread.
     *
     * @param listenerDetail the detail associated with the listener
     *
     * @throws IllegalStateException if no transactional task is bound to
     *                               this thread
     */
    void addListener(TransactionListenerDetail listenerDetail);

    /**
     * Sets the detail for all objects accessed during the task as
     * reported to the <code>AccessCoordinator</code>.
     * 
     * @param detail all detail of the accessed objects
     *
     * @throws IllegalStateException if no transactional task is bound to
     *                               this thread
     */
    void setAccessedObjectsDetail(AccessedObjectsDetail detail);

    /**
     * Tells the collector that the current task associated with the
     * calling thread (as associated by a call to
     * <code>startTask</code>) has now successfully finished.
     *
     * @param tryCount the number of times that the task has tried to run
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    void finishTask(int tryCount);

    /**
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to <code>startTask</code>) is now
     * finished and that an exception occured during its execution.
     *
     * @param tryCount the number of times that the task has tried to run
     * @param t the <code>Throwable</code> thrown during task execution
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    void finishTask(int tryCount, Throwable t);
    
    /**
     * Returns the underlying profile collector that this interface controls.
     * 
     * @return the underlying profile collector that this interface controls
     */
    ProfileCollector getCollector();
}
