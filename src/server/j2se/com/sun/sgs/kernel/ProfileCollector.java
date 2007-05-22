/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * This is the main aggregation point for profiling data. Implementations of
 * this interface are used to collect data from arbitrary sources (typically
 * <code>ProfileConsumer</code>s or the scheduler itself) and keep
 * track of which tasks are generating which data.
 * <p>
 * This interface allows instances of <code>ProfileOperationListener</code>
 * to register as listeners for reported data. All reporting to these
 * listeners is done synchronously, such that listeners do not need to worry
 * about being called concurrently. Listeners should be efficient in handling
 * reports, since they may be blocking all other listeners.
 */
public interface ProfileCollector {

    /**
     * Adds a <code>ProfileOperationListener</code> as a listener for
     * profiling data reports. The listener is immediately updated on
     * the current set of operations and the number of scheduler
     * threads.
     *
     * @param listener the <code>ProfileOperationListener</code> to add
     */
    public void addListener(ProfileOperationListener listener);

    /**
     * Notifies the collector that a thread has been added to the scheduler.
     */
    public void notifyThreadAdded();

    /**
     * Notifies the collector that a thread has been removed from the
     * scheduler.
     */
    public void notifyThreadRemoved();

    /**
     * Tells the collector that a new task is starting in the context of
     * the calling thread. Any previous task must have been cleared from the
     * context of this thread via a call to <code>finishTask</code>.
     *
     * @param task the <code>KernelRunnable</code> that is starting
     * @param owner the <code>TaskOwner</code> of the task
     * @param scheduledStartTime the requested starting time for the task
     * @param readyCount the number of ready tasks at the scheduler
     *
     * @throws IllegalStateException if a task is already bound to this thread
     */
    public void startTask(KernelRunnable task, TaskOwner owner,
                          long scheduledStartTime, int readyCount);

    /**
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to <code>startTask</code>) is
     * transactional. This does not mean that all operations of the task
     * are transactional, but that at least some of the task is run in a
     * transactional context.
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    public void noteTransactional();

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
    public void addParticipant(ProfileParticipantDetail participantDetail);

    /**
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to <code>startTask</code>) is now
     * finished.
     *
     * @param tryCount the number of times that the task has tried to run
     * @param taskSucceeded <code>true</code> if the task ran to completion,
     *                      <code>false</code> if the task failed and is
     *                      going to be re-tried or dropped
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    public void finishTask(int tryCount, boolean taskSucceeded);

}
