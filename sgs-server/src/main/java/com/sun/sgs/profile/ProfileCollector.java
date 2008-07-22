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

/**
 * This is the main aggregation point for profiling data. Implementations of
 * this interface are used to collect data from arbitrary sources (typically
 * <code>ProfileConsumer</code>s or the scheduler itself) and keep
 * track of which tasks are generating which data.
 * <p>
 * This interface allows instances of <code>ProfileListener</code>
 * to register as listeners for reported data. All reporting to these
 * listeners is done synchronously, such that listeners do not need to worry
 * about being called concurrently. Listeners should be efficient in handling
 * reports, since they may be blocking all other listeners.
 */
public interface ProfileCollector {

    /**
     *  The valid choices for
     * {@value com.sun.sgs.impl.kernel.Kernel#PROFILE_PROPERTY}.
     */
    public enum ProfileLevel {
        /** Collect minimal profiling data. */
        MIN,
        /** Collect a medium amount of profiling data. */
        MEDIUM,
        /** Collect all profiling data available. */
        MAX,
    }
    
    /** 
     * The current global profiling level, which is the default level
     * for any newly created {@code ProfileConsumer} and can be set at
     * startup with the property 
     * {@value com.sun.sgs.impl.kernel.Kernel#PROFILE_PROPERTY}.  
     * @return the current profiling level
     */
    public ProfileLevel getGlobalProfileLevel();
    
    /**
     * Set the current global profiling level.  Setting this level will 
     * set each of the {@code ProfileConsumer} profile levels, making this
     * method a convient way to enable or disable all profiling.
     * 
     * @param level the profiling level
     */
    public void setGlobalProfileLevel(ProfileLevel level);

    /** 
     * Shuts down the ProfileCollector, reclaiming resources as necessary.
     */
    
    public void shutdown();
    
    /**
     * Adds a <code>ProfileListener</code> as a listener for
     * profiling data reports. The listener is immediately updated on
     * the current set of operations and the number of scheduler
     * threads.
     *
     * @param listener the {@code ProfileListener} to add
     */
    public void addListener(ProfileListener listener);
    
    /**
     * Instantiates a {@code ProfileListener}.  The listener must
     * implement a constructor of the form ({@code java.util.Properties},
     * {@code com.sun.sgs.kernel.TaskOwner},
     * {@code com.sun.sgs.kernel.ComponentRegistry}). 
     * 
     * @param listenerClassName the fully qualified class name of the 
     *                          listener to instantiate.
     * 
     * @return the instantiated listener or null if there was an error
     */
    public ProfileListener instantiateListener(String listenerClassName);
    

    /**
     * Returns a read-only list of {@code ProfileListener}s which have been
     * added.
     * 
     * @return the list of listeners
     */
    public List<ProfileListener> getListeners();
    
    /**
     * Removes a {@code ProfileListener} and calls
     * {@link ProfileListener#shutdown} on the listener.  If the
     * {@code listener} has never been added with {@link #addListener}, no
     * action is taken.
     *
     * @param listener the listener to remove
     */
    public void removeListener(ProfileListener listener);
    
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
    public void startTask(KernelRunnable task, Identity owner,
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
     * Tells the collector that the current task associated with the
     * calling thread (as associated by a call to
     * <code>startTask</code>) has now successfully finished.
     *
     * @param tryCount the number of times that the task has tried to run
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    public void finishTask(int tryCount);

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
    public void finishTask(int tryCount, Throwable t);

}
