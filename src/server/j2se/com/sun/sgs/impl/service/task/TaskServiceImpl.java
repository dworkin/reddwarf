
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
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;

import java.io.Serializable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
    static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(NAME));

    static final Level LOG_OLD_TASK_CLEANUP = Level.FINE;

    /**
     * The name prefix used to bind all service-level objects associated
     * with this service.
     */
    public static final String DS_PREFIX = NAME + ".";

    // the name prefix of the maps that store pending tasks persistently.
    // NOTE: there are MAX_PT_MAPS number of these to reduce contention;
    // which map is used is computed based on the object's id.
    // TODO: may not be the best approach in multi-stack; could be
    // wise to include some knowledge of which node the object was
    // created on (two-level data structure?) -JM
    private static final String DS_PENDING_TASK_MAPS =
	DS_PREFIX + "PendingTaskMaps.";
    
    private static final String DS_PENDING_TASKS =
	DS_PREFIX + "PendingTasks.";
    
    // the number of PendingTask maps to use
    private static final int MAX_PENDING_TASK_MAPS = 1;

    // the internal value used to represent a task with no delay
    private static final long START_NOW = 0;

    // the internal value used to represent a task that does not repeat
    private static final long PERIOD_NONE = -1;

    // flags indicating that configuration has been done successfully,
    // and that we're still in the process of configuring
    private boolean isConfigured = false;
    private boolean isConfiguring = false;

    // the system's task scheduler, where tasks actually run
    TaskScheduler taskScheduler = null;

    // a proxy providing access to the transaction state
    static TransactionProxy transactionProxy = null;

    // the data service used in the same context
    DataService dataService = null;

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
     * @param properties startup properties
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
                          TransactionProxy txnProxy) {
        if (isConfigured)
            throw new IllegalStateException("Task Service already configured");
        isConfiguring = true;

        logger.log(Level.CONFIG, "starting TaskService configuration");

        if (txnProxy == null)
            throw new NullPointerException("null proxy not allowed");

        // keep track of the proxy and the data service
        TaskServiceImpl.transactionProxy = txnProxy;
        dataService = serviceRegistry.getComponent(DataService.class);

        int pendingTaskCount = 0;
        long nextTaskId = 0;
        // fetch the maps of pending tasks, or create them if they don't
        // already exist (which only happens if this service has never been
        // used before)
        // TODO: record and examine the number of maps used during the
        // previous run, and handle things correctly if that number changes.
        for (int i = 0; i < MAX_PENDING_TASK_MAPS; ++i) {
            try {
                PendingMap pmap = dataService.getServiceBinding(
            	    getPmapKey(i), PendingMap.class);
                pendingTaskCount += pmap.size();
                nextTaskId = Math.max(nextTaskId, pmap.nextId);
                logger.log(Level.FINEST, "found existing pending map {0}", i);
            } catch (NameNotBoundException nnbe) {
                logger.log(Level.FINEST, "creating new pending map {0}", i);
                PendingMap pmap = new PendingMap();
                try {
                    dataService.setServiceBinding(getPmapKey(i), pmap);
                } catch (RuntimeException re) {
                    if (logger.isLoggable(Level.SEVERE))
                        logger.logThrow(Level.SEVERE, re, 
                    	    "failed to bind pending map {0}", i);
                    throw re;
                }
            }
        }

        // reset the name generator
        nameGenerator = new AtomicLong(nextTaskId);

        // if there are no pending tasks then we're finished configuring,
        // so join the transaction only so we can set our isConfigured flag
        // on commit
        if (pendingTaskCount == 0) {
            txnProxy.getCurrentTransaction().join(this);
            return;
        }

        if (logger.isLoggable(Level.INFO))
            logger.log(Level.INFO, "processing {0} pending tasks",
        	    pendingTaskCount);
        
        pendingTaskCount = 0;
        
        List<String> logOldTasks = new LinkedList<String>();

        // re-start all of the pending tasks
        for (int i = 0; i < MAX_PENDING_TASK_MAPS; ++i) {
            PendingMap pmap = dataService.getServiceBinding(
        	    getPmapKey(i), PendingMap.class);
            for (Iterator<Entry<String,ManagedReference>>
            	 iter = pmap.entrySet().iterator(); iter.hasNext(); )
            {
        	final Entry<String,ManagedReference> entry = iter.next();
        	final String objName = entry.getKey();
        	final ManagedReference taskRef = entry.getValue();
        	PendingTask task;
        	try {
        	    task = taskRef.get(PendingTask.class);
        	} catch (ObjectNotFoundException onfe) {
        	    if (logger.isLoggable(LOG_OLD_TASK_CLEANUP)) {
        		logOldTasks.add(objName);
        	    }
        	    dataService.markForUpdate(pmap);
        	    iter.remove();
        	    try {
        		dataService.removeServiceBinding(getTaskKey(objName));
        	    } catch (NameNotBoundException nnbe) { /* ignore */ }
        	    continue;
        	}
        	pendingTaskCount++;
                TaskRunner runner = new TaskRunner(this, objName);
                TaskOwner owner =
                    new TaskOwnerImpl(task.getIdentity(), txnProxy.
                                      getCurrentOwner().getContext());
                if (task.getPeriod() == PERIOD_NONE) {
                    // this is a non-periodic task
                    scheduleTask(runner, owner, task.getStartTime(), defaultPriority);
                } else {
                    // this is a periodic task
                    long startTime = task.getStartTime();
                    long now = System.currentTimeMillis();
                    long period = task.getPeriod();
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
                    txnState.recurringMap.put(objName, handle);
                }
            }
        }
        
        if (logger.isLoggable(LOG_OLD_TASK_CLEANUP)) {
	    Collections.sort(logOldTasks);
            logger.log(LOG_OLD_TASK_CLEANUP,
        	    "Removing old tasks: {0}",
        	    logOldTasks);
        }

        if (logger.isLoggable(Level.INFO))
            logger.log(Level.INFO, "rescheduled {0} pending tasks",
        	    pendingTaskCount);
    }
    
    /** {@inheritDoc} */
    public void shutdownTransactional() {
	// cancelAllTasks();
	cleanupPendingTasks();
    }
    
    void cleanupPendingTasks() {
	for (int i = 0; i < MAX_PENDING_TASK_MAPS; ++i) {
	    cleanupPendingTasks(i);
	}
    }
    
    void cleanupPendingTasks(int pmapNum) {
        PendingMap pmap = dataService.getServiceBinding(
    	    getPmapKey(pmapNum), PendingMap.class);
        for (Iterator<Entry<String,ManagedReference>>
             iter = pmap.entrySet().iterator(); iter.hasNext(); )
        {
            final Entry<String,ManagedReference> entry = iter.next();
            final String objName = entry.getKey();
            final ManagedReference taskRef = entry.getValue();
            try {
        	taskRef.get(PendingTask.class);
            } catch (ObjectNotFoundException onfe) {
        	dataService.markForUpdate(pmap);
        	iter.remove();
        	try {
        	    dataService.removeServiceBinding(getTaskKey(objName));
        	} catch (NameNotBoundException nnbe) { /* ignore */ }
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
            logger.log(Level.WARNING, "weren''t prepared for txn: {0}", txn);
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
                       "at {0,date,yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ}", startTime);

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

        Identity owner = transactionProxy.getCurrentOwner().getIdentity();

        PendingTask ptask;
        if (task instanceof ManagedObject) {
            ManagedReference taskRef =
        	dataService.createReference((ManagedObject)task);
	    ptask = PendingTask.create(taskRef, startTime, period, owner);
        } else {
            ptask = PendingTask.create(task, startTime, period, owner);
        }

        // get the next id, which we turn into a name
        long nextId = nameGenerator.getAndIncrement();
        String objName = getTaskName(ptask, nextId);
        dataService.setServiceBinding(getTaskKey(objName), ptask);

        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "creating pending task {0}", objName);
        
        // remember the task in a pending map, and also store a safe value
        // for nextId so we can start there when the system comes back up
        PendingMap pmap = getPmapForName(objName);
        dataService.markForUpdate(pmap);
        ManagedReference ptaskRef = dataService.createReference(ptask);
        pmap.put(objName, ptaskRef);
        pmap.nextId = nextId + MAX_PENDING_TASK_MAPS;

        return new TaskRunner(this, objName);
    }

    private PendingMap getPmapForName(String name) {
	long id = getTaskIdFromName(name);
	return getPmapForId(id);
	
    }
    
    private String getTaskName(PendingTask ptask, long id) {
        return String.valueOf(id);
    }
    
    private long getTaskIdFromName(String name) {
	return Long.parseLong(name);
    }
    
    private String getTaskKey(String objName) {
	return DS_PENDING_TASKS + objName;
    }
    
    private String getPmapKey(int pmapNum) {
	return DS_PENDING_TASK_MAPS + pmapNum;
    }

    private PendingMap getPmapForId(long id) {
	String pmapName = getPmapKey((int) (id % MAX_PENDING_TASK_MAPS));
        return dataService.getServiceBinding(pmapName, PendingMap.class);
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
                       (startTime == START_NOW ? "immediate" :
                	   "at {0,date,yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ}"),
                	   startTime);

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
            logger.logThrow(Level.FINE, tre, "couldn''t get a reservation");
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
    PendingTask fetchPendingTask(String objName) {
	try {
	    // get the task from the datastore
	    PendingTask ptask =
		dataService.getServiceBinding(getTaskKey(objName),
			PendingTask.class);

	    // if it's not periodic, remove it
	    if (ptask.getPeriod() == PERIOD_NONE) {
		removePendingTask(ptask);
	    }
	    
	    return ptask;
	    
	} catch (NameNotBoundException e) {
	} catch (ObjectNotFoundException e) {
        }
	// It's been deleted; let this runner end.
	return null;
    }

    void removePendingTask(String objName) {
	removePendingTask(dataService.getServiceBinding(
		getTaskKey(objName), PendingTask.class));
    }
    
    void removePendingTask(PendingTask ptask) {
	dataService.removeObject(ptask);
	// TODO: put the name onto a queue and clean out the PendingMap
	// and datastore name bindings on some interval.  Already done
	// on startup. -JM
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
    void notifyNonRetry(final String objName) {

        // check if the task is in the recurring map, in which case we don't
        // do anything else, because we don't remove recurring tasks except
        // when they're cancelled...note that this may yield a false negative,
        // because in another transaction the task may have been cancelled and
        // therefore already removed from this map, but this is an extremely
        // rare case, and at worst it simply causes a task to be scheduled
        // that will have no effect once run (because fetchPendingTask will
        // look at the pending task data, see that it's recurring, and
        // leave it in the map)
        if (recurringMap.containsKey(objName)) {
            if (logger.isLoggable(Level.INFO))
                logger.log(Level.INFO, "skipping this run of periodic task {0}",
                           objName);
            return;
        }

        if (logger.isLoggable(Level.INFO))
            logger.log(Level.INFO, "removing non-retried task {0}",
                       objName);
        
        TransactionRunner taskRemover =
            new TransactionRunner(new KernelRunnable() {
                public void run() throws Exception {
                    removePendingTask(objName);
                }
            });

        try {
            taskScheduler.scheduleTask(taskRemover,
                                       transactionProxy.getCurrentOwner());
        } catch (TaskRejectedException tre) {
        	logger.logThrow(Level.WARNING, tre,
        			"couldn''t schedule task to remove " +
        			"non-retried task {0}: giving up", objName);
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
    void cancelPeriodicTask(String objName) {
        if (logger.isLoggable(Level.FINEST))
            logger.log(Level.FINEST, "cancelling periodic task {0}", objName);

        TxnState txnState = getTxnState();
        
        try {
            PendingTask ptask = fetchPendingTask(objName);

            if (txnState.cancelledSet == null)
                txnState.cancelledSet = new HashSet<String>();
            txnState.cancelledSet.add(objName);
            
            removePendingTask(ptask);
        } catch (ObjectNotFoundException e) {
            // either some one else cancelled this already, or it was only
            // just created in this transaction
            if ((txnState.recurringMap != null) &&
                (txnState.recurringMap.containsKey(objName))) {
                txnState.recurringMap.remove(objName).cancel();
            } else {
                throw new ObjectNotFoundException("task is already cancelled");
            }
        }
    }

    /**
     * Inner class that is used to track state associated with a single
     * transaction. This is indexed in the local transaction map.
     */
    static class TxnState {
        public boolean prepared = false;
        public HashSet<TaskReservation> reservationSet = null;
        public HashMap<String,RecurringTaskHandle> recurringMap = null;
        public HashSet<String> cancelledSet = null;
    }

    /**
     * Nested class used to manage the pending tasks in a managed object.
     * The <code>nextId</code> field is used to start the object name
     * atomic long when the stack starts up again.
     */
    static class PendingMap extends HashMap<String,ManagedReference>
        implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1;
        public long nextId = 0;
    }

    /**
     * Nested class that represents a single pending task in the managed map.
     */
    static abstract class PendingTask implements Serializable, ManagedObject {
        private static final long serialVersionUID = 1;
        private final long startTime;
        private final long period;
        private final Identity identity;
        static PendingTask create(Task task, long startTime, long period, Identity identity) {
            return new PendingDirectTask(task, startTime, period, identity);
        }
        static PendingTask create(ManagedReference taskRef, long startTime, long period, Identity identity) {
            return new PendingManagedTask(taskRef, startTime, period, identity);
        }
        protected PendingTask(long startTime, long period, Identity identity) {
            this.startTime = startTime;
            this.period = period;
            this.identity = identity;
        }
        abstract Task getTask();
	Identity getIdentity() { return identity; }
	long getPeriod()       { return period; }
	long getStartTime()    { return startTime; }
    }

    static class PendingDirectTask extends PendingTask {
        private static final long serialVersionUID = 1;
	private final Task task;
	protected PendingDirectTask(Task task, long startTime, long period, Identity identity) {
	    super(startTime, period, identity);
	    this.task = task;
	}
        Task getTask() { return task; }
    }

    static class PendingManagedTask extends PendingTask {
        private static final long serialVersionUID = 1;
        private final ManagedReference taskRef;
	protected PendingManagedTask(ManagedReference taskRef, long startTime, long period, Identity identity) {
	    super(startTime, period, identity);
	    this.taskRef = taskRef;
	}
        Task getTask() {
            try {
        	return taskRef.get(Task.class);
            } catch (ObjectNotFoundException e) {
        	return null;
            }
        }
    }

    /**
     * Private implementation of <code>KernelRunnable</code> that is used to
     * run the <code>Task</code>s scheduled by the application.
     */
    static class TaskRunner implements KernelRunnable {
        final TaskServiceImpl taskService;
        final String objName;
	final KernelRunnable taskRunner;
        TaskRunner(TaskServiceImpl aTaskService, String anObjName) {
            this.taskService = aTaskService;
            this.objName = anObjName;
            this.taskRunner = (new TransactionRunner(new KernelRunnable() {
                public void run() throws Exception {
                    PendingTask pendingTask = taskService.fetchPendingTask(objName);
                    if (pendingTask == null) {
                	return;
                    }
                    if (logger.isLoggable(Level.FINEST))
                        logger.log(Level.FINEST, "running task {0} " +
                                   "scheduled to run " +
                                   (pendingTask.getStartTime() == START_NOW ? "immediate" :
                        	   "at {1,date,yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ}"),
                        	   objName, pendingTask.getStartTime());
                    Task task = pendingTask.getTask();
                    if (task == null) {
                	// cancel this PendingTask
                	taskService.removePendingTask(pendingTask);
                	return;
                    }
                    task.run();
                }
            }));
        }
        String getObjName() {
            return objName;
        }
        public void run() throws Exception {
            try {
                // run the task in a transactional context
                taskRunner.run();
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
        private static final long serialVersionUID = 1;
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
