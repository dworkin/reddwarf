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

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileParticipantDetail;
import com.sun.sgs.profile.ProfileSample;

import java.beans.PropertyChangeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.LinkedList;
import java.util.Stack;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * This is the implementation of <code>ProfileCollector</code> used by the
 * kernel to collect and report profiling data. It uses a single thread to
 * consume and report profiling data.
 */
public class ProfileCollectorImpl implements ProfileCollector {

    private final AtomicInteger opIdCounter;

    private final ConcurrentHashMap<String,ProfileOperationImpl> ops;

    private final ConcurrentHashMap<String,ProfileSample> taskLocalSamples;

    private final ConcurrentHashMap<String,ProfileSample> aggregateSamples;

    private final ConcurrentHashMap<String,ProfileCounter> taskLocalCounters;

    private final ConcurrentHashMap<String,ProfileCounter> aggregateCounters;
    
    // the number of threads currently in the scheduler
    private volatile int schedulerThreadCount;

    // the set of registered listeners
    private ArrayList<ProfileListener> listeners;

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
    /**
     * Creates an instance of <code>ProfileCollectorImpl</code>.
     */
    public ProfileCollectorImpl() {
        schedulerThreadCount = 0;
        ops = new ConcurrentHashMap<String,ProfileOperationImpl>();
        listeners = new ArrayList<ProfileListener>();
        queue = new LinkedBlockingQueue<ProfileReportImpl>();
        opIdCounter = new AtomicInteger(0);

        aggregateSamples = new ConcurrentHashMap<String,ProfileSample>(); 
        taskLocalSamples = new ConcurrentHashMap<String,ProfileSample>();

        aggregateCounters = new ConcurrentHashMap<String,ProfileCounter>(); 
        taskLocalCounters = new ConcurrentHashMap<String,ProfileCounter>(); 

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
    }
    /** {@inheritDoc} */
    public void addListener(ProfileListener listener) {
        listeners.add(listener);
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    null, schedulerThreadCount);

