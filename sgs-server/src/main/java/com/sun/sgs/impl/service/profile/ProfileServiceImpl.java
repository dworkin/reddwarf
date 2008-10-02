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

package com.sun.sgs.impl.service.profile;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.service.ProfileService;
import com.sun.sgs.service.TransactionProxy;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * An implementation of {@code ProfileService}.
 */
public class ProfileServiceImpl implements ProfileService {
    /** The name of this class. */
    private static final String CLASSNAME =
	"com.sun.sgs.impl.service.profile.ProfileServiceImpl";
    
     /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));
    
    private ProfileCollector collector;
    private ConcurrentMap<String, Object> registeredMBeans;
    private boolean shutdown = false;
    
    /**
     * Create an instance of the profile service.  This constructor
     * is used by the kernel.
     * 
     * @param	properties the properties for configuring this service
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     * @throws	Exception if a problem occurs creating the service
     */
    public ProfileServiceImpl(Properties properties, 
                              ComponentRegistry systemRegistry,
                              TransactionProxy txnProxy)
        throws Exception
    {
        logger.log(Level.CONFIG, 
                 "Creating ProfileServiceImpl properties:{0}", properties);
        collector = systemRegistry.getComponent(ProfileCollector.class);
        finishConstruction();
    }
    
    /** 
     * Creates an instance of the profile service, used by some unit tests.
     * @param collector the system profile collector
     */
    public ProfileServiceImpl(ProfileCollector collector) {
        logger.log(Level.CONFIG, 
                 "Creating ProfileServiceImpl for testing");
        this.collector = collector;
        finishConstruction();
    }
    
    private void finishConstruction() {
        registeredMBeans = new ConcurrentHashMap<String, Object>();
        
        // Create the task aggregator, add it as a listener, and register
        // it as an MBean.  We do this here so we can gather task data for
        // all services that are started after us.
        TaskAggregate taskAgg = new TaskAggregate();
        collector.addListener(taskAgg, true);
        try {
            registerMBean(taskAgg, TaskAggregate.TASK_AGGREGATE_MXBEAN_NAME);
        } catch (JMException e) {
            // Continue on if we couldn't register this bean, although
            // it's probably a very bad sign
            logger.logThrow(Level.CONFIG, e, "Could not register MBean");
        }
    }
    /* -- implement ProfileService -- */
    /** {@inheritDoc} */
    public ProfileCollector getProfileCollector() {
        return collector;
    }

    /** {@inheritDoc} */
    public void registerMBean(Object mBean, String mBeanName) 
        throws JMException 
    {
        // Register beans with Platform MBeanServer
        MBeanServer platServer = ManagementFactory.getPlatformMBeanServer();
        
        try {
            ObjectName name = new ObjectName(mBeanName);
            platServer.registerMBean(mBean, name);
//                new StandardMBean(stats, DataStoreStatsMXBean.class) { },
                    // Still not clear why I'd use an anon class here
                    // Can provide descriptors for my attributes: how?
                
            registeredMBeans.putIfAbsent(mBeanName, mBean);
            logger.log(Level.CONFIG, "Registered MBean {0}", name);
        } catch (JMException ex) {
            logger.logThrow(Level.CONFIG, ex, 
                            "Could not register MBean {0}", mBeanName);
            throw ex;
        }
    }

    /** {@inheritDoc} */
    public Object getRegisteredMBean(String mBeanName) {
        return registeredMBeans.get(mBeanName);
    }

    /* -- implement Service -- */
    /** {@inheritDoc} */
    public String getName() {
        return toString();
    }

    /** {@inheritDoc} */
    public void ready() throws Exception {
        // do nothing
    }

    /** {@inheritDoc} */
    public synchronized boolean shutdown() {
        if (shutdown) {
            throw new IllegalArgumentException("Service already shut down");
        }
        // attempt to unregister all our registered MBeans
        MBeanServer platServer = ManagementFactory.getPlatformMBeanServer();
        Set<String> keys = registeredMBeans.keySet();
        for (String name : keys) {
            try {
                platServer.unregisterMBean(new ObjectName(name));
            } catch (MalformedObjectNameException ex) {
                logger.logThrow(Level.WARNING, ex, 
                                "Could not unregister MBean {0}", name);
            } catch (InstanceNotFoundException ex) {
                logger.logThrow(Level.WARNING, ex, 
                                "Could not unregister MBean {0}", name);
            } catch (MBeanRegistrationException ex) {
                logger.logThrow(Level.WARNING, ex, 
                                "Could not unregister MBean {0}", name);
            } finally {
                registeredMBeans.remove(name);
            }
        }
        shutdown = true;
        return true;
    }
}
