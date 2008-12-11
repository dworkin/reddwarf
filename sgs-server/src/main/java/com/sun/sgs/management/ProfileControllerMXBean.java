/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.management;

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;

/**
 * The management information for this node's profiling data.  The profiling
 * levels for each of the individual profile consumers can be modified through 
 * this interface.
 * <p>
 * An instance implementing this MBean can be obtained from the from the 
 * {@link java.lang.management.ManagementFactory.html#getPlatformMBeanServer() 
 * getPlatformMBeanServer} method.
 * <p>
 * The {@code ObjectName} for uniquely identifying this MBean is
 * {@value #MXBEAN_NAME}.
 * 
 */
public interface ProfileControllerMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=ProfileController";
    
    // Maybe add a way to add/remove listeners?
    
    /**
     * Get the default profile level for all newly created consumers.
     * @return the default profile level for consumer creation
     */
    ProfileLevel getDefaultProfileLevel();
    
    /**
     * Set the default profile level for all newly created consumers.
     * @param level the default profile level for consumer creation
     */
    void setDefaultProfileLevel(ProfileLevel level);
    
    /**
     * Get the names of all profile consumers in the system.
     * 
     * @return the names of all the profile consumers
     */
    String[] getProfileConsumers();
    
    /**
     * Get the current profile level of the named consumer.
     * 
     * @param consumer the consumer name
     *
     * @return the profile level for the named consumer
     */
    ProfileLevel getConsumerLevel(String consumer);
    
    /**
     * Set the current profile level of the named consumer.
     * 
     * @param consumer the consumer name
     * @param level the profile level
     */
    void setConsumerLevel(String consumer, ProfileLevel level);
}
