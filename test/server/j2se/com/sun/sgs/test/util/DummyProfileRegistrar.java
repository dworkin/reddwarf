/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.util;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;

import com.sun.sgs.impl.kernel.MinimalTestKernel.TestResourceCoordinator;

import com.sun.sgs.impl.kernel.profile.OperationLoggingProfileOpListener;
import com.sun.sgs.impl.kernel.profile.ProfileCollector;
import com.sun.sgs.impl.kernel.profile.ProfileConsumerImpl;

import java.util.Properties;


/** Simple implementation of ProfileRegistrar to support tests. */
public class DummyProfileRegistrar implements ProfileRegistrar {

    // the resource coordinator used to run report consuming threads
    private final TestResourceCoordinator coordinator;

    // the production collector
    private final ProfileCollector collector;

    // a dummy task that represents all reports
    private static final KernelRunnable task = new KernelRunnable() {
            public String getBaseTaskType() {
                return getClass().getName();
            }
            public void run() throws Exception {}
        };

    // a dummy owner for all reports
    private static final DummyTaskOwner owner = new DummyTaskOwner();

    // a single instance that will be non-null if we're profiling
    private static DummyProfileRegistrar instance = null;

    // a lock to ensure shutdown is done correctly
    private static final Object lockObject = new String("lock");

    /** Creates an instance of DummyProfileRegistrar */
    private DummyProfileRegistrar() {
        coordinator = new TestResourceCoordinator();
        collector = new ProfileCollector(coordinator);
        OperationLoggingProfileOpListener listener =
            new OperationLoggingProfileOpListener(System.getProperties(),
                                                  owner, null, coordinator);
        collector.addListener(listener);
    }

    /** Registers a producer */
    public ProfileConsumer registerProfileProducer(ProfileProducer producer) {
        return new ProfileConsumerImpl(producer, collector);
    }

    /** Profiles the given producer, starting profiling if not started */
    public static void startProfiling(ProfileProducer producer) {
        synchronized (lockObject) {
            if (instance == null)
                instance = new DummyProfileRegistrar();
            producer.setProfileRegistrar(instance);
        }
    }

    /** Stops all profiling */
    public static void stopProfiling() {
        synchronized (lockObject) {
            if (instance != null)
                instance.shutdown();
            instance = null;
        }
    }

    /** Signals that a single task is starting in the current thread */
    public static void startTask() {
        synchronized (lockObject) {
            if (instance != null) {
                try {
                    instance.collector.
                        startTask(task, owner, System.currentTimeMillis(), 0);
                    instance.collector.noteTransactional();
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    /** Signals that the current thread's task is done */
    public static void endTask(boolean committed) {
        synchronized (lockObject) {
            if (instance != null)
                instance.collector.finishTask(1, committed);
        }
    }

    /** Shuts down the associated resource coordinator */
    public void shutdown() {
        synchronized (lockObject) {
            coordinator.shutdown();
            instance = null;
        }
    }

}
