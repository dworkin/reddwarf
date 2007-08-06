/*
 * Copyright 2007 Sun Microsystems, Inc.
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

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
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
public class TaskServiceImpl
    implements ProfileProducer, TaskService, NonDurableTransactionParticipant
{

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

    // flags indicating that configuration has been done successfully,
    // and that we're still in the process of configuring
    private boolean isConfigured = false;
    private boolean isConfiguring = false;

    // the system's task scheduler, where tasks actually run
    private TaskScheduler taskScheduler = null;

    // a proxy providing access to the transaction state
    private static TransactionProxy transactionProxy = null;

    // the data service used in the same context
    private DataService dataService = null;

    // the state map for each active transaction
    private ConcurrentHashMap<Transaction,TxnState> txnMap;

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
     */
    public TaskServiceImpl(Properties properties,
                           ComponentRegistry systemRegistry) {
        if (properties == null)
            throw new NullPointerException("Null properties not allowed");
        if (systemRegistry == null)
            throw new NullPointerException("Null registry not allowed");

        txnMap = new ConcurrentHashMap<Transaction,TxnState>();
        recurringMap = new ConcurrentHashMap<String,RecurringTaskHandle>();

        // the scheduler is the only system component that we use
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return NAME;
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
     */
    public void configure(ComponentRegistry serviceRegistry,
                          TransactionProxy proxy) {
        if (isConfigured)
            throw new IllegalStateException("Task Service already configured");
        isConfiguring = true;

        logger.log(Level.CONFIG, "starting TaskService configuration");

        if (serviceRegistry == null)
            throw new NullPointerException("null registry not allowed");
        if (proxy == null)
            throw new NullPointerException("null proxy not allowed");

        // keep track of the proxy and the data service
        TaskServiceImpl.transactionProxy = proxy;
        dataService = serviceRegistry.getComponent(DataService.class);

        logger.log(Level.CONFIG, "re-scheduling pending tasks");

        // start iterating from the root of the pending task namespace
        String name = dataService.nextServiceBoundName(DS_PENDING_SPACE);
        int taskCount = 0;

        while ((name != null) && (name.startsWith(DS_PENDING_SPACE))) {
            PendingTask task =
                dataService.getServiceBinding(name, PendingTask.class);
            TaskRunner runner = new TaskRunner(name, task.getBaseTaskType());
            TaskOwner owner =
                new TaskOwnerImpl(task.identity, transactionProxy.
                                  getCurrentOwner().getContext());

            if (task.period == PERIOD_NONE) {
                // this is a non-periodic task
                scheduleTask(runner, owner, task.startTime, defaultPriority);
            } else {
                // this is a periodic task
                long startTime = task.startTime;
                long now = System.currentTimeMillis();
                long period = task.period;
                // if the start time has already passed, figure out the next
                // period interval from now, and use that as the start time
                // NOTE: this behavior may be generalized in the scheduler,
                // in which case it can be removed from here
                if (startTime < now) {
                    startTime += (((int)((now - startTime) / period)) + 1) *
                        period;
                }
                RecurringTaskHandle handle =
                    taskScheduler.scheduleRecurringTask(runner, owner,
                                                        startTime, period);

                // keep track of the handle
                TxnState txnState = getTxnState();
                if (txnState.recurringMap == null)
                    txnState.recurringMap =
                        new HashMap<String,RecurringTaskHandle>();
                txnState.recurringMap.put(name, handle);
            }

            // finally, get the next name, and increment the task count
            name = dataService.nextServiceBoundName(name);
            taskCount++;
        }

        // if we didn't re-schedule anything then we also didn't join the
        // transaction, so take of that now to handle the configure bit
        if (taskCount == 0)
            getTxnState();

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "re-scheduled {0} tasks", taskCount);
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
    public boolean prepare(Transaction txn) throws Exception {
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "preparing txn:{0}", txn);

        // resolve the current transaction and the local state
        TxnState txnState = txnMap.get(txn);

        // make sure that we're still actively participating
        if (txnState == null) {
            if (logger.isLoggable(Level.FINER))
                logger.log(Level.FINER, "not participating in txn:{0}", txn);
            throw new IllegalStateException("TaskService " + NAME + "is no " +
                                            "longer participating in this " +
                                            "transaction");
        }

        // make sure that we haven't already been called to prepare
        if (txnState.prepared) {
            if (logger.isLoggable(Level.FINER))
                logger.log(Level.FINER, "already prepared for txn:{0}", txn);
            throw new IllegalStateException("TaskService " + NAME + " has " +
                                            "already been prepared");
        }

        // mark ourselves as being prepared
        txnState.prepared = true;

        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "prepare txn:{0} succeeded", txn);
        
        // if we joined the transaction it's because we have reserved some
        // task(s) or have to cancel some task(s) so always return false
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void commit(Transaction txn) {
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "committing txn:{0}", txn);

        // see if we we're committing the configuration transaction
        if (isConfiguring) {
            isConfigured = true;
            isConfiguring = false;
        }

        // resolve the current transaction and the local state, removing the
        // state so we can't accidentally use it further in the future
        TxnState txnState = txnMap.remove(txn);

        // make sure that we're still actively participating
        if (txnState == null) {
            if (logger.isLoggable(Level.WARNING))
                logger.log(Level.WARNING, "not participating in txn:{0}", txn);
            throw new IllegalStateException("TaskService " + NAME + "is no " +
                                            "longer participating in this " +
                                            "transaction");
        }

        // make sure that we were already called to prepare
        if (! txnState.prepared) {
            if (logger.isLoggable(Level.WARNING))
                logger.log(Level.WARNING, "were not prepared for txn:{0}",
                           txn);
            throw new IllegalStateException("TaskService " + NAME + " has " +
                                            "not been prepared");
        }

        // we're in the right state, so start using the reservations...
        if (txnState.reservationSet != null)
            for (TaskReservation reservation : txnState.reservationSet)
                reservation.use();

        // ...and periodic tasks.
        if (txnState.recurringMap != null) {
            for (Entry<String,RecurringTaskHandle> entry :
                     txnState.recurringMap.entrySet()) {
                RecurringTaskHandle handle = entry.getValue();
                recurringMap.put(entry.getKey(), handle);
                handle.start();
            }
        }

        // finally, cancel the cancelled periodic tasks and remove them
        // from the local map
        if (txnState.cancelledSet != null) {
            for (String objName : txnState.cancelledSet) {
                RecurringTaskHandle handle = recurringMap.remove(objName);
                if (handle != null)
                    handle.cancel();
            }
        }

        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "commit txn:{0} succeeded", txn);
    }

    /**
     * {@inheritDoc}
     */
    public void prepareAndCommit(Transaction txn) throws Exception {
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "prepareAndCommit on txn:{0}", txn);
        prepare(txn);
        commit(txn);
    }

    /**
     * {@inheritDoc}
     */
    public void abort(Transaction txn) {
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "aborting txn:{0}", txn);

        // resolve the current transaction and the local state, removing the
        // state so we can't accidentally use it further in the future
        TxnState txnState = txnMap.remove(txn);

        // make sure that we were participating
        if (txnState == null) {
            if (logger.isLoggable(Level.WARNING))
                logger.log(Level.WARNING, "not participating txn:{0}", txn);
            throw new IllegalStateException("TaskService " + NAME + "is " +
                                            "not participating in this " +
                                            "transaction");
        }

        // cancel all the reservations for tasks and recurring tasks that
        // were made during the transaction
        if (txnState.reservationSet != null)
            for (TaskReservation reservation : txnState.reservationSet)
                reservation.cancel();
        if (txnState.recurringMap != null)
            for (RecurringTaskHandle handle : txnState.recurringMap.values())
                handle.cancel();
    }

    /**
     * Private helper that gets the transaction state, or creates it (and
     * joins to the transaction) if the state doesn't exist.
     */
    private TxnState getTxnState() {
        // resolve the current transaction and the local state
        Transaction txn = transactionProxy.getCurrentTransaction();
        TxnState txnState = txnMap.get(txn);

        // if it didn't exist yet then create it and join the transaction
        if (txnState == null) {
            txnState = new TxnState();
            txnMap.put(txn, txnState);
            txn.join(this);
        } else {
            // if it's already been prepared then we shouldn't be using
            // it...note that this shouldn't be a problem, since the system
            // shouldn't let this case get tripped, so this is just defensive
            if (txnState.prepared) {
                if (logger.isLoggable(Level.WARNING))
                    logger.log(Level.WARNING, "already prepared txn:{0}", txn);
                throw new IllegalStateException("Trying to access prepared " +
                                                "transaction for scheduling");
            }
        }

        return txnState;
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

        if (task == null)
            throw new NullPointerException("Task must not be null");

        if ((delay < 0) || (period < 0))
            throw new IllegalArgumentException("Times must not be null");

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "scheduling a periodic task starting " +
                       "at {0}", startTime);

        // setup the runner for this task
        TxnState txnState = getTxnState();
        TaskRunner taskRunner = getRunner(task, startTime, period);
        String objName = taskRunner.getObjName();

        // get a handle from the scheduler and save it
        TaskOwner owner = transactionProxy.getCurrentOwner();
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(taskRunner, owner, startTime,
                                                period);
        if (txnState.recurringMap == null)
            txnState.recurringMap = new HashMap<String,RecurringTaskHandle>();
        txnState.recurringMap.put(objName, handle);

        return new PeriodicTaskHandleImpl(objName);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task) {
        if (scheduleNDTaskOp != null)
            scheduleNDTaskOp.report();
        scheduleTask(task, transactionProxy.getCurrentOwner(), START_NOW,
                     defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task, long delay) {
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
        PendingTask ptask = new PendingTask(task, startTime, period,
                                            dataService);

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
        if (task == null)
            throw new NullPointerException("Task must not be null");

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "reserving a task starting " +
                       (startTime == START_NOW ? "now" : "at " + startTime));

        TxnState txnState = getTxnState();

        // reserve a space for this task
        if (txnState.reservationSet == null)
            txnState.reservationSet = new HashSet<TaskReservation>();
        TaskReservation reservation = null;
        try {
            // see if this should be scheduled as a task to run now, or as
            // a task to run after a delay
            if (startTime == START_NOW)
                reservation =
                    taskScheduler.reserveTask(task, owner, priority);
            else
                reservation =
                    taskScheduler.reserveTask(task, owner, startTime);
        } catch (TaskRejectedException tre) {
            if (logger.isLoggable(Level.FINE))
                logger.logThrow(Level.FINE, tre,
                                "could not get a reservation");
            throw tre;
        }

        // keep track of the reservation in our local transaction state
        txnState.reservationSet.add(reservation);
    }

    /**
     * Private helper that fetches the task associated with the given name. If
     * this is a non-periodic task, then the task is also removed from the
     * managed map of pending tasks. This method is typically used when a
     * task actually runs. If the Task was managed by the application, and
     * has been removed by the application, then this methods returns null
     * meaning that there is no task to run.
     */
    private PendingTask fetchPendingTask(String objName) {
        PendingTask ptask =
            dataService.getServiceBinding(objName, PendingTask.class);
        boolean isAvailable = ptask.isTaskAvailable();

        // if it's not periodic, remove both the task and the name binding
        if (ptask.period == PERIOD_NONE) {
            dataService.removeServiceBinding(objName);
            dataService.removeObject(ptask);
        } else {
            // Make sure that the task is still available, because if it's
            // not, then we need to remove the mapping and cancel the task.
            // Note that this should be a very rare case
            if (! isAvailable)
                cancelPeriodicTask(objName);
        }

        if (isAvailable)
            return ptask;
        else
            return null;
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
     * Private helper that cancels a periodic task. This is only ever called
     * by <code>PeriodicTaskHandleImpl</code>. This method cancels the
     * underlying recurring task, removes the task and name binding, and
     * notes the cancelled task in the local transaction state so the handle
     * can be removed from the local map at commit.
     */
    private void cancelPeriodicTask(String objName) {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "cancelling periodic task {0}", objName);

        TxnState txnState = getTxnState();
        PendingTask ptask = null;

        try {
            ptask = dataService.getServiceBinding(objName, PendingTask.class);
        } catch (NameNotBoundException nnbe) {
            // either some one else cancelled this already, or it was only
            // just created in this transaction
            if ((txnState.recurringMap != null) &&
                (txnState.recurringMap.containsKey(objName))) {
                txnState.recurringMap.remove(objName).cancel();
                return;
            } else {
                throw new ObjectNotFoundException("task is already cancelled");
            }
        }

        // note this as cancelled...
        if (txnState.cancelledSet == null)
            txnState.cancelledSet = new HashSet<String>();
        txnState.cancelledSet.add(objName);

        // ...and remove from the data service
        dataService.removeServiceBinding(objName);
        dataService.removeObject(ptask);
    }

    /**
     * Inner class that is used to track state associated with a single
     * transaction. This is indexed in the local transaction map.
     */
    private static class TxnState {
        boolean prepared = false;
        HashSet<TaskReservation> reservationSet = null;
        HashMap<String,RecurringTaskHandle> recurringMap = null;
        HashSet<String> cancelledSet = null;
    }

    /**
     * Nested class that represents a single pending task in the managed map.
     */
    private static class PendingTask implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1;
        private Task task = null;
        private ManagedReference taskRef = null;
        private final String taskType;
        long startTime;
        long period;
        private Identity identity;
        /**
         * Creates an instance of <code>PendingTask</code>, handling the
         * task reference correctly. Note that the <code>DataService</code>
         * parameter is not kept as state, it is just used (if needed) to
         * resolve a reference in the constructor.
         */
        PendingTask(Task task, long startTime, long period,
                    DataService dataService) {
            // if the Task is also a ManagedObject then the assumption is
            // that the object was already managed by the application so we
            // just keep a reference...otherwise, we make it part of our
            // state, which has the effect of persisting the task
            if (task instanceof ManagedObject)
                taskRef = dataService.createReference((ManagedObject)task); 
            else
                this.task = task;

            this.taskType = task.getClass().getName();
            this.startTime = startTime;
            this.period = period;
            this.identity = TaskServiceImpl.transactionProxy.
                getCurrentOwner().getIdentity();
        }
        /**
         * Provides the name of the type of the task that is contained in
         * this pending task.
         */
        String getBaseTaskType() {
            return taskType;
        }
        /**
         * Checks that the underlying task is available, which is only at
         * question if the task was managed by the application and therefore
         * could have been removed by the application
         */
        boolean isTaskAvailable() {
            if (task != null)
                return true;
            try {
                taskRef.get(Task.class);
                return true;
            } catch (ObjectNotFoundException onfe) {
                logger.log(Level.FINER, "Task was removed by application");
                return false;
            }
        }
        /**
         * Runs this {@code PendingTask}'s underlying task.
         *
         * @throws Exception if the underlying task throws an exception
         */
        void run() throws Exception {
            Task runTask = null;
            try {
                runTask = (task != null) ? task : taskRef.get(Task.class);
            } catch (ObjectNotFoundException onfe) {
                // This only happens when the application removed the task
                // object but didn't cancel the task, and the fetchPendingTask
                // cleans up after this, so we're done
                logger.log(Level.FINER, "tried to run task that was removed " +
                           "previously from the data service; giving up");
                return;
            }
            runTask.run();
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
                            if (logger.isLoggable(Level.FINEST))
                                logger.log(Level.FINEST, "running task {0} " +
                                           "scheduled to run at {1}",
                                           ptask.startTime, objName);
                            if (ptask != null)
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
        private boolean cancelled = false;
        PeriodicTaskHandleImpl(String objName) {
            this.objName = objName;
        }
        /** {@inheritDoc} */
        public void cancel() {
            if (cancelled)
                throw new ObjectNotFoundException("Task has already been " +
                                                  "cancelled");
            cancelled = true;
            TaskServiceImpl.transactionProxy.getService(TaskServiceImpl.class).
                cancelPeriodicTask(objName);
        }
    }

}
