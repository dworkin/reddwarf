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
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
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
    private Set<ObjectName> registeredMBeans;
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
        registeredMBeans = 
                Collections.synchronizedSet(new HashSet<ObjectName>());
    }
    
    /** 
     * Creates an instance of the profile service, used by some unit tests.
     * @param collector the system profile collector
     */
    public ProfileServiceImpl(ProfileCollector collector) {
        logger.log(Level.CONFIG, 
                 "Creating ProfileServiceImpl for testing");
        this.collector = collector;
        registeredMBeans = 
                Collections.synchronizedSet(new HashSet<ObjectName>());
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
            platServer.registerMBean(
//                new StandardMBean(stats, DataStoreStatsMXBean.class) { },
                    // Still not clear why I'd use an anon class here
                    // Can provide descriptors for my attributes: how?
                mBean, name);
            registeredMBeans.add(name);
            logger.log(Level.CONFIG, "Registered MBean {0}", name);
        } catch (JMException ex) {
            logger.logThrow(Level.CONFIG, ex, 
                            "Could not register MBean {0}", mBeanName);
            throw ex;
        }
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
        Set<ObjectName> setCopy = new HashSet<ObjectName>(registeredMBeans);
        for (ObjectName name : setCopy) {
            try {
                platServer.unregisterMBean(name);
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
