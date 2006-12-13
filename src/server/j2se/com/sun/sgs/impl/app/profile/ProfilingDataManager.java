
package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;


/**
 * This is an implementation of <code>DataManager</code> used for profiling
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
public class ProfilingDataManager implements DataManager, ProfilingManager {

    // the data manager that this manager calls through to
    private final DataManager backingManager;

    // the reporting interface
    private ProfileReporter reporter = null;

    /**
     * Creates an instance of <code>ProfilingDataManager</code>.
     *
     * @param backingManager the <code>DataManager</code> to call through to
     */
    public ProfilingDataManager(DataManager backingManager) {
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
    public <T> T getBinding(String name, Class<T> type) {
        return backingManager.getBinding(name, type);
    }

    /**
     * {@inheritDoc}
     */
    public void setBinding(String name, ManagedObject object) {
        backingManager.setBinding(name, object);
    }

    /**
     * {@inheritDoc}
     */
    public void removeBinding(String name) {
        backingManager.removeBinding(name);
    }

    /**
     * {@inheritDoc}
     */
    public void removeObject(ManagedObject object) {
        backingManager.removeObject(object);
    }

    /**
     * {@inheritDoc}
     */
    public void markForUpdate(ManagedObject object) {
        backingManager.markForUpdate(object);
    }

    /**
     * {@inheritDoc}
     */
    public ManagedReference createReference(ManagedObject object) {
        return backingManager.createReference(object);
    }

}
