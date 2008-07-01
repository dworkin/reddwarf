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
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.contention.ContentionManagementComponent;

import com.sun.sgs.impl.kernel.schedule.SchedulerQueue;

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

    /**
     * The property used to define which queue implementation should back
     * this scheduler.
     */
    public static final String SCHEDULER_QUEUE_PROPERTY =
        "com.sun.sgs.impl.kernel.scheduler.queue";

    // the default scheduler
    private static final String DEFAULT_SCHEDULER_QUEUE =
        "com.sun.sgs.impl.kernel.schedule.FIFOSchedulerQueue";

    /**
     * The property used to define the default number of initial consumer
     * threads.
     */
    public static final String CONSUMER_THREADS_PROPERTY =
        "com.sun.sgs.impl.kernel.transaction.threads";

    // the default number of initial consumer threads
    private static final String DEFAULT_CONSUMER_THREADS = "4";

    // the default priority for tasks
    private static final Priority defaultPriority =
        Priority.getDefaultPriority();

    // the coordinator used to create and coordinate transactions
    private final TransactionCoordinator transactionCoordinator;

    // the backing scheduler queue used for ordering tasks
    private final SchedulerQueue backingQueue;

    // the collector used for profiling data
    private final ProfileCollector profileCollector;

    private final ContentionManagementComponent contentionMgmt;

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
     * Creates an instance of {@code TransactionSchedulerImpl} with a
     * {@code null} {@link ContentionManagementComponent}.  This
     * constructor should be used only be the testing code when
     * contention management is not necessary.
     *
     * @param properties the {@code Properties} for the system
     * @param transactionCoordinator the {@code TransactionCoordinator} used
     *                               by the system to manage transactions
     * @param profileCollector the {@code ProfileCollector} used by the
     *                         system to collect profiling data, or
     *                         {@code null} if profiling is disabled
     *
     * @throws InvocationTargetException if there is a failure initializing
     *                                   the {@code SchedulerQueue}
     * @throws Exception if there is any failure creating the scheduler
     */
    TransactionSchedulerImpl(Properties properties,
                             TransactionCoordinator transactionCoordinator,
                             ProfileCollector profileCollector) 
	throws Exception 
    {
	this(properties, transactionCoordinator, profileCollector, null);
    }
    

    /**
     * Creates an instance of {@code TransactionSchedulerImpl}.
     *
     * @param properties the {@code Properties} for the system
     * @param transactionCoordinator the {@code TransactionCoordinator} used
     *                               by the system to manage transactions
     * @param profileCollector the {@code ProfileCollector} used by the
     *                         system to collect profiling data, or
     *                         {@code null} if profiling is disabled
     * @param contentionMgmt TODO
     *
     * @throws InvocationTargetException if there is a failure initializing
     *                                   the {@code SchedulerQueue}
     * @throws Exception if there is any failure creating the scheduler
     */
    TransactionSchedulerImpl(Properties properties,
                             TransactionCoordinator transactionCoordinator,
                             ProfileCollector profileCollector,
			     ContentionManagementComponent contentionMgmt)
        throws Exception
    {
        logger.log(Level.CONFIG, "Creating a Transaction Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");
        if (transactionCoordinator == null)
            throw new NullPointerException("Coordinator cannot be null");
        if (contentionMgmt == null)
            throw new NullPointerException("Contention Management cannot " + 
					   "be null");

        this.transactionCoordinator = transactionCoordinator;
        this.profileCollector = profileCollector;
	this.contentionMgmt = contentionMgmt;

        String queueName = properties.getProperty(SCHEDULER_QUEUE_PROPERTY,
                                                  DEFAULT_SCHEDULER_QUEUE);
        try {
            Class<?> queueClass = Class.forName(queueName);
            Constructor<?> queueCtor =
                queueClass.getConstructor(Properties.class);
            this.backingQueue = 
                (SchedulerQueue)(queueCtor.newInstance(properties));
        } catch (InvocationTargetException e) {
            if (logger.isLoggable(Level.CONFIG)) 
                logger.logThrow(Level.CONFIG, e.getCause(), "Queue {0} " +
                                "failed to initialize", queueName);
            throw e;
        } catch (Exception e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e, "Queue {0} unavailable",
                                queueName);
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
        ScheduledTaskImpl t =
            new ScheduledTaskImpl(task, owner, defaultPriority,
                                  System.currentTimeMillis());
        return backingQueue.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       long startTime) {
        ScheduledTaskImpl t =
            new ScheduledTaskImpl(task, owner, defaultPriority, startTime);
        return backingQueue.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner) {
        backingQueue.
            addTask(new ScheduledTaskImpl(task, owner, defaultPriority,
                                          System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             long startTime) {
        backingQueue.
            addTask(new ScheduledTaskImpl(task, owner, defaultPriority,
                                          startTime));
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     Identity owner,
                                                     long startTime,
                                                     long period) {
        ScheduledTaskImpl scheduledTask =
            new ScheduledTaskImpl(task, owner, defaultPriority, startTime,
                                  period);
        RecurringTaskHandle handle =
            backingQueue.createRecurringTaskHandle(scheduledTask);
        scheduledTask.setRecurringTaskHandle(handle);
        return handle;
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
            ScheduledTaskImpl scheduledTask =
                new ScheduledTaskImpl(task, owner, defaultPriority,
                                      System.currentTimeMillis());
            waitForTask(scheduledTask, false);
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
        ScheduledTaskImpl t = new ScheduledTaskImpl(task, owner, priority,
                                                    System.currentTimeMillis());
        return backingQueue.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             Priority priority) {
        backingQueue.
            addTask(new ScheduledTaskImpl(task, owner, priority,
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
        backingQueue.shutdown();
    }

    /*
     * Utility methods and classes.
     */

    /**
     * Private method that blocks until the task has completed, re-throwing
     * any exception resulting from the task failing.
     */
    private void waitForTask(ScheduledTaskImpl task, boolean unbounded)
        throws Exception
    {
        Throwable t = null;

        try {
            // wait for the task to complete
            executeTask(task, unbounded);
            t = task.get();
        } catch (InterruptedException ie) {
            // we were interrupted, so try to cancel the task, re-throwing
            // the interruption if that succeeds or looking at the result
            // if the task completes before it can be cancelled
            if (task.cancel(false)) {
                backingQueue.notifyCancelled(task);
                throw ie;
            }
            if (task.isCancelled())
                throw ie;
            t = task.get();
        }

        // if the result of the task was a permananent failure, then
        // re-throw the exception
        if (t != null) {
            if (t instanceof Exception)
                throw (Exception)t;
            throw (Error)t;
        }
    }

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

        // NOTE: in the current system we only use this method once, and
        // that's when the application is initialized, in which case there
        // is no other task trying to run...if we decide to start using
        // this method more broadly, then it should probably use a separate
        // thread-pool so that it doesn't affect transaction latency

        ScheduledTaskImpl scheduledTask =
            new ScheduledTaskImpl(task, owner, defaultPriority,
                                  System.currentTimeMillis());
        waitForTask(scheduledTask, true);
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
     * from the {@code SchedulerQueue}. Once started, it will continue
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
                    ScheduledTaskImpl task =
                        (ScheduledTaskImpl)(backingQueue.getNextTask(true));

                    // run the task, checking if it completed
                    if (executeTask(task, false)) {
                        // if it's a recurring task, schedule the next run
                        if (task.isRecurring()) {
                            long nextStart =
                                task.getStartTime() + task.getPeriod();
                            task = new ScheduledTaskImpl(task, nextStart);
                            backingQueue.addTask(task);
                        }
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
     * state and handling re-try as appropriate. If the thread calling this
     * method is interrupted before the task can complete, then the associated
     * {@code InterruptedException} is re-thrown. Providing {@code true} for
     * the {@code unbounded} parameter results in a transaction with timeout
     * value as specified by the value of the
     * {@code TransactionCoordinator.TXN_UNBOUNDED_TIMEOUT_PROPERTY} property.
     * <p>
     * This method returns {@code true} if the task was completed or failed
     * permanently, and {@code false} otherwise. If {@code false} is returned
     * then the task is scheduled to be re-tried at some point in the future,
     * possibly by another thread, by this method. The caller may query the
     * status of the task and wait for the task to complete or fail permanently
     * through the {@code ScheduledTaskImpl} interface.
     */
    private boolean executeTask(ScheduledTaskImpl task, boolean unbounded)
        throws InterruptedException
    {
        logger.log(Level.FINEST, "starting a new transactional task");

        // store the current owner, and then push the new thread detail
        Identity parent = ContextResolver.getCurrentOwner();
        ContextResolver.setTaskState(kernelContext, task.getOwner());

        try {
            // keep trying to run the task until we succeed, tracking how
            // many tries it actually took
            while (true) {
                if (! task.setRunning(true)) {
                    // this task is already finished
                    return true;
                }

                if (profileCollector != null) {
                    // NOTE: We could report the two queue sizes separately,
                    // so we should figure out how we want to represent these
                    int waitSize =
                        backingQueue.getReadyCount() + dependencyCount.get();
                    profileCollector.startTask(task.getTask(), task.getOwner(),
                                               task.getStartTime(), waitSize);
                    profileCollector.noteTransactional();
                }

                Transaction transaction = null;

                try {
                    // setup the transaction state
                    TransactionHandle handle =
                        transactionCoordinator.createTransaction(unbounded);
                    transaction = handle.getTransaction();
                    ContextResolver.setCurrentTransaction(transaction);
		    if (contentionMgmt != null)
			contentionMgmt.registerTransaction(transaction, 
							   task.getTask().
							   getBaseTaskType(),
							   task.getTryCount());

                    task.incrementTryCount();
                    try {
                        // run the task in the new transactional context
                        task.getTask().run();
                    } finally {
                        // regardless of the outcome, always clear the current
                        // transaction state before proceeding...
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
                    task.setDone(null);
                    return true;
                } catch (InterruptedException ie) {
                    // make sure the transaction was aborted
                    if (! transaction.isAborted())
                        transaction.abort(ie);
                    if (profileCollector != null)
                        profileCollector.finishTask(task.getTryCount(), ie);
                    // if the task didn't finish because of the interruption
                    // then we want to re-queue it to run in a usable thread
                    if (! task.isDone()) {
                        task.setRunning(false);
                        if (! handoffRetry(task, ie)) {
                            // if the task couldn't be re-queued, then there's
                            // nothing left to do but drop it
                            task.setRunning(true);
                            task.setDone(ie);
                            if (logger.isLoggable(Level.WARNING))
                                logger.logThrow(Level.WARNING, ie, "dropping " +
                                                "an interrupted task: {0}" +
                                                task);
                        }
                    }
                    // always re-throw the interruption
                    throw ie;
                } catch (Throwable t) {
                    // make sure the transaction was aborted
                    if (! transaction.isAborted())
                        transaction.abort(t);
                    if (profileCollector != null)
                        profileCollector.finishTask(task.getTryCount(), t);
                    // some error occurred, so see if we should re-try
                    if (! shouldRetry(task, t)) {
                        // the task is not being re-tried
                        task.setDone(t);
                        return true;
                    } else {
                        // see if the re-try should be handed-off
                        task.setRunning(false);
                        if (handoffRetry(task, t))
                            return false;
                    }
                }
            }
        } finally {
            // always restore the previous owner before leaving...
            ContextResolver.setTaskState(kernelContext, parent);
        }
    }

    /**
     * Private method that determines whether a given task should be re-tried
     * based on the given {@code Throwable} that caused failure. If this
     * returns {@code true} then the task should be re-tried. Otherwise, the
     * task should be dropped.
     */
    private boolean shouldRetry(ScheduledTaskImpl task, Throwable t) {
        // NOTE: as a first-pass implementation this simply instructs the
        // caller to try again if retry is requested, but other strategies
        // (like the number of times re-tried) might be considered later
        if ((t instanceof ExceptionRetryStatus) &&
            (((ExceptionRetryStatus)t).shouldRetry()))
            return true;

        // we're not re-trying the task, so log that it's being dropped
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

    /**
     * Private method that determines how to handoff a task that needs to
     * be re-tried. If this returns {@code true} then the task has been
     * taken and handed-off, and the caller is therefore no longer responsible
     * for executing the task. If this returns {@code false} then it's up to
     * the caller to try running the task again.
     */
    private boolean handoffRetry(ScheduledTaskImpl task, Throwable t) {
        // NOTE: this is a very simple initial policy that always causes
        // tasks to re-try "in place" unless they were interrupted, in which
        // case there's nothing to do but re-queue the task
        if (t instanceof InterruptedException) {
            try {
                backingQueue.addTask(task);
                return true;
            } catch (TaskRejectedException tre) {
                return false;
            }
        }
        return false;
    }

    /** Private implementation of {@code TaskQueue}. */
    private final class TaskQueueImpl implements TaskQueue {
        private final Queue<ScheduledTaskImpl> queue =
            new LinkedList<ScheduledTaskImpl>();
        private boolean inScheduler = false;
        /** {@inheritDoc} */
        public void addTask(KernelRunnable task, Identity owner) {
            ScheduledTaskImpl schedTask =
                new ScheduledTaskImpl(task, owner, defaultPriority,
                                      System.currentTimeMillis());
            schedTask.setTaskQueue(this);

            synchronized (this) {
                if (inScheduler) {
                    dependencyCount.incrementAndGet();
                    queue.offer(schedTask);
                } else {
                    inScheduler = true;
                    backingQueue.addTask(schedTask);
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
                    // re-set the start time before scheduling, since the
                    // task isn't really requested to start until all
                    // tasks ahead of it have run
                    ScheduledTaskImpl schedTask = queue.poll();
                    schedTask.resetStartTime();
                    backingQueue.addTask(schedTask);
                }
            }
        }
    }

}
