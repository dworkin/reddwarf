/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.test.impl.kernel.schedule;

import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.MinimalTestKernel.TestResourceCoordinator;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;

import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.DummyTaskOwner;
import com.sun.sgs.test.util.NameRunner;

import java.util.HashSet;
import java.util.Properties;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.junit.runner.RunWith;


/**
 * Basic tests for <code>MasterTaskScheduler</code>. Note that this does not
 * do an exhaustive set of tests of the various reservation and scheduling
 * methods because these cases are already covered by the system and
 * application scheduling tests.
 */
@RunWith(NameRunner.class)
public class TestMasterTaskSchedulerImpl {

    // the default owner for tests
    private static final DummyTaskOwner testOwner = new DummyTaskOwner();

    // the default context used for the tests
    private static final KernelAppContext testContext =
        testOwner.getContext();

    // the resource coordinator used in many tests
    private TestResourceCoordinator resourceCoordinator;

    // the scheduler used in many tests
    private MasterTaskScheduler masterTaskScheduler;

    public TestMasterTaskSchedulerImpl() {}

    @Before public void startup() {
        resourceCoordinator = null;
        masterTaskScheduler = null;
    }

    @After public void shutdown() {
        if (resourceCoordinator != null)
            resourceCoordinator.shutdown();
        if (masterTaskScheduler != null)
            masterTaskScheduler.shutdown();
    }

    /**
     * Test constructor.
     */

    @Test public void constructorValidArgs() throws Exception {
        getScheduler();
    }

    @Test (expected=NullPointerException.class)
        public void constructorNullProperties() throws Exception {
        new MasterTaskScheduler(null, new TestResourceCoordinator(),
                                MinimalTestKernel.getTaskHandler(),
                                null, testContext);
    }

    @Test (expected=NullPointerException.class)
        public void constructorNullResourceCoordinator() throws Exception {
        new MasterTaskScheduler(new Properties(), null,
                                MinimalTestKernel.getTaskHandler(),
                                null, testContext);
    }

    @Test (expected=NullPointerException.class)
        public void constructorNullTaskHandler() throws Exception {
        new MasterTaskScheduler(new Properties(),
                                new TestResourceCoordinator(), null,
                                null, testContext);
    }

    @Test public void constructorCustomSystemScheduler() throws Exception {
        Properties p = new Properties();
        p.setProperty(MasterTaskScheduler.SYSTEM_SCHEDULER_PROPERTY,
                      "com.sun.sgs.impl.kernel.schedule." +
                      "RoundRobinSystemScheduler");
        getScheduler(p);
    }

    @Test (expected=ClassNotFoundException.class)
        public void constructorUnknownSystemScheduler() throws Exception {
        Properties p = new Properties();
        p.setProperty(MasterTaskScheduler.SYSTEM_SCHEDULER_PROPERTY, "foo");
        getScheduler(p);
    }

    /**
     * Test registerApplication.
     */

    @Test public void registerValidApp() throws Exception {
        MasterTaskScheduler scheduler = getScheduler();
        scheduler.registerApplication((new DummyTaskOwner()).getContext(),
                                      new Properties());
    }

    @Test (expected=NullPointerException.class)
        public void registerAppNullContext() throws Exception {
        MasterTaskScheduler scheduler = getScheduler();
        scheduler.registerApplication(null, new Properties());
    }

    @Test (expected=NullPointerException.class)
        public void registerAppNullProperties() throws Exception {
        MasterTaskScheduler scheduler = getScheduler();
        scheduler.registerApplication((new DummyTaskOwner()).getContext(),
                                      null);
    }

    @Test (expected=IllegalArgumentException.class)
        public void registerAppExisting() throws Exception {
        MasterTaskScheduler scheduler = getScheduler();
        scheduler.registerApplication(testContext, new Properties());
    }

    /**
     * Test reserveTasks.
     */

    @Test public void reserveTasksValid() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        scheduler.reserveTasks(getTaskGroup(), testOwner);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTasksTasksNull() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        scheduler.reserveTasks(null, testOwner);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTasksOwnerNull() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        scheduler.reserveTasks(getTaskGroup(), null);
    }

    @Test public void useTasksReservation() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TaskReservation r = scheduler.reserveTasks(getTaskGroup(), testOwner);
        r.use();
    }

    @Test public void cencelTasksReservation() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TaskReservation r = scheduler.reserveTasks(getTaskGroup(), testOwner);
        r.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseTasksReservation() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TaskReservation r = scheduler.reserveTasks(getTaskGroup(), testOwner);
        r.use();
        r.use();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelTasksReservation() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TaskReservation r = scheduler.reserveTasks(getTaskGroup(), testOwner);
        r.cancel();
        r.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void useThenCancelTasksReservation() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TaskReservation r = scheduler.reserveTasks(getTaskGroup(), testOwner);
        r.use();
        r.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelThenUseTasksReservation() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TaskReservation r = scheduler.reserveTasks(getTaskGroup(), testOwner);
        r.cancel();
        r.use();
    }

    /**
     * Utility methods.
     */

    private MasterTaskScheduler getScheduler() throws Exception {
        return getScheduler(new Properties());
    }

    private MasterTaskScheduler getScheduler(int numThreads) throws Exception {
        Properties p = new Properties();
        p.setProperty(MasterTaskScheduler.INITIAL_CONSUMER_THREADS_PROPERTY,
                      String.valueOf(numThreads));
        return getScheduler(p);
    }

    private MasterTaskScheduler getScheduler(Properties p) throws Exception {
        resourceCoordinator = new TestResourceCoordinator();
        masterTaskScheduler =
            new MasterTaskScheduler(p, resourceCoordinator,
                                    MinimalTestKernel.getTaskHandler(),
                                    null, testContext);
        return masterTaskScheduler;
    }

    private HashSet<KernelRunnable> getTaskGroup() {
        HashSet<KernelRunnable> set = new HashSet<KernelRunnable>();
        set.add(new DummyKernelRunnable());
        set.add(new DummyKernelRunnable());
        return set;
    }

    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestMasterTaskSchedulerImpl.class);
    }

}
