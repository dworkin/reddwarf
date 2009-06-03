/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.nio.channels;

/**
 * The management interface for a channel pool.
 * <p>
 * A Java virtual machine has several instances of the implementation class
 * of this interface. A class implementing this interface is an
 * {@code javax.management.MXBean} that is obtained by calling the
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
