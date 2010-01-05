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
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.PriorityScheduler;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.kernel.schedule.SchedulerRetryPolicy;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.service.Transaction;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Package-private implementation of {@code TransactionScheduler} that is
 * used by the system for scheduling and running all transactional tasks.
 * This class supports the following configuration properties:
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>{@value #CONSUMER_THREADS_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_CONSUMER_THREADS}</code>
 *
 * <dd style="padding-top: .5em">The number of initial threads used to process
 *      transactional tasks.<p>
 * 
 * <dt> <i>Property:</i> <code><b>{@value #SCHEDULER_QUEUE_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_SCHEDULER_QUEUE}</code>
 * 
 * <dd style="padding-top: .5em">The implementation class used to track
 *      access to define which queue implementation should back this scheduler.
 *      The value of this property should be the
 *      name of a public, non-abstract class that implements the
 *      {@link SchedulerQueue} interface, and that provides a public
 *      constructor with the parameters {@link Properties}<p>
 *
 * <dt> <i>Property:</i> <code><b>{@value #SCHEDULER_RETRY_PROPERTY}
 *	</b></code> <br>
 *	<i>Default:</i> <code>{@value #DEFAULT_SCHEDULER_RETRY}</code>
 *
 * <dd style="padding-top: .5em">The implementation class used to define
 *      which retry policy implementation to use when tasks fail or abort.
 *      The value of this property should be the
 *      name of a public, non-abstract class that implements the
 *      {@link SchedulerRetryPolicy} interface, and that provides a public
 *      constructor with the parameters {@link Properties}<p>
 *
 * </dl>
 */
final class TransactionSchedulerImpl
    implements TransactionScheduler, PriorityScheduler, ProfileListener 
{

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

    /**
     * The default scheduler.
     */
    public static final String DEFAULT_SCHEDULER_QUEUE =
            "com.sun.sgs.impl.kernel.schedule.FIFOSchedulerQueue";

    /**
     * The property used to define which retry policy should be used in
     * this scheduler
     */
    public static final String SCHEDULER_RETRY_PROPERTY =
            "com.sun.sgs.impl.kernel.scheduler.retry";

    /**
     * The default retry policy
     */
    public static final String DEFAULT_SCHEDULER_RETRY =
            "com.sun.sgs.impl.kernel.schedule.ImmediateRetryPolicy";

    /**
     * The property used to define the default number of initial consumer
     * threads.
     */
    public static final String CONSUMER_THREADS_PROPERTY =
        "com.sun.sgs.impl.kernel.transaction.threads";

    /**
     * The default number of initial consumer threads.
     */
    public static final String DEFAULT_CONSUMER_THREADS = "4";

    // the default priority for tasks
    private static final Priority defaultPriority =
        Priority.getDefaultPriority();

    // the coordinator used to create and coordinate transactions
    private final TransactionCoordinator transactionCoordinator;

    // the backing scheduler queue used for ordering tasks
    private final SchedulerQueue backingQueue;

    // the retry policy used for this scheduler
    private final SchedulerRetryPolicy retryPolicy;

    // the collector handle used for profiling data
    private final ProfileCollectorHandle profileCollectorHandle;

    // the coordinator for all transactional object access
    private final AccessCoordinatorHandle accessCoordinator;

    // the executor service used to manage our threads
    private final ExecutorService executor;

    // the actual number of threads we're currently using
    private final AtomicInteger threadCount = new AtomicInteger(0);

    // the number of requested consumer threads
    private final int requestedThreads;

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
     * @param profileCollectorHandle the {@code ProfileCollectorHandler} used to
     *          manage collection of per-task profiling data
     * @param accessCoordinator the {@code AccessCoordinator} used by
     *                          the system to managed shared data
     *
     * @throws InvocationTargetException if there is a failure initializing
     *                                   the {@code SchedulerQueue}
     * @throws Exception if there is any failure creating the scheduler
     */
    TransactionSchedulerImpl(Properties properties,
                             TransactionCoordinator transactionCoordinator,
                             ProfileCollectorHandle profileCollectorHandle,
                             AccessCoordinatorHandle accessCoordinator)
        throws Exception
    {
        logger.log(Level.CONFIG, "Creating a Transaction Scheduler");

        if (properties == null) {
            throw new NullPointerException("Properties cannot be null");
        }
        if (transactionCoordinator == null) {
            throw new NullPointerException("Coordinator cannot be null");
        }
        if (profileCollectorHandle == null) {
            throw new NullPointerException("Collector handle cannot be null");
        }
	if (accessCoordinator == null) {
	    throw new NullPointerException("AccessCoordinator cannot be null");
        }

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        this.transactionCoordinator = transactionCoordinator;
        this.profileCollectorHandle = profileCollectorHandle;
        this.accessCoordinator = accessCoordinator;
        
        this.backingQueue = wrappedProps.getClassInstanceProperty(
                SCHEDULER_QUEUE_PROPERTY, DEFAULT_SCHEDULER_QUEUE,
                SchedulerQueue.class, new Class[]{Properties.class},
                properties);
        this.retryPolicy = wrappedProps.getClassInstanceProperty(
                SCHEDULER_RETRY_PROPERTY, DEFAULT_SCHEDULER_RETRY,
                SchedulerRetryPolicy.class, new Class[]{Properties.class},
                properties);

        // startup the requested number of consumer threads
        // NOTE: this is a simple implmentation to replicate the previous
        // behvavior, with the assumption that it will change if the
        // scheduler starts trying to add or drop consumers adaptively
        this.requestedThreads =
            Integer.parseInt(properties.getProperty(CONSUMER_THREADS_PROPERTY,
                                                    DEFAULT_CONSUMER_THREADS));
        if (logger.isLoggable(Level.CONFIG)) {
            logger.log(Level.CONFIG, "Using {0} transaction consumer threads",
                       requestedThreads);
        }
        this.executor = Executors.newCachedThreadPool();
        for (int i = 0; i < requestedThreads; i++) {
            executor.submit(new TaskConsumer());
        }

        // initialize the default timeout for scheduled tasks
        ScheduledTaskImpl.Builder.setDefaultTimeout(
                transactionCoordinator.getDefaultTimeout());
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
        ScheduledTaskImpl t = new ScheduledTaskImpl.Builder(
                task, owner, defaultPriority).build();
        return backingQueue.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       long startTime) 
    {
        ScheduledTaskImpl t = new ScheduledTaskImpl.Builder(
                task, owner, defaultPriority).startTime(startTime).build();
        return backingQueue.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner) {
        backingQueue.addTask(new ScheduledTaskImpl.Builder(
                task, owner, defaultPriority).build());
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             long startTime) 
    {
        backingQueue.addTask(new ScheduledTaskImpl.Builder(
                task, owner, defaultPriority).startTime(startTime).build());
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     Identity owner,
                                                     long startTime,
                                                     long period) 
    {
        ScheduledTaskImpl scheduledTask = new ScheduledTaskImpl.Builder(
                task, owner, defaultPriority).
                startTime(startTime).
                period(period).
                build();
        RecurringTaskHandle handle =
            backingQueue.createRecurringTaskHandle(scheduledTask);
        scheduledTask.setRecurringTaskHandle(handle);
        return handle;
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

    /**
     * {@inheritDoc}
     */
    public void runTask(KernelRunnable task, Identity owner) throws Exception {
        if (isShutdown) {
            throw new IllegalStateException("Scheduler is shutdown");
        }
        if (ContextResolver.isCurrentTransaction()) {
            // we're already active in a transaction, so just run the task
            task.run();
        } else {
            // we're starting a new transaction
            ScheduledTaskImpl scheduledTask = new ScheduledTaskImpl.Builder(
                    task, owner, defaultPriority).build();
            waitForTask(scheduledTask);
        }
    }

    /*
     * Implementations of the PriorityScheduler interface.
     */

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       Priority priority)
    {
        ScheduledTaskImpl t = new ScheduledTaskImpl.Builder(
                task, owner, priority).build();
        return backingQueue.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             Priority priority)
    {
        backingQueue.addTask(new ScheduledTaskImpl.Builder(
                task, owner, priority).build());
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
            if (isShutdown) {
                return; // return silently
            }
            isShutdown = true;

            executor.shutdownNow();
            backingQueue.shutdown();
        }
    }

    /*
     * Utility methods and classes.
     */

    /**
     * Private method that blocks until the task has completed, re-throwing
     * any exception resulting from the task failing.
     */
    private void waitForTask(ScheduledTaskImpl task)
        throws Exception
    {
        Throwable t = null;

        try {
            // NOTE: calling executeTask() directly means that we're trying
            // to run the transaction in the calling thread, so there are
            // actually more threads running tasks simulaneously than there
            // are threads in the scheduler pool. This could be changed to
            // hand-off the task and wait for the result if we wanted more
            // direct control over concurrent transactions
            executeTask(task, false);
            // wait for the task to complete...at this point it may have
            // already completed, or else it is being re-tried in a
            // scheduler thread
            t = task.get();
        } catch (InterruptedException ie) {
            // we were interrupted, so try to cancel the task, re-throwing
            // the interruption if that succeeds or looking at the result
            // if the task completes before it can be cancelled
            if (task.cancel(false)) {
                backingQueue.notifyCancelled(task);
                throw ie;
            }
            if (task.isCancelled()) {
                throw ie;
            }
            t = task.get();
        }

        // if the result of the task was a permananent failure, then
        // re-throw the exception
        if (t != null) {
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw (Error) t;
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
        if (isShutdown) {
            throw new IllegalStateException("Scheduler is shutdown");
        }
        if (ContextResolver.isCurrentTransaction()) {
            throw new IllegalStateException("Cannot be called from within " +
                                            "an active transaction");
        }

        // NOTE: in the current system we only use this method once, and
        // that's when the application is initialized, in which case there
        // is no other task trying to run...if we decide to start using
        // this method more broadly, then it should probably use a separate
        // thread-pool so that it doesn't affect transaction latency

        ScheduledTaskImpl scheduledTask = new ScheduledTaskImpl.Builder(
                task, owner, defaultPriority).
                timeout(ScheduledTask.UNBOUNDED).
                build();
        waitForTask(scheduledTask);
    }

    /**
     * Notifies the scheduler that a thread has been started to consume
     * tasks as they become ready.
     */
    private void notifyThreadJoining() {
        profileCollectorHandle.notifyThreadAdded();
        threadCount.incrementAndGet();
    }

    /**
     * Notifies the scheduler that a thread has been interrupted and is
     * finishing its work.
     */
    private void notifyThreadLeaving() {
        profileCollectorHandle.notifyThreadRemoved();
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
                        (ScheduledTaskImpl) (backingQueue.getNextTask(true));

                    // run the task, checking if it completed
                    if (executeTask(task, true)) {
                        // if it's a recurring task, schedule the next run
                        if (task.isRecurring()) {
                            long nextStart =
                                 task.getStartTime() + task.getPeriod();
                            task = new ScheduledTaskImpl.Builder(
                                    task).startTime(nextStart).build();
                            backingQueue.addTask(task);
                        }
                        // if it has dependent tasks, schedule the next one
                        TaskQueueImpl queue =
                                      (TaskQueueImpl) (task.getTaskQueue());
                        if (queue != null) {
                            queue.scheduleNextTask();
                        }
                    }
                }
            } catch (InterruptedException ie) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.logThrow(Level.FINE, ie, "Consumer is finishing");
                }
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
     * method is interrupted before the task can complete then this method
     * attempts to re-schedule the task to run in another thread if
     * {@code retryOnInterruption} is {@code true} and always re-throws
     * the associated {@code InterruptedException}.
     * <p>
     * This method returns {@code true} if the task was completed or failed
     * permanently, and {@code false} otherwise. If {@code false} is returned
     * then the task is scheduled to be re-tried at some point in the future,
     * possibly by another thread, by this method. The caller may query the
     * status of the task and wait for the task to complete or fail permanently
     * through the {@code ScheduledTaskImpl} interface.
     */
    private boolean executeTask(ScheduledTaskImpl task,
                                boolean retryOnInterruption)
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
                if (!task.setRunning(true)) {
                    // this task is already finished
                    return true;
                }

                // NOTE: We could report the queue sizes separately,
                // so we should figure out how we want to represent these
                int waitSize =
                    backingQueue.getReadyCount() +
                    dependencyCount.get();
                profileCollectorHandle.startTask(task.getTask(), 
                                                 task.getOwner(),
                                                 task.getStartTime(), 
                                                 waitSize);
                task.incrementTryCount();

                Transaction transaction = null;

                try {
                    // setup the transaction state
                    TransactionHandle handle = 
                            transactionCoordinator.createTransaction(
                            task.getTimeout());
                    transaction = handle.getTransaction();
                    ContextResolver.setCurrentTransaction(transaction);
                    
                    try {
                        // notify the profiler and access coordinator
                        profileCollectorHandle.noteTransactional(
                                                    transaction.getId());
                        accessCoordinator.
                            notifyNewTransaction(transaction,
						 task.getStartTime(),
                                                 task.getTryCount());

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
                    if (transaction.isAborted()) {
                        throw transaction.getAbortCause();
                    }
                    handle.commit();

                    // the task completed successfully, so we're done
                    profileCollectorHandle.finishTask(task.getTryCount());
                    task.setDone(null);
                    return true;
                } catch (InterruptedException ie) {
                    // make sure the transaction was aborted
                    if (!transaction.isAborted()) {
                        transaction.abort(ie);
                    }
                    profileCollectorHandle.finishTask(task.getTryCount(), ie);
                    task.setLastFailure(ie);

                    // if the task didn't finish because of the interruption
                    // then we want to note that and possibly re-queue the
                    // task to run in a usable thread
                    if (task.setInterrupted() && retryOnInterruption) {
                        if (!handoff(task)) {
                            // if the task couldn't be re-queued, then there's
                            // nothing left to do but drop it
                            task.setDone(ie);
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.logThrow(Level.WARNING, ie,
                                                "dropping an " +
                                                "interrupted task: {0}",
                                                task);
                            }
                        }
                    }
                    // always re-throw the interruption
                    throw ie;
                } catch (Throwable t) {
                    // make sure the transaction was aborted
                    if ((transaction != null) && (!transaction.isAborted())) {
                        transaction.abort(t);
                    }
                    profileCollectorHandle.finishTask(task.getTryCount(), t);
                    task.setLastFailure(t);

                    // some error occurred, so see if we should re-try
                    switch (retryPolicy.getRetryAction(task)) {
                        case DROP:
                            task.setDone(t);
                            if (logger.isLoggable(Level.WARNING)) {
                                if (task.isRecurring()) {
                                    logger.logThrow(Level.WARNING, t,
                                                    "skipping a recurrence " +
                                                    "of a task that failed: " +
                                                    "{0}", task);
                                } else {
                                    logger.logThrow(Level.WARNING, t,
                                                    "dropping a task that " +
                                                    "failed: {0}", task);
                                }
                            }
                            return true;
                        case RETRY_LATER:
                            task.setRunning(false);
                            if (handoff(task)) {
                                return false;
                            }
                            break;
                        case RETRY_NOW:
                            task.setRunning(false);
                            break;
                        default:
                            // we should never get here
                    }
                }
            }
        } finally {
            // always restore the previous owner before leaving...
            ContextResolver.setTaskState(kernelContext, parent);
        }
    }

    /**
     * Hands off the task to the backing queue.
     *
     * @param task the task to handoff
     * @return {@code true} if handoff was successful, {@code false} otherwise
     */
    private boolean handoff(ScheduledTaskImpl task) {
        try {
            backingQueue.addTask(task);
            return true;
        } catch (TaskRejectedException tre) {
            return false;
        }
    }

    /** Private implementation of {@code TaskQueue}. */
    private final class TaskQueueImpl implements TaskQueue {
        private final Queue<ScheduledTaskImpl> queue =
            new LinkedList<ScheduledTaskImpl>();
        private boolean inScheduler = false;
        /** {@inheritDoc} */
        public void addTask(KernelRunnable task, Identity owner) {
            ScheduledTaskImpl schedTask = new ScheduledTaskImpl.Builder(
                    task, owner, defaultPriority).build();
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
