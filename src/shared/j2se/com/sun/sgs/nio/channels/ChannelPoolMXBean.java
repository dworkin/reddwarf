package com.sun.sgs.nio.channels;

public interface ChannelPoolMXBean {

    /**
     * Returns the name representing this channel pool.
     *
     * @return the name of this channel pool
     */
    String getName();

    /**
     * Returns an estimate of the number of open channels in this pool.
     * <p>
     * The number of open channels is the number of channels opened since
     * the Java virtual machine has started execution minus the number of
     * channels that have been closed.
     *
     * @return an estimate of the number of open channels in this pool
     */
    long getCount();
}
