package com.sun.sgs.nio.channels;

import javax.management.MXBean;

/**
 * The management interface for a channel pool.
 * <p>
 * A Java virtual machine has several instances of the implementation class
 * of this interface. A class implementing this interface is an
 * {@link MXBean} that is obtained by calling the
 * {@link Channels#getChannelPoolMXBeans()} method
 * [[NOT IMPLEMENTED: or from the platform MBeanServer]].
 * <p>
 * The {@code ObjectName} for uniquely identifying the MXBean of this type
 * within an {@code MBeanServer} is:
 * <blockquote>
 * <code>java.nio:type=ChannelPool,name=<i>pool name</i></code> 
 * </blockquote>
 */
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
