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
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

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
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.WatchdogService;

import java.io.Serializable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is an implementation of {@code TaskService} that works on a
 * single node and across multiple nodes. It handles persisting tasks and
 * keeping track of which tasks have not yet run to completion, so that in
 * the event of a system failure the tasks can be run on re-start.
 * FIXME: re-write this description.
 * FIXME: should the recurring tasks be cancelled immediately on identity move?
 * FIXME: when the Revovery Service is ready, use it to remove handoff sets.
 */
public class TaskServiceImpl implements ProfileProducer, TaskService {

    /**
     * The identifier used for this {@code Service}.
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

    // the namespace where pending tasks are kept (this name is always
    // followed by the pending task's identity)
    private static final String DS_PENDING_SPACE = DS_PREFIX + "Pending.";

    // the transient set of identities known to be active on the current node,
    // and how many tasks are pending for that identity
    private ConcurrentHashMap<Identity,AtomicInteger> activeIdentityMap;

    // the transient set of identies thought to be mapped to this node
    private HashSet<Identity> mappedIdentitySet;

    // a queue for communicating task count changes
    private LinkedBlockingQueue<IdentityCount> taskCountQueue;

    // the thread that will consume from the count queue
    private final Thread countQueueThread;

    // the base namespace where all tasks are handed off (this name is always
    // followed by the recipient node's identifier)
    private static final String DS_HANDOFF_SPACE = DS_PREFIX + "Handoff.";

    // the local node's hand-off namespace
    private final String localHandoffSpace;

    /** The property key to set how long to wait between hand-off checks. */
    public static final String HANDOFF_PERIOD_PROPERTY =
        NAME + "handoffPeriod";

    /** The default length of time to wait between hand-off checks. */
    public static final long HANDOFF_PERIOD_DEFAULT = 500L;

    // the actual amount of time to wait between hand-off checks
    private long handoffPeriod;

    // a handle to the periodic hand-off task
    private RecurringTaskHandle handoffTaskHandle = null;

    // the internal value used to represent a task with no delay
    private static final long START_NOW = 0;

    // the internal value used to represent a task that does not repeat
    private static final long PERIOD_NONE = -1;

    // the system's task scheduler, where tasks actually run
    private final TaskScheduler taskScheduler;

    // a proxy providing access to the transaction state
    private static TransactionProxy transactionProxy = null;

    // the owning application context used for re-starting tasks...note that
    // this is assigned in the constructor, so it should not be used for any
    // tasks that need the AppContext or other application-level features
    private final TaskOwner appOwner;

    // the data service used in the same context
    private final DataService dataService;

    // the mapping service used in the same context
    private final NodeMappingService nodeMappingService;

    // the factory used to manage transaction state
    private final TransactionContextFactory<TxnState> ctxFactory;

    // the transient map for all recurring tasks' handles
    private ConcurrentHashMap<String,RecurringTaskHandle> recurringMap;

    // the transient map for all recurring handles based on identity
    private ConcurrentHashMap<Identity,HashSet<RecurringTaskHandle>>
        identityRecurringMap;

    // a local copy of the default priority, which is used in almost all
    // cases for tasks submitted by this service
    private static Priority defaultPriority = Priority.getDefaultPriority();

    // the profiled operations
    private ProfileOperation scheduleNDTaskOp = null;
    private ProfileOperation scheduleNDTaskDelayedOp = null;
    private ProfileOperation scheduleNDTaskPrioritizedOp = null;

