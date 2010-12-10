/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.task;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.RunWithNewIdentity;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.app.util.ScalableHashSet;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import com.sun.sgs.profile.ProfileCollector;

import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeMappingListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.UnknownIdentityException;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.service.task.ContinuePolicy;

import java.io.Serializable;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMException;


/**
 * This is an implementation of {@code TaskService} that works on a
 * single node and across multiple nodes. It handles persisting tasks and
 * keeping track of which tasks have not yet run to completion, so that in
 * the event of a system failure the tasks can be run on re-start.
 * <p>
 * Durable tasks that have not yet run are persisted as instances of
 * {@code PendingTask}, indexed by the owning identity. When a given identity
 * is mapped to the local node, all tasks associated with that identity are
 * started running on the local node. As long as an identity still has pending
 * tasks scheduled to run locally, that identity is marked as active. To
 * help minimize object creation, a cache of {@code PendingTask}s is created
 * for each identity.
 * <p>
 * When an identity is moved from the local node to a new node, then all
 * recurring tasks for that identity are cancelled, and all tasks for that
 * identity are re-scheduled on the identity's new node. When an
 * already-scheduled, persisted task tries to run on the old node, that task
 * is dropped since it is already scheduled to run on the new node. After an
 * identity has been moved, any subsequent attempts to schedule durable tasks
 * on behalf of that identity on the old node will result in the tasks being
 * scheduled to run on the new node. This is called task handoff.
 * <p>
 * Task handoff between nodes is done by noting the task in a node-specific
 * entry in the data store. Each node will periodically query this entry to
 * see if any tasks have been handed off. The time in milliseconds for this
 * period is configurable via the {@value #HANDOFF_PERIOD_PROPERTY} property
 * described below. This checking
 * will be delayed on node startup to give the system a chance to finish
 * initializing. The time in milliseconds for this delay is configurable via
 * the {@value #HANDOFF_START_PROPERTY} property.
 * <p>
 * When the final task for an identity completes, or an initial task for an
 * identity is scheduled, the status of that identity as reported by this
 * service changes. Rather than immediately reporting this status change,
 * however, a delay is taken to see if the status is about to change back to
 * its previous state. This helps avoid voting too frequently. The time in
 * milliseconds for delaying this vote is configurable via the
 * {@value #VOTE_DELAY_PROPERTY} property.
 * <p>
 * The {@code TaskServiceImpl} supports the following configuration properties,
 * some of which have already been mentioned above:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #HANDOFF_PERIOD_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #HANDOFF_PERIOD_DEFAULT}
 *
 * <dd style="padding-top: .5em">Specifies the periodic time in milliseconds
 *      that the {@code TaskServiceImpl} will regularly query its handoff
 *      queue for tasks that have been handed off by other nodes.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #HANDOFF_START_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #HANDOFF_START_DEFAULT}
 *
 * <dd style="padding-top: .5em">Specifies the time in milliseconds that the
 *      {@code TaskServiceImpl} will wait at startup before querying its
 *      handoff queue for the first time.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #VOTE_DELAY_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #VOTE_DELAY_DEFAULT}
 *
 * <dd style="padding-top: .5em">Specifies the time in milliseconds to wait
 *      before reporting a status change for an identity.  The status change
 *      is not reported if the identity's status changes back to its original
 *      state during this delay period.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #CONTINUE_POLICY_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #CONTINUE_POLICY_DEFAULT}
 *
 * <dd style="padding-top: .5em">Specifies the fully qualified class name of
 *      the class which will be used as the {@link ContinuePolicy} for the task
 *      service.  The given class should be a non-abstract class that implements
 *      the {@code ContinuePolicy} interface, and that provides a
 *      constructor with the parameters ({@link Properties},
 *      {@link ComponentRegistry}, {@link TransactionProxy})<p>
 *
 *
 * </dl> <p>
 */
