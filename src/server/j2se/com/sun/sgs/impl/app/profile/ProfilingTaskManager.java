
package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.kernel.ProfiledOperation;
import com.sun.sgs.kernel.ProfilingConsumer;
import com.sun.sgs.kernel.ProfilingProducer;


/**
 * This is an implementation of <code>TaskManager</code> used to support
 * profiling. It simply calls its backing manager for each manager method
 * after first reporting the call. If no <code>ProfilingConsumer</code> is
 * provided via <code>setProfilingConsumer</code> then this manager does no
 * reporting, and only calls through to the backing manager. If the backing
 * manager is also an instance of <code>ProfilingProducer</code> then it too
 * will be supplied with the <code>ProfilingConsumer</code> as described in
 * <code>setProfilingConsumer</code>.
 * <p>
 * All of the standard Manager methods implemented here are profiled
 * directly.
 */
public class ProfilingTaskManager implements TaskManager, ProfilingProducer {

    // the task manager that this manager calls through to
    private final TaskManager backingManager;

    // the reporting interface
    private ProfilingConsumer consumer = null;

    // the operations being profiled
    private ProfiledOperation scheduleTaskOp = null;
    private ProfiledOperation scheduleTaskDelayedOp = null;
    private ProfiledOperation scheduleTaskPeriodicOp = null;

    /**
     * Creates an instance of <code>ProfilingTaskManager</code>.
     *
     * @param backingManager the <code>TaskManager</code> to call through to
     */
    public ProfilingTaskManager(TaskManager backingManager) {
        this.backingManager = backingManager;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that if the backing manager supplied to the constructor is also
     * an instance of <code>ProfilingProducer</code> then its
     * <code>setProfilingConsumer</code> will be invoked when this method
     * is called. The backing manager is provided the same instance of
     * <code>ProfilingConsumer</code> so reports from the two managers are
     * considered to come from the same source.
     *
     * @throws IllegalStateException if a <code>ProfilingConsumer</code>
     *                               has already been set
     */
    public void setProfilingConsumer(ProfilingConsumer profilingConsumer) {
        if (consumer != null)
            throw new IllegalStateException("consumer is already set");
        consumer = profilingConsumer;
 
        scheduleTaskOp = consumer.registerOperation("scheduleTask");
        scheduleTaskDelayedOp =
            consumer.registerOperation("scheduleDelayedTask");
        scheduleTaskPeriodicOp =
            consumer.registerOperation("schedulePeriodicTask");

        // call on the backing manager, if it's also profiling
        if (backingManager instanceof ProfilingProducer)
            ((ProfilingProducer)backingManager).
                setProfilingConsumer(consumer);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        if (scheduleTaskOp != null)
            scheduleTaskOp.report();
        backingManager.scheduleTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        if (scheduleTaskDelayedOp != null)
            scheduleTaskDelayedOp.report();
        backingManager.scheduleTask(task, delay);
    }

    /**
     * {@inheritDoc}
     */
    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                                   long period) {
        if (scheduleTaskPeriodicOp != null)
            scheduleTaskPeriodicOp.report();
        return backingManager.schedulePeriodicTask(task, delay, period);
    }

}