        listener.propertyChange(event);
	for (ProfileOperationImpl p : ops.values()) {
	    event = new PropertyChangeEvent(this, "com.sun.sgs.profile.newop",
					    null, p);
	    listener.propertyChange(event);
	}
    }

    /** {@inheritDoc} */
    public void notifyThreadAdded() {
        schedulerThreadCount++;
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    schedulerThreadCount - 1, 
				    schedulerThreadCount);

        for (ProfileListener listener : listeners)
            listener.propertyChange(event);
    }

    /** {@inheritDoc} */
    public void notifyThreadRemoved() {
        schedulerThreadCount--;
	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.threadcount",
				    schedulerThreadCount + 1, 
				    schedulerThreadCount);

        for (ProfileListener listener : listeners)
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
     * Package-private method used by <code>ProfileConsumerImpl</code> to
     * handle operation registrations.
     *
     * @param opName the name of the operation
     * @param producerName the name of the <code>ProfileProducer</code>
     *                     registering this operation
     *
     * @return the canonical <code>ProfileOperation</code> that will
     *         report back to this collector.
     */
    ProfileOperation registerOperation(String opName, String producerName) {
	String name = producerName + "::" + opName;
	
	ProfileOperationImpl op = ops.get(name);

	if (op == null) {
	    int opId = opIdCounter.getAndIncrement();
	    op = new ProfileOperationImpl(opName, opId);
	    ops.putIfAbsent(name, op);
	    op = ops.get(name);
	}

	PropertyChangeEvent event = 
	    new PropertyChangeEvent(this, "com.sun.sgs.profile.newop",null, op);

        for (ProfileListener listener : listeners)
            listener.propertyChange(event);
        return op;
    }

    /**
     * A private implementation of <code>ProfileOperation</code> that is
     * returned from any call to <code>registerOperation</code>.
     */
    private class ProfileOperationImpl implements ProfileOperation {
        private final String opName;
        private final int opId;
        ProfileOperationImpl(String opName, int opId) {
            this.opName = opName;
            this.opId = opId;
        }

        public String getOperationName() {
            return opName;
        }

        public int getId() {
            return opId;
        }

        public String toString() {
            return opName;
        }
        /**
         * Note that this throws <code>IllegalStateException</code> if called
         * outside the scope of a started task.
         */
        public void report() {
            try {
                ProfileReportImpl profileReport = profileReports.get().peek();
                profileReport.ops.add(this);
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            }
        }
    }

    /**
     * Package-private method used by <code>ProfileConsumerImpl</code> to
     * handle counter registrations.
     *
     * @param counterName the name of the counter
     * @param producerName the name of the <code>ProfileProducer</code>
     *                     registering this counter
     * @param taskLocal <code>true</code> if this counter is local to tasks,
     *                  <code>false</code> otherwise
     *
     * @return a new <code>ProfileCounter</code> that will report back
     *         to this collector or the previously registered {@code
     *         ProfileCounter} with the provided name.
     */
    ProfileCounter registerCounter(String counterName, String producerName,
                                   boolean taskLocal) {

	String name = producerName + "::" + counterName;
	if (taskLocal) {
	    if (taskLocalCounters.containsKey(name))
		return taskLocalCounters.get(name);
	    else {
		ProfileCounter counter = 
		    new TaskLocalProfileCounter(counterName);
		taskLocalCounters.put(name, counter);
		return counter;
	    }
	}	
	else {
	    if (aggregateCounters.containsKey(name)) {
		return aggregateCounters.get(name);
	    }
	    else {
		ProfileCounter counter = 
		    new AggregateProfileCounter(counterName);
		aggregateCounters.put(name, counter);
		return counter;
	    }
	}
    }

    /**
     * A private implementation of <code>ProfileCounter</code> that is
     * returned from any call to <code>registerCounter</code>.
     */
    private abstract class AbstractProfileCounter implements ProfileCounter {
        private final String name;
        private final boolean taskLocal;
        AbstractProfileCounter(String name, boolean taskLocal) {
            this.name = name;
            this.taskLocal = taskLocal;
        }
        public String getCounterName() {
            return name;
        }
        public boolean isTaskLocal() {
            return taskLocal;
        }
        protected ProfileReportImpl getReport() {
            try {
                return profileReports.get().peek();
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            }
        }
    }

    /**
     * The concrete implementation of <code>AbstractProfileCounter</code> used
     * for counters that aggregate across tasks.
     */
    private class AggregateProfileCounter extends AbstractProfileCounter {
        private AtomicLong count;
        AggregateProfileCounter(String name) {
            super(name, false);
            count = new AtomicLong();
        }
        public void incrementCount() {
            getReport().updateAggregateCounter(getCounterName(),
                                               count.incrementAndGet());
        }
        public void incrementCount(long value) {
            if (value < 0)
                throw new IllegalArgumentException("Increment value must be " +
                                                   "greater than zero");
            getReport().updateAggregateCounter(getCounterName(),
                                               count.addAndGet(value));
        }
    }


    /**
     * The concrete implementation of <code>AbstractProfileCounter</code> used
     * for counters that are local to tasks.
     */
    private class TaskLocalProfileCounter extends AbstractProfileCounter {
        TaskLocalProfileCounter(String name) {
            super(name, true);
        }
        public void incrementCount() {
            getReport().incrementTaskCounter(getCounterName(), 1L);
        }
        public void incrementCount(long value) {
            if (value < 0)
                throw new IllegalArgumentException("Increment value must be " +
                                                   "greater than zero");
            getReport().incrementTaskCounter(getCounterName(), value);
        }
    }


    /**
     * Package-private method used by <code>ProfileConsumerImpl</code> to
     * handle sample registrations.
     *
     * @param name the name of the sample
     * @param taskLocal <code>true</code> if this counter is local to tasks,
     *                  <code>false</code> otherwise
     * @param maxSamples the maximum number of samples to keep at one
     *        time before lossy behavior starts and the oldest values
     *        are removed
     *
     * @return a new {@code ProfileCounter} that will report back to
     *         this collector or the previously registered {@code
     *         ProfileSample} with the provided name.
     */
    public ProfileSample registerSampleSource(String name, boolean taskLocal,
					       long maxSamples) {
	// REMINDER: this assume maxSamples isn't necessary when
	// deciding whether a sample source is already present.
	if (taskLocal) {
	    if (taskLocalSamples.containsKey(name))
		return taskLocalSamples.get(name);
	    else {
		ProfileSample sample = new TaskLocalProfileSample(name);
		taskLocalSamples.put(name, sample);
		return sample;
	    }
	}	
	else {
	    if (aggregateSamples.containsKey(name)) {
		return aggregateSamples.get(name);
	    }
	    else {
		ProfileSample sample = 
		    new AggregateProfileSample(name, maxSamples);
		aggregateSamples.put(name,sample);
		return sample;
	    }
	}
    }

    /** A base-class for creating {@code ProfileSample}s. */
    private abstract class AbstractProfileSample implements ProfileSample {

        private final String name;

        private final boolean taskLocal;

        AbstractProfileSample(String name, boolean taskLocal) {
            this.name = name;
            this.taskLocal = taskLocal;
        }

        public String getSampleName() {
            return name;
        }

        public boolean isTaskLocal() {
            return taskLocal;
        }

        protected ProfileReportImpl getReport() {
            try {
                return profileReports.get().peek();
            } catch (EmptyStackException ese) {
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            }
        }

    }

    /** The task-local implementation of {@code ProfileSample} */
    private class TaskLocalProfileSample extends AbstractProfileSample {
       
 
	TaskLocalProfileSample(String name) {
            super(name, true);
        }
        public void addSample(long value) {
            getReport().addLocalSample(getSampleName(), value);
        }

    }


    /**
     * The {@code ProfileSample} implementation that collects samples
     * for the lifetime the program.
     */
    private class AggregateProfileSample extends AbstractProfileSample {
        
	private final LinkedList<Long> samples;

	private final long maxSamples;       
	
	AggregateProfileSample(String name, long maxSamples) {
            super(name, false);
	    this.maxSamples = maxSamples;
	    samples = new LinkedList<Long>();
        }

        public void addSample(long value) {
	    if (samples.size() == maxSamples)
		samples.removeFirst(); // remove oldest
	    samples.add(value);
	    // NOTE: we return a sub-list view to ensure that the
	    // ProfileReport only sees the samples that were added,
	    // during its lifetime.  Creating a sub-list view is much
	    // faster than copying the list, and is suitable for this
	    // situation.  However, we still rely on the the
	    // ProfileReport to make the view unmodifyable.  This
	    // isn't done here since we can avoid doing that operation
	    // until the getSamples() call, instead of having to do it
	    // for each sample addition
	    getReport().
		registerAggregateSamples(getSampleName(), 
					 samples.subList(0, samples.size()));
        }

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

                    // make sure that the collections can't be modified by
                    // a listener
                    profileReport.ops =
                        Collections.unmodifiableList(profileReport.ops);
                    profileReport.participants =
                        Collections.
                        unmodifiableSet(profileReport.participants);

                    for (ProfileListener listener : listeners)
                        listener.report(profileReport);
                }
            } catch (InterruptedException ie) {}
        }
	/**
	 * Modify interrupt to keep track of whether the thread has ever been
	 * interrupted.  That way we can have the thread exit if an interrupt
	 * has occurred, even if something (say logging) catches an interrupt
	 * and forgets to reset the interrupt status.
	 */
	@Override
	public void interrupt() {
	    synchronized (this) {
		interrupted = true;
	    }
	    super.interrupt();
	}
    }
}