public class TaskServiceImpl 
        extends AbstractService
        implements TaskService, NodeMappingListener, RecoveryListener 
{

    /**
     * The identifier used for this {@code Service}.
     */
    public static final String NAME = 
        "com.sun.sgs.impl.service.task.TaskServiceImpl";

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

    /** The name of the version key. */
    private static final String VERSION_KEY = NAME + ".service.version";

    /** The major version. */
    private static final int MAJOR_VERSION = 1;
    
    /** The minor version. */
    private static final int MINOR_VERSION = 0;
    
    // the transient set of identities known to be active on the current node,
    // and how many tasks are pending for that identity
    private HashMap<Identity, Integer> activeIdentityMap;

    // the transient set of identities thought to be mapped to this node
    private HashSet<Identity> mappedIdentitySet;

    // a timer used to delay status votes
    private final Timer statusUpdateTimer;

    /** The property key to set the delay in milliseconds for status votes. */
    public static final String VOTE_DELAY_PROPERTY =
        NAME + ".vote.delay";

    /** The default delay in milliseconds for status votes. */
    public static final long VOTE_DELAY_DEFAULT = 5000L;

    // the length of time to delay status votes
    private final long voteDelay;

    // a map of any pending status change timer tasks
    private ConcurrentHashMap<Identity, TimerTask> statusTaskMap;

    // the base namespace where all tasks are handed off (this name is always
    // followed by the recipient node's identifier)
    private static final String DS_HANDOFF_SPACE = DS_PREFIX + "Handoff.";

    // the local node's hand-off namespace
    private final String localHandoffSpace;

    /** The property key to set the delay before hand-off checking starts. */
    public static final String HANDOFF_START_PROPERTY =
        NAME + ".handoff.start";

    /** The default delay in milliseconds before hand-off checking starts. */
    public static final long HANDOFF_START_DEFAULT = 2500L;

    // the actual amount of time to wait before hand-off checking starts
    private final long handoffStart;

    /** The property key to set how long to wait between hand-off checks. */
    public static final String HANDOFF_PERIOD_PROPERTY =
        NAME + ".handoff.period";

    /** The default length of time in milliseconds to wait between hand-off
        checks. */
    public static final long HANDOFF_PERIOD_DEFAULT = 500L;

    // the actual amount of time to wait between hand-off checks
    private final long handoffPeriod;

    // a handle to the periodic hand-off task
    private RecurringTaskHandle handoffTaskHandle = null;

    /**
     * The property key to specify which class to use as the continue policy.
     */
    public static final String CONTINUE_POLICY_PROPERTY =
            NAME + ".continue.policy";

    /** The default continue policy. */
    public static final String CONTINUE_POLICY_DEFAULT =
            "com.sun.sgs.impl.service.task.FixedTimeContinuePolicy";

    // the actual continue policy
    private final ContinuePolicy continuePolicy;

    // the internal value used to represent a task with no delay
    static final long START_NOW = -1;

    // the internal value used to represent a task that does not repeat
    static final long PERIOD_NONE = -1;

    // the internal value used to represent a periodic task that has never been
    // run
    static final long NEVER = -1;

    // the identifier for the local node
    private final long nodeId;

    // the mapping service used in the same context
    private final NodeMappingService nodeMappingService;

    // the watchdog service
    private final WatchdogService watchdogService;

    // the factory used to manage transaction state
    private final TransactionContextFactory<TxnState> ctxFactory;

    // the transient map for all recurring tasks' handles
    private ConcurrentHashMap<BigInteger, RecurringDetail> recurringMap;

    // the transient map for all recurring handles based on identity
    private HashMap<Identity, Set<RecurringTaskHandle>> identityRecurringMap;

    // the transient map for available pending task entries...note that
    // while this map is concurrent, the individual sets need to have
    // all access synchronized
    private final ConcurrentHashMap<Identity, Set<BigInteger>>
        availablePendingMap;

    // the profiled operations
    private final TaskServiceStats serviceStats;
    
    /**
     * Creates an instance of {@code TaskServiceImpl}. See the class javadoc
     * for applicable properties.
     *
     * @param properties application properties
     * @param systemRegistry the registry of system components
     * @param transactionProxy the system's {@code TransactionProxy}
     *
     * @throws Exception if the service cannot be created
     */
    public TaskServiceImpl(Properties properties,
                           ComponentRegistry systemRegistry,
                           TransactionProxy transactionProxy)
        throws Exception
    {
        super(properties, systemRegistry, transactionProxy, logger);
        logger.log(Level.CONFIG, "Creating TaskServiceImpl");

        // create the transient local collections
        activeIdentityMap = new HashMap<Identity, Integer>();
        mappedIdentitySet = new HashSet<Identity>();
        statusTaskMap = new ConcurrentHashMap<Identity, TimerTask>();
        recurringMap = new ConcurrentHashMap<BigInteger, RecurringDetail>();
        identityRecurringMap =
                new HashMap<Identity, Set<RecurringTaskHandle>>();
        availablePendingMap =
                new ConcurrentHashMap<Identity, Set<BigInteger>>();

        // create the factory for managing transaction context
        ctxFactory = new TransactionContextFactoryImpl(txnProxy);

        // keep a reference to the other Services that are needed
        nodeMappingService = txnProxy.getService(NodeMappingService.class);
        
        // note that the application is always active locally, so there's
        // no chance of voting the application as inactive
        activeIdentityMap.put(taskOwner, 1);

        // register for identity mapping updates
        nodeMappingService.addNodeMappingListener(this);

        /*
         * Check service version.
         */
        transactionScheduler.runTask(new KernelRunnable() {
            public String getBaseTaskType() {
                return NAME + ".VersionCheckRunner";
            }
            public void run() {
                checkServiceVersion(
                        VERSION_KEY, MAJOR_VERSION, MINOR_VERSION);
            } }, taskOwner);
                
        // get the current node id for the hand-off namespace, and register
        // for recovery notices to manage cleanup of hand-off bindings
        watchdogService = txnProxy.getService(WatchdogService.class);
        nodeId = dataService.getLocalNodeId();
        localHandoffSpace = DS_HANDOFF_SPACE + nodeId;
        watchdogService.addRecoveryListener(this);

        // get the start delay and the length of time between hand-off checks
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        handoffStart = wrappedProps.getLongProperty(HANDOFF_START_PROPERTY,
                                                    HANDOFF_START_DEFAULT);
        if (handoffStart < 0) {
            throw new IllegalStateException("Handoff Start property must " +
                                            "be non-negative");
        }
        handoffPeriod = wrappedProps.getLongProperty(HANDOFF_PERIOD_PROPERTY,
                                                     HANDOFF_PERIOD_DEFAULT);
        if (handoffPeriod < 0) {
            throw new IllegalStateException("Handoff Period property must " +
                                            "be non-negative");
        }

        // get the continue policy
        continuePolicy = wrappedProps.getClassInstanceProperty(
                CONTINUE_POLICY_PROPERTY,
                CONTINUE_POLICY_DEFAULT,
                ContinuePolicy.class, new Class[]{Properties.class,
                                                  ComponentRegistry.class,
                                                  TransactionProxy.class},
                properties, systemRegistry, txnProxy);

        // create our profiling info and register our MBean
        ProfileCollector collector = 
            systemRegistry.getComponent(ProfileCollector.class);
        serviceStats = new TaskServiceStats(collector);
        try {
            collector.registerMBean(serviceStats, TaskServiceStats.MXBEAN_NAME);
        } catch (JMException e) {
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }

        // finally, create a timer for delaying the status votes and get
        // the delay used in submitting status votes
        statusUpdateTimer = new Timer("TaskServiceImpl Status Vote Timer");
        voteDelay = wrappedProps.getLongProperty(VOTE_DELAY_PROPERTY,
                                                 VOTE_DELAY_DEFAULT);
        if (voteDelay < 0) {
            throw new IllegalStateException("Vote Delay property must " +
                                            "be non-negative");
        }

        logger.log(Level.CONFIG,
                   "Created TaskServiceImpl with properties:" +
                   "\n  " + CONTINUE_POLICY_PROPERTY + "=" +
                   continuePolicy.getClass().getName() +
                   "\n  " + HANDOFF_PERIOD_PROPERTY + "=" + handoffPeriod +
                   "\n  " + HANDOFF_START_PROPERTY + "=" + handoffStart +
                   "\n  " + VOTE_DELAY_PROPERTY + "=" + voteDelay);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return NAME;
    }

    /* -- Implement AbstractService -- */

    /** {@inheritDoc} */
    protected void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion)
    {
	throw new IllegalStateException(
	    "unable to convert version:" + oldVersion +
	    " to current version:" + currentVersion);
    }
    
    /**
     * {@inheritDoc}
     */
    public void doReady() {
        logger.log(Level.CONFIG, "readying TaskService");

        // bind the node-local hand-off set, noting that there's a (very
        // small) chance that another node may have already tried to hand-off
        // to us, in which case the set will already exist
        try {
            transactionScheduler.runTask(new KernelRunnable() {
                    public String getBaseTaskType() {
                        return NAME + ".HandoffBindingRunner";
                    }
                    public void run() throws Exception {
                        try {
                            dataService.getServiceBinding(localHandoffSpace);
                        } catch (NameNotBoundException nnbe) {
                            dataService.setServiceBinding(localHandoffSpace,
                                                          new StringHashSet());
                        }
                    }
                }, taskOwner);
        } catch (Exception e) {
            throw new AssertionError("Failed to setup node-local sets");
        }

        // assert that the application identity is active, so that there
        // is always a mapping somewhere for these tasks
        // NOTE: in our current system, there may be a large number of
        // tasks owned by the application (e.g., any tasks started
        // during the application's initialize() method), but hopefully
        // this will change when we add APIs for creating identities
        nodeMappingService.assignNode(getClass(), taskOwner);

        // kick-off a periodic hand-off task, but delay a little while so
        // that the system has a chance to finish setup
        handoffTaskHandle = transactionScheduler.
            scheduleRecurringTask(new HandoffRunner(), taskOwner,
                                  System.currentTimeMillis() + handoffStart,
                                  handoffPeriod);
        handoffTaskHandle.start();

        logger.log(Level.CONFIG, "TaskService is ready");
    }

    /**
     * {@inheritDoc}
     */
    public void doShutdown() {
        // stop the handoff and status processing tasks
        if (handoffTaskHandle != null) {
            handoffTaskHandle.cancel();
        }

        statusUpdateTimer.cancel();
    }

    /**
     * {@inheritDoc}
     */
    public void recover(Node node, SimpleCompletionHandler handler) {
        final long failedNodeId =  node.getId();
        final String handoffSpace = DS_HANDOFF_SPACE + failedNodeId;

        // remove the handoff set and binding for the failed node
        try {
            transactionScheduler.runTask(new KernelRunnable() {
                    public String getBaseTaskType() {
                        return NAME + ".HandoffCleanupRunner";
                    }
                    public void run() throws Exception {
                        StringHashSet set = null;
                        try {    
                            set = (StringHashSet)
				dataService.getServiceBinding(handoffSpace);
                        } catch (NameNotBoundException nnbe) {
                            // this only happens when this recover method
                            // is called more than once, and just means that
                            // this cleanup has already happened, so we can
                            // quietly ignore this case
                            return;
                        }
                        dataService.removeObject(set);
                        dataService.removeServiceBinding(handoffSpace);
                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO, "Cleaned up handoff set " +
                                       "for failed node: " + failedNodeId);
                        }
                   }
                }, taskOwner);
        } catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.logThrow(Level.WARNING, e, "Failed to cleanup handoff " +
                                "set for failed node: " + failedNodeId);
            }
        }

        handler.completed();
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        serviceStats.scheduleTaskOp.report();
        scheduleSingleTask(task, START_NOW);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        serviceStats.scheduleTaskDelayedOp.report();
        long appStartTime = watchdogService.currentAppTimeMillis() + delay;

        if (delay < 0) {
            throw new IllegalArgumentException("Delay must not be negative");
        }

        scheduleSingleTask(task, appStartTime);
    }

    /** Private helper for common scheduling code. */
    private void scheduleSingleTask(Task task, long appStartTime) {
        if (task == null) {
            throw new NullPointerException("Task must not be null");
        }
        if (shuttingDown()) {
            throw new IllegalStateException("Service is shutdown");
        }

        // persist the task regardless of where it will ultimately run
        Identity owner = getTaskOwner(task);
        TaskRunner runner = getRunner(task, owner, appStartTime, PERIOD_NONE);

        // check where the owner is active to get the task running
        if (!isMappedLocally(owner)) {
            if (handoffTask(generateObjName(owner, runner.getObjId()), owner)) {
                return;
            }
            runner.markIgnoreIsLocal();
        }
        scheduleTask(runner, owner, appStartTime, true);
    }

    /**
     * {@inheritDoc}
     */
    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                                   long period)
    {
        serviceStats.scheduleTaskPeriodicOp.report();
        // note the start time
        long appStartTime = watchdogService.currentAppTimeMillis() + delay;

        if (task == null) {
            throw new NullPointerException("Task must not be null");
        }
        if ((delay < 0) || (period < 0)) {
            throw new IllegalArgumentException("Times must not be null");
        }
        if (shuttingDown()) {
            throw new IllegalStateException("Service is shutdown");
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "scheduling a periodic task starting " +
                       "at {0}", appStartTime);
        }

        // persist the task regardless of where it will ultimately run
        Identity owner = getTaskOwner(task);
        TaskRunner runner = getRunner(task, owner, appStartTime, period);
        BigInteger objId = runner.getObjId();

        // check where the owner is active to get the task running
        if (!isMappedLocally(owner)) {
            String objName = generateObjName(owner, objId);
            if (handoffTask(objName, owner)) {
                return new PeriodicTaskHandleImpl(objName);
            }
            runner.markIgnoreIsLocal();
        }
        PendingTask ptask = 
                (PendingTask) (dataService.createReferenceForId(objId).
                getForUpdate());
        ptask.setRunningNode(nodeId);

        RecurringTaskHandle handle =
            transactionScheduler.scheduleRecurringTask(
                runner, owner,
                watchdogService.getSystemTimeMillis(appStartTime), period);
        ctxFactory.joinTransaction().addRecurringTask(objId, handle, owner);
        return new PeriodicTaskHandleImpl(generateObjName(owner, objId));
    }

    /**
     * {@inheritDoc}
     */
    public boolean shouldContinue() {
        return continuePolicy.shouldContinue();
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task,
                                       boolean transactional)
    {
        if (task == null) {
            throw new NullPointerException("Task must not be null");
        }
        if (shuttingDown()) {
            throw new IllegalStateException("Service is shutdown");
        }
        serviceStats.scheduleNDTaskOp.report();

        Identity owner = getTaskOwner(task);
        scheduleTask(new NonDurableTask(task, owner, transactional), owner,
                     START_NOW, false);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleNonDurableTask(KernelRunnable task, long delay,
                                       boolean transactional)
    {
        if (task == null) {
            throw new NullPointerException("Task must not be null");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("Delay must not be negative");
        }
        if (shuttingDown()) {
            throw new IllegalStateException("Service is shutdown");
        }
        serviceStats.scheduleNDTaskDelayedOp.report();

        Identity owner = getTaskOwner(task);
        scheduleTask(new NonDurableTask(task, owner, transactional), owner,
                     watchdogService.currentAppTimeMillis() + delay, false);
    }

    /**
     * Private helper that creates a {@code KernelRunnable} for the task,
     * also generating a unique name for this task and persisting the
     * associated {@code PendingTask}.
     */
    private TaskRunner getRunner(Task task, Identity identity, 
                                 long appStartTime, long period)
    {
        logger.log(Level.FINEST, "setting up a pending task");

        // create a new pending task that will be used when the runner runs
        BigInteger objId =
            allocatePendingTask(task, identity, appStartTime, period);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "created pending task {0} for {1}",
                       objId, identity);
        }

        return new TaskRunner(objId, task.getClass().getName(), identity);
    }

    /** Helper that generates the name for a pending object. */
    private static String generateObjName(Identity owner, BigInteger objId) {
        return DS_PENDING_SPACE + owner.getName() + "." + objId;
    }

    /** Helper that gets the id from a name. */
    private static BigInteger getIdFromName(String objName) {
        return new BigInteger(objName.substring(objName.lastIndexOf('.') + 1));
    }

    /** Helper that allocates a pending task, re-using one if possible. */
    private BigInteger allocatePendingTask(Task task, Identity identity,
                                           long appStartTime, long period)
    {
        PendingTask ptask = null;
        BigInteger objId = null;
        Set<BigInteger> set = availablePendingMap.get(identity);

        // a null set means that the identity isn't mapped here, so the task
        // will get handed-off to another node...since this isn't a common
        // case, just create a new entry (at least for now)

        if (set != null) {
            synchronized (set) {
                if (!set.isEmpty()) {
                    objId = set.iterator().next();
                    set.remove(objId);
                    ctxFactory.joinTransaction().
                        notePendingIdAllocated(identity, objId);
                }
            }
        }

        if (objId != null) {
            // a pending task might be available for re-use, so try
            // to get it but handle the possibility that another node
            // could have removed or re-used this entry already
            try {
                ptask = (PendingTask) (dataService.createReferenceForId(objId).
                        get());
                if (!ptask.isReusable()) {
                    objId = null;
                }
            } catch (ObjectNotFoundException onfe) {
                objId = null;
            }
        }

        if (objId == null) {
            // there was no available pending task, so create one now
            ptask = new PendingTask(identity);
            ManagedReference<PendingTask> taskRef =
                dataService.createReference(ptask);
            objId = taskRef.getId();
            dataService.
                setServiceBinding(generateObjName(identity, objId), ptask);
        }

        ptask.resetValues(task, appStartTime, period);
        
        return objId;
    }

    /**
     * Private helper that handles scheduling a task by getting a reservation
     * from the scheduler. This is used for both the durable and non-durable
     * tasks, but not for periodic tasks.
     */
    private void scheduleTask(KernelRunnable task, Identity owner,
                              long appStartTime, boolean transactional)
    {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "reserving a task starting " +
                                     (appStartTime == START_NOW
                                      ? "now" : "at " + appStartTime));
        }

        // reserve a space for this task
        try {
            TxnState txnState = ctxFactory.joinTransaction();
            TaskReservation res = null;
            // see if this should be scheduled as a task to run now, or as
            // a task to run after a delay, and which scheduler to use
            if (appStartTime == START_NOW) {
                if (transactional) {
                    res = transactionScheduler.reserveTask(task, owner);
                } else {
                    res = taskScheduler.reserveTask(task, owner);
                }
            } else {
                if (transactional) {
                    res = transactionScheduler.reserveTask(
                            task, owner,
                            watchdogService.getSystemTimeMillis(appStartTime));
                } else {
                    res = taskScheduler.reserveTask(
                            task, owner,
                            watchdogService.getSystemTimeMillis(appStartTime));
                }
            }
            txnState.addReservation(res, owner);
        } catch (TaskRejectedException tre) {
            if (logger.isLoggable(Level.FINE)) {
                logger.logThrow(Level.FINE, tre,
                                "could not get a reservation");
            }
            throw tre;
        }
    }

    /**
     * Private helper that fetches the task associated with the given ID. If
     * this is a non-periodic task, then the task is also removed from the
     * managed map of pending tasks. This method is typically used when a
     * task actually runs. If the Task was managed by the application and
     * has been removed by the application, or another TaskService task has
     * already removed the pending task entry, then this method returns null
     * meaning that there is no task to run.
     */
    PendingTask fetchPendingTask(BigInteger objId) {
        PendingTask ptask = null;
        try {
            ptask = (PendingTask) (dataService.createReferenceForId(objId).
                    get());
        } catch (ObjectNotFoundException onfe) {
            // the task was already removed, so check if this is a recurring
            // task, because then we need to cancel it (this may happen if
            // the task was cancelled on a different node than where it is
            // currently running)
            if (recurringMap.containsKey(objId)) {
                ctxFactory.joinTransaction().
                    cancelRecurringTask(objId, txnProxy.getCurrentOwner());
            } else {
                ctxFactory.joinTransaction().
                    decrementStatusCount(txnProxy.getCurrentOwner());
            }
            return null;
        }
        boolean isAvailable = ptask.isTaskAvailable();

        // if it's not periodic then note that the pending task will be
        // available if the transaction commits, checking that this doesn't
        // change the identity's status
        if (!ptask.isPeriodic()) {
            TxnState state = ctxFactory.joinTransaction();
            if (isAvailable) {
                state.noteCurrentIdFreed(objId);
            }
            state.decrementStatusCount(ptask.getIdentity());
        } else {
            // Make sure that the task is still available, because if it's
            // not, then we need to remove the mapping and cancel the task.
            // Note that this should be a very rare case
            if (!isAvailable) {
                cancelPeriodicTask(ptask, objId);
            }
        }

        return isAvailable ? ptask : null;
    }

    /**
     * Private helper that cancels a periodic task. This method cancels the
     * underlying recurring task, removes the task and name binding, and
     * notes the cancelled task in the local transaction state.
     */
    private void cancelPeriodicTask(PendingTask ptask, BigInteger objId) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "cancelling periodic task {0}", objId);
        }

        ctxFactory.joinTransaction().
            cancelRecurringTask(objId, ptask.getIdentity());

        // remove the object rather than allowing it to be re-used to make
        // sure that any outstanding handles don't get confused later
        dataService.
            removeServiceBinding(generateObjName(ptask.getIdentity(),
                                                 objId));
        dataService.removeObject(ptask);
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
    private void notifyNonRetry(final BigInteger objId) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "trying to remove non-retried task {0}",
                       objId);
        }

        // check if the task is in the recurring map, in which case we don't
        // do anything else, because we don't remove recurring tasks except
        // when they're cancelled...note that this may yield a false negative,
        // because in another transaction the task may have been cancelled and
        // therefore already removed from this map, but this is an extremely
        // rare case, and at worst it simply causes a task to be scheduled
        // that will have no effect once run (because fetchPendingTask will
        // look at the pending task data, see that it's recurring, and
        // leave it in the map)
        if (recurringMap.containsKey(objId)) {
            return;
        }
        
        try {
            transactionScheduler.
                scheduleTask(new NonRetryCleanupRunnable(objId),
                             txnProxy.getCurrentOwner());
        } catch (TaskRejectedException tre) {
            // Note that this should (essentially) never happen, but if it
            // does then the pending task will always remain, and this node
            // will never consider this identity as inactive
            if (logger.isLoggable(Level.WARNING)) {
                logger.logThrow(Level.WARNING, tre, "could not schedule " +
                                "task to remove non-retried task {0}: " +
                                "giving up", objId);
            }
            throw tre;
        }
    }

    /**
     * Private helper runnable that cleans up after a non-retried task. See
     * block comment above in notifyNonRetry for more detail.
     */
    private class NonRetryCleanupRunnable implements KernelRunnable {
        private final BigInteger objId;
        NonRetryCleanupRunnable(BigInteger objId) {
            this.objId = objId;
        }
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return NonRetryCleanupRunnable.class.getName();
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            if (shuttingDown()) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "Service is shutdown, so a " +
                               "non-retried task {0} will not be removed",
                               objId);
                }
                throw new IllegalStateException("Service is shutdown");
            }
            PendingTask ptask = fetchPendingTask(objId);
            if ((ptask != null) && (!ptask.isPeriodic())) {
                ptask.setReusable();
            }
        }
    }

    /**
     * Private class that is used to track state associated with a single
     * transaction and handle commit and abort operations.
     */
    private class TxnState extends TransactionContext {
        private HashSet<TaskReservation> reservationSet = null;
        private HashMap<Identity, HashSet<BigInteger>> allocatedTaskIds = null;
        private HashMap<BigInteger, RecurringDetail> addedRecurringMap = null;
        private HashSet<BigInteger> cancelledRecurringSet = null;
        private HashMap<Identity, Integer> statusMap =
                new HashMap<Identity, Integer>();
        private BigInteger currentTaskId = null;
        private Identity currentTaskOwner = null;
        /** Creates context tied to the given transaction. */
        TxnState(Transaction txn) {
            super(txn);
        }
        /** {@inheritDoc} */
        public void commit() {
            // cancel the cancelled periodic tasks...
            if (cancelledRecurringSet != null) {
                for (BigInteger objId : cancelledRecurringSet) {
                    RecurringDetail detail = recurringMap.remove(objId);
                    if (detail != null) {
                        detail.handle.cancel();
                        removeHandleForIdentity(detail.handle, detail.identity);
                        decrementStatusCount(detail.identity);
                    }
                }
            }
            // ...and hand-off any pending status votes
            for (Entry<Identity, Integer> entry : statusMap.entrySet()) {
                int countChange = entry.getValue();
                if (countChange != 0) {
                    submitStatusChange(entry.getKey(), countChange);
                }
            }
            // with the status counts updated, use the reservations...
            if (reservationSet != null) {
                for (TaskReservation reservation : reservationSet) {
                    reservation.use();
                }
            }
            // ... and start the periodic tasks
            if (addedRecurringMap != null) {
                for (Entry<BigInteger, RecurringDetail> entry :
                         addedRecurringMap.entrySet())
                {
                    RecurringDetail detail = entry.getValue();
                    recurringMap.put(entry.getKey(), detail);
                    addHandleForIdentity(detail.handle, detail.identity);
                    detail.handle.start();
                }
            }
            // finally, return the ID of this task if it's now available
            if (currentTaskId != null) {
                Set<BigInteger> set = availablePendingMap.get(currentTaskOwner);
                if (set != null) {
                    synchronized (set) {
                        set.add(currentTaskId);
                    }
                }
            }
        }
        /** {@inheritDoc} */
        public void abort(boolean retryable) {
            // cancel all the reservations for tasks and recurring tasks that
            // were made during the transaction
            if (reservationSet != null) {
                for (TaskReservation reservation : reservationSet) {
                    reservation.cancel();
                }
            }
            if (addedRecurringMap != null) {
                for (RecurringDetail detail : addedRecurringMap.values()) {
                    detail.handle.cancel();
                }
            }
            // return any taken pending tasks
            if (allocatedTaskIds != null) {
                for (Entry<Identity, HashSet<BigInteger>> entry :
                         allocatedTaskIds.entrySet())
                {
                    Set<BigInteger> localSet =
                        availablePendingMap.get(entry.getKey());
                    if (localSet != null) {
                        synchronized (localSet) {
                            localSet.addAll(entry.getValue());
                        }
                    }
                }
            }
        }
        /** Adds a reservation to use at commit-time. */
        void addReservation(TaskReservation reservation, Identity identity) {
            if (reservationSet == null) {
                reservationSet = new HashSet<TaskReservation>();
            }
            reservationSet.add(reservation);
            incrementStatusCount(identity);
        }
        /** Adds a handle to start at commit-time. */
        void addRecurringTask(BigInteger objId, RecurringTaskHandle handle,
                              Identity identity)
        {
            if (addedRecurringMap == null) {
                addedRecurringMap = new HashMap<BigInteger, RecurringDetail>();
            }
            addedRecurringMap.put(objId, new RecurringDetail(handle, identity));
            incrementStatusCount(identity);
        }
        /**
         * Tries to cancel the associated recurring task, recognizing whether
         * the task was scheduled within this transaction or previously.
         */
        void cancelRecurringTask(BigInteger objId, Identity identity) {
            RecurringDetail detail = (addedRecurringMap != null) ?
                addedRecurringMap.remove(objId) : null;
            
            if ((addedRecurringMap == null) || (detail == null)) {
                // the task wasn't created in this transaction, so make
                // sure that it gets cancelled at commit
                if (cancelledRecurringSet == null) {
                    cancelledRecurringSet = new HashSet<BigInteger>();
                }
                cancelledRecurringSet.add(objId);
            } else {
                // the task was created in this transaction, so we just have
                // to make sure that it doesn't start
                detail.handle.cancel();
                decrementStatusCount(identity);
            }
        }
        /** Notes that a task has been added for the given identity. */
        void incrementStatusCount(Identity identity) {
            if (statusMap.containsKey(identity)) {
                statusMap.put(identity, statusMap.get(identity) + 1);
            } else {
                statusMap.put(identity, 1);
            }
        }
        /** Notes that a task has been removed for the given identity. */
        void decrementStatusCount(Identity identity) {
            if (statusMap.containsKey(identity)) {
                statusMap.put(identity, statusMap.get(identity) - 1);
            } else {
                statusMap.put(identity, -1);
            }
        }
        /** Notes that the given id has been allocated to a task. */
        void notePendingIdAllocated(Identity identity, BigInteger objId) {
            if (allocatedTaskIds == null) {
                allocatedTaskIds = new HashMap<Identity, HashSet<BigInteger>>();
            }
            HashSet<BigInteger> set = allocatedTaskIds.get(identity);
            if (set == null) {
                set = new HashSet<BigInteger>();
                allocatedTaskIds.put(identity, set);
            }
            set.add(objId);
        }
        /** Notes the current tasks's id to be freed. */
        void noteCurrentIdFreed(BigInteger objId) {
            assert currentTaskId == null : "The id of the current task " +
                "has already been assigned to be freed on commit";
            currentTaskId = objId;
            currentTaskOwner = txnProxy.getCurrentOwner();
        }
    }

    /** Private implementation of {@code TransactionContextFactory}. */
    private class TransactionContextFactoryImpl
        extends TransactionContextFactory<TxnState>
    {
        /** Creates an instance with the given proxy. */
        TransactionContextFactoryImpl(TransactionProxy proxy) {
            super(proxy, NAME);
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
        private final BigInteger objId;
        private final String objTaskType;
        private final Identity taskIdentity;
        private boolean doLocalCheck = true;
        TaskRunner(BigInteger objId, String objTaskType,
                   Identity taskIdentity)
        {
            this.objId = objId;
            this.objTaskType = objTaskType;
            this.taskIdentity = taskIdentity;
        }
        BigInteger getObjId() {
            return objId;
        }
        /**
         * This method is used in the case where the associated identity is
         * not mapped to the local node, but no assignment exists yet. In
         * these cases, the task is just run locally, so no check should be
         * done to see if the identity is local.
         */
        void markIgnoreIsLocal() {
            doLocalCheck = false;
        }
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return objTaskType;
        }
        /** {@inheritDoc} */
        public void run() throws Exception {
            if (shuttingDown()) {
                return;
            }

            // check that the task's identity is still active on this node,
            // and if not then return, cancelling the task if it's recurring
            if ((doLocalCheck) && (!isMappedLocally(taskIdentity))) {
                RecurringDetail detail = recurringMap.remove(objId);
                if (detail != null) {
                    detail.handle.cancel();
                    removeHandleForIdentity(detail.handle, detail.identity);
                }
                submitStatusChange(taskIdentity, -1);
                return;
            }

            try {
                // fetch the task, making sure that it's available
                PendingTask ptask = fetchPendingTask(objId);
                if (ptask == null) {
                    logger.log(Level.FINER, "tried to run a task that was " +
                               "removed previously from the data service; " +
                               "giving up");
                    return;
                }
                // check that the task isn't perdiodic and now on another node
                if (ptask.isPeriodic()) {
                    long node = ptask.getRunningNode();
                    if (node != nodeId) {
                        // someone else picked it up, so just cancel it locally
                        ctxFactory.joinTransaction().
                            cancelRecurringTask(objId, taskIdentity);
                        return;
                    }
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "running task {0} scheduled to " +
                               "run at {1}", objId, ptask.getStartTime());
                }
                // finally, run the task itself, and set for re-use as needed
                if (ptask.isPeriodic()) {
                    // Persistently record the start time of periodic tasks so
                    // that if the task is handed off, we can approximate what
                    // time to use as the new restart time of the periodic task.
                    // Note that this is not a permanent solution as it leaves
                    // open the possibility that executions of periodic tasks
                    // could be skipped when handed off.
                    // TBD: Update this so that the persistent task data is
                    // updated with the "authoritative" start time of the
                    // task as it is reported by the TransactionScheduler,
                    // Profiler, or some other location yet to be determined.
                    ptask.setLastStartTime(
                            watchdogService.currentAppTimeMillis());
                }
                ptask.run();
                if (!ptask.isPeriodic()) {
                    ptask.setReusable();
                }
            } catch (Exception e) {
                // catch exceptions just before they go back to the scheduler
                // to see if the task will be re-tried...if not, then we need
                // to notify the service
                if ((!(e instanceof ExceptionRetryStatus)) ||
                        (!((ExceptionRetryStatus) e).shouldRetry()))
                {
                    notifyNonRetry(objId);
                }
                throw e;
            }
        }
    }

    /**
     * Private wrapper class for all non-durable tasks. This makes sure that
     * when a non-durable task runs the status count for the associated
     * identity is decremented, and runs the task within a transaction if
     * this was requested.
     */
    private class NonDurableTask implements KernelRunnable {
        private final KernelRunnable runnable;
        private final Identity identity;
        private final boolean transactional;
        NonDurableTask(KernelRunnable runnable, Identity identity,
                       boolean transactional)
        {
            this.runnable = runnable;
            this.identity = identity;
            this.transactional = transactional;
        }
        public String getBaseTaskType() {
            return runnable.getBaseTaskType();
        }
        public void run() throws Exception {
            if (shuttingDown()) {
                return;
            }
            try {
                if (transactional) {
                    transactionScheduler.runTask(runnable, identity);
                } else {
                    runnable.run();
                }
            } finally {
                submitStatusChange(identity, -1);
            }
        }
    }

    /**
     * Private helper that restarts all of the tasks associated with the
     * given identity. This must be called within a transaction.
     */
    private void restartTasks(String identityName) {
        // start iterating from the root of the pending task namespace
        String prefix = DS_PENDING_SPACE + identityName + ".";
        String objName = dataService.nextServiceBoundName(prefix);
        int taskCount = 0;

        // loop through all bound names for the given identity, starting
        // each pending task in a separate transaction
        while ((objName != null) && (objName.startsWith(prefix))) {
            scheduleNonDurableTask(new TaskRestartRunner(objName), true);
            objName = dataService.nextServiceBoundName(objName);
            taskCount++;
        }

        if (logger.isLoggable(Level.CONFIG)) {
            logger.log(Level.CONFIG, "re-scheduled {0} tasks for identity {1}",
                       taskCount, identityName);
        }
    }

    /**
     * Private helper that restarts a single named task. This must be called
     * within a transaction.
     */
    private void restartTask(String objName) {
        PendingTask ptask = null;
        try {
            ptask = (PendingTask) dataService.getServiceBinding(objName);
        } catch (NameNotBoundException nnbe) {
            // this happens when a task is scheduled for an identity that
            // hasn't yet been mapped or is in the process of being mapped,
            // so we can just return, since the task has already been run
            return;
        }

        // check that the task is supposed to run here, or if not, that
        // we were able to hand it off
        Identity identity = ptask.getIdentity();
        if (!isMappedLocally(identity)) {
            // if we handed off the task, we're done
            if (handoffTask(objName, identity)) {
                return;
            }
        }

        BigInteger objId = getIdFromName(objName);

        // if the pending task is reusable then it's a placeholder and
        // there's no task to run, so just add it to the local list
        if (ptask.isReusable()) {
            Set<BigInteger> set = availablePendingMap.get(identity);
            if (set != null) {
                synchronized (set) {
                    set.add(objId);
                }
            }
            return;
        }

        TaskRunner runner = new TaskRunner(objId, ptask.getBaseTaskType(),
                                           identity);
        runner.markIgnoreIsLocal();

        if (ptask.getPeriod() == PERIOD_NONE) {
            // this is a non-periodic task
            scheduleTask(runner, identity, ptask.getStartTime(), true);
        } else {
            // this is a periodic task...there is a rare but possible
            // scenario where a periodic task starts for an un-mapped
            // identity, and then the identity gets mapped to this node,
            // which would cause two copies of the task to start, so
            // the recurringMap is checked to make sure it doesn't already
            // contain the task being restarted
            if (recurringMap.containsKey(objId)) {
                return;
            }

            // get the times associated with this task, and if the start
            // time has already passed, figure out the next period
            // interval from now to use as the new start time
            long originalStartTime = ptask.getStartTime();
            long lastStartTime = ptask.getLastStartTime();
            long restartTime;
            // TBD: remove the check for lastStartTime < originalStartTime
            // when the fix is put in place such that the lastStartTime is
            // the "authoritative" start time and not an observed application
            // time.  This check is only needed because the TransactionScheduler
            // implementation allows for a remote possibility of running a task
            // before it is actually scheduled to run (less than 15ms before).
            if (lastStartTime == NEVER || lastStartTime < originalStartTime) {
                restartTime = originalStartTime;
            } else {
                long period = ptask.getPeriod();
                long runCount = (lastStartTime - originalStartTime) / period;
                restartTime = originalStartTime + period * (runCount + 1);
            }

            // mark the task as running on this node so that it doesn't
            // also run somewhere else
            dataService.markForUpdate(ptask);
            ptask.setRunningNode(nodeId);

            RecurringTaskHandle handle = 
                transactionScheduler.scheduleRecurringTask(
                    runner, identity,
                    watchdogService.getSystemTimeMillis(restartTime),
                    ptask.getPeriod());
            ctxFactory.joinTransaction().
                addRecurringTask(objId, handle, identity);
        }
    }

    /** A private runnable used to re-start a single task. */
    private class TaskRestartRunner implements KernelRunnable {
        private final String objName;
        TaskRestartRunner(String objName) {
            this.objName = objName;
        }
        public String getBaseTaskType() {
            return getClass().getName();
        }
        public void run() throws Exception {
            restartTask(objName);
        }
    }

    /** A private extension of HashSet to provide type info. */
    private static class StringHashSet extends ScalableHashSet<String> {
        private static final long serialVersionUID = 1;
    }

    /** A private class to track details of recurring tasks. */
    private static class RecurringDetail {
        final RecurringTaskHandle handle;
        final Identity identity;
        RecurringDetail(RecurringTaskHandle handle, Identity identity) {
            this.handle = handle;
            this.identity = identity;
        }
    }

    /** Private helper to add a recurring handle to the set for an identity. */
    private void addHandleForIdentity(RecurringTaskHandle handle,
                                      Identity identity)
    {
        synchronized (identityRecurringMap) {
            Set<RecurringTaskHandle> set = identityRecurringMap.get(identity);
            if (set == null) {
                set = new HashSet<RecurringTaskHandle>();
                identityRecurringMap.put(identity, set);
            }
            set.add(handle);
        }
    }

    /**
     * Private helper to remove a recurring handle from the set for an
     * identity.
     */
    private void removeHandleForIdentity(RecurringTaskHandle handle,
                                         Identity identity)
    {
        synchronized (identityRecurringMap) {
            Set<RecurringTaskHandle> set = identityRecurringMap.get(identity);
            if (set != null) {
                set.remove(handle);
                if (set.isEmpty()) {
                    identityRecurringMap.remove(identity);
                }
            }
        }
    }

    /** Private helper that cancels all recurring tasks for an identity. */
    private void cancelHandlesForIdentity(Identity identity) {
        synchronized (identityRecurringMap) {
            Set<RecurringTaskHandle> set =
                identityRecurringMap.remove(identity);
            if (set != null) {
                for (RecurringTaskHandle handle : set) {
                    handle.cancel();
                }
            }
        }
    }

    /**
     * Private helper method that hands-off a durable task from the current
     * node to a new node. The task needs to have already been persisted
     * as a {@code PendingTask} under the given name binding. This method
     * must be called in the context of a valid transaction. If this method
     * returns {@code false} then the task was not handed-off, and so it
     * must be run on the local node.
     * NOTE: we may want to revisit this final assumption, perhaps delaying
     * such tasks, or coming up with some other policy
     */
    private boolean handoffTask(String objName, Identity identity) {
        Node handoffNode = null;
        try {
            handoffNode = nodeMappingService.getNode(identity);
        } catch (UnknownIdentityException uie) {
            // this should be a rare case, but in the event that there isn't
            // a mapping available, there's really nothing to be done except
            // just run the task locally, and in a separate thread try to get
            // the assignment taken care of
            if (logger.isLoggable(Level.FINE)) {
                logger.logThrow(Level.FINE, uie, "No mapping exists for " +
                                "identity {0} so task {1} will run locally",
                                identity.getName(), objName);
            }
            assignNode(identity);
            return false;
        }

        // since the call to get an assigned node can actually return a
        // failed node, check for this case first
        if (!handoffNode.isAlive()) {
            // since the mapped node is down, run the task locally
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Mapping for identity {0} was to " +
                           "node {1} which has failed so task {2} will " +
                           "run locally", identity.getName(),
                           handoffNode.getId(), objName);
            }
            return false;
        }

        long newNodeId = handoffNode.getId();
        if (newNodeId == nodeId) {
            // a timing issue caused us to try handing-off to ourselves, so
            // just return from here
            return false;
        }

        String handoffName = DS_HANDOFF_SPACE + String.valueOf(newNodeId);
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Handing-off task {0} to node {1}",
                       objName, newNodeId);
        }
        try {
            StringHashSet set =
		(StringHashSet) dataService.getServiceBinding(handoffName);
            set.add(objName);
        } catch (NameNotBoundException nnbe) {
            // this will only happen in the unlikely event that the identity
            // has been assigned to a node that is still coming up and hasn't
            // bound its hand-off set yet, in which case the new node will
            // run this task when it learns about the identity mapping
        }

        return true;
    }

    /** Private helper that kicks off a thread to do node assignment. */
    private void assignNode(final Identity identity) {
        (new Thread(new Runnable() {
                public void run() {
                    nodeMappingService.assignNode(TaskServiceImpl.class,
                                                  identity);
                }
            })).start();
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
            StringHashSet set = (StringHashSet) dataService.getServiceBinding(
		localHandoffSpace);
            if (!set.isEmpty()) {
                Iterator<String> it = set.iterator();
                while (it.hasNext()) {
                    scheduleNonDurableTask(new TaskRestartRunner(it.next()),
                                           true);
                    it.remove();
                }
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

    /**
     * Private helper that checks if the given {@code Identity} is running
     * any tasks on the local node. This does not need to be called from
     * within a transaction.
     */
    private boolean isActiveLocally(Identity identity) {
        synchronized (activeIdentityMap) {
            return activeIdentityMap.containsKey(identity);
        }
    }

    /**
     * Private helper that accepts status change votes. This method does
     * not access any transactional context or other services, but may
     * be called in a transaction. Note that a count of 0 is ignored.
     */
    private void submitStatusChange(Identity identity, int change) {
        // a change of zero means that nothing would change
        if (change == 0) {
            return;
        }

        // apply the count change, and see if this changes the status
        synchronized (activeIdentityMap) {
            boolean active;
            if (activeIdentityMap.containsKey(identity)) {
                // there is currently a count, so we'll need to see what
                // affect the change has
                int current = activeIdentityMap.get(identity) + change;
                assert current >= 0 : "task count went negative for " +
                    "identity: " + identity.getName();
                if (current == 0) {
                    activeIdentityMap.remove(identity);
                    active = false;
                } else {
                    activeIdentityMap.put(identity, current);
                    return;
                }
            } else {
                // unless the count is negative, we're going active
                assert change >= 0 : "task count went negative for identity: " +
                    identity.getName();
                activeIdentityMap.put(identity, change);
                active = true;
            }

            // if we got here then there is a change in the status, as noted
            // by the "active" boolean flag
            TimerTask task = statusTaskMap.remove(identity);
            if (task != null) {
                // if there was a timer task pending, then we've just negated
                // it with the new status, so cancel the task
                task.cancel();
            } else {
                // there was no pending task, so set one up
                task = new StatusChangeTask(identity, active);
                statusTaskMap.put(identity, task);
                statusUpdateTimer.schedule(task, voteDelay);
            }
        }
    }

    /** Private TimerTask implementation for delaying status votes. */
    private class StatusChangeTask extends TimerTask {
        private final Identity identity;
        private final boolean active;
        StatusChangeTask(Identity identity, boolean active) {
            this.identity = identity;
            this.active = active;
        }
        public void run() {
            // remove this handle from the pending map, and if this is
            // successful, then no one has tried to cancel this task, so
            // finish running
            if (statusTaskMap.remove(identity) != null) {
                try {
                    nodeMappingService.setStatus(TaskServiceImpl.class,
                                                 identity, active);
                } catch (UnknownIdentityException uie) {
                    if (active) {
                        nodeMappingService.assignNode(TaskServiceImpl.class,
                                                      identity);
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void mappingAdded(Identity id, Node oldNode) {
        if (shuttingDown()) {
            return;
        }

        // keep track of the new identity, returning if the identity was
        // already mapped to this node
        synchronized (mappedIdentitySet) {
            if (!mappedIdentitySet.add(id)) {
                return;
            }
        }

        // add an entry for the local cache of pending tasks
        availablePendingMap.putIfAbsent(id, new HashSet<BigInteger>());

        // start-up the pending tasks for this identity
        final String identityName = id.getName();
        try {
            transactionScheduler.runTask(new KernelRunnable() {
                    public String getBaseTaskType() {
                        return NAME + ".TaskRestartRunner";
                    }
                    public void run() throws Exception {
                        restartTasks(identityName);
                    }
                }, taskOwner);
        } catch (Exception e) {
            // this should only happen if the restart task fails, which
            // would indicate some kind of corrupted state
            throw new AssertionError("Failed to restart tasks for " +
                                     id.getName() + ": " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    public void mappingRemoved(final Identity id, Node newNode) {
        if (shuttingDown()) {
            return;
        }

        // if the newNode is null, this means that the identity has been
        // removed entirely, so if there are still local tasks, keep
        // running them and push-back on the mapping service
        if ((newNode == null) && (isActiveLocally(id))) {
            nodeMappingService.assignNode(TaskServiceImpl.class, id);
            return;
        }
        
        // note that the identity is no longer on this node
        synchronized (mappedIdentitySet) {
            mappedIdentitySet.remove(id);
        }
        // cancel all of the identity's recurring tasks
        cancelHandlesForIdentity(id);

        // remove the local cache of available pending tasks, and remove
        // the entries in the data store if the identity has been removed
        availablePendingMap.remove(id);
        if (newNode == null) {
            try {
                transactionScheduler.runTask(new KernelRunnable() {
                        public String getBaseTaskType() {
                            return NAME + ".PendingTaskCleanupRunner";
                        }
                        public void run() throws Exception {
                            String prefix =
                                DS_PENDING_SPACE + id.getName() + ".";
                            String objName =
                                dataService.nextServiceBoundName(prefix);
                            while ((objName != null) &&
                                   (objName.startsWith(prefix)))
                            {
                                Object obj =
                                    dataService.getServiceBinding(objName);
                                dataService.removeServiceBinding(objName);
                                dataService.removeObject(obj);
                                objName = dataService.
                                    nextServiceBoundName(objName);
                            }
                        }
                    }, id);
            } catch (Exception e) {
                logger.logThrow(Level.WARNING, e, "Failed to remove reusable" +
                                " pending tasks for {0}", id);
            }
        }
        
        // immediately vote that the identity is active iif there are
        // still tasks running locally
        if (isActiveLocally(id)) {
            try {
                nodeMappingService.setStatus(TaskServiceImpl.class, id, true);
            } catch (UnknownIdentityException uie) {
                nodeMappingService.assignNode(TaskServiceImpl.class, id);
            }
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
            TaskServiceImpl service = TaskServiceImpl.txnProxy.
                getService(TaskServiceImpl.class);
            if (service.shuttingDown()) {
                throw new IllegalStateException("Service is shutdown");
            }
            // resolve the task, which checks if the task was already cancelled
            try {
                PendingTask ptask =
                        (PendingTask) (service.dataService.
                        getServiceBinding(objName));
                service.cancelPeriodicTask(ptask, TaskServiceImpl.
                                                  getIdFromName(objName));
            } catch (NameNotBoundException nnbe) {
                throw new ObjectNotFoundException("task was already cancelled");
            }
        }
    }

    /** Private method to get or create an owner for a task. */
    private Identity getTaskOwner(Object task) {
        if (task.getClass().getAnnotation(RunWithNewIdentity.class) != null) {
            return new DynamicIdentity(nodeId);
        } else {
            return txnProxy.getCurrentOwner();
        }
    }

    /** Private implementation of {@code Identity} for new task owners. */
    private static class DynamicIdentity implements Identity, Serializable {
        private static final long serialVersionUID = 1L;
        // a counter just to make all identity names unique
        private static final AtomicLong counter = new AtomicLong(0);
        // the name of this new identity
        private final String name;
        /** Create an instance of {@code DynamicIdentity}. */
        DynamicIdentity(long nodeId) {
            this.name = "id:" + nodeId + "." + counter.getAndIncrement();
        }
        /** {@inheritDoc} */
        public String getName() {
            return name;
        }
        /** This identity may never log in. */
        public void notifyLoggedIn() {
            throw new AssertionError("Logged in should not be called");
        }
        /** This identity may never log out. */
        public void notifyLoggedOut() {
            throw new AssertionError("Logged out should not be called");
        }
        /** {@inheritDoc} */
        public boolean equals(Object o) {
            return (o instanceof DynamicIdentity) &&
                this.name.equals(((DynamicIdentity) o).name);
        }
        /** {@inheritDoc} */
        public int hashCode() {
            return name.hashCode();
        }
    }

}
