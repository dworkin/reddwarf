
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a basic implemenation of <code>ApplicationScheduler</code> that
 * accepts tasks into an un-bounded queue and runs them in the order that
 * they are ready.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class FIFOApplicationScheduler
    implements ApplicationScheduler, ProfilingConsumer {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(FIFOApplicationScheduler.
                                           class.getName()));

    // our parent scheduler
    private final SystemScheduler systemScheduler;

    // the milliseconds in the future that a task must be to be stuck
    // in the timer instead of executed directly
    private static final int FUTURE_THRESHOLD = 15;

    // the timer used for future execution
    private Timer timer;

    /**
     * Creates an instance of <code>FIFOApplicationScheduler</code>.
     *
     * @param systemScheduler the <code>SystemScheduler</code> that is the
     *                        parent of this <code>ApplicationScheduler</code>
     */
    public FIFOApplicationScheduler(SystemScheduler systemScheduler) {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Creating a FIFO Application Scheduler");

        this.systemScheduler = systemScheduler;
        timer = new Timer();
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        return new FIFOTaskReservation(this, task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        // get a new TimerTask if this task is scheduled to run far enough
        // in the future that we want to give it to the Timer
        TimerTask tt = null;
        if (task.getStartTime() > (System.currentTimeMillis() +
                                   FUTURE_THRESHOLD))
            tt = new FIFOTimerTask(this, task);

        // next, see if this is a recurring task, and if so keep track of
        // the new TimerTask, or just return if the task has been cancelled
        if (task.isRecurring()) {
            FIFORecurringHandle handle =
                (FIFORecurringHandle)(task.getRecurringTaskHandle());
            synchronized (handle) {
                if (handle.isCancelled())
                    return;
                handle.setTimerTask(tt);
            }
        }

        // finally, hand off the task to the appropriate place
        if (tt == null) {
            try {
                systemScheduler.giveReadyTask(task);
            } catch (InterruptedException ie) {
                throw new TaskRejectedException("Request was interrupted", ie);
            }
        } else {
            timer.schedule(tt, new Date(task.getStartTime()));
        }
    }

    /**
     * Private helper used by our <code>FIFOTimerTask</code>s to notify
     * the scheduler that a delayed task has now hit its time to run.
     */
    private void addTimedTask(ScheduledTask task) {
        while (true) {
            // NOTE: This method is not allowed to fail, because when we
            // accepted the task for future execution, we guaranteed that
            // it would run. As a result, any interruptions here are
            // ignored, and we keep trying to give the task. The alternative
            // would be to add this back into the Timer and hope that it
            // executes next time, but that could lead to falling well
            // behind without visibility into this problem. When management
            // is added to the stack, that may help define why an interruption
            // occurred and how we should actually react.
            try {
                systemScheduler.giveReadyTask(task);
                break;
            } catch (InterruptedException ie) {}
        }
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        FIFORecurringHandle handle = new FIFORecurringHandle(this, task);
        if (! task.setRecurringTaskHandle(handle)) {
            logger.log(Level.SEVERE, "a scheduled task was given a new " +
                       "RecurringTaskHandle");
            throw new IllegalArgumentException("cannot re-assign handle");
        }
        return handle;
    }

    /**
     * Private inner class implementation of <code>TaskReservation</code>.
     * All reservations given out by <code>FIFOApplicationScheduler</code>
     * are of this type. These reservations are fairly light-weight since
     * the scheduler is unbounded, and therefore doesn't actually track
     * these reservations or use them to actually reserve any space.
     */
    private static class FIFOTaskReservation implements TaskReservation {
        private volatile boolean finished = false;
        private final FIFOApplicationScheduler scheduler;
        private final ScheduledTask task;
        public FIFOTaskReservation(FIFOApplicationScheduler scheduler,
                                   ScheduledTask task) {
            this.scheduler = scheduler;
            this.task = task;
        }
        public void cancel() {
            if (finished)
                throw new IllegalStateException("cannot cancel reservation");
            // do nothing, because there is nothing stored
            finished = true;
        }
        public void use() {
            synchronized (this) {
                if (finished)
                    throw new IllegalStateException("cannot use reservation");
                finished = true;
            }
            scheduler.addTask(task);
        }
    }

    /**
     * Private inner class implementation of <code>RecurringTaskHandle</code>.
     * All of the handles given out by <code>FIFOApplicationScheduler</code>
     * are of this type. Unlike reservations, these handles do need to do
     * a little work, since cancelling a handle also involves cancelling
     * its current <code>TimerTask</code>, if it has one.
     */
    private static class FIFORecurringHandle implements RecurringTaskHandle {
        private final ApplicationScheduler scheduler;
        private final ScheduledTask task;
        private TimerTask timerTask = null;
        private boolean cancelled = false;
        private boolean started = false;
        public FIFORecurringHandle(ApplicationScheduler scheduler,
                                   ScheduledTask task) {
            this.scheduler = scheduler;
            this.task = task;
        }
        synchronized void setTimerTask(TimerTask timerTask) {
            this.timerTask = timerTask;
        }
        synchronized boolean isCancelled() {
            return cancelled;
        }
        public void cancel() {
            synchronized (this) {
                if (cancelled)
                    throw new IllegalStateException("cannot cancel task");
                cancelled = true;
            }
            if (timerTask != null)
                timerTask.cancel();
        }
        public void start() {
            synchronized (this) {
                if ((cancelled) || (started))
                    throw new IllegalStateException("cannot start task");
                started = true;
            }
            scheduler.addTask(task);
        }
    }

    /**
     * Private inner class implementation of <code>TimerTask</code>. This is
     * used to schedule all delayed tasks with a single <code>Timer</code>.
     * The handles keep a reference to their current task, so they can
     * cancel it when they are cancelled.
     */
    private static class FIFOTimerTask extends TimerTask {
        private final FIFOApplicationScheduler scheduler;
        private final ScheduledTask task;
        private volatile boolean cancelled = false;
        public FIFOTimerTask(FIFOApplicationScheduler scheduler,
                             ScheduledTask task) {
            this.scheduler = scheduler;
            this.task = task;
        }
        public boolean cancel() {
            if (! cancelled) {
                cancelled = true;
                return true;
            }
            return false;
        }
        public long scheduledExecutionTime() {
            return task.getStartTime();
        }
        public void run() {
            synchronized (this) {
                if (! cancelled)
                    scheduler.addTimedTask(task);
                cancelled = true;
            }
        }
    }

}