    /**
     * Creates an instance of {@code TaskServiceImpl}. See the class javadoc
     * for applicable properties.
     *
     * @param properties application properties
     * @param systemRegistry the registry of system components
     * @param transactionProxy the system's {@code TransactionProxy}
     */
    public TaskServiceImpl(Properties properties,
                           ComponentRegistry systemRegistry,
                           TransactionProxy transactionProxy)
        throws Exception
    {
        if (properties == null)
            throw new NullPointerException("Null properties not allowed");
        if (systemRegistry == null)
            throw new NullPointerException("Null registry not allowed");
        if (transactionProxy == null)
            throw new NullPointerException("Null proxy not allowed");

        logger.log(Level.CONFIG, "creating TaskServiceImpl");

        // create the transient local collections
        activeIdentityMap = new ConcurrentHashMap<Identity,AtomicInteger>();
        mappedIdentitySet = new HashSet<Identity>();
        taskCountQueue = new LinkedBlockingQueue<IdentityCount>();
        recurringMap = new ConcurrentHashMap<String,RecurringTaskHandle>();
        identityRecurringMap =
            new ConcurrentHashMap<Identity,HashSet<RecurringTaskHandle>>();

        // create the factory for managing transaction context
        ctxFactory = new TransactionContextFactoryImpl(transactionProxy);

        // keep a reference to the system components...
        this.transactionProxy = transactionProxy;
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

        // ...and to the other Services that are needed
        dataService = transactionProxy.getService(DataService.class);
        nodeMappingService =
            transactionProxy.getService(NodeMappingService.class);

        // get application's context, and note that this identity is always
        // active locally
        appOwner = transactionProxy.getCurrentOwner();
        Identity appIdentity = appOwner.getIdentity();
        activeIdentityMap.put(appIdentity, new AtomicInteger(1));
        mappedIdentitySet.add(appIdentity);

        // register for identity mapping updates
        nodeMappingService.
            addNodeMappingListener(new NodeMappingListenerImpl());

        // get the current node id for the hand-off namespace and create
        // the handoff set for this node
        localHandoffSpace = DS_HANDOFF_SPACE + transactionProxy.
            getService(WatchdogService.class).getLocalNodeId();
        taskScheduler.runTask(new TransactionRunner(new KernelRunnable() {
                public String getBaseTaskType() {
                    return getClass().getName();
                }
                public void run() throws Exception {
                    dataService.setServiceBinding(localHandoffSpace,
                                                  new HandoffSet());
                }
            }), transactionProxy.getCurrentOwner(), true);

        // get the length of time between hand-off checks
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        handoffPeriod = wrappedProps.getLongProperty(HANDOFF_PERIOD_PROPERTY,
                                                     HANDOFF_PERIOD_DEFAULT);

        // finally, create a consumer thread for task count updates
        countQueueThread = new Thread(new TaskCountRunner(),
                                      appIdentity.getName() + ":TaskCounter");
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
    public void ready() {
        logger.log(Level.CONFIG, "readying TaskService");

        // kick-off a periodic hand-off task, but delay a little while so
        // that the system has a chance to finish setup
        handoffTaskHandle = taskScheduler.
            scheduleRecurringTask(new TransactionRunner(new HandoffRunner()),
                                  transactionProxy.getCurrentOwner(),
                                  System.currentTimeMillis() + 2500,
                                  handoffPeriod);

        // start the thread that will consume task count updates
        countQueueThread.start();

        logger.log(Level.CONFIG, "TaskService is ready");
    }

    /**
     * {@inheritDoc}
     * <p>
     * FIXME: This is just a stub implementation right now. It needs to be
     * implemented. It has been added here to support the new shutdown
     * method on {@code Service}.
     */
    public boolean shutdown() {
        handoffTaskHandle.cancel();
        countQueueThread.interrupt();
        return false;
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
                consumer.registerOperation("scheduleNonDurableTaskPrioritized");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        if (task == null)
            throw new NullPointerException("Task must not be null");

        // persist the task regardless of where it will ultimately run
        TaskOwner owner = transactionProxy.getCurrentOwner();
        Identity identity = owner.getIdentity();
        TaskRunner runner = getRunner(task, identity, START_NOW, PERIOD_NONE);

        // check where the owner is active to get the task running
        if (isMappedLocally(identity))
            scheduleTask(runner, owner, START_NOW, defaultPriority);
        else
            handoffTask(runner.getObjName(), identity);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        long startTime = System.currentTimeMillis() + delay;

        if (task == null)
            throw new NullPointerException("Task must not be null");
        if (delay < 0)
            throw new IllegalArgumentException("Delay must not be negative");

        // persist the task regardless of where it will ultimately run
        TaskOwner owner = transactionProxy.getCurrentOwner();
        Identity identity = owner.getIdentity();
        TaskRunner runner = getRunner(task, identity, startTime, PERIOD_NONE);

        // check where the owner is active to get the task running
        if (isMappedLocally(identity))
            scheduleTask(runner, owner, startTime, defaultPriority);
        else
            handoffTask(runner.getObjName(), identity);
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

        // persist the task regardless of where it will ultimately run
        TaskOwner owner = transactionProxy.getCurrentOwner();
        Identity identity = owner.getIdentity();
        TaskRunner runner = getRunner(task, identity, startTime, period);
        String objName = runner.getObjName();

        // check where the owner is active to get the task running
        if (isMappedLocally(identity)) {
            RecurringTaskHandle handle =
                taskScheduler.scheduleRecurringTask(runner, owner, startTime,
                                                    period);
            ctxFactory.joinTransaction().addRecurringTask(objName, handle,
                                                          identity);
        } else {
            handoffTask(objName, identity);
        }

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

        TaskOwner owner = transactionProxy.getCurrentOwner();
        scheduleTask(new NonDurableTask(task, owner.getIdentity()), owner,
                     START_NOW, defaultPriority);
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

        TaskOwner owner = transactionProxy.getCurrentOwner();
        scheduleTask(new NonDurableTask(task, owner.getIdentity()), owner,
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

        TaskOwner owner = transactionProxy.getCurrentOwner();
        scheduleTask(new NonDurableTask(task, owner.getIdentity()), owner,
                     START_NOW, priority);
    }

    /**
     * Private helper that creates a {@code KernelRunnable} for the task,
     * also generating a unique name for this task and persisting the
     * associated {@code PendingTask}.
     */
    private TaskRunner getRunner(Task task, Identity identity, long startTime,
                                 long period) {
        logger.log(Level.FINEST, "setting up a pending task");

        // create a new pending task that will be used when the runner runs
        PendingTask ptask =
            new PendingTask(task, startTime, period, identity, dataService);

        // get the name of the new object and bind that into the pending
        // namespace for recovery on startup
        ManagedReference taskRef = dataService.createReference(ptask);
        String objName = DS_PENDING_SPACE + identity.getName() + "." +
            taskRef.getId();
        dataService.setServiceBinding(objName, ptask);

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "created pending task {0}", objName);

        return new TaskRunner(objName, ptask.getBaseTaskType(), identity);
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

        // reserve a space for this task
        try {
            TxnState txnState = ctxFactory.joinTransaction();
            // see if this should be scheduled as a task to run now, or as
            // a task to run after a delay
            if (startTime == START_NOW)
                txnState.addReservation(taskScheduler.
                                        reserveTask(task, owner, priority),
                                        owner.getIdentity());
            else
                txnState.addReservation(taskScheduler.
                                        reserveTask(task, owner, startTime),
                                        owner.getIdentity());
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

        // if it's not periodic, remove both the task and the name binding,
        // checking that this doesn't change the identity's status
        if (ptask.getPeriod() == PERIOD_NONE) {
            dataService.removeServiceBinding(objName);
            dataService.removeObject(ptask);
            ctxFactory.joinTransaction().
                decrementStatusCount(ptask.getIdentity());
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

        // make sure the recurring task gets cancelled on commit
        txnState.cancelRecurringTask(objName);

        // remove the pending task from the data service
        dataService.removeServiceBinding(objName);
        dataService.removeObject(ptask);

        // finally, decrement the count
        txnState.decrementStatusCount(ptask.getIdentity());
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
            // Note that this should (essentially) never happen, but if it
            // does then the pending task will always remain, and this node
            // will never consider this identity as inactive
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
     * Private class that is used to track state associated with a single
     * transaction and handle commit and abort operations.
     */
    private class TxnState extends TransactionContext {
        private HashSet<TaskReservation> reservationSet = null;
        private HashMap<String,RecurringTaskHandle> addedRecurringMap = null;
        private HashSet<String> cancelledRecurringSet = null;
        private HashMap<Identity,Integer> statusMap = null;
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
            // ...cancel the cancelled periodic tasks...
            if (cancelledRecurringSet != null) {
                for (String objName : cancelledRecurringSet) {
                    RecurringTaskHandle handle = recurringMap.remove(objName);
                    if (handle != null)
                        handle.cancel();
                }
            }
            // ...and hand-off any pending status votes
            if (statusMap != null) {
                for (Entry<Identity,Integer> entry : statusMap.entrySet()) {
                    int countChange = entry.getValue();
                    if (countChange != 0)
                        submitStatusChange(entry.getKey(), countChange);
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
        void addReservation(TaskReservation reservation, Identity identity) {
            if (reservationSet == null)
                reservationSet = new HashSet<TaskReservation>();
            reservationSet.add(reservation);
            incrementStatusCount(identity);
        }
        /** Adds a handle to start at commit-time. */
        void addRecurringTask(String name, RecurringTaskHandle handle,
                              Identity identity) {
            if (addedRecurringMap == null)
                addedRecurringMap = new HashMap<String,RecurringTaskHandle>();
            addedRecurringMap.put(name, handle);
            incrementStatusCount(identity);
        }
        /**
         * Tries to cancel the associated recurring task, recognizing whether
         * the task was scheduled within this transaction or previously.
         */
        void cancelRecurringTask(String name) {
            RecurringTaskHandle handle = null;
            if ((addedRecurringMap == null) ||
                ((handle = addedRecurringMap.remove(name)) == null)) {
                // the task wasn't created in this transaction, so it's
                // already running and needs to be stopped at commit
                if (cancelledRecurringSet == null)
                    cancelledRecurringSet = new HashSet<String>();
                cancelledRecurringSet.add(name);
            } else {
                // the task was created in this transaction, so we just have
                // to make sure that it doesn't start
                handle.cancel();
            }
        }
        /** Notes that a task has been added for the given identity. */
        void incrementStatusCount(Identity identity) {
            if (statusMap == null) {
                statusMap = new HashMap<Identity,Integer>();
                statusMap.put(identity, 1);
            } else {
                if (statusMap.containsKey(identity))
                    statusMap.put(identity, statusMap.get(identity) + 1);
                else
                    statusMap.put(identity, 1);
            }
        }
        /** Notes that a task has been removed for the given identity. */
        void decrementStatusCount(Identity identity) {
            if (statusMap == null) {
                statusMap = new HashMap<Identity,Integer>();
                statusMap.put(identity, -1);
            } else {
                if (statusMap.containsKey(identity))
                    statusMap.put(identity, statusMap.get(identity) - 1);
                else
                    statusMap.put(identity, -1);
            }
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
     * Private implementation of {@code KernelRunnable} that is used to
     * run the {@code Task}s scheduled by the application.
     */
    private class TaskRunner implements KernelRunnable {
        private final String objName;
        private final String objTaskType;
        private final Identity taskIdentity;
        TaskRunner(String objName, String objTaskType, Identity taskIdentity) {
            this.objName = objName;
            this.objTaskType = objTaskType;
            this.taskIdentity = taskIdentity;
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
            // check that the task's identity is still active on this node,
            // and if not then return, cancelling the task if it's recurring
            if (! isMappedLocally(taskIdentity)) {
                RecurringTaskHandle handle = recurringMap.remove(objName);
                if (handle != null)
                    handle.cancel();
                return;
            }

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
     * Private wrapper class for all non-durable tasks. This simply makes
     * sure that when a non-durable task runs, the status count for the
     * associated identity is decremented.
     */
    private class NonDurableTask implements KernelRunnable {
        private final KernelRunnable runnable;
        private final Identity identity;
        NonDurableTask(KernelRunnable runnable, Identity identity) {
            this.runnable = runnable;
            this.identity = identity;
        }
        public String getBaseTaskType() {
            return runnable.getBaseTaskType();
        }
        public void run() throws Exception {
            try {
                runnable.run();
                submitStatusChange(identity, -1);
            } catch (Throwable t) {
                if ((! (t instanceof ExceptionRetryStatus)) ||
                    (! ((ExceptionRetryStatus)t).shouldRetry()))
                    submitStatusChange(identity, -1);

                if (t instanceof Error)
                    throw (Error)t;
                else
                    throw (Exception)t;
            }
        }
    }

    /**
     * Private helper that restarts all of the tasks associated with the
     * given identity. This must be called within a transaction.
     * TODO: should this be split up? If there are more than N tasks?
     */
    private void restartTasks(String identityName) {
        // start iterating from the root of the pending task namespace
        TxnState txnState = ctxFactory.joinTransaction();
        String prefix = DS_PENDING_SPACE + identityName + ".";
        String objName = dataService.nextServiceBoundName(prefix);
        int taskCount = 0;

        // loop through all bound names for the given identity, starting
        // each pending task
        while ((objName != null) && (objName.startsWith(prefix))) {
            restartTask(objName, txnState);
            objName = dataService.nextServiceBoundName(objName);
            taskCount++;
        }

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "re-scheduled {0} tasks for identity {1}",
                       taskCount, identityName);
    }

    /**
     * Private helper that restarts a single named task. This must be called
     * within a transaction.
     */
    private void restartTask(String objName, TxnState txnState) {
        PendingTask ptask =
            dataService.getServiceBinding(objName, PendingTask.class);
        Identity identity = ptask.getIdentity();
        TaskRunner runner = new TaskRunner(objName, ptask.getBaseTaskType(),
                                           identity);
        TaskOwner owner =
            new TaskOwnerImpl(identity, transactionProxy.getCurrentOwner().
                              getContext());

        if (ptask.getPeriod() == PERIOD_NONE) {
            // this is a non-periodic task
            scheduleTask(runner, owner, ptask.getStartTime(), defaultPriority);
        } else {
            // this is a periodic task
            long start = ptask.getStartTime();
            long now = System.currentTimeMillis();
            long period = ptask.getPeriod();
            // if the start time has already passed, figure out the next
            // period interval from now, and use that as the start time
            if (start < now)
                start += (((int)((now - start) / period)) + 1) * period;

            RecurringTaskHandle handle =
                taskScheduler.scheduleRecurringTask(runner, owner, start,
                                                    period);
            txnState.addRecurringTask(objName, handle, identity);
        }
    }

    /**
     * Private managed set for handoff strings.
     * NOTE: This is only being used as a place-holder until the new
     * scalable set is committed.
     */
    private static class HandoffSet extends HashSet<String>
        implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1;
    }

    /**
     * Private helper method that hands-off a durable task from the current
     * node to a new node. The task needs to have already been persisted
     * as a {@code PendingTask} under the given name binding. This method
     * must be called in the context of a valid transaction.
     */
    private void handoffTask(String objName, Identity identity) {
        Node handoffNode = null;
        try {
            handoffNode = nodeMappingService.getNode(identity);
        } catch (UnknownIdentityException uie) {
            // FIXME: what do we do about this?
            return;
        }

        long newNodeId = handoffNode.getId();
        String handoffName = DS_HANDOFF_SPACE + String.valueOf(newNodeId);
        if (logger.isLoggable(Level.FINER))
            logger.log(Level.FINER, "Handing-off task {0} to node {1}",
                       objName, newNodeId);
        HandoffSet set =
            dataService.getServiceBinding(handoffName, HandoffSet.class);
        dataService.markForUpdate(set);
        set.add(objName);
    }

    /**
     * Private runnable that periodically checks to see if any tasks have
     * been handed-off from another node.
     */
    private class HandoffRunner implements KernelRunnable {
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return HandoffRunner.class.getName();
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            HandoffSet set = dataService.getServiceBinding(localHandoffSpace,
                                                           HandoffSet.class);
            if (! set.isEmpty()) {
                TxnState txnState = ctxFactory.joinTransaction();
                dataService.markForUpdate(set);
                // TODO: should this only do N re-starts? Should it split
                // the re-start work into different transactions?
                // FIXME: should this check that the identity is actually
                // mapped here, and hand-off otherwise?
                for (String objName : set)
                    restartTask(objName, txnState);
                set.clear();
            }
        }
    }

    /**
     * Private helper that checks whether the given {@code Identity} is
     * currently thought to be mapped to the local node. This does not need
     * to be called from within a transaction.
     */
    private boolean isMappedLocally(Identity identity) {
        synchronized (mappedIdentitySet) {
            return mappedIdentitySet.contains(identity);
        }
    }

    /** Private association of identity and a count. */
    private static class IdentityCount {
        final Identity identity;
        final int count;
        IdentityCount(Identity identity, int count) {
            this.identity = identity;
            this.count = count;
        }
    }

    /**
     * Private helper that accepts status change votes. This method does
     * not access any transactional context or other services, but may
     * be called in a transaction.
     */
    private void submitStatusChange(Identity identity, int count) {
        /*
        int newCount = 0;
        AtomicInteger ai = activeIdentityMap.get(identity);
        if (ai == null) {
            if (activeIdentityMap.putIfAbsent(new AtomicInteger() != null)
            //if (activeIdentityMap.
        } else {
            newCount = ai.addAndGet(count);
            }*/
        // note that this offer() should never return false here, but
        // in case it ever does, loop again
        while (! taskCountQueue.offer(new IdentityCount(identity, count)));
    }

    /** Private runnable that consumes task count changes. */
    private class TaskCountRunner implements Runnable {
        public void run() {
            try {
                while (true) {
                    if (Thread.interrupted())
                        return;
                    IdentityCount ic = taskCountQueue.take();
                    // 0: change map to have integers (non-atomic)
                    // 1: apply this change to the count
                    // 2: decide if this changes the status
                    // 3: either vote, or queue up a delayed vote
                }
            } catch (InterruptedException ie) {}
        }
    }

    /**
     * Private implementation of {@code NodeMappingListener} used to get
     * updates about {@code Identity} joins and leaves from this node.
     */
    private class NodeMappingListenerImpl implements NodeMappingListener {
        /** {@inheritDoc} */
        public void mappingAdded(Identity id, Node oldNode) {
            // keep track of the new identity
            activeIdentityMap.putIfAbsent(id, new AtomicInteger());
            synchronized (mappedIdentitySet) {
                mappedIdentitySet.add(id);
            }
            // start-up the pending tasks for this identity
            final String identityName = id.getName();
            try {
                taskScheduler.
                    runTask(new TransactionRunner(new KernelRunnable() {
                            public String getBaseTaskType() {
                                return getClass().getName();
                            }
                            public void run() throws Exception {
                                restartTasks(identityName);
                            }
                        }), appOwner, true);
            } catch (Exception e) {
                // FIXME: what can be done here?
            }
        }
        /** {@inheritDoc} */
        public void mappingRemoved(Identity id, Node newNode) {
            // note that the identity is no longer on this node
            synchronized (mappedIdentitySet) {
                mappedIdentitySet.remove(id);
            }
            // FIXME: do we keep a node-local pointer to the new node for
            // the case where getNode() fails?
            // FIXME: vote that the identity is active iif there are
            // still tasks running locally
        }
    }

    /**
     * Private implementation of {@code PeriodicTaskHandle} that is
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
