
package com.sun.sgs.test.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.kernel.MinimalTestKernel;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.LinkedBlockingQueue;


/**
 * This implementation of <code>TaskScheduler</code> is provided for testing
 * only. Note that it ignores task owners, since the tests are run with only
 * one owner present.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DummyTaskScheduler implements TaskScheduler {

    // flag to reject all tasks
    private final boolean rejectTasks;

    // the timer used to run all tasks
    private Timer timer;

    // the queue of tasks
    private LinkedBlockingQueue<KernelRunnable> queue;

    // the allocated threads
    private HashSet<Thread> threads;

    /**
     * Creates an instance of <code>DummyTaskScheduler</code>. This scheduler
     * is intended only for testing purposes. It is either an infinite
     * scheduler that will accept any number of tasks and run them as soon
     * as possible, or an empty scheduler that will reject all tasks.
     *
     * @param context the <code>KernelAppContext</code> to use for the tasks
     *                run through this scheduler, which can be
     *                <code>null</code> if <code>rejectTasks</code> is
     *                <code>true</code>
     * @param rejectTasks <code>false</code> if this is an infinite scheduler,
     *                    <code>true</code> to cause all scheduling methods
     *                    to throw <code>TaskRejectedException</code>
     */
    public DummyTaskScheduler(KernelAppContext context, boolean rejectTasks) {
        this.rejectTasks = rejectTasks;
        this.timer = new Timer();

        this.queue = new LinkedBlockingQueue<KernelRunnable>();
        this.threads = new HashSet<Thread>();

        if (! rejectTasks) {
            // NOTE: this is just to make sure there's more than one thread,
            // but this should probably be tunable
            for (int i = 0; i < 2; i++) {
                Thread thread =
                    MinimalTestKernel.createThread(new ConsumerRunnable(),
                                                   context);
                threads.add(thread);
                thread.start();
            }
        }
    }

    /**
     * A simple routine that interrupts all the threads so that they will
     * stop running.
     */
    public void shutdown() {
        for (Thread thread : threads)
            thread.interrupt();
        timer.cancel();
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner) {
        if (rejectTasks)
            throw new TaskRejectedException("Reservation unavailable");
        return new TaskReservationImpl(task);
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Note that this ignores priority. All tasks are scheduled at the
     * default priority.
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
                                       Priority priority) {
        if (rejectTasks)
            throw new TaskRejectedException("Reservation unavailable");
        return new TaskReservationImpl(task);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, TaskOwner owner,
                                       long startTime) {
        if (rejectTasks)
            throw new TaskRejectedException("Reservation unavailable");
        return new TaskReservationImpl(task, startTime);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTasks(Collection<? extends KernelRunnable>
                                        tasks, TaskOwner owner) {
        if (rejectTasks)
            throw new TaskRejectedException("Reservation unavailable");
        return new TaskReservationImpl(tasks);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner) {
        scheduleTask(task, owner, System.currentTimeMillis());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that this ignores priority. All tasks are scheduled at the
     * default priority.
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner,
                             Priority priority) {
        scheduleTask(task, owner, System.currentTimeMillis());
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, TaskOwner owner,
                             long startTime) {
        if (startTime <= System.currentTimeMillis()) {
            // if the time has passed then schedule to run immediately...
            try {
                queue.put(task);
            } catch (InterruptedException ie) {}
        } else {
            // ...otherwise add to the timer for future execution
            timer.schedule(new TimerTaskImpl(task, startTime),
                           new Date(startTime));
        }
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     TaskOwner owner,
                                                     long startTime,
                                                     long period) {
        if (rejectTasks)
            throw new TaskRejectedException("could not schedule task");
        return new RecurringTaskHandleImpl(task, startTime, period);
    }
    
    /**
     * A private implementation of <code>TaskReservation</code> that
     * makes no attempt to actually reserve space, since the scheduler
     * is inifinite (or empty).
     */
    private class TaskReservationImpl implements TaskReservation {
        private final KernelRunnable task;
        private final Collection<? extends KernelRunnable> tasks;
        private final long startTime;
        private boolean cancelledOrUsed = false;
        TaskReservationImpl(KernelRunnable task) {
            this(task, System.currentTimeMillis());
        }
        TaskReservationImpl(KernelRunnable task, long startTime) {
            this.task = task;
            this.tasks = null;
            this.startTime = startTime;
        }
        TaskReservationImpl(Collection<? extends KernelRunnable> tasks) {
            this.task = null;
            this.tasks = tasks;
            this.startTime = System.currentTimeMillis();
        }
        public synchronized void cancel() {
            if (cancelledOrUsed)
                throw new IllegalStateException("cannot cancel");
            cancelledOrUsed = true;
        }
        public synchronized void use() {
            if (cancelledOrUsed)
                throw new IllegalStateException("cannot use"); 
            cancelledOrUsed = true;
            if (task != null) {
                scheduleTask(task, null, startTime);
            } else {
                for (KernelRunnable runnable : tasks)
                    scheduleTask(runnable, null, startTime);
            }
        }
    }

    /**
     * A private implementation of <code>RecurringTaskHandle</code>.
     */
    private class RecurringTaskHandleImpl implements RecurringTaskHandle {
        final KernelRunnable task;
        private final long startTime;
        final long period;
        private boolean cancelled = false;
        RecurringTaskHandleImpl(KernelRunnable task, long startTime,
                                long period) {
            this.task = task;
            this.startTime = startTime;
            this.period = period;
        }
        public synchronized void cancel() {
            if (cancelled)
                throw new IllegalStateException("cannot cancel");
            cancelled = true;
        }
        synchronized boolean isCancelled() {
            return cancelled;
        }
        public synchronized void start() {
            if (cancelled)
                throw new IllegalStateException("cannot start");
            timer.schedule(new TimerTaskImpl(this, startTime),
                           new Date(startTime));
        }
    }

    /**
     * A private class used used to manage all pending tasks.
     */
    private class TimerTaskImpl extends TimerTask {
        private final KernelRunnable task;
        private final RecurringTaskHandleImpl recurringTask;
        private final long runTime;
        private boolean cancelled = false;
        TimerTaskImpl(KernelRunnable task, long runTime) {
            this.task = task;
            this.recurringTask = null;
            this.runTime = runTime;
        }
        TimerTaskImpl(RecurringTaskHandleImpl recurringTask, long runTime) {
            this.task = recurringTask.task;
            this.recurringTask = recurringTask;
            this.runTime = runTime;
        }
        public boolean cancel() {
            if (cancelled)
                return false;
            cancelled = true;
            reschedule();
            return true;
        }
        public long scheduledExecutionTime() {
            return runTime;
        }
        public void run() {
            if (! cancelled) {
                try {
                    queue.put(task);
                } catch (InterruptedException ie) {}
            }
            reschedule();
        }
        /** Used to re-schedule recurring, non-cancelled tasks */
        private void reschedule() {
            if ((recurringTask == null) || (recurringTask.isCancelled()))
                return;
            long newTime = runTime + recurringTask.period;
            timer.schedule(new TimerTaskImpl(recurringTask, newTime),
                           new Date(newTime));
        }
    }

    /**
     * A simple consumer that is run by all of the scheduler threads to
     * fetch tasks and run them.
     */
    private class ConsumerRunnable implements Runnable {
        public void run() {
            while (true) {
                KernelRunnable nextTask;
                try {
                    nextTask = queue.take();
                } catch (InterruptedException ie) {
                    return;
                }

                while (true) {
                    try {
                        nextTask.run();
                        break;
                    } catch (Exception e) {
                        // if the exception didn't specify retry, we're done
                        if ((! (e instanceof ExceptionRetryStatus)) ||
                            (! ((ExceptionRetryStatus)e).shouldRetry()))
                            break;
                    }
                }
            }
        }
    }

}
