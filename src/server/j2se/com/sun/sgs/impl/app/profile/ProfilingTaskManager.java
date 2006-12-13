
package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;


/**
 * This is an implementation of <code>TaskManager</code> used for profiling
 * each method call. It simply calls its backing manager for each manager
 * method after first reporting the call. If no <code>ProfileReporter</code>
 * is provided via <code>setProfileReporter</code> then this manager does
 * no reporting, and only calls through to the backing manager. If the backing
 * manager is also an instance of <code>ProfilingManager</code> then it too
 * will be supplied with the <code>ProfileReporter</code> as described in
 * <code>setProfileReporter</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class ProfilingTaskManager implements TaskManager, ProfilingManager {

    // the task manager that this manager calls through to
    private final TaskManager backingManager;

    // the reporting interface
    private ProfileReporter reporter = null;

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
     * an instance of <code>ProfilingManager</code> then its
     * <code>setProfileReporter</code> will be invoked when this method
     * is called. The backing manager is provided the same instance of
     * <code>ProfileReporter</code> so reports from the two managers are
     * considered to come from the same source.
     *
     * @throws IllegalStateException if a <code>ProfileReporter</code>
     *                               has already been set
     */
    public void setProfileReporter(ProfileReporter profileReporter) {
        if (reporter != null)
            throw new IllegalStateException("reporter is already set");

        reporter = profileReporter;
        if (backingManager instanceof ProfilingManager)
            ((ProfilingManager)backingManager).
                setProfileReporter(profileReporter);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task) {
        backingManager.scheduleTask(task);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(Task task, long delay) {
        backingManager.scheduleTask(task, delay);
    }

    /**
     * {@inheritDoc}
     */
    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                                   long period) {
        return backingManager.schedulePeriodicTask(task, delay, period);
    }

}
