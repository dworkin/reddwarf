
package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileOperationListener;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskOwner;

import java.util.ArrayList;
import java.util.Collections;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * This is the main aggregation point for profiling data. Instances of
 * this class are used to collect data from arbitrary sources (typically
 * <code>ProfileConsumer</code>s or the scheduler itself) and keep
 * track of which tasks are generating which data.
 * <p>
 * This class allows instances of <code>ProfileOperationListener</code> to
 * register as listeners for reported data. All reporting to these
 * listeners is done in a single thread. This means that listeners do
 * not need to worry about being called from multiple threads. It also
 * means that listeners should be efficient in handling reports, since
 * they are blocking all other listeners.
 */
public class ProfileCollector {

    // the next free operation identifier to use
    private AtomicInteger nextOpId;

    // the maximum number of operations we allow
    // NOTE: this should become dynamic, but for now we will never
    // hit this upper-bound
    static final int MAX_OPS = 256;

    // the set of already-allocated operations
    private ProfileOperationImpl [] ops = new ProfileOperationImpl[MAX_OPS];

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
     * Creates an instance of <code>ProfileCollector</code>.
     *
     * @param resourceCoordinator a <code>ResourceCoordinator</code> used
     *                            to run collecting and reporting tasks
     */
    public ProfileCollector(ResourceCoordinator resourceCoordinator) {
        nextOpId = new AtomicInteger(0);
        schedulerThreadCount = 0;
        listeners = new ArrayList<ProfileOperationListener>();
        queue = new LinkedBlockingQueue<ProfileReportImpl>();

        // start a long-lived task to consume the other end of the queue
        resourceCoordinator.startTask(new CollectorRunnable(), null);
    }

    /**
     * Adds a <code>ProfileOperationListener</code> as a listener for
     * profiling data reports. The listener is immediately updated on
     * the current set of operations and the number of scheduler
     * threads.
     *
     * @param listener the <code>ProfileOperationListener</code> to add
     */
    public void addListener(ProfileOperationListener listener) {
        listeners.add(listener);
        listener.notifyThreadCount(schedulerThreadCount);
        for (int i = 0; i < nextOpId.get(); i++)
            listener.notifyNewOp(ops[i]);
    }

    /**
     * Notifies the collector that a thread has been added to the scheduler.
     */
    public void notifyThreadAdded() {
        schedulerThreadCount++;
        for (ProfileOperationListener listener : listeners)
            listener.notifyThreadCount(schedulerThreadCount);
    }

    /**
     * Notifies the collector that a thread has been removed from the
     * scheduler.
     */
    public void notifyThreadRemoved() {
        schedulerThreadCount--;
        for (ProfileOperationListener listener : listeners)
            listener.notifyThreadCount(schedulerThreadCount);
    }

    /**
     * Tells the collector that a new task is starting in the context of
     * the calling thread. Any previous task must have been cleared from the
     * context of this thread via a call to <code>finishTask</code>.
     *
     * @param task the <code>KernelRunnable</code> that is starting
     * @param owner the <code>TaskOwner</code> of the task
     * @param scheduledStartTime the requested starting time for the task
     *
     * @throws IllegalStateException if a task is already bound to this thread
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
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to <code>startTask</code>) is
     * transactional. This does not mean that all operations of the task
     * are transactional, but that at least some of the task is run in a
     * transactional context.
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    public void noteTransactional() {
        ProfileReportImpl profileReport = profileReports.get();
        if (profileReport == null)
            throw new IllegalStateException("No task is being Profile in " +
                                            "this thread");
        profileReport.transactional = true;
    }

    /**
     * Tells the collector that the current task associated with the calling
     * thread (as associated by a call to <code>startTask</code>) is now
     * finished.
     *
     * @param tryCount the number of times that the task has tried to run
     * @param taskSucceeded <code>true</code> if the task ran to completion,
     *                      <code>false</code> if the task failed and is
     *                      going to be re-tried or dropped
     *
     * @throws IllegalStateException if no task is bound to this thread
     */
    public void finishTask(int tryCount, boolean taskSucceeded) {
        long stopTime = System.currentTimeMillis();
        ProfileReportImpl profileReport = profileReports.get();
        if (profileReport == null)
            throw new IllegalStateException("No task is being Profile in " +
                                            "this thread");
        profileReports.set(null);

        profileReport.runningTime = stopTime - profileReport.actualStartTime;
        profileReport.tryCount = tryCount;
        profileReport.succeeded = taskSucceeded;

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
     *         to this collector
     *
     * @throws IllegalStateException if no more operations can be registered
     */
    ProfileOperation registerOperation(String opName, String producerName) {
        int opId = nextOpId.getAndIncrement();
        if (opId >= MAX_OPS) {
            nextOpId.set(MAX_OPS);
            throw new IllegalStateException("No more operations may be " +
                                            "registered in the collector");
        }
        ops[opId] = new ProfileOperationImpl(opName, opId);
        for (ProfileOperationListener listener : listeners)
            listener.notifyNewOp(ops[opId]);
        return ops[opId];
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

                    // make sure that the list can't be modified by a listener
                    profileReport.ops =
                        Collections.unmodifiableList(profileReport.ops);

                    for (ProfileOperationListener listener : listeners)
                        listener.report(profileReport);
                }
            } catch (InterruptedException ie) {}
        }
    }

}
