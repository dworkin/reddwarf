
package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.kernel.ProfiledOperation;
import com.sun.sgs.kernel.ProfilingConsumer;
import com.sun.sgs.kernel.ProfilingProducer;


/**
 * This is an implementation of <code>DataManager</code> used to suport
 * profiling. It simply calls its backing manager for each manager method. If
 * no <code>ProfilingConsumer</code> is provided via
 * <code>setProfilingConsumer</code> then this manager does no reporting, and
 * only calls through to the backing manager. If the backing manager is also
 * an instance of <code>ProfilingProducer</code> then it too will be supplied
 * with the <code>ProfilingConsumer</code> as described in
 * <code>setProfilingConsumer</code>.
 */
public class ProfilingDataManager implements DataManager, ProfilingProducer {

    // the data manager that this manager calls through to
    private final DataManager backingManager;

    // the reporting interface
    private ProfilingConsumer consumer = null;

    // the operations being profiled
    private ProfiledOperation createReferenceOp = null;

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

        createReferenceOp = consumer.registerOperation("createReference");

        // call on the backing manager, if it's also profiling
        if (backingManager instanceof ProfilingProducer)
            ((ProfilingProducer)backingManager).
                setProfilingConsumer(consumer);
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
    public String nextBoundName(String name) {
        return backingManager.nextBoundName(name);
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
     * <p>
     * Note that this method is the only one that is directly reported by
     * this manager, if profiling is enabled.
     */
    public ManagedReference createReference(ManagedObject object) {
        if (createReferenceOp != null)
            createReferenceOp.report();
        return backingManager.createReference(object);
    }

}
