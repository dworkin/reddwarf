
package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;

import com.sun.sgs.kernel.ProfilingConsumer;
import com.sun.sgs.kernel.ProfilingProducer;


/**
 * This is an implementation of <code>ChannelManager</code> used to support
 * profiling. It simply calls its backing manager for each manager method. If
 * no <code>ProfilingConsumer</code> is provided via
 * <code>setProfilingConsumer</code> then this manager does no reporting, and
 * only calls through to the backing manager. If the backing manager is also
 * an instance of <code>ProfilingProducer</code> then it too will be supplied
 * with the <code>ProfilingConsumer</code> as described in
 * <code>setProfilingConsumer</code>.
 * <p>
 * Note that at present no operations are directly profiled by this class.
 */
public class ProfilingChannelManager
    implements ChannelManager, ProfilingProducer {

    // the channel manager that this manager calls through to
    private final ChannelManager backingManager;

    // the reporting interface
    private ProfilingConsumer consumer = null;

    /**
     * Creates an instance of <code>ProfilingChannelManager</code>.
     *
     * @param backingManager the <code>ChannelManager</code> to call through to
     */
    public ProfilingChannelManager(ChannelManager backingManager) {
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
        if (backingManager instanceof ProfilingProducer)
            ((ProfilingProducer)backingManager).
                setProfilingConsumer(consumer);
    }

    /**
     * {@inheritDoc}
     */
    public Channel createChannel(String name, ChannelListener listener,
                                 Delivery delivery) {
        return backingManager.createChannel(name, listener, delivery);
    }

    /**
     * {@inheritDoc}
     */
    public Channel getChannel(String name) {
        return backingManager.getChannel(name);
    }

}
