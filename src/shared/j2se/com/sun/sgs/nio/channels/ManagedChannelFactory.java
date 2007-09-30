package com.sun.sgs.nio.channels;

import java.nio.channels.Channel;
import java.util.List;

/**
 * An object with a management interface to pools of channels.
 * <p>
 * This interface is intended to be implemented by factory classes that
 * construct {@link Channel} objects. It defines the
 * {@link ManagedChannelFactory#getChannelPoolMXBeans getChannelPoolMXBeans}
 * method to return a list of {@link ChannelPoolMXBean} objects that represent
 * the management interface to the pool of channels constructed by the factory.
 * 
 * @see Channels#getChannelPoolMXBeans()
 */
public interface ManagedChannelFactory {

    /**
     * Returns a list of {@link ChannelPoolMXBean} objects representing the
     * management interfaces to one or more pools of channels. An object may
     * add or remove channel pools during execution of the Java virtual
     * machine.
     * 
     * @return a list of {@link ChannelPoolMXBean} objects representing the
     *         management interfaces to zero or more pools of channels
     */
    List<ChannelPoolMXBean> getChannelPoolMXBeans();
}
