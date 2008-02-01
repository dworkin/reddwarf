/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.profile;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The interface used to report profiling data associated with a complete
 * task run through the scheduler.
 */
public interface ProfileReport {

    /**
     * Returns the run task that generated this report.
     *
     * @return the <code>KernelRunnable</code> that was run
     */
    public KernelRunnable getTask();

    /**
     * Returns the owner of the run task.
     *
     * @return the <code>Identity</code> of the task owner
     */
    public Identity getTaskOwner();

    /**
     * Returns whether any of the task was transactional.
     *
     * @return <code>true</code> if any part of the task ran transactionally,
     *         <code>false</code> otherwise
     */
    public boolean wasTaskTransactional();

    /**
     * Returns detail about each participant in the transaction, or an
     * empty <code>Set</code> if the task was not transactional.
     *
     * @return a <code>Set</code> of <code>ProfileParticipantDetail</code>
     */
    public Set<ProfileParticipantDetail> getParticipantDetails();

    /**
     * Returns whether the task successfully ran to completion. If this
     * task was transactional, then this means that the task committed
     * successfully.
     *
     * @return <code>true</code> if this task completed successfully,
     *         <code>false</code> otherwise
     */
    public boolean wasTaskSuccessful();

    /**
     * Returns the time at which that task was scheduled to run.
     *
     * @return the requested starting time for the task in milliseconds
     *         since January 1, 1970
     */
    public long getScheduledStartTime();

    /**
     * Returns the time at which the task actually started running.
     *
     * @return the actual starting time for the task in milliseconds
     *         since January 1, 1970
     */
    public long getActualStartTime();

    /**
     * Returns the length of time spent running the task. Note that this
     * is wall-clock time, not the time actually spent running on the
     * processor.
     *
     * @return the length in milliseconds to execute the task
     */
    public long getRunningTime();

    /**
     * Returns the number of times this task has been tried. If this is
     * the first time the task has been run, then this method returns 1.
     * 
     * @return the number of times this task has been tried
     */
    public int getRetryCount();

    /**
     * Returns the operations that were reported as executed during the
     * running of the task. If no operations were reported, then an
     * empty <code>List</code> is returned.
     *
     * @return a <code>List</code> of <code>ProfileOperation</code>
     *         representing the ordered set of reported operations
     */
    public List<ProfileOperation> getReportedOperations();

    /**
     * Returns the updated values of the aggregate counters that were
     * updated during the running of the task. If no aggregate
     * counters were updated, an empty <code>Map</code> is
     * returned. The <code>Map</code> is a mapping from counter name
     * to counter value. Note that the reported values are the values
     * observed during the running of the task, not the value (which
     * may have changed) at the time this report is provided to any
     * listeners.
     *
     * @return a <code>Map</code> from counter name to observed value
     */
    public Map<String,Long> getUpdatedAggregateCounters();

    /**
     * Returns the values of the task-local counters that were updated
     * during the running of the task. If no task-local counters were
     * updated, then an empty {@code Map} is returned. The
     * <code>Map</code> is a mapping from counter name to counter
     * value.
     *
     * @return a <code>Map</code> from counter name to observed value
     */
    public Map<String,Long> getUpdatedTaskCounters();

    /**
     * Returns a mapping for each sample that records for the lifetime
     * of the application that was updated, to the entire list of
     * samples for that name. If no lifetime samples were updated,
     * then an empty <code>Map</code> is returned. The
     * <code>Map</code> is a mapping from sample name to an
     * oldest-first list of sample values.  The list of samples
     * includes all samples collected during the lifetime of the
     * application.
     *
     * @return a <code>Map</code> from sample name to a list of values
     *         added during the task.     
     */
    public Map<String,List<Long>> getUpdatedAggregateSamples();

    /**
     * Returns the list of values for the task-local samples that were
     * updated during the running of the task. If no task-local
     * samples were updated, then an empty {@code Map} is
     * returned. The <code>Map</code> is a mapping from sample name
     * to an oldest-first list of sample values.
     *
     * @return a <code>Map</code> from sample name to a list of values
     *         added during the task.
     */
    public Map<String,List<Long>> getUpdatedTaskSamples();  

    /**
     * Returns the number of tasks in the same context as this report's task
     * that were in the scheduler and ready to run when this report's task
     * was started. Note that some schedulers may not differentiate between
     * application contexts, so this value may represent some other ready
     * count, such as the total number of tasks ready to run across all
     * contexts.
     *
     * @return the number of ready tasks in the same context.
     */
    public int getReadyCount();


    /**
     * Returns any exception that occurred during the execution of
     * this report's task, or <code>null</code> if no exception
     * occurred.  This exception will always be <code>null</code> if
     * {@link #wasTaskSuccessful()} returns <code>true</code>.
     * 
     * @return the exception that occurred or <code>null</code> if
     *         none occurred.
     */
    public Exception getException();

}
