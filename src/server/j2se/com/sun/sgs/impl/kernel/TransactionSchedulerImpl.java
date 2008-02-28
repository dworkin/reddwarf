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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ExceptionRetryStatus;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.schedule.ApplicationScheduler;
import com.sun.sgs.impl.kernel.schedule.ScheduledTask;

import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.PriorityScheduler;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TransactionScheduler;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import com.sun.sgs.service.Transaction;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.beans.PropertyChangeEvent;

import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Package-private implementation of {@code TransactionScheduler} that is
 * used by the system for scheduling and running all transactional tasks.
 */
final class TransactionSchedulerImpl
    implements TransactionScheduler, PriorityScheduler, ProfileListener {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(TransactionSchedulerImpl.
                                           class.getName()));

    // the default scheduler
    private static final String DEFAULT_APPLICATION_SCHEDULER =
            "com.sun.sgs.impl.kernel.schedule.FIFOApplicationScheduler";

    /**
     * The property used to define the default number of initial consumer
     * threads.
     */
    public static final String CONSUMER_THREADS_PROPERTY =
        "com.sun.sgs.impl.kernel.TransactionConsumerThreads";

    // the default number of initial consumer threads
    private static final String DEFAULT_CONSUMER_THREADS = "4";

    // the default priority for tasks
    private static final Priority defaultPriority =
        Priority.getDefaultPriority();

    // the coordinator used to create and coordinate transactions
    private final TransactionCoordinator transactionCoordinator;

    // the scheduler used for ordering tasks
    private final ApplicationScheduler scheduler;

    // the collector used for profiling data
    private final ProfileCollector profileCollector;

    // the executor service used to manage our threads
    private final ExecutorService executor;

    // the actual number of threads we're currently using
    private final AtomicInteger threadCount = new AtomicInteger(0);

    // flag to note that this scheduler has shutdown
    private volatile boolean isShutdown = false;

    // the context we're using for the application's tasks
    private volatile KernelContext kernelContext = null;

    // the number of dependent tasks sitting in queues
    private final AtomicInteger dependencyCount = new AtomicInteger(0);

    /**
     * Creates an instance of {@code TransactionSchedulerImpl}.
     *
     * @param properties the {@code Properties} for the system
     * @param transactionCoordinator the {@code TransactionCoordinator} used
     *                               by the system to manage transactions
     * @param profileCollector the {@code ProfileCollector} used by the
     *                         system to collect profiling data, or
     *                         {@code null} if profiling is disabled
     *
     * @throws InvocationTargetException if there is a failure initializing
     *                                   the {@code ApplicationScheduler}
     * @throws Exception if there is any failure creating the scheduler
     */
    TransactionSchedulerImpl(Properties properties,
                             TransactionCoordinator transactionCoordinator,
                             ProfileCollector profileCollector)
        throws Exception
    {
        logger.log(Level.CONFIG, "Creating a Transaction Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");
        if (transactionCoordinator == null)
            throw new NullPointerException("Coordinator cannot be null");

        this.transactionCoordinator = transactionCoordinator;
        this.profileCollector = profileCollector;

        String schedulerName =
            properties.getProperty(ApplicationScheduler.
                                   APPLICATION_SCHEDULER_PROPERTY,
                                   DEFAULT_APPLICATION_SCHEDULER);
        try {
            Class<?> schedulerClass = Class.forName(schedulerName);
            Constructor<?> schedulerCtor =
                schedulerClass.getConstructor(Properties.class);
            this.scheduler = 
                (ApplicationScheduler)(schedulerCtor.newInstance(properties));
        } catch (InvocationTargetException e) {
            if (logger.isLoggable(Level.CONFIG)) 
                logger.logThrow(Level.CONFIG, e.getCause(), "Scheduler {0} " +
                                "failed to initialize", schedulerName);
            throw e;
        } catch (Exception e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e, "Scheduler {0} unavailable",
                                schedulerName);
            throw e;
        }

        // startup the requested number of consumer threads
        // NOTE: this is a simple implmentation to replicate the previous
        // behvavior, with the assumption that it will change if the
        // scheduler starts trying to add or drop consumers adaptively
        int requestedThreads =
            Integer.parseInt(properties.getProperty(CONSUMER_THREADS_PROPERTY,
                                                    DEFAULT_CONSUMER_THREADS));
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Using {0} transaction consumer threads",
                       requestedThreads);
        this.executor = Executors.newCachedThreadPool();
        for (int i = 0; i < requestedThreads; i++)
            executor.submit(new TaskConsumer());
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
     * Implementations of the TransactionScheduler interface.
     */

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner) {
        ScheduledTask t = new ScheduledTask(task, owner, defaultPriority,
                                            System.currentTimeMillis());
        return scheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       long startTime) {
        ScheduledTask t = new ScheduledTask(task, owner, defaultPriority,
                                            startTime);
        return scheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner) {
        scheduler.
            addTask(new ScheduledTask(task, owner, defaultPriority,
                                      System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             long startTime) {
        scheduler.
            addTask(new ScheduledTask(task, owner, defaultPriority,
                                      startTime));
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     Identity owner,
                                                     long startTime,
                                                     long period) {
        return scheduler.
            addRecurringTask(new ScheduledTask(task, owner, defaultPriority,
                                               startTime, period));
    }

    /**
     * {@inheritDoc}
     */
    public TaskQueue createTaskQueue() {
        if (isShutdown)
            throw new IllegalStateException("Scheduler is shutdown");
        return new TaskQueueImpl();
    }

    /**
     * {@inheritDoc}
     */
    public void runTask(KernelRunnable task, Identity owner) throws Exception {
        if (isShutdown)
            throw new IllegalStateException("Scheduler is shutdown");
        if (ContextResolver.isCurrentTransaction()) {
            // we're already active in a transaction, so just run the task
            task.run();
        } else {
            // we're starting a new transaction
            // NOTE: when we start applying re-try behavior, then this will
            // need to create some kind of future mechanism so that, if
            // needed, the calling thread can block until this transactional
            // task is finally finished
            executeTask(new ScheduledTask(task, owner, defaultPriority,
                                          System.currentTimeMillis()),
                        false, true);
        }
    }

    /*
     * Implementations of the PriorityScheduler interface.
     */

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       Priority priority) {
        ScheduledTask t = new ScheduledTask(task, owner, priority,
                                            System.currentTimeMillis());
        return scheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             Priority priority) {
        scheduler.
            addTask(new ScheduledTask(task, owner, priority,
                                      System.currentTimeMillis()));
    }

    /*
     * Implementations for the ProfileListener interface.
     */

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
        // see comment in notifyThreadLeaving
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        // see comment in notifyThreadLeaving
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        synchronized (this) {
            if (isShutdown)
                throw new IllegalStateException("Already shutdown");
            isShutdown = true;
        }
        executor.shutdownNow();
        scheduler.shutdown();
    }

    /*
     * Utility methods and classes.
     */

    /**
     * Package-private method that runs the given task in a transaction that
     * is not bound by any timeout value (i.e., is bound only by the
     * {@code com.sun.sgs.txn.timeout.unbounded} property value).
     *
     * @param task the {@code KernelRunnable} to run transactionally
     * @param owner the {@code Identity} that owns the task
     *
     * @throws IllegalStateException if this method is called from an
     *                               actively running transaction
     * @throws Exception if there is any failure that does not result in
     *                   re-trying the task
     */
    void runUnboundedTask(KernelRunnable task, Identity owner)
        throws Exception
    {
        if (isShutdown)
            throw new IllegalStateException("Scheduler is shutdown");
        if (ContextResolver.isCurrentTransaction())
            throw new IllegalStateException("Cannot be called from within " +
                                            "an active transaction");

        // NOTE: see see comment in runTask() about re-try issues

        // NOTE: in the current system we only use this method once, and
        // that's when the application is initialized, in which case there
        // is no other task trying to run...if we decide to start using
        // this method more broadly, then it should probably use a separate
        // thread-pool so that it doesn't affect transaction latency

        executeTask(new ScheduledTask(task, owner, defaultPriority,
                                      System.currentTimeMillis()),
                    true, true);
    }

    /**
     * Notifies the scheduler that a thread has been started to consume
     * tasks as they become ready.
     */
    private void notifyThreadJoining() {
        if (profileCollector != null)
            profileCollector.notifyThreadAdded();
        threadCount.incrementAndGet();
    }

    /**
     * Notifies the scheduler that a thread has been interrupted and is
     * finishing its work.
     */
    private void notifyThreadLeaving() {
        if (profileCollector != null)
            profileCollector.notifyThreadRemoved();
        // NOTE: we're not yet trying to adapt the number of threads being
        // used, so we assume that threads are only lost when the system
        // wants to shutdown...in practice, this should look at some
        // threshold and see if another consumer needs to be created
        if (threadCount.decrementAndGet() == 0) {
            logger.log(Level.CONFIG, "No more threads are consuming tasks");
            shutdown();
        }
    }

    /**
     * Private {@code Runnable} used to consume tasks as they become available
     * from the {@code ApplicationScheduler}. Once started, it will continue
     * running until it catches an {@code InterruptedException}.
     */
    private class TaskConsumer implements Runnable {
        /** {@inheritDoc} */
        public void run() {
            logger.log(Level.FINE, "Starting a consumer for transactions");
            notifyThreadJoining();

            try {
                while (true) {
                    // wait for the next task, at which point we may get
                    // interrupted and should therefore return
                    ScheduledTask task = scheduler.getNextTask(true);

                    // run the task, checking if it completed
                    if (executeTask(task, false, false)) {
                        // if it's a recurring task, schedule the next run
                        if (task.isRecurring())
                            task.scheduleNextRecurrence();
                        // if it has dependent tasks, schedule the next one
                        TaskQueueImpl queue =
                            (TaskQueueImpl)(task.getTaskQueue());
                        if (queue != null)
                            queue.scheduleNextTask();
                    }
                }
            } catch (InterruptedException ie) {
                if (logger.isLoggable(Level.FINE))
                    logger.logThrow(Level.FINE, ie, "Consumer is finishing");
            } catch (Exception e) {
                // this should never happen, since running the task should
                // never throw an exception that isn't handled
                logger.logThrow(Level.SEVERE, e, "Fatal error for consumer");
            } finally {
                notifyThreadLeaving();
            }
        }
    }

    /**
     * Private method that executes a single task, creating the transaction
     * state and handling re-try as appropriate. If an {@code Exception} is
     * thrown, it means that the task failed such that it was not re-tried.
     * Providing {@code false} for the {@code rethrowExceptions} parameter
     * will ensure that no {@code Exception}s are thrown from this method.
     * Providing {@code true} for the {@code unbounded} parameter results
     * in a transaction with timeout value as specified by the value of the
     * {@code TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY} property.
     * <p>
     * NOTE: This method will currently return only when the task has either
     * completed successfully or failed permanently. Once more complex
     * re-try logic is added, this method may also return because the task
     * has been put into some queue to try again in the future. The value
     * returned from this method denotes which case happened: {@code true}
     * if the task is finished, {@code false} if the task is still to be
     * run in the future. For the current implementation, this will always
     * return {@code true}.
     */
    private boolean executeTask(ScheduledTask task, boolean unbounded,
                                boolean rethrowExceptions)
        throws Exception
    {
        logger.log(Level.FINEST, "starting a new transactional task");

        // store the current owner, and then push the new thread detail
        Identity parent = ContextResolver.getCurrentOwner();
        ContextResolver.setTaskState(kernelContext, task.getOwner());

        try {
            // keep trying to run the task until we succeed, tracking how
            // many tries it actually took
            while (true) {
                if (profileCollector != null) {
                    // NOTE: We could report the two queue sizes separately,
                    // so we should figure out how we want to represent these
                    int waitSize =
                        scheduler.getReadyCount() + dependencyCount.get();
                    profileCollector.startTask(task.getTask(), task.getOwner(),
                                               task.getStartTime(), waitSize);
                    profileCollector.noteTransactional();
                }

                // setup the transaction state
                TransactionHandle handle =
                    transactionCoordinator.createTransaction(unbounded);
                Transaction transaction = handle.getTransaction();
                ContextResolver.setCurrentTransaction(transaction);

                try {
                    task.incrementTryCount();
                    try {
                        // run the task in the new transactional context
                        task.getTask().run();
                    } finally {
                        // regardless of the outcome, always clear the current
                        // transaction state before proceeding
                        ContextResolver.clearCurrentTransaction(transaction);
                    }

                    // try to commit the transaction...note that there's the
                    // chance that the application code masked the orginal
                    // cause of a failure, so we'll check for that first,
                    // re-throwing the root cause in that case
                    if (transaction.isAborted())
                        throw transaction.getAbortCause();
                    handle.commit();

                    // the task completed successfully, so we're done
                    if (profileCollector != null)
                        profileCollector.finishTask(task.getTryCount());
                    return true;
                } catch (InterruptedException ie) {
                    // make sure the transaction was aborted
                    if (! transaction.isAborted())
                        transaction.abort(ie);
                    // note the interruption but always re-throw the exception
                    if (profileCollector != null)
                        profileCollector.finishTask(task.getTryCount(), ie);
                    if (logger.isLoggable(Level.WARNING))
                        logger.logThrow(Level.WARNING, ie, "skipping a task " +
                                        "that was interrupted: {0}", task);
                    // NOTE: At this point the task is being dropped, but it
                    // should probably be re-tried in a different thread,
                    // unless this stack is shutting down. This can't be done
                    // without handling re-try handoff correctly (as described
                    // in runTask and shouldRetry), so while the current
                    // behavior is technically valid, this task should be
                    // re-queued when that behavior is available
                    throw ie;
                } catch (Throwable t) {
                    // make sure the transaction was aborted
                    if (! transaction.isAborted())
                        transaction.abort(t);
                    if (profileCollector != null)
                        profileCollector.finishTask(task.getTryCount(), t);
                    // some error occurred, so see if we should try again or
                    // give up for now
                    if (! shouldRetry(task, t)) {
                        if (rethrowExceptions) {
                            if (t instanceof Exception)
                                throw (Exception)t;
                            throw (Error)t;
                        }
                        return true;
                    }
                }
            }
        } finally {
            // always restore the previous owner before leaving
            ContextResolver.setTaskState(kernelContext, parent);
        }
    }

    /**
     * Private method that determines whether a given task should be re-tried
     * immediately based on the given {@code Throwable} that caused failure.
     * If this returns {@code true} then the task should immediately be
     * re-tried. Otherwise, the task should be dropped.
     * <p>
     * NOTE: The current implementation of this method will only check the
     * {@code Throwable} to see if it's requesting to be re-tried. This is
     * so that the initial commit of this new code can mimic the previous
     * behavior. In the next phase, this method may actually move this task
     * into some queue for future execution.
     */
    private boolean shouldRetry(ScheduledTask task, Throwable t) {
        // NOTE: as a first-pass implementation this simply instructs the
        // caller to try again if retry is requested, but when this changes
        // and could result in the the task being handed-off, this will
        // also force a change in the behavior of the runTask() and
        // runUnboundedTask() methods (see comments there for details)
        if ((t instanceof ExceptionRetryStatus) &&
            (((ExceptionRetryStatus)t).shouldRetry()))
            return true;

        // since we don't currently ever hand-off the task to try again in
        // the future, if we get here then the task is being dropped, so
        // log this fact
        if (logger.isLoggable(Level.WARNING)) {
            if (task.isRecurring()) {
                logger.logThrow(Level.WARNING, t, "skipping a recurrence of " +
                                "a task that failed with a non-retryable " +
                                "exception: {0}", task);
            } else {
                logger.logThrow(Level.WARNING, t, "dropping a task that " +
                                "failed with a non-retryable exception: {0}",
                                task);
            }
        }

        return false;
    }

    /** Private implementation of {@code TaskQueue}. */
    private final class TaskQueueImpl implements TaskQueue {
        private final Queue<ScheduledTask> queue =
            new LinkedList<ScheduledTask>();
        private boolean inScheduler = false;
        /** {@inheritDoc} */
        public void addTask(KernelRunnable task, Identity owner) {
            ScheduledTask schedTask =
                new ScheduledTask(task, owner, defaultPriority,
                                  System.currentTimeMillis());
            schedTask.setTaskQueue(this);

            synchronized (this) {
                if (inScheduler) {
                    dependencyCount.incrementAndGet();
                    queue.offer(schedTask);
                } else {
                    inScheduler = true;
                    scheduler.addTask(schedTask);
                }
            }
        }
        /** Private method to schedule the next task, if any. */
        void scheduleNextTask() {
            synchronized (this) {
                if (queue.isEmpty()) {
                    inScheduler = false;
                } else {
                    dependencyCount.decrementAndGet();
                    scheduler.addTask(queue.poll());
                }
            }
        }
    }

}
