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

package com.sun.sgs.impl.profile;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileParticipantDetail;

import java.beans.PropertyChangeEvent;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * This is the implementation of {@code ProfileCollector} used by the
 * kernel to collect and report profiling data. It uses a single thread to
 * consume and report profiling data.
 */
public final class ProfileCollectorImpl implements ProfileCollector {
    
    // A map from profile consumer name to profile consumer object
    private final ConcurrentHashMap<String, ProfileConsumerImpl> consumers;
    
    // the number of threads currently in the scheduler
    private volatile int schedulerThreadCount;

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
        consumers = new ConcurrentHashMap<String, ProfileConsumerImpl> ();

        defaultProfileLevel = level;
        
        // start a long-lived task to consume the other end of the queue
        reporterThread = new Thread(new CollectorRunnable());
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
    }

    /** {@inheritDoc} */
    public ProfileLevel getDefaultProfileLevel() {
        return defaultProfileLevel;
    }

    /** {@inheritDoc} */
    public void setDefaultProfileLevel(ProfileLevel level) {
        defaultProfileLevel = level;
    }
    
    /** {@inheritDoc} */
    public Map<String, ProfileConsumer> getConsumers() {
        // Create a copy of the map to get the type correct.
        ConcurrentHashMap<String, ProfileConsumer> retMap = 
                new ConcurrentHashMap<String, ProfileConsumer>(consumers);
        return Collections.unmodifiableMap(retMap);
    }
    
    /** Called by the profile registrar */
    ProfileConsumer registerProfileProducer(String name) {
        ProfileConsumerImpl pc = new ProfileConsumerImpl(this, name);
        consumers.put(pc.getName(), pc);
        return pc;
    }
    
    /** {@inheritDoc} */
    public void addListener(ProfileListener listener, boolean canRemove) {
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
    }
 
    /** {@inheritDoc}  */
    public void addListener(String listenerClassName) throws Exception {              
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
        // Check to see if we're allowed to remove this listener
        Boolean canRemove = listeners.get(listener);
        if (canRemove != null && canRemove.equals(Boolean.TRUE)) {
            listeners.remove(listener);
            listener.shutdown();
        }
    }

    /** {@inheritDoc} */
    public void notifyThreadAdded() {
        schedulerThreadCount++;
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    schedulerThreadCount - 1, 
				    schedulerThreadCount);

        for (ProfileListener listener : listeners.keySet())
            listener.propertyChange(event);
    }

    /** {@inheritDoc} */
    public void notifyThreadRemoved() {
        schedulerThreadCount--;
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    schedulerThreadCount + 1, 
				    schedulerThreadCount);

        for (ProfileListener listener : listeners.keySet())
            listener.propertyChange(event);
    }

    /** {@inheritDoc} */
    public void startTask(KernelRunnable task, Identity owner,
                          long scheduledStartTime, int readyCount) {
        profileReports.get().push(new ProfileReportImpl(task, owner,
                                                        scheduledStartTime,
                                                        readyCount));
    }

    /** {@inheritDoc} */
    public void noteTransactional() {
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().peek();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }

        profileReport.transactional = true;
    }

    /** {@inheritDoc} */
    public void addParticipant(ProfileParticipantDetail participantDetail) {
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().peek();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }
        if (! profileReport.transactional)
            throw new IllegalStateException("Participants cannot be added " +
                                            "to a non-transactional task");
        profileReport.participants.add(participantDetail);
    }

    /** {@inheritDoc} */
    public void finishTask(int tryCount) {
	finishTask(tryCount, null);
    }

    /** {@inheritDoc} */
    public void finishTask(int tryCount, Throwable t) {
        long stopTime = System.currentTimeMillis();
        ProfileReportImpl profileReport = null;
        try {
            profileReport = profileReports.get().pop();
        } catch (EmptyStackException ese) {
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        }

        // collect the final details about the report
        profileReport.runningTime = stopTime - profileReport.actualStartTime;
        profileReport.tryCount = tryCount;
        profileReport.succeeded = t == null;
        profileReport.throwable = t;
        
        // if this was a nested report, then merge all of the collected
        // data into the parent
        if (! profileReports.get().empty())
            profileReports.get().peek().merge(profileReport);

        // queue up the report to be reported to our listeners
        queue.offer(profileReport);
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
    private class CollectorRunnable implements Runnable {
        /*private volatile long queueSize = 0;
          private volatile long queueSamples = 0;*/
        public void run() {
            try {
                while (true) {
                    if (Thread.interrupted())
                        return;

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

                    // make sure that the collections can't be modified by
                    // a listener
                    profileReport.ops =
                        Collections.unmodifiableList(profileReport.ops);
                    profileReport.participants =
                        Collections.
                        unmodifiableSet(profileReport.participants);

                    for (ProfileListener listener : listeners.keySet())
                        listener.report(profileReport);
                }
            } catch (InterruptedException ie) {}
        }
    }
}
