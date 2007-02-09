
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.TaskReservation;


/**
 * Package-private utility implementation of <code>TaskReservation</code>.
 * These reservations are fairly light-weight, and assume that the scheduler
 * is unbounded and therefore doesn't actually track these reservations or
 * use them to actually reserve any space. If your scheduler is bound, or
 * needs to explicitly reserve space, then it will need to define its own
 * implementation of <code>TaskReservation</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class SimpleTaskReservation implements TaskReservation {

    // whether the reservation has been used or cancelled
    private boolean finished = false;

    // the associated scheduler
    private final ApplicationScheduler scheduler;

    // the actual reserved task
    private final ScheduledTask task;

    /**
     * Creates an instance of <code>SimpleTaskReservation</code>.
     *
     * @param scheduler the associated <code>ApplicationScheduler</code>
     * @param task the <code>ScheduledTask</code> being reserved
     */
    public SimpleTaskReservation(ApplicationScheduler scheduler,
                                 ScheduledTask task) {
        if (scheduler == null)
            throw new NullPointerException("Scheduler cannot be null");
        if (task == null)
            throw new NullPointerException("Task cannot be null");

        this.scheduler = scheduler;
        this.task = task;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void cancel() {
        if (finished)
            throw new IllegalStateException("cannot cancel reservation");
        finished = true;
    }

    /**
     * {@inheritDoc}
     */
    public void use() {
        synchronized (this) {
            if (finished)
                throw new IllegalStateException("cannot use reservation");
            finished = true;
        }
        scheduler.addTask(task);
    }

}
