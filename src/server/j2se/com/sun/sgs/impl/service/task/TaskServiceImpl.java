
package com.sun.sgs.impl.service.task;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionRunner;

import java.io.Serializable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is an implementation of <code>TaskService</code> that works on a
 * single node. It handles persisting tasks and keeping track of which tasks
 * have not yet run to completion, so that in the event of a system failure
 * the tasks can be run on re-start.
 * <p>
 * Note that the current implementation is fairly inefficient, in that it
 * uses a single managed map for all of the pending tasks. This means that
 * there is contention on the map object each time a task is given to the
 * service, and each time the scheduler runs a task submitted by the service.
 * The right way to handle this problem is to use an efficient, hierarchical
 * data structure, but we haven't written one of those yet. The assumption
 * is that we will produce one of these soon, and in the mean time accept
 * the performance limitations of this service.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class TaskServiceImpl
    implements TaskService, NonDurableTransactionParticipant
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

    // the name of the single map for the pending tasks
    private static final String DS_PENDING_TASKS = DS_PREFIX + "PendingTasks";

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

    // a simple generator for unique names for each persisted task
    // NOTE: when we move to a multi-stack system this will need to either
    // use some global facility, prefix with the node name, or be generated
    // as a universally unique identifier
    private AtomicLong nameGenerator;

    // a local copy of the default priority, which is used in almost all
    // cases for tasks submitted by this service
    private static Priority defaultPriority = Priority.getDefaultPriority();

    /**
     * Creates an instance of <code>TaskServiceImpl</code>. Note that this
     * service does not currently use any properties.
     *
     * @param properties startup propoerties
     * @param systemRegistry the registry of system components
     */
    public TaskServiceImpl(Properties properties,
                           ComponentRegistry systemRegistry) {
        txnMap = new ConcurrentHashMap<Transaction,TxnState>();
        recurringMap = new ConcurrentHashMap<String,RecurringTaskHandle>();
        nameGenerator = new AtomicLong();

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
    public void configure(ComponentRegistry serviceRegistry,
                          TransactionProxy transactionProxy) {
        if (isConfigured)
            throw new IllegalStateException("Task Service already configured");
        isConfiguring = true;

        logger.log(Level.CONFIG, "starting TaskService configuration");

        if (transactionProxy == null)
            throw new NullPointerException("null proxy not allowed");

        // keep track of the proxy and the data service
        TaskServiceImpl.transactionProxy = transactionProxy;
        dataService = serviceRegistry.getComponent(DataService.class);

        // fetch the map of pending tasks, or create it if it doesn't
        // already exist (which only happens if this service has never been
        // used before)
        PendingMap pmap = null;
        try {
            pmap = dataService.
                getServiceBinding(DS_PENDING_TASKS, PendingMap.class);
            logger.log(Level.CONFIG, "found existing pending map");
        } catch (NameNotBoundException nnbe) {
            logger.log(Level.CONFIG, "creating new pending map");
            pmap = new PendingMap();
            try {
                dataService.setServiceBinding(DS_PENDING_TASKS, pmap);
            } catch (RuntimeException re) {
                if (logger.isLoggable(Level.SEVERE))
                    logger.log(Level.SEVERE, "failed to bind pending map", re);
                throw re;
            }
        }

        // if there are no pending tasks then we're finished configuring,
        // so join the transaction only so we can set our isConfigured flag
        // on commit
        if (pmap.isEmpty()) {
            transactionProxy.getCurrentTransaction().join(this);
            return;
        }

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.INFO, "re-scheduling {0} pending tasks",
                       pmap.size());

        // re-start all of the pending tasks
        // FIXME: The one missing element right now is how to reconstruct
        // the task owner correctly...this will be fixed when the identity
        // and login component is added. Until then, the code below correctly
        // re-schedules all tasks, but those tasks can't run because they
        // have no valid context
        for (Entry<String,PendingTask> entry : pmap.entrySet()) {
            PendingTask task = entry.getValue();
            TaskRunner runner = new TaskRunner(this, entry.getKey());
            if (task.period == PERIOD_NONE) {
                // this is a non-periodic task
                scheduleTask(runner, null, task.startTime, defaultPriority);
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
                    taskScheduler.scheduleRecurringTask(runner, null,
                                                        startTime, period);

                // keep track of the handle
                TxnState txnState = getTxnState();
                if (txnState.recurringMap == null)
                    txnState.recurringMap =
                        new HashMap<String,RecurringTaskHandle>();
                txnState.recurringMap.put(entry.getKey(), handle);
            }
        }
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
                logger.log(Level.WARNING, "weren't prepared for txn:{0}", txn);
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
        if (txnState.cancelledSet != null)
            for (String objName : txnState.cancelledSet)
                recurringMap.remove(objName).cancel();

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
        scheduleTask(task, transactionProxy.getCurrentOwner(), START_NOW,
                     defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task, long delay) {
        scheduleTask(task, transactionProxy.getCurrentOwner(),
                     System.currentTimeMillis() + delay, defaultPriority);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task,
                                       Priority priority) {
        scheduleTask(task, transactionProxy.getCurrentOwner(), START_NOW,
                     priority);
    }

    /**
     * Private helper that creates a <code>KernelRunnable</code> for the
     * associated task, also generating a unique name for this task, taking
     * care of persisting the task if that hasn't already been done, and
     * tracking the task in the map of pending tasks.
     */
    private TaskRunner getRunner(Task task, long startTime, long period) {
        String objName = generateTaskName();

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "creating pending task {0}", objName);

        PendingTask ptask = new PendingTask();
        ptask.startTime = startTime;
        ptask.period = period;

        // if the Task is also a ManagedObject then the assumption is that
        // object was already managed by the application so we just keep a
        // reference...otherwise, we make it part of our state, which has
        // the effect of persisting the task in the map
        if (task instanceof ManagedObject)
            ptask.taskRef = dataService.createReference((ManagedObject)task); 
        else
            ptask.task = task;

        // remember the task in our pending map
        PendingMap pmap =
            dataService.getServiceBinding(DS_PENDING_TASKS, PendingMap.class);
        dataService.markForUpdate(pmap);
        pmap.put(objName, ptask);

        return new TaskRunner(this, objName);
    }

    /**
     * Private helper that generates a unique name for a task. Note that this
     * is a separate method so that it's easier to change the implementation
     * when we support a multi-stack naming scheme.
     */
    private String generateTaskName() {
        return String.valueOf(nameGenerator.getAndIncrement());
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
                logger.log(Level.FINE, "couldn't get a reservation", tre);
            throw tre;
        }

        // keep track of the reservation in our local transaction state
        txnState.reservationSet.add(reservation);
    }

    /**
     * Private helper that fetches the task associated with the given name. If
     * this is a non-periodic task, then the task is also removed from the
     * managed map of pending tasks. This method is typically used when a
     * task actually runs.
     */
    private PendingTask fetchPendingTask(String objName) {
        // get the task from the pending map
        PendingMap pmap =
            dataService.getServiceBinding(DS_PENDING_TASKS, PendingMap.class);
        PendingTask ptask = pmap.get(objName);

        if (ptask == null) {
            if (logger.isLoggable(Level.WARNING))
                logger.log(Level.WARNING, "couldn't fetch task {0}", objName);
            throw new ObjectNotFoundException("Unknown task: " + objName);
        }

        // if it's not periodic, remove it from the map
        if (ptask.period == PERIOD_NONE) {
            dataService.markForUpdate(pmap);
            pmap.remove(objName);
        }

        return ptask;
    }

    /**
     * Private helper that notifies the service about a task that failed
     * and is not being re-tried. This happens whenever a task is run by
     * the scheduler, and throws an exception that doesn't requst the
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
        // rare case, and at wost it simply causes a task to be scheduled
        // that will have no effect once run (because fetchPendingTask will
        // look at the pending task data, see that it's recurring, and
        // leave it in the map)
        if (recurringMap.containsKey(objName))
            return;
        
        TransactionRunner transactionRunner =
            new TransactionRunner(new KernelRunnable() {
                public void run() throws Exception {
                    fetchPendingTask(objName);
                }
            });

        try {
            // FIXME: decide if this is the system's task or the user's
            taskScheduler.scheduleTask(transactionRunner, null);
        } catch (TaskRejectedException tre) {
            if (logger.isLoggable(Level.WARNING))
                logger.log(Level.WARNING, "couldn't schedule task to remove " +
                           "non-retried task {0}: giving up", tre, objName);
            throw tre;
        }
    }

    /**
     * Private helper that cancels a periodic task. This is only ever called
     * by <code>PeriodicTaskHandleImpl</code>. This method cancels the
     * underlying recurring task, removes the task from the pending map, and
     * notes the cancelled task in the local transaction state so the handle
     * can be removed from the local map at commit.
     */
    private void cancelPeriodicTask(String objName) {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "cancelling periodic task {0}", objName);

        TxnState txnState = getTxnState();
        PendingMap pmap =
            dataService.getServiceBinding(DS_PENDING_TASKS, PendingMap.class);

        if (! pmap.containsKey(objName)) {
            // either some one else cancelled this already, or it was only
            // just created in this transaction
            if ((txnState.recurringMap != null) &&
                (txnState.recurringMap.containsKey(objName))) {
                txnState.recurringMap.remove(objName).cancel();
            } else {
                throw new ObjectNotFoundException("task is already cancelled");
            }
        } else {
            if (txnState.cancelledSet == null)
                txnState.cancelledSet = new HashSet<String>();
            txnState.cancelledSet.add(objName);
        }

        dataService.markForUpdate(pmap);
        pmap.remove(objName);
    }

    /**
     * Inner class that is used to track state associated with a single
     * transaction. This is indexed in the local transaction map.
     */
    private class TxnState {
        public boolean prepared = false;
        public HashSet<TaskReservation> reservationSet = null;
        public HashMap<String,RecurringTaskHandle> recurringMap = null;
        public HashSet<String> cancelledSet = null;
    }

    /**
     * Nested class used to manage the pending tasks in a managed object.
     */
    private static class PendingMap extends HashMap<String,PendingTask>
        implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1;
    }

    /**
     * Nested class that represents a single pending task in the managed map.
     */
    private static class PendingTask implements Serializable {
        private static final long serialVersionUID = 1;
        public Task task = null;
        public ManagedReference taskRef = null;
        public long startTime = START_NOW;
        public long period = PERIOD_NONE;
        // FIXME: this will need to include details about the owner as well,
        // which aren't ready until the login/auth component is defined
        Task getTask() {
            return (task != null) ? task : ((Task)(taskRef.get()));
        }
    }

    /**
     * Private implementation of <code>KernelRunnable</code> that is used to
     * run the <code>Task</code>s scheduled by the application.
     */
    private class TaskRunner implements KernelRunnable {
        private final TaskServiceImpl taskService;
        private final String objName;
        public TaskRunner(TaskServiceImpl taskService, String objName) {
            this.taskService = taskService;
            this.objName = objName;
        }
        String getObjName() {
            return objName;
        }
        public void run() throws Exception {
            try {
                // run the task in a transactional context
                (new TransactionRunner(new KernelRunnable() {
                        public void run() throws Exception {
                            PendingTask ptask =
                                taskService.fetchPendingTask(objName);
                            if (logger.isLoggable(Level.FINEST))
                                logger.log(Level.FINEST, "running task {0} " +
                                           "scheduled to run at {1}",
                                           ptask.startTime, objName);
                            ptask.getTask().run();
                        }
                    })).run();
            } catch (Exception e) {
                // catch exceptions just before they go back to the scheduler
                // to see if the task will be re-tried...if not, then we need
                // notify the service
                if ((! (e instanceof ExceptionRetryStatus)) ||
                    (! ((ExceptionRetryStatus)e).shouldRetry()))
                    taskService.notifyNonRetry(objName);
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
        private final String objName;
        private boolean cancelled = false;
        PeriodicTaskHandleImpl(String objName) {
            this.objName = objName;
        }
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
