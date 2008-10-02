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

package com.sun.sgs.service;

import com.sun.sgs.profile.ProfileCollector;
import javax.management.JMException;

/**
 * This {@code Service} provides facilities for generating profiling data
 * and registering JMX MBeans.
 */
public interface ProfileService extends Service {

    /**
     * Get the system profile collector, which is used to manage
     * profiling data and listeners.
     * 
     * @return the system profile collector
     */
    ProfileCollector getProfileCollector();
    
    /**
     * Register the given MBean with the current VM's platform MBean server,
     * allowing it to be monitored via JConsole.
     * 
     * @param mBean the MBean or MXBean to be registered
     * @param mBeanName the name under which it should be registered
     * 
     * @throws JMException if there were any problems reported
     *    by the JMX system during registration
     */
    void registerMBean(Object mBean, String mBeanName) throws JMException;
    
    /**
     * Return the object registered under the given name, or {@code null}
     * if no object has been registered with that name.
     * 
     * @param mBeanName the name the object was registered under
     * @return the object passed into {@link #registerMBean(Object, String)
     *         registerMBean} with the given {@code mBeanName}
     */
    Object getRegisteredMBean(String mBeanName);
}
