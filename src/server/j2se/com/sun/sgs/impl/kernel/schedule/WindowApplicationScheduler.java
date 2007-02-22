
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Collection;
import java.util.Properties
;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>WindowApplicationScheduler</code> tries to
 * provide fairness between users without taking too long to service any
 * given user's tasks. This is typified by the phrase "no one gets seconds
 * until everyone who wants them has had firsts."
 * <p>
 * The implementation uses a priority queue, keyed off what is called a
 * window value. Users submit tasks into increasing window values, and
 * may never submit tasks into windows that have already passed. See
 * SUN070191 for more details.
 */
class WindowApplicationScheduler
    implements ApplicationScheduler, TimedTaskConsumer {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(WindowApplicationScheduler.
                                           class.getName()));

    // the priority queue
    private PriorityBlockingQueue<QueueElement> queue;

    // the map of users to their windows
    private ConcurrentHashMap<TaskOwner,QueueUser> userMap;

    // the handler for all delayed tasks
    private final TimedTaskHandler timedTaskHandler;

    /**
     * Creates an instance of <code>WindowApplicationScheduler</code>.
     *
     * @param properties the application <code>Properties</code>
     */
    public WindowApplicationScheduler(Properties properties) {
        logger.log(Level.CONFIG, "Creating a Window Application Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");

        queue = new PriorityBlockingQueue<QueueElement>();
        userMap = new ConcurrentHashMap<TaskOwner,QueueUser>();
        timedTaskHandler = new TimedTaskHandler(this);
    }

    /**
     * {@inheritDoc}
     */
    public int getReadyCount() {
        return queue.size();
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask(boolean wait)
        throws InterruptedException
    {
        // try to get the next element, and return the result if we're
        // not waiting, otherwise block
        QueueElement element = queue.poll();
        if (element != null)
            return element.getTask();
        if (! wait)
            return null;
        return queue.take().getTask();
    }

    /**
     * {@inheritDoc}
     */
    public int getNextTasks(Collection<ScheduledTask> tasks, int max) {
        for (int i = 0; i < max; i++) {
            QueueElement element = queue.poll();
            if (element == null)
                return i;
            tasks.add(element.getTask());
        }
        return max;
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        if (task.isRecurring())
            throw new TaskRejectedException("Recurring tasks cannot get " +
                                            "reservations");

        return new SimpleTaskReservation(this, task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        if (task == null)
            throw new NullPointerException("Task cannot be null");

        if (! timedTaskHandler.runDelayed(task))
            timedTaskReady(task);
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        if (task == null)
            throw new NullPointerException("Task cannot be null");
        if (! task.isRecurring())
            throw new IllegalArgumentException("Not a recurring task");

        InternalRecurringTaskHandle handle =
            new RecurringTaskHandleImpl(this, task);
        if (! task.setRecurringTaskHandle(handle)) {
            logger.log(Level.SEVERE, "a scheduled task was given a new " +
                       "RecurringTaskHandle");
            throw new IllegalArgumentException("cannot re-assign handle");
        }
        return handle;
    }

    /**
     * {@inheritDoc}
     */
    public void notifyCancelled(ScheduledTask task) {
        // FIXME: do we want to pull the task out of the queue?
    }

    /**
     * {@inheritDoc}
     */
    public void timedTaskReady(ScheduledTask task) {
        // get the user details, creating an entry for the user if it's not
        // there already
        QueueUser user = userMap.get(task.getOwner());
        if (user == null) {
            userMap.putIfAbsent(task.getOwner(), new QueueUser());
            user = userMap.get(task.getOwner());
        }

        QueueElement nextElement = null;
        long scheduledWindow = 0L;

        // make sure that we're only scheduling one task for a given user,
        // so that we get a consistant view on the user's window counter
        synchronized (user) {
            // see what window we're currently on, which will be the user's
            // next counter if there's nothing in the queue...this does
            // break the intent of the counter always going up, but this
            // is also a rare case in active schedulers, and active users
            // should catch-up the window pretty quickly
            long currentWindow = 0L;
            nextElement = queue.peek();
            if (nextElement != null)
                currentWindow = nextElement.getWindow();
            scheduledWindow = (currentWindow > user.nextWindow) ?
                currentWindow : user.nextWindow;
            user.nextWindow = scheduledWindow + 1;
            user.lastScheduled = task.getStartTime();
        }

        nextElement = new QueueElement(scheduledWindow, task);
        while (! queue.offer(nextElement));
    }

    public void shutdown() {
        timedTaskHandler.shutdown();
    }

    // Private class used to manage the priority queue
    private class QueueElement implements Comparable<QueueElement> {
        private final long window;
        private final ScheduledTask task;
        private final long timestamp;
        public QueueElement(long window, ScheduledTask task) {
            this.window = window;
            this.task = task;
            this.timestamp = task.getStartTime();
        }
        public long getWindow() {
            return window;
        }
        public ScheduledTask getTask() {
            return task;
        }
        public int compareTo(QueueElement other) {
            // if the other window is bigger, then their priority is lower
            if (window < other.window)
                return -1;
            // if the other window is smaller, then their priority is higher
            if (window > other.window)
                return 1;
            // the windows are the same, so check timestamps, with the
            // same rules as above
            if (timestamp < other.timestamp)
                return -1;
            if (timestamp > other.timestamp)
                return 1;
            // NOTE: if the windows and timestamps are the same, here is
            // where we might fall-back on other values, but for now we'll
            // just say they've got the same priority
            return 0;
        }
    }

    // NOTE: for this first-pass implementation, we're not using any
    // notion of priority, so there's just a single window counter
    // NOTE: the lastScheduled field is there so that periodically we can
    // kick off a thread to reap any users that haven't been scheduled
    // within some delta, but for now there won't be enough users to worry
    // about this case, so this feature isn't implemented
    private class QueueUser {
        public long nextWindow = 0L;
        public long lastScheduled;
    }

}
