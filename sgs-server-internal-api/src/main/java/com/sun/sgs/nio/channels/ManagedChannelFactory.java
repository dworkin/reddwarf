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
     * @return a list of {@code ChannelPoolMXBean} objects representing the
     *         management interfaces to zero or more pools of channels
     */
    List<ChannelPoolMXBean> getChannelPoolMXBeans();
}
