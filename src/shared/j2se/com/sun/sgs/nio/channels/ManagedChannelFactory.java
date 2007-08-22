package com.sun.sgs.nio.channels;

import java.util.List;

public interface ManagedChannelFactory {

    /**
     * Returns a list of ChannelPoolMXBean objects representing the
     * management interfaces to one or more pools of channels. An object may
     * add or remove channel pools during execution of the Java virtual
     * machine.
     * 
     * @return a list of ChannelPoolMXBean objects representing the
     *         management interfaces to zero or more pools of channels
     */
    List<ChannelPoolMXBean> getChannelPoolMXBeans();
}
