/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.ConfigManager;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.impl.profile.ProfileCollectorHandleImpl;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;

import com.sun.sgs.impl.profile.listener.OperationLoggingProfileOpListener;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import java.util.Properties;
import javax.management.JMException;


/** Simple profiling utility to support tests. */
public class DummyProfileCoordinator {

    // the production collector
    private final ProfileCollectorImpl collector;
    // and its management handle
    private final ProfileCollectorHandle collectorHandle;

    // a dummy task that represents all reports
    private static final KernelRunnable task = new DummyKernelRunnable();

    // a dummy owner for all reports
    private static final Identity owner = new DummyIdentity();

    // a single instance that will be non-null if we're profiling
    private static DummyProfileCoordinator instance = 
            new DummyProfileCoordinator();

    // a lock to ensure shutdown is done correctly
    private static final Object lockObject = new String("lock");

    // a test transaction id used in reporting
    private static final byte [] dummyTxnId = {0x01};

    /** Creates an instance of DummyProfileCoordinator */
    private DummyProfileCoordinator() {
        Properties props = System.getProperties();
        collector = new ProfileCollectorImpl(ProfileLevel.MIN, props, null);
        collectorHandle = new ProfileCollectorHandleImpl(collector);
        OperationLoggingProfileOpListener listener =
            new OperationLoggingProfileOpListener(props, owner, null);
        collector.addListener(listener, true);
        
        ConfigManager config = new ConfigManager(props);
        try {
            collector.registerMBean(config, ConfigManager.MXBEAN_NAME);
        } catch (JMException e) {
            System.out.println("Could not register ConfigManager" + e);
        }
    }

    /** Get the singleton, backing collector. */
    public static ProfileCollectorImpl getCollector() {
        return instance.collector;
    }

    /** Starts profiling */
    public static void startProfiling() {
        synchronized (lockObject) {
            instance.collector.setDefaultProfileLevel(ProfileLevel.MAX);
        }
    }

    /** Stops all profiling */
    public static void stopProfiling() {
        synchronized (lockObject) {
            instance.collector.setDefaultProfileLevel(ProfileLevel.MIN);
        }
    }

    /** Signals that a single task is starting in the current thread */
    public static void startTask() {
        synchronized (lockObject) {
            if (instance != null) {
                try {
                    instance.collectorHandle.
                        startTask(task, owner, System.currentTimeMillis(), 0);
                    instance.collectorHandle.noteTransactional(dummyTxnId);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    /** Signals that the current thread's task is done */
    public static void endTask(boolean committed) {
        synchronized (lockObject) {
            if (instance != null) {
                if (committed)
                    instance.collectorHandle.finishTask(1);
                else
                    instance.collectorHandle.finishTask(1, new Exception(""));
            }
        }
    }

    /** Shuts down the associated resource coordinator */
    public void shutdown() {
        synchronized (lockObject) {
            instance.shutdown();
            instance = null;
        }
    }

}
