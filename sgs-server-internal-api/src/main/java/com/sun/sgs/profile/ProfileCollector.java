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

package com.sun.sgs.profile;

import java.util.List;
import java.util.Map;
import javax.management.JMException;

/**
 * This is the main aggregation point for profiling data. Implementations of
 * this interface are used to collect data from arbitrary sources (typically
 * <code>ProfileConsumer</code>s or the scheduler itself) and keep
 * track of which tasks are generating which data.
 * <p>
 * This interface allows instances of <code>ProfileListener</code>
 * to register as listeners for reported data. All reporting to these
 * listeners is done synchronously, such that listeners do not need to worry
 * about being called concurrently. Listeners should be efficient in handling
 * reports, since they may be blocking all other listeners.
 */
public interface ProfileCollector {

    /**
     *  The profiling levels.
     */
    enum ProfileLevel {
        /** 
         * Collect minimal profiling data, used by the system internally.
         * This is the default profiling level.  This level of profiling 
         * is appropriate for monitoring of production systems.
         */
        MIN,
        /** 
         * Collect a medium amount of profiling data.  This level of profiling
         * provides more data than {@code MIN}, but is still appropriate for 
         * monitoring of production systems.
         */
        MEDIUM,
        /** 
         * Collect all profiling data available.  Because this could be an
         * extensive amount of data, this level may only be appropriate for 
         * debugging systems under development.
         */
        MAX,
    }
    
    /** 
     * Gets the default system profiling level, which is the default level
     * for any newly created {@code ProfileConsumer}.  
     * 
     * @return the default profiling level
     */
    ProfileLevel getDefaultProfileLevel();

    /**
     * Sets the default profile level, used as the initial level when creating
     * new {@code ProfileConsumer}s.
     * 
     * @param level the new default profile level
     */
    void setDefaultProfileLevel(ProfileLevel level);
    
    /** 
     * Shuts down the ProfileCollector, reclaiming resources as necessary.
     */
    void shutdown();
    
    /**
     * Adds a <code>ProfileListener</code> as a listener for
     * profiling data reports. The listener is immediately updated on
     * the current set of operations and the number of scheduler
     * threads. The listener can be marked as unable to be removed by
     * {@link #removeListener removeListener} or shutdown by {@link #shutdown};
     * if these operations are performed on a listener that does not allow them,
     * they are silently ignored.
     *
     * @param listener the {@code ProfileListener} to add
     * @param canRemove {@code true} if this listener can be removed or 
     *                  shut down by the {@code ProfileCollector}.  This 
     *                  parameter should usually be set to {@code true}.
     */
    void addListener(ProfileListener listener, boolean canRemove);
       
    /**
     * Instantiates and adds a {@code ProfileListener}. The listener must
     * implement a constructor of the form ({@code java.util.Properties},
     * {@code com.sun.sgs.kernel.TaskOwner},
     * {@code com.sun.sgs.kernel.ComponentRegistry}). 
     * The listener is immediately updated on
     * the current set of operations and the number of scheduler
     * threads.
     * 
     * @param listenerClassName the fully qualified class name of the 
     *                          listener to instantiate and add.
     * 
     * @throws Exception if any were generated during instantiation
     */
    void addListener(String listenerClassName) throws Exception;

    /**
     * Returns a read-only list of {@code ProfileListener}s which have been
     * added.
     * 
     * @return the list of listeners
     */
    List<ProfileListener> getListeners();

    /**
     * Removes a {@code ProfileListener} and calls
     * {@link ProfileListener#shutdown} on the listener.  If the
     * {@code listener} has never been added with 
     * {@link #addListener addListener}, no action is taken.
     *
     * @param listener the listener to remove
     */
    void removeListener(ProfileListener listener);
    
    /**
     * Returns the named {@code ProfileConsumer}, or creates a new one with
     * that name.  
     * <p>
     * Note that the name must be unique for a new {@code ProfileConsumer} to 
     * be created. Consumers created by the core server packages have a prefix
     * of {@code com.sun.sgs.}
     * to distinguish their namespace. Consumers created by code outside of the
     * core server packages should create their own unique namespace.
     * <p>
     * Calling {@link #getConsumers} will return a map keyed by names already 
     * in use.
     *
     * @param name the unique name of the profile consumer
     *
     * @return a {@code ProfileConsumer} with the given name
     */
    ProfileConsumer getConsumer(String name);
    
    /**
     * Returns a read-only map of {@code ProfileConsumer} names to the 
     * {@code ProfileConsumer}s which have been created through a call to 
     * {@link #getConsumer getConsumer}.
     * 
     * @return the map of names to consumers
     */
    Map<String, ProfileConsumer> getConsumers();
    
    /**
     * Registers the given MBean with the current VM's platform MBean server,
     * allowing it to be monitored by tools like JConsole.
     * 
     * @param mBean the MBean or MXBean to be registered
     * @param mBeanName the name under which it should be registered
     * 
     * @throws JMException if there were any problems reported
     *    by the JMX system during registration, including if an object
     *    has already been registered with the {@code mBeanName}
     */
    void registerMBean(Object mBean, String mBeanName) throws JMException;
    
    /**
     * Returns the object registered under the given name, or {@code null}
     * if no object has been registered with that name.
     * 
     * @param mBeanName the name the object was registered under
     * @return the object passed into {@link #registerMBean(Object, String)
     *         registerMBean} with the given {@code mBeanName}
     */
    Object getRegisteredMBean(String mBeanName);
}
