/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.task;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.TaskOwnerImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileProducer;
import com.sun.sgs.profile.ProfileRegistrar;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;

import java.io.Serializable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is an implementation of <code>TaskService</code> that works on a
 * single node. It handles persisting tasks and keeping track of which tasks
 * have not yet run to completion, so that in the event of a system failure
 * the tasks can be run on re-start.
 */
public class TaskServiceImpl implements ProfileProducer, TaskService {

    /**
     * The identifier used for this <code>Service</code>.
     */
    public static final String NAME = TaskServiceImpl.class.getName();

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(NAME));

    /**
     * The name prefix used to bind all service-level objects associated
     * with this service.
     */
    public static final String DS_PREFIX = NAME + ".";

    // the namespace where pending tasks are kept
    // NOTE: in the multi-node case, this will have to have the node
    // identifier, or some other unique value, appended when the service
    // is constructed
    private static final String DS_PENDING_SPACE = DS_PREFIX + "Pending.";

    // the internal value used to represent a task with no delay
    private static final long START_NOW = 0;

    // the internal value used to represent a task that does not repeat
    private static final long PERIOD_NONE = -1;

    // the system's task scheduler, where tasks actually run
    private TaskScheduler taskScheduler = null;

    // a proxy providing access to the transaction state
    private static TransactionProxy transactionProxy = null;

    // the data service used in the same context
    private final DataService dataService;

    // the factory used to manage transaction state
    private final TransactionContextFactory<TxnState> ctxFactory;

    // the transient map for all recurring tasks' handles
    private ConcurrentHashMap<String,RecurringTaskHandle> recurringMap;

    // a local copy of the default priority, which is used in almost all
    // cases for tasks submitted by this service
    private static Priority defaultPriority = Priority.getDefaultPriority();

    // the profiled operations
    private ProfileOperation scheduleNDTaskOp = null;
    private ProfileOperation scheduleNDTaskDelayedOp = null;
    private ProfileOperation scheduleNDTaskPrioritizedOp = null;

    /**
     * Creates an instance of <code>TaskServiceImpl</code>. Note that this
     * service does not currently use any properties.
     *
     * @param properties startup properties
     * @param systemRegistry the registry of system components
     * @param txnProxy the transaction proxy
     * @throws Exception if there is an error creating the service
     */
    public TaskServiceImpl(Properties properties,
                           final ComponentRegistry systemRegistry,
			   final TransactionProxy txnProxy) 
	throws Exception
    {
        if (properties == null)
            throw new NullPointerException("Null properties not allowed");
        if (systemRegistry == null)
            throw new NullPointerException("Null registry not allowed");
	if (txnProxy == null)
	    throw new NullPointerException("Null proxy not allowed");

        recurringMap = new ConcurrentHashMap<String,RecurringTaskHandle>();

        // the scheduler is the only system component that we use
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

        // keep track of the proxy and the data service
        TaskServiceImpl.transactionProxy = txnProxy;
        dataService = txnProxy.getService(DataService.class);

        // create the factory for managing transaction context
        ctxFactory = new TransactionContextFactoryImpl(txnProxy);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return NAME;
    }

    /** {@inheritDoc} */
    public void ready() throws Exception {
	taskScheduler.runTask(
	    new TransactionRunner(
		new AbstractKernelRunnable() {
		    public void run() {
			readyInternal();
		    }
		}),
	    transactionProxy.getCurrentOwner(), true);
    }

    /** Reschedule existing tasks when the stack is ready. */
    private void readyInternal() {
        logger.log(Level.CONFIG, "re-scheduling pending tasks");

        // start iterating from the root of the pending task namespace
        TxnState txnState = ctxFactory.joinTransaction();
        String name = dataService.nextServiceBoundName(DS_PENDING_SPACE);
        int taskCount = 0;

        while ((name != null) && (name.startsWith(DS_PENDING_SPACE))) {
            PendingTask ptask =
                dataService.getServiceBinding(name, PendingTask.class);
            TaskRunner runner = new TaskRunner(name, ptask.getBaseTaskType());
            TaskOwner owner =
                new TaskOwnerImpl(ptask.getIdentity(), transactionProxy.
                                  getCurrentOwner().getContext());

            if (ptask.getPeriod() == PERIOD_NONE) {
                // this is a non-periodic task
                scheduleTask(runner, owner, ptask.getStartTime(),
                             defaultPriority);
            } else {
                // this is a periodic task
                long startTime = ptask.getStartTime();
                long now = System.currentTimeMillis();
                long period = ptask.getPeriod();
                // if the start time has already passed, figure out the next
                // period interval from now, and use that as the start time
                if (startTime < now) {
                    startTime += (((int)((now - startTime) / period)) + 1) *
                        period;
                }

                RecurringTaskHandle handle =
                    taskScheduler.scheduleRecurringTask(runner, owner,
                                                        startTime, period);
                txnState.addRecurringTask(name, handle);
            }

            // finally, get the next name, and increment the task count
            name = dataService.nextServiceBoundName(name);
            taskCount++;
        }

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "re-scheduled {0} tasks", taskCount);
    }

    /**
     * {@inheritDoc}
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        ProfileConsumer consumer =
            profileRegistrar.registerProfileProducer(this);

	if (consumer != null) {
	    scheduleNDTaskOp =
		consumer.registerOperation("scheduleNonDurableTask");
	    scheduleNDTaskDelayedOp =
		consumer.registerOperation("scheduleNonDurableTaskDelayed");
	    scheduleNDTaskPrioritizedOp =
		consumer.registerOperation(
		    "scheduleNonDurableTaskPrioritized");
	}
    }

    /**
     * {@inheritDoc}
     * <p>
     * FIXME: This is just a stub implementation right now. It needs to be
     * implemented. It has been added here to support the new shutdown
     * method on <code>Service</code>.
     */
    public boolean shutdown() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        scheduleTask(getRunner(task, START_NOW, PERIOD_NONE),
                     transactionProxy.getCurrentOwner(), START_NOW,
                     defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        if (delay < 0)
            throw new IllegalArgumentException("Delay must not be negative");
        long startTime = System.currentTimeMillis() + delay;
        scheduleTask(getRunner(task, startTime, PERIOD_NONE),
                     transactionProxy.getCurrentOwner(), startTime,
                     defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                                   long period) {
        // note the start time
        long startTime = System.currentTimeMillis() + delay;

        if ((delay < 0) || (period < 0))
            throw new IllegalArgumentException("Times must not be null");

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "scheduling a periodic task starting " +
                       "at {0}", startTime);

        // setup the runner for this task
        TaskRunner taskRunner = getRunner(task, startTime, period);
        String objName = taskRunner.getObjName();

        // get a handle from the scheduler and save it
        TaskOwner owner = transactionProxy.getCurrentOwner();
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(taskRunner, owner, startTime,
                                                period);
        ctxFactory.joinTransaction().addRecurringTask(objName, handle);

        return new PeriodicTaskHandleImpl(objName);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task) {
        if (task == null)
            throw new NullPointerException("Task must not be null");
        if (scheduleNDTaskOp != null)
            scheduleNDTaskOp.report();
        scheduleTask(task, transactionProxy.getCurrentOwner(), START_NOW,
                     defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task, long delay) {
        if (task == null)
            throw new NullPointerException("Task must not be null");
        if (delay < 0)
            throw new IllegalArgumentException("Delay must not be negative");
        if (scheduleNDTaskDelayedOp != null)
            scheduleNDTaskDelayedOp.report();
        scheduleTask(task, transactionProxy.getCurrentOwner(),
                     System.currentTimeMillis() + delay, defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task,
                                       Priority priority) {
        if (task == null)
            throw new NullPointerException("Task must not be null");
        if (priority == null)
            throw new NullPointerException("Priority must not be null");
        if (scheduleNDTaskPrioritizedOp != null)
            scheduleNDTaskPrioritizedOp.report();
        scheduleTask(task, transactionProxy.getCurrentOwner(), START_NOW,
                     priority);
    }

    /**
     * Private helper that creates a <code>KernelRunnable</code> for the
     * associated task, also generating a unique name for this task, taking
     * care of persisting the task if that hasn't already been done.
     */
    private TaskRunner getRunner(Task task, long startTime, long period) {
        logger.log(Level.FINEST, "setting up pending task");

        if (task == null)
            throw new NullPointerException("Task must not be null");

        // create a new pending task that will be used when the runner runs
        Identity identity = transactionProxy.getCurrentOwner().getIdentity();
        PendingTask ptask =
            new PendingTask(task, startTime, period, identity, dataService);

        // get the name of the new object and bind that into the pending
        // namespace for recovery on startup
        ManagedReference taskRef = dataService.createReference(ptask);
        String objName = DS_PENDING_SPACE + taskRef.getId();
        dataService.setServiceBinding(objName, ptask);

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "created pending task {0}", objName);

        return new TaskRunner(objName, ptask.getBaseTaskType());
    }

    /**
     * Private helper that handles scheduling a task by getting a reservation
     * from the scheduler. This is used for both the durable and non-durable
     * tasks, but not for periodic tasks.
     */
    private void scheduleTask(KernelRunnable task, TaskOwner owner,
                              long startTime, Priority priority) {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "reserving a task starting " +
                       (startTime == START_NOW ? "now" : "at " + startTime));

        TxnState txnState = ctxFactory.joinTransaction();

        // reserve a space for this task
        try {
            // see if this should be scheduled as a task to run now, or as
            // a task to run after a delay
            if (startTime == START_NOW)
                txnState.addReservation(taskScheduler.
                                        reserveTask(task, owner, priority));
            else
                txnState.addReservation(taskScheduler.
                                        reserveTask(task, owner, startTime));
        } catch (TaskRejectedException tre) {
            if (logger.isLoggable(Level.FINE))
                logger.logThrow(Level.FINE, tre,
                                "could not get a reservation");
            throw tre;
        }
    }

    /**
     * Private helper that fetches the task associated with the given name. If
     * this is a non-periodic task, then the task is also removed from the
     * managed map of pending tasks. This method is typically used when a
     * task actually runs. If the Task was managed by the application and
     * has been removed by the application, or another TaskService task has
     * already removed the pending task entry, then this method returns null
     * meaning that there is no task to run.
     */
    private PendingTask fetchPendingTask(String objName) {
        PendingTask ptask = null;
        try {
            ptask = dataService.getServiceBinding(objName, PendingTask.class);
        } catch (NameNotBoundException nnbe) {
            return null;
        }
        boolean isAvailable = ptask.isTaskAvailable();

        // if it's not periodic, remove both the task and the name binding
        if (ptask.getPeriod() == PERIOD_NONE) {
            dataService.removeServiceBinding(objName);
            dataService.removeObject(ptask);
        } else {
            // Make sure that the task is still available, because if it's
            // not, then we need to remove the mapping and cancel the task.
            // Note that this should be a very rare case
            if (! isAvailable)
                cancelPeriodicTask(objName);
        }

        return isAvailable ? ptask : null;
    }

    /**
     * Private helper that notifies the service about a task that failed
     * and is not being re-tried. This happens whenever a task is run by
     * the scheduler, and throws an exception that doesn't request the
     * task be re-tried. In this case, the transaction gets aborted, so
     * the pending task stays in the map. This method is called to start
     * a new task with the sole purpose of creating a new transactional
     * task where the pending task can be removed, if that task is not
     * periodic. Note that this method is not called within an active
     * transaction.
     */
    private void notifyNonRetry(final String objName) {
        if (logger.isLoggable(Level.INFO))
            logger.log(Level.INFO, "trying to remove non-retried task {0}",
                       objName);

        // check if the task is in the recurring map, in which case we don't
        // do anything else, because we don't remove recurring tasks except
        // when they're cancelled...note that this may yield a false negative,
        // because in another transaction the task may have been cancelled and
        // therefore already removed from this map, but this is an extremely
        // rare case, and at worst it simply causes a task to be scheduled
        // that will have no effect once run (because fetchPendingTask will
        // look at the pending task data, see that it's recurring, and
        // leave it in the map)
        if (recurringMap.containsKey(objName))
            return;
        
        TransactionRunner transactionRunner =
            new TransactionRunner(new NonRetryCleanupRunnable(objName));
        try {
            taskScheduler.scheduleTask(transactionRunner,
                                       transactionProxy.getCurrentOwner());
        } catch (TaskRejectedException tre) {
            if (logger.isLoggable(Level.WARNING))
                logger.logThrow(Level.WARNING, tre, "could not schedule " +
                                "task to remove non-retried task {0}: " +
                                "giving up", objName);
            throw tre;
        }
    }

    /**
     * Private helper runnable that cleans up after a non-retried task. See
     * block comment above in notifyNonRetry for more detail.
     */
    private class NonRetryCleanupRunnable implements KernelRunnable {
        private final String objName;
        NonRetryCleanupRunnable(String objName) {
            this.objName = objName;
        }
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return NonRetryCleanupRunnable.class.getName();
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            fetchPendingTask(objName);
        }
    }

    /**
     * Private helper that cancels a periodic task. This method cancels the
     * underlying recurring task, removes the task and name binding, and
     * notes the cancelled task in the local transaction state.
     */
    private void cancelPeriodicTask(String objName) {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "cancelling periodic task {0}", objName);

        TxnState txnState = ctxFactory.joinTransaction();
        PendingTask ptask = null;

        // resolve the task, which checks if the task was already cancelled
        try {
            ptask = dataService.getServiceBinding(objName, PendingTask.class);
        } catch (NameNotBoundException nnbe) {
            throw new ObjectNotFoundException("task was already cancelled");
        }

        // if the task was scheduled within this transaction, then we just
        // need to remove it from the transaction's state...otherwise, we
        // explicitly cancel the task
        if (! txnState.undoRecurringTask(objName))
            txnState.cancelRecurringTask(objName);

        // remove the task from the data service
        dataService.removeServiceBinding(objName);
        dataService.removeObject(ptask);
    }

    /**
     * Private class that is used to track state associated with a single
     * transaction and handle commit and abort operations.
     */
    private class TxnState extends TransactionContext {
        private HashSet<TaskReservation> reservationSet = null;
        private HashMap<String,RecurringTaskHandle> addedRecurringMap = null;
        private HashSet<String> cancelledRecurringSet = null;
        /** Creates context tied to the given transaction. */
        TxnState(Transaction txn) {
            super(txn);
        }
        /** {@inheritDoc} */
        public void commit() {
            // use the reservations...
            if (reservationSet != null)
                for (TaskReservation reservation : reservationSet)
                    reservation.use();
            // ...start the periodic tasks...
            if (addedRecurringMap != null) {
                for (Entry<String,RecurringTaskHandle> entry :
                         addedRecurringMap.entrySet()) {
                    RecurringTaskHandle handle = entry.getValue();
                    recurringMap.put(entry.getKey(), handle);
                    handle.start();
                }
            }
            // ...and cancel the cancelled periodic tasks
            if (cancelledRecurringSet != null) {
                for (String objName : cancelledRecurringSet) {
                    RecurringTaskHandle handle = recurringMap.remove(objName);
                    if (handle != null)
                        handle.cancel();
                }
            }
        }
        /** {@inheritDoc} */
        public void abort(boolean retryable) {
            // cancel all the reservations for tasks and recurring tasks that
            // were made during the transaction
            if (reservationSet != null)
                for (TaskReservation reservation : reservationSet)
                    reservation.cancel();
            if (addedRecurringMap != null)
                for (RecurringTaskHandle handle : addedRecurringMap.values())
                    handle.cancel();
        }
        /** Adds a reservation to use at commit-time. */
        void addReservation(TaskReservation reservation) {
            if (reservationSet == null)
                reservationSet = new HashSet<TaskReservation>();
            reservationSet.add(reservation);
        }
        /** Adds a handle to start at commit-time. */
        void addRecurringTask(String name, RecurringTaskHandle handle) {
            if (addedRecurringMap == null)
                addedRecurringMap = new HashMap<String,RecurringTaskHandle>();
            addedRecurringMap.put(name, handle);
        }
        /**
         * Tries to remove and cancel a handle added in the current transaction
         * returning {@code true} if successful or {@code false} if no such
         * task was added during this transaction.
         */
        boolean undoRecurringTask(String name) {
            if (addedRecurringMap == null)
                return false;
            RecurringTaskHandle handle = addedRecurringMap.remove(name);
            if (handle == null)
                return false;
            handle.cancel();
            return true;
        }
        /** Adds an already-running recurring task to cancel at commit-time. */
        void cancelRecurringTask(String name) {
            if (cancelledRecurringSet == null)
                cancelledRecurringSet = new HashSet<String>();
            cancelledRecurringSet.add(name);
        }
    }

    /** Private implementation of {@code TransactionContextFactory}. */
    private class TransactionContextFactoryImpl
        extends TransactionContextFactory<TxnState>
    {
        /** Creates an instance with the given proxy. */
        TransactionContextFactoryImpl(TransactionProxy proxy) {
            super(proxy);
        }
        /** {@inheritDoc} */
        protected TxnState createContext(Transaction txn) {
            return new TxnState(txn);
        }
    }

    /**
     * Private implementation of <code>KernelRunnable</code> that is used to
     * run the <code>Task</code>s scheduled by the application.
     */
    private class TaskRunner implements KernelRunnable {
        private final String objName;
        private final String objTaskType;
        TaskRunner(String objName, String objTaskType) {
            this.objName = objName;
            this.objTaskType = objTaskType;
        }
        String getObjName() {
            return objName;
        }
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return objTaskType;
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            try {
                // run the task in a transactional context
                (new TransactionRunner(new KernelRunnable() {
                        public String getBaseTaskType() {
                            return objTaskType;
                        }
                        public void run() throws Exception {
                            PendingTask ptask = fetchPendingTask(objName);
                            if (ptask == null) {
                                logger.log(Level.FINER, "tried to run a task "
                                           + "that was removed previously " +
                                           "from the data service; giving up");
                                return;
                            }
                            if (logger.isLoggable(Level.FINEST))
                                logger.log(Level.FINEST, "running task {0} " +
                                           "scheduled to run at {1}",
                                           ptask.getStartTime(), objName);
                            ptask.run();
                        }
                    })).run();
            } catch (Exception e) {
                // catch exceptions just before they go back to the scheduler
                // to see if the task will be re-tried...if not, then we need
                // notify the service
                if ((! (e instanceof ExceptionRetryStatus)) ||
                    (! ((ExceptionRetryStatus)e).shouldRetry()))
                    notifyNonRetry(objName);
                throw e;
            }
        }
    }

    /**
     * Private implementation of <code>PeriodicTaskHandle</code> that is
     * provided to application developers so they can cancel their tasks
     * in the future. This class uses the internally assigned name to
     * reference the task in the future, and uses the thread local service
     * reference to find its service.
     */
    private static class PeriodicTaskHandleImpl
        implements PeriodicTaskHandle, Serializable
    {
        private static final long serialVersionUID = 1;
        private final String objName;
        PeriodicTaskHandleImpl(String objName) {
            this.objName = objName;
        }
        /** {@inheritDoc} */
        public void cancel() {
            TaskServiceImpl.transactionProxy.getService(TaskServiceImpl.class).
                cancelPeriodicTask(objName);
        }
    }

}
