
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


public class DummyProfileRegistrar implements ProfileRegistrar {

    //
    private final TestResourceCoordinator coordinator;

    //
    private final ProfileCollector collector;

    //
    private static final KernelRunnable task = new KernelRunnable() {
            public void run() throws Exception {}
        };

    //
    private static final DummyTaskOwner owner = new DummyTaskOwner();

    //
    private static DummyProfileRegistrar instance = null;

    /**  */
    public DummyProfileRegistrar() {
        coordinator = new TestResourceCoordinator();
        collector = new ProfileCollector(coordinator);
        OperationLoggingProfileOpListener listener =
            new OperationLoggingProfileOpListener(System.getProperties(),
                                                  owner, null, coordinator);
        collector.addListener(listener);
    }

    /**  */
    public ProfileConsumer registerProfileProducer(ProfileProducer producer) {
        return new ProfileConsumerImpl(producer, collector);
    }

    /**  */
    public static void startProfiling(ProfileProducer producer) {
        if (instance == null)
            instance = new DummyProfileRegistrar();
        producer.setProfileRegistrar(instance);
    }

    /**  */
    public static void stopProfiling() {
        if (instance != null)
            instance.shutdown();
        instance = null;
    }

    /**  */
    public static void startTask() {
        if (instance != null) {
            try {
                instance.collector.
                    startTask(task, owner, System.currentTimeMillis(), 0);
                instance.collector.noteTransactional();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    /**  */
    public static void endTask(boolean committed) {
        if (instance != null)
            instance.collector.finishTask(1, committed);
    }

    /**  */
    public void shutdown() {
        coordinator.shutdown();
    }

}
