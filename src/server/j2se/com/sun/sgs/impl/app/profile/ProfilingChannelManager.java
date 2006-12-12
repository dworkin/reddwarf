
package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;


/**
 * This is an implementation of <code>ChannelManager</code> used for profiling
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
public class ProfilingChannelManager
    implements ChannelManager, ProfilingManager {

    // the channel manager that this manager calls through to
    private final ChannelManager backingManager;

    // the reporting interface
    private ProfileReporter reporter = null;

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
