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

package com.sun.sgs.impl.profile;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.management.ProfileControllerMXBean;

import com.sun.sgs.management.TaskAggregateMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.TransactionListenerDetail;

import java.beans.PropertyChangeEvent;

import java.lang.management.ManagementFactory;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;


/**
 * This is the implementation of {@code ProfileCollector} used by the
 * kernel to collect and report profiling data. It uses a single thread to
 * consume and report profiling data to {@code ProfileListeners}.
 * <p>
 * The {@link #ProfileCollectorImpl constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.profile.create.mbeanserver
 *	</b></code><br>
 *	<i>Default:</i> {@code false} <br>
 *      Specifies whether a new {@code MBeanServer} should be created, rather
 *      than using the existing platform {@code MBeanServer}. <p>
 * 
 * </dl> <p>
 */
public final class ProfileCollectorImpl implements ProfileCollector {

    /**
     * The standard prefix for consumer names created by core packages.
     */
    public static final String CORE_CONSUMER_PREFIX = "com.sun.sgs.";
    
    // the logger for this class
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(ProfileCollectorImpl.
                                           class.getName()));
    
    /**
     * The property telling us whether we should use the default platform
     * MBeanServer or create a new one.
     */ 
    public static final String CREATE_MBEAN_SERVER_PROPERTY = 
            "com.sun.sgs.impl.profile.create.mbeanserver";
    
    // A map from profile consumer name to profile consumer object
    private final ConcurrentHashMap<String, ProfileConsumerImpl> consumers;
    
    // the number of threads currently in the scheduler
    private volatile int schedulerThreadCount;

    // the identifier for the local node
    private volatile long localNodeId = -1;

    // A map from registered listener to whether it can be removed or shutdown
    private final ConcurrentHashMap<ProfileListener, Boolean> listeners;

    // thread-local detail about the current task, used to let us
    // aggregate data across all participants in a given task
    private ThreadLocal<Stack<ProfileReportImpl>> profileReports =
        new ThreadLocal<Stack<ProfileReportImpl>>() {
            protected Stack<ProfileReportImpl> initialValue() {
                return new Stack<ProfileReportImpl>();
            }
        };

    // the incoming report queue
    private LinkedBlockingQueue<ProfileReportImpl> queue;

    // long-running thread to report data
    private final Thread reporterThread;
    
    // The default profiling level.  This is initially set from 
    // properties at startup.
    private ProfileLevel defaultProfileLevel;
    
    // The application properties, used to instantiate {@code ProfileListener}s
    private final Properties appProperties;
    
    // The system registry, used to instantiate {@code ProfileListener}s
    private final ComponentRegistry systemRegistry;
    
    // The MBeans registered for this node.
    private final ConcurrentMap<String, Object> registeredMBeans;
    
    // Our MBeanServer
    private final MBeanServer mbeanServer;
    
    // The statistics MBean for tasks
    private final TaskAggregateStats taskStats;

    /**
     * Creates an instance of {@code ProfileCollectorImpl}.
     * @param level the default system profiling level
     * @param appProperties the application properties, used for instantiating
     *          {@code ProfileListener}s
     * @param systemRegistry the system registry, used for instantiating
     *          {@code ProfileListener}s
     */
    public ProfileCollectorImpl(ProfileLevel level, 
                                Properties appProperties, 
                                ComponentRegistry systemRegistry) 
    {
        this.appProperties = appProperties;
        this.systemRegistry = systemRegistry;
        
        schedulerThreadCount = 0;
        listeners = new ConcurrentHashMap<ProfileListener, Boolean>();
        queue = new LinkedBlockingQueue<ProfileReportImpl>();
        consumers = new ConcurrentHashMap<String, ProfileConsumerImpl>();

        defaultProfileLevel = level;
        
        registeredMBeans = new ConcurrentHashMap<String, Object>();

        PropertiesWrapper wrappedProps = new PropertiesWrapper(appProperties);
        boolean createServer = 
           wrappedProps.getBooleanProperty(CREATE_MBEAN_SERVER_PROPERTY, false);
        if (createServer) {
            mbeanServer = MBeanServerFactory.createMBeanServer();
        } else {
            mbeanServer = ManagementFactory.getPlatformMBeanServer();
        }
        
        // Create the task aggregator MBean and register it, as well as
        // the profile controller MBean.
        taskStats = new TaskAggregateStats(this,
                CORE_CONSUMER_PREFIX + "TaskAggregateStats");
        try {
            registerMBean(taskStats, TaskAggregateMXBean.MXBEAN_NAME);
            registerMBean(new ProfileController(this),
                          ProfileControllerMXBean.MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }

        // start a long-lived task to consume the other end of the queue
        reporterThread = new CollectorThread();
        reporterThread.start();
     }

    /** {@inheritDoc} */
    public void shutdown() {
        // Shut down the reporterThread, waiting for it to finish what
        // its doing
        reporterThread.interrupt();
	try {
	    reporterThread.join();
	} catch (InterruptedException e) {
	    // do nothing
	}

        // Shut down each of the listeners if it was added with remove and
        // shutdown enabled.
        for (Map.Entry<ProfileListener, Boolean> entry : listeners.entrySet()) {
            if (entry.getValue().equals(Boolean.TRUE)) {
                entry.getKey().shutdown();
            }
        }
        
        // attempt to unregister all our registered MBeans
        Set<String> keys = registeredMBeans.keySet();
        for (String name : keys) {
            try {
                mbeanServer.unregisterMBean(new ObjectName(name));
            } catch (MalformedObjectNameException ex) {
                logger.logThrow(Level.WARNING, ex,
                                "Could not unregister MBean {0}", name);
            } catch (InstanceNotFoundException ex) {
                logger.logThrow(Level.WARNING, ex,
                                "Could not unregister MBean {0}", name);
            } catch (MBeanRegistrationException ex) {
                logger.logThrow(Level.WARNING, ex,
                                "Could not unregister MBean {0}", name);
            } finally {
                registeredMBeans.remove(name);
            }
        }
    }
    
    /** {@inheritDoc} */
    public ProfileLevel getDefaultProfileLevel() {
        return defaultProfileLevel;
    }

    /** {@inheritDoc} */
    public void setDefaultProfileLevel(ProfileLevel level) {
        if (level == null) {
            throw new NullPointerException("Profile level cannot be null");
        }
        defaultProfileLevel = level;
    }
    
    /** {@inheritDoc} */
    public ProfileConsumer getConsumer(String name) {
        if (name == null) {
            throw new NullPointerException("Name cannot be null");
        }
        
        ProfileConsumerImpl pc = new ProfileConsumerImpl(this, name);

        ProfileConsumerImpl oldpc = consumers.putIfAbsent(name, pc);
        if (oldpc != null) {
            logger.log(Level.FINE, 
                   "Found consumer {0} already created", name);
            return oldpc;
        } else {
            logger.log(Level.FINE, "Created consumer named {0}", name);
            return pc;
        }
    }
    
    /** {@inheritDoc} */
    public Map<String, ProfileConsumer> getConsumers() {
        // Create a copy of the map to get the type correct.
        ConcurrentHashMap<String, ProfileConsumer> retMap = 
                new ConcurrentHashMap<String, ProfileConsumer>(consumers);
        return Collections.unmodifiableMap(retMap);
    }
    
    /** {@inheritDoc} */
    public void addListener(ProfileListener listener, boolean canRemove) {
        if (listener == null) {
            throw new NullPointerException("Cannot add a null listener");
        }
        listeners.put(listener, canRemove);
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    null, schedulerThreadCount);

        listener.propertyChange(event);
        for (ProfileConsumerImpl pc : consumers.values()) {
            for (ProfileOperation p : pc.getOperations()) {
                event = 
                    new PropertyChangeEvent(this, "com.sun.sgs.profile.newop",
					    null, p);
                listener.propertyChange(event); 
            }
        }

        if (localNodeId > -1) {
            event =
                new PropertyChangeEvent(this, "com.sun.sgs.profile.nodeid",
                                        null, localNodeId);
            listener.propertyChange(event);
        }
    }
 
    /** {@inheritDoc}  */
    public void addListener(String listenerClassName) 
            throws Exception 
    {              
        if (listenerClassName == null) {
            throw new NullPointerException("Class name cannot not be null");
        }
        // make sure we can resolve the listener
        Class<?> listenerClass = Class.forName(listenerClassName);
        Constructor<?> listenerConstructor =
            listenerClass.getConstructor(Properties.class,
                                         Identity.class,
                                         ComponentRegistry.class);

        // create a new identity for the listener
        IdentityImpl owner = new IdentityImpl(listenerClassName);

        // try to create and register the listener
        Object obj =
            listenerConstructor.newInstance(appProperties, owner,
                                            systemRegistry);
        addListener((ProfileListener) obj, true);
    }
    
    /** {@inheritDoc} */
    public List<ProfileListener> getListeners() {
        // Create a list from the keys
        ArrayList<ProfileListener> list = 
                new ArrayList<ProfileListener>(listeners.keySet());
        // and return a read-only copy.
        return Collections.unmodifiableList(list);
    }
    
    /** {@inheritDoc} */
    public void removeListener(ProfileListener listener) {
        if (listener == null) {
            throw new NullPointerException("Listener cannot be null");
        }
        // Check to see if we're allowed to remove this listener
        Boolean canRemove = listeners.get(listener);
        if (canRemove != null && canRemove.equals(Boolean.TRUE)) {
            listeners.remove(listener);
            listener.shutdown();
        }
    }

    /** {@inheritDoc} */
    public void registerMBean(Object mBean, String mBeanName)
        throws JMException
    {
        try {
            ObjectName name = new ObjectName(mBeanName);
            mbeanServer.registerMBean(mBean, name);

            registeredMBeans.putIfAbsent(mBeanName, mBean);
            logger.log(Level.CONFIG, "Registered MBean {0}", name);
        } catch (JMException ex) {
            logger.logThrow(Level.CONFIG, ex,
                            "Could not register MBean {0}", mBeanName);
            throw ex;
        }
    }

    /** {@inheritDoc} */
    public Object getRegisteredMBean(String mBeanName) {
        return registeredMBeans.get(mBeanName);
    }

    /* -- Methods to support ProfileCollectorHandle -- */
    
    /**
     * Notifies the collector that a thread has been added to the scheduler.
     */
    void notifyThreadAdded() {
        schedulerThreadCount++;
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    schedulerThreadCount - 1, 
				    schedulerThreadCount);

        for (ProfileListener listener : listeners.keySet()) {
            listener.propertyChange(event);
        }
    }

    /**
     * Notifies the collector that a thread has been removed from the
     * scheduler.
     */
    void notifyThreadRemoved() {
        schedulerThreadCount--;
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    schedulerThreadCount + 1, 
				    schedulerThreadCount);

        for (ProfileListener listener : listeners.keySet()) {
            listener.propertyChange(event);
        }
    }

    /**
     * Notifies the collector that the node has been assigned its identifier.
     */
    void notifyNodeIdAssigned(long nodeId) {
        localNodeId = nodeId;
        PropertyChangeEvent event =
            new PropertyChangeEvent(this, "com.sun.sgs.profile.nodeid",
                                    null, nodeId);
        for (ProfileListener listener : listeners.keySet()) {
            listener.propertyChange(event);
        }
    }

    /**
     * Tells the collector that a new task is starting in the context of
     * the calling thread.
     * 
     * @param task the <code>KernelRunnable</code> that is starting
     * @param owner the <code>Identity</code> of the task owner
     * @param scheduledStartTime the requested starting time for the task
     * @param readyCount the number of ready tasks at the scheduler
     */
    void startTask(KernelRunnable task, Identity owner,
                   long scheduledStartTime, int readyCount)
    {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        if (owner == null) {
            throw new NullPointerException("Owner cannot be null");
        }
        profileReports.get().push(new ProfileReportImpl(task, owner,
                                                        scheduledStartTime,
                                                        readyCount));
    }

    /**
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to {@code startTask}) is
     * transactional. 
     * 
     * @param txnId the identifier for the transaction
     */
    void noteTransactional(byte [] txnId) {
        if (txnId == null) {
            throw new NullPointerException("Transaction id cannot be null");
        }
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().peek();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }

        profileReport.transactionId = txnId;
    }

    /**
     * Tells the collector about a participant of a transaction when that
     * participant has finished participating (i.e., has committed, has
     * prepared read-only, or has aborted).
     * 
     * @param participantDetail the detail associated with the participant
     */
    void addParticipant(ProfileParticipantDetail participantDetail) {
        if (participantDetail == null) {
            throw new NullPointerException("Participant detail cannot be null");
        }
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().peek();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }
        if (!profileReport.wasTaskTransactional()) {
            throw new IllegalStateException("Participants cannot be added " +
                                            "to a non-transactional task");
        }
        profileReport.addParticipant(participantDetail);
    }

    /**
     * Tells the collector about a listener of a transaction when that
     * listener is finished with its work (i.e., after its afterCompletion
     * method has been called).
     *
     * @param listenerDetail the detail associated with the listener
     */
    void addTransactionListener(TransactionListenerDetail listenerDetail) {
        if (listenerDetail == null) {
            throw new NullPointerException("Listener detail cannot be null");
        }
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().peek();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }
        if (!profileReport.wasTaskTransactional()) {
            throw new IllegalStateException("Listeners cannot be added " +
                                            "to a non-transactional task");
        }
        profileReport.addListener(listenerDetail);
    }

    /**
     * Sets the detail for all objects accessed during the task as
     * reported to the {@code AccessCoordinator}.
     * 
     * @param detail all detail of the accessed objects
     */
    void setAccessedObjectsDetail(AccessedObjectsDetail detail) {
        if (detail == null) {
            throw new NullPointerException("Access detail cannot be null");
        }
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().peek();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }
        if (!profileReport.wasTaskTransactional()) {
            throw new IllegalStateException("Object access cannot be added " +
                                            "to a non-transactional task");
        }
        profileReport.setAccessedObjectsDetail(detail);
    }

    /**
     * Tells the collector that the current task associated with the
     * calling thread (as associated by a call to
     * {@code startTask}) has now successfully finished.
     * 
     * @param tryCount the number of times that the task has tried to run
     */
    void finishTask(int tryCount) {
	finishTask(tryCount, null);
    }

    /**
     * Tells the collector that the current task associated with the
     * calling thread (as associated by a call to
     * {@code startTask}) has now finished.
     * 
     * @param tryCount the number of times that the task has tried to run
     * @param t the {@code Throwable} thrown during task execution
     */
    void finishTask(int tryCount, Throwable t) {
        long stopTime = System.currentTimeMillis();
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().pop();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }

        // collect the final details about the report
        long runtime = stopTime - profileReport.actualStartTime;    
        profileReport.runningTime = runtime;
        profileReport.tryCount = tryCount;
        boolean successful = t == null;
        profileReport.succeeded = successful;
        profileReport.throwable = t;
        
        // if this was a nested report, then merge all of the collected
        // data into the parent
        if (!profileReports.get().empty()) {
            profileReports.get().peek().merge(profileReport);
        }

        // Note that we're done modifying this report
        profileReport.finish();
        
        // queue up the report to be reported to our listeners
        if (!queue.offer(profileReport)) {
            logger.log(Level.FINE, "ProfileCollector queue is full");
        }
        
        // Update the task aggregate data 
        boolean trans = profileReport.wasTaskTransactional();
        if (successful) {
            long lagtime = profileReport.actualStartTime -
                           profileReport.scheduledStartTime;
            taskStats.taskFinishedSuccess(trans, profileReport.readyCount, 
                                          runtime, lagtime);
        } else {
            taskStats.taskFinishedFail(trans, profileReport.readyCount);
        }
    }

    /**
     * Package private method to notify listeners of a property change.
     * 
     * @param event the change the listeners will be notified about
     */
    void notifyListeners(PropertyChangeEvent event) {
        for (ProfileListener listener : listeners.keySet()) {
            listener.propertyChange(event);
        }
    }
    
    /** 
     * Package private method to get the current profile report,
     * used by the operations, counters, and samples.
     * 
     * @return the thread-local profile report for the currently running task
     */
    ProfileReportImpl getCurrentProfileReport() {
        return profileReports.get().peek();
    }

    /**
     * Private class that implements the long-running collector and reporter
     * of task data. The task blocks on the queue, and whenever there is a
     * report ready notifies all installed listeners.
     * <p>
     * NOTE: The commented-out code here may still be useful to make sure
     * that the single-threaded queue keeps up. It was originally used just
     * to to observe performance, and it's unclear whether it's worth
     * reporting anywhere, and where to report it.
     */
    private class CollectorThread extends Thread {
        /*private volatile long queueSize = 0;
          private volatile long queueSamples = 0;*/
	private boolean interrupted;
        public void run() {
            try {
                while (true) {
		    synchronized (this) {
			if (interrupted) {
			    return;
			}
		    }

                    ProfileReportImpl profileReport = queue.poll();
                    if (profileReport == null) {
                        profileReport = queue.take();
                    } /*else {
                        queueSize += queue.size();
                        queueSamples++;
                        }
                        
                        double avgQueueSize = (queueSamples == 0) ? 0 :
                        (double)queueSize / (double)queueSamples;
                        double percentQueueNotEmpty =
                        (double)queueSamples / (double)totalTasks;
                        reportStr += " [  AvgStatQueueSize=" + avgQueueSize +
                        "  StatQueueNonEmpty=" + percentQueueNotEmpty +
                        "%  ]\n\n";
                     */

                    for (ProfileListener listener : listeners.keySet()) {
                        try {
                            listener.report(profileReport);
                        } catch (Throwable t) {
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.logThrow(Level.WARNING, t,
                                                "{0}: report method failed",
                                                listener.getClass());
                            }
                        }
                    }
                }
            } catch (InterruptedException ie) { }
        }
	/**
	 * Modify interrupt to keep track of whether the thread has ever been
	 * interrupted.  That way we can have the thread exit if an interrupt
	 * has occurred, even if something (say logging) catches an interrupt
	 * and forgets to reset the interrupt status.
	 */
	@Override public void interrupt() {
	    synchronized (this) {
		interrupted = true;
	    }
	    super.interrupt();
	}
    }
}
