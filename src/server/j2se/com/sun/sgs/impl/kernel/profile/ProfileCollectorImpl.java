/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileCollector;
import com.sun.sgs.kernel.ProfileCounter;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileOperationListener;
import com.sun.sgs.kernel.ProfileParticipantDetail;
import com.sun.sgs.kernel.ProfileSample;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

    // NOTE: this is not used by this class but is necessary for other
    //       classes which reference it for determining how many
    //       operations they should support
    static final int MAX_OPS = 1024; 

    // the next free operation identifier to use
    private AtomicInteger nextOpId;

    private final AtomicInteger opIdCounter;

    private final ConcurrentHashMap<String,ProfileOperationImpl> ops;

    private final ConcurrentHashMap<String,ProfileSample> taskLocalSamples;

    private final ConcurrentHashMap<String,ProfileSample> lifetimeSamples;

    private final ConcurrentHashMap<String,ProfileCounter> taskLocalCounters;

    private final ConcurrentHashMap<String,ProfileCounter> lifetimeCounters;

    
    // the number of threads currently in the scheduler
    private volatile int schedulerThreadCount;

    // the set of registered listeners
    private ArrayList<ProfileOperationListener> listeners;

    // thread-local detail about the current task, used to let us
    // aggregate data across all participants in a given task
    private ThreadLocal<ProfileReportImpl> profileReports =
        new ThreadLocal<ProfileReportImpl>() {
            protected ProfileReportImpl initialValue() {
                return null;
            }
        };

    // the incoming report queue
    private LinkedBlockingQueue<ProfileReportImpl> queue;

    /**
     * Creates an instance of <code>ProfileCollectorImpl</code>.
     *
     * @param resourceCoordinator a <code>ResourceCoordinator</code> used
     *                            to run collecting and reporting tasks
     */
    public ProfileCollectorImpl(ResourceCoordinator resourceCoordinator) {

        schedulerThreadCount = 0;
	ops = new ConcurrentHashMap<String,ProfileOperationImpl>();
        listeners = new ArrayList<ProfileOperationListener>();
        queue = new LinkedBlockingQueue<ProfileReportImpl>();
	opIdCounter = new AtomicInteger(0);

	lifetimeSamples = new ConcurrentHashMap<String,ProfileSample>(); 
	taskLocalSamples = new ConcurrentHashMap<String,ProfileSample>();

	lifetimeCounters = new ConcurrentHashMap<String,ProfileCounter>(); 
	taskLocalCounters = new ConcurrentHashMap<String,ProfileCounter>(); 

        // start a long-lived task to consume the other end of the queue
        resourceCoordinator.startTask(new CollectorRunnable(), null);
    }

    /**
     * {@inheritDoc}
     */
    public void addListener(ProfileOperationListener listener) {
        listeners.add(listener);
        listener.notifyThreadCount(schedulerThreadCount);
	for (ProfileOperationImpl p : ops.values()) 
	    listener.notifyNewOp(p);
    }

    /**
     * {@inheritDoc}
     */
    public void notifyThreadAdded() {
        schedulerThreadCount++;
        for (ProfileOperationListener listener : listeners)
            listener.notifyThreadCount(schedulerThreadCount);
    }

    /**
     * {@inheritDoc}
     */
    public void notifyThreadRemoved() {
        schedulerThreadCount--;
        for (ProfileOperationListener listener : listeners)
            listener.notifyThreadCount(schedulerThreadCount);
    }

    /**
     * {@inheritDoc}
     */
    public void startTask(KernelRunnable task, TaskOwner owner,
                          long scheduledStartTime, int readyCount) {
        if (profileReports.get() != null)
            throw new IllegalStateException("A task is already being " +
                                            "profiled in this thread");
        profileReports.set(new ProfileReportImpl(task, owner,
                                                 scheduledStartTime,
                                                 readyCount));
    }

    /**
     * {@inheritDoc}
     */
    public void noteTransactional() {
        ProfileReportImpl profileReport = profileReports.get();
        if (profileReport == null)
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        profileReport.transactional = true;
    }

    /**
     * {@inheritDoc}
     */
    public void addParticipant(ProfileParticipantDetail participantDetail) {
        ProfileReportImpl profileReport = profileReports.get();
        if (profileReport == null)
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        if (! profileReport.transactional)
            throw new IllegalStateException("Participants cannot be added " +
                                            "to a non-transactional task");
        profileReport.participants.add(participantDetail);
    }

    /**
     * {@inheritDoc}
     */
    public void finishTask(int tryCount, boolean taskSucceeded) {
	finishTask(tryCount, taskSucceeded, null);
    }

    /**
     * {@inheritDoc}
     */
    public void finishTask(int tryCount, boolean taskSucceeded, 
			   Exception exception) {
        long stopTime = System.currentTimeMillis();
        ProfileReportImpl profileReport = profileReports.get();
        if (profileReport == null)
            throw new IllegalStateException("No task is being profiled in " +
                                            "this thread");
        profileReports.set(null);

        profileReport.runningTime = stopTime - profileReport.actualStartTime;
        profileReport.tryCount = tryCount;
        profileReport.succeeded = taskSucceeded;
	profileReport.exception = exception;
	taskLocalSamples.clear();
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
     * @return a new <code>ProfileOperation</code> that will report back
     *         to this collector.
     *
     * @throws IllegalStateException if no more operations can be registered
     */
    ProfileOperation registerOperation(String opName, String producerName) {
	String name = producerName + "::" + opName;
	int opId = opIdCounter.getAndIncrement();
	ProfileOperationImpl op = new ProfileOperationImpl(opName, opId);
	ProfileOperationImpl prev = ops.putIfAbsent(name,op);
	if (prev != null)
	    op = prev;

        for (ProfileOperationListener listener : listeners)
            listener.notifyNewOp(op);
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
            ProfileReportImpl profileReport = profileReports.get();
            if (profileReport == null)
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            profileReport.ops.add(this);
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
	    if (lifetimeCounters.containsKey(name)) {
		return lifetimeCounters.get(name);
	    }
	    else {
		ProfileCounter counter = 
		    new AggregateProfileCounter(counterName);
		lifetimeCounters.put(name, counter);
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
            ProfileReportImpl profileReport = profileReports.get();
            if (profileReport == null)
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            return profileReport;
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
	    if (lifetimeSamples.containsKey(name)) {
		return lifetimeSamples.get(name);
	    }
	    else {
		ProfileSample sample = 
		    new LifetimeProfileSample(name, maxSamples);
		lifetimeSamples.put(name,sample);
		return sample;
	    }
	}
    }

    /**
     * A base-class for creating {@code ProfileSample}s.
     */
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
            ProfileReportImpl profileReport = profileReports.get();
            if (profileReport == null)
                throw new IllegalStateException("Cannot report operation " +
                                                "because no task is active");
            return profileReport;
        }

    }

    /**
     * The task-local implementation of {@code ProfileSample}
     */
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
    private class LifetimeProfileSample extends AbstractProfileSample {
        
	private final LinkedList<Long> samples;

	private final long maxSamples;       
	
	LifetimeProfileSample(String name, long maxSamples) {
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
	    getReport().registerLifetimeSamples(getSampleName(), 
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

                    for (ProfileOperationListener listener : listeners)
                        listener.report(profileReport);
                }
            } catch (InterruptedException ie) {}
        }
    }
}
