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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;


import java.util.LinkedList;
import java.util.Properties;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Package-private implementation of {@code TaskScheduler} that is used by
 * the system scheduling and running all non-transactional, arbitrary-length
 * tasks. This is an intentionally simple implementation that uses a backing
 * {@code Executor} instead of a {@code SchedulerQueue} until there
 * is better understanding of what (if any) custom scheduling behavior will
 * help these kinds of tasks.
 * <p>
 * This class supports the following configuration properties:
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #CONSUMER_THREADS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_CONSUMER_THREADS}</code>
 *
 * <dd style="padding-top: .5em">The number of initial threads used to process
 *      non-transactional tasks.<p>
 * </dl>
 * FIXME: the profiling code needs a way to learn about the thread count
 * from this scheduler separately from the transaction pool. When this gets
 * added, this class should start tracking thread counts.
 */
final class TaskSchedulerImpl implements TaskScheduler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(TaskSchedulerImpl.
                                           class.getName()));

    /**
     * The property used to define the default number of initial consumer
     * threads.
     */
    public static final String CONSUMER_THREADS_PROPERTY =
        "com.sun.sgs.impl.kernel.task.threads";

    /**
     * The default number of initial consumer threads.
     */
    public static final String DEFAULT_CONSUMER_THREADS = "4";

    // the executor used to run tasks
    private final ScheduledExecutorService executor;

    // the collector handle used for profiling data
    private final ProfileCollectorHandle profileCollectorHandle;

    // the number of tasks waiting to run
    private final AtomicInteger waitingSize = new AtomicInteger(0);

    // flag to note that this scheduler has shutdown
    private volatile boolean isShutdown = false;

    // the context we're using for the application's tasks
    private volatile KernelContext kernelContext = null;

    /**
     * Creates an instance of {@code TaskSchedulerImpl}.
     *
     * @param properties the {@code Properties} for the system
     * @param profileCollectorHandle the {@code ProfileCollectorHandler} used to
     *          manage collection of per-task profiling data
     *
     * @throws Exception if there is any failure creating the scheduler
     */
    TaskSchedulerImpl(Properties properties,
                      ProfileCollectorHandle profileCollectorHandle) 
        throws Exception 
    {
        logger.log(Level.CONFIG, "Creating a Task Scheduler");

        if (properties == null) {
            throw new NullPointerException("Properties cannot be null");
        }
        if (profileCollectorHandle == null) {
            throw new NullPointerException("Collector handle cannot be null");
        }

        this.profileCollectorHandle = profileCollectorHandle;

        int requestedThreads =
            Integer.parseInt(properties.getProperty(CONSUMER_THREADS_PROPERTY,
                                                    DEFAULT_CONSUMER_THREADS));
        if (logger.isLoggable(Level.CONFIG)) {
            logger.log(Level.CONFIG, "Using {0} task consumer threads",
                       requestedThreads);
        }
        // NOTE: this is replicating previous behavior where there is a
        // fixed-size pool for running tasks, but in practice we may
        // want a flexible pool that allows (e.g.) for tasks that run
        // for the lifetime of a stack
        this.executor = Executors.newScheduledThreadPool(requestedThreads);
    }

    /**
     * Package-private method used to set the context being used by the kernel.
     *
     * @param kernelContext the {@code KernelContext} for this scheduler
     */
    void setContext(KernelContext kernelContext) {
        this.kernelContext = kernelContext;
    }

    /*
     * Implementations of the TaskScheduler interface.
     */

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner) {
        TaskDetail detail = new TaskDetail(task, owner,
                                           System.currentTimeMillis());
        return new TaskReservationImpl(detail);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       long startTime) 
    {
        return new TaskReservationImpl(new TaskDetail(task, owner, startTime));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner) {
        try {
            TaskDetail detail = new TaskDetail(task, owner,
                                               System.currentTimeMillis());
            executor.submit(new TaskRunner(detail));
            waitingSize.incrementAndGet();
        } catch (RejectedExecutionException ree) {
            throw new TaskRejectedException("Couldn't schedule task", ree);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             long startTime) 
    {
        try {
            TaskDetail detail = new TaskDetail(task, owner, startTime);
            executor.schedule(new TaskRunner(detail),
                              startTime - System.currentTimeMillis(),
                              TimeUnit.MILLISECONDS);
            waitingSize.incrementAndGet();
        } catch (RejectedExecutionException ree) {
            throw new TaskRejectedException("Couldn't schedule task", ree);
        }
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     Identity owner,
                                                     long startTime,
                                                     long period) 
    {
        if (period <= 0) {
            throw new IllegalArgumentException("Illegal period: " + period);
        }

        return new RecurringTaskHandleImpl(new TaskDetail(task, owner,
                                                          startTime, period));
    }

    /**
     * {@inheritDoc}
     */
    public TaskQueue createTaskQueue() {
        if (isShutdown) {
            throw new IllegalStateException("Scheduler is shutdown");
        }
        return new TaskQueueImpl();
    }

    /*
     * Utility methods and classes.
     */

    /**
     * Tells this scheduler to shutdown.
     *
     */
    void shutdown() {
        synchronized (this) {
            if (isShutdown) {
                return; // return silently
            }
            isShutdown = true;
            executor.shutdown();
        }
    }

    /** Private implementation of {@code TaskReservation}. */
    private class TaskReservationImpl implements TaskReservation {
        private final TaskDetail taskDetail;
        private boolean usedOrCancelled = false;
        /** Creates an instance of {@code TaskReservationImpl}. */
        TaskReservationImpl(TaskDetail taskDetail) {
            this.taskDetail = taskDetail;
        }
        /** {@inheritDoc} */
        public synchronized void cancel() {
            if (usedOrCancelled) {
                throw new IllegalStateException("This reservation cannot be " +
                                                "cancelled");
            }
            usedOrCancelled = true;
        }
        /**
         * {@inheritDoc}
         * <p>
         * @throws TaskRejectedException if the system has become too
         *                               overloaded to honor this reservation
         */
        public void use() {
            synchronized (this) {
                if (usedOrCancelled) {
                    throw new IllegalStateException("This reservation cannot " +
                                                    "be used");
                }
                usedOrCancelled = true;
            }

            try {
                long delay = taskDetail.startTime - System.currentTimeMillis();
                executor.schedule(new TaskRunner(taskDetail), delay,
                                  TimeUnit.MILLISECONDS);
                waitingSize.incrementAndGet();
            } catch (RejectedExecutionException ree) {
                throw new TaskRejectedException("The system has run out of " +
                                                "resources and cannot run " +
                                                "the requested task", ree);
            }
        }
    }

    /** Private implementation of {@code RecurringTaskHandle}.  */
    private class RecurringTaskHandleImpl implements RecurringTaskHandle {
        private final TaskDetail taskDetail;
        private boolean isCancelled = false;
        private boolean isStarted = false;
        private volatile ScheduledFuture<?> future = null;
        /** Creates an instance of {@code RecurringTaskHandleImpl}. */
        RecurringTaskHandleImpl(TaskDetail taskDetail) {
            if (isShutdown) {
                throw new IllegalStateException("Scheduler is shutdown");
            }
            this.taskDetail = taskDetail;
        }
        /** {@inheritDoc} */
        public void cancel() {
            synchronized (this) {
                if (isCancelled) {
                    throw new IllegalStateException("Handle already cancelled");
                }
                isCancelled = true;
            }
            if (future != null) {
                future.cancel(false);
            }
        }
        /** {@inheritDoc} */
        public void start() {
            synchronized (this) {
                if (isCancelled) {
                    throw new IllegalStateException("Handle already cancelled");
                }
                if ((future != null) || (isStarted)) {
                    throw new IllegalStateException("Handle already used");
                }
                isStarted = true;
            }

            long delay = taskDetail.startTime - System.currentTimeMillis();
            try {
                future =
                    executor.scheduleAtFixedRate(new TaskRunner(taskDetail),
                                                 delay, taskDetail.period,
                                                 TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ree) {
                throw new TaskRejectedException("The system has run out of " +
                                                "resources and cannot start " +
                                                "the requested task", ree);
            }
        }
    }

    /** Private class used to maintain task detail. */
    private static class TaskDetail {
        final KernelRunnable task;
        final Identity owner;
        volatile long startTime;
        final long period;
        final TaskQueueImpl queue;
        /** Creates an instance of {@code TaskDetail}. */
        TaskDetail(KernelRunnable task, Identity owner, long startTime) {
            this(task, owner, startTime, 0);
        }
        /** Creates an instance of {@code TaskDetail}. */
        TaskDetail(KernelRunnable task, Identity owner, long startTime,
                   long period) 
        {
            if (task == null) {
                throw new NullPointerException("Task cannot be null");
            }
            if (owner == null) {
                throw new NullPointerException("Owner cannot be null");
            }

            this.task = task;
            this.owner = owner;
            this.startTime = startTime;
            this.period = period;
            this.queue = null;
        }
        /** Creates an instance of {@code TaskDetail} with dependency. */
        TaskDetail(KernelRunnable task, Identity owner, TaskQueueImpl queue) {
            if (task == null) {
                throw new NullPointerException("Task cannot be null");
            }
            if (owner == null) {
                throw new NullPointerException("Owner cannot be null");
            }
            if (queue == null) {
                throw new NullPointerException("TaskQueue cannot be null");
            }

            this.task = task;
            this.owner = owner;
            this.startTime = System.currentTimeMillis();
            this.period = 0;
            this.queue = queue;
        }
        /** Returns whether this task is recurring. */
        boolean isRecurring() {
            return period != 0;
        }
    }

    /**
     * Private {@code Runnable} used to wrap all {@code KernelRunnable} tasks
     * submitted to this scheduler.
     */
    private class TaskRunner implements Runnable {
        private final TaskDetail taskDetail;
        /** Creates an instance of {@code TaskRunner} to run the task. */
        TaskRunner(TaskDetail taskDetail) {
            this.taskDetail = taskDetail;
        }
        /** {@inheritDoc} */
        public void run() {
            logger.log(Level.FINE, "Running a non-transactional task");

            int queueSize = (taskDetail.isRecurring() ? waitingSize.get() :
                             waitingSize.decrementAndGet());
            profileCollectorHandle.startTask(taskDetail.task, taskDetail.owner,
                                       taskDetail.startTime, queueSize);
            if (taskDetail.isRecurring()) {
                taskDetail.startTime += taskDetail.period;
            }

            // store the current owner, and then push the new thread detail
            Identity parent = ContextResolver.getCurrentOwner();
            ContextResolver.setTaskState(kernelContext, taskDetail.owner);

            try {
                taskDetail.task.run();
                profileCollectorHandle.finishTask(1);
            } catch (Exception e) {
                profileCollectorHandle.finishTask(1, e);
                if (logger.isLoggable(Level.WARNING)) {
                    if (taskDetail.isRecurring()) {
                        logger.logThrow(Level.WARNING, e, "failed to run " +
                                        "task {0}", taskDetail.task);
                    } else {
                        logger.logThrow(Level.WARNING, e, "failed to run " +
                                        "recurrence of task {0}",
                                        taskDetail.task);
                    }
                }
            } finally {
                // always restore the previous owner before leaving
                ContextResolver.setTaskState(kernelContext, parent);
                // schedule the next task, if any
                if (taskDetail.queue != null) {
                    taskDetail.queue.scheduleNextTask();
                }
            }
        }
    }

    /** Private implementation of {@code TaskQueue}. */
    private final class TaskQueueImpl implements TaskQueue {
        private final LinkedList<TaskDetail> queue =
            new LinkedList<TaskDetail>();
        private boolean inScheduler = false;
        /** {@inheritDoc} */
        public void addTask(KernelRunnable task, Identity owner) {
            TaskDetail detail = new TaskDetail(task, owner, this);
	    waitingSize.incrementAndGet();
            synchronized (this) {
                if (inScheduler) {
                    queue.offer(detail);
                } else {
                    inScheduler = true;
                    executor.submit(new TaskRunner(detail));
                }
            }
        }
        /** Private method to schedule the next task, if any. */
        void scheduleNextTask() {
            synchronized (this) {
                if (queue.isEmpty()) {
                    inScheduler = false;
                } else {
                    // re-set the start time before scheduling, since the
                    // task isn't really requested to start until all
                    // tasks ahead of it have run
                    TaskDetail detail = queue.poll();
                    detail.startTime = System.currentTimeMillis();
                    executor.submit(new TaskRunner(detail));
                }
            }
        }
    }

}
