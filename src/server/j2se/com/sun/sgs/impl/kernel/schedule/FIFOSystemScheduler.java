
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a very simple implementation of <code>SystemScheduler</code> that
 * is backed by a single <code>FIFOApplicationScheduler</code>. All tasks
 * for all applications are placed in the same queue, and run in a First-In
 * First-Out model. No attempt is made to prioritize tasks, and no application
 * is given any advantage over any other.
 * <p>
 * This scheduler uses an un-bounded queue. Unless the system runs out of
 * memory, this should always accept any tasks from any user.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class FIFOSystemScheduler implements SystemScheduler, ProfilingConsumer {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(FIFOSystemScheduler.
                                           class.getName()));

    // the single application scheduler used to handle delay and period
    private FIFOApplicationScheduler appScheduler;

    /**
     * Creates a new instance of <code>FIFOSystemScheduler</code>.
     */
    public FIFOSystemScheduler(Properties properties) {
        logger.log(Level.CONFIG, "Creating a FIFO System Scheduler");

        appScheduler = new FIFOApplicationScheduler(properties);
    } 

    /**
     * {@inheritDoc}
     */
    public void registerApplication(KernelAppContext context) {
        // this scheduler lumps all applications into the same queue, so
        // there's no need to kep track of any specific context
    }

    /**
     * {@inheritDoc}
     */
    public ScheduledTask getNextTask() throws InterruptedException {
        return appScheduler.getNextTask(true);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(ScheduledTask task) {
        return appScheduler.reserveTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void addTask(ScheduledTask task) {
        appScheduler.addTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
        return appScheduler.addRecurringTask(task);
    }

}
