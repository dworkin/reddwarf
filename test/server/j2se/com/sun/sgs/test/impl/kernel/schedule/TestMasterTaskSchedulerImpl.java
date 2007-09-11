/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.MinimalTestKernel.TestResourceCoordinator;

import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;

import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.DummyTaskOwner;
import com.sun.sgs.test.util.NameRunner;

import java.util.HashSet;
import java.util.Properties;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

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
     * Test runTask.
     */

    @Test (expected=NullPointerException.class)
        public void runTaskNullTask() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        scheduler.runTask(null, testOwner, false);
    }

    @Test (expected=NullPointerException.class)
        public void runTaskNullOwner() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        scheduler.runTask(new TestRunner(new Runnable() {
                public void run() {}
            }), null, false);
    }

    @Test (expected=IllegalStateException.class)
        public void runTaskNested() throws Exception {
        final MasterTaskScheduler scheduler = getScheduler(0);
        scheduler.runTask(new NestedTestRunner(scheduler), testOwner, false);
    }

    @Test public void runTaskSingleTask() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        RunCountTestRunner runner = new RunCountTestRunner(1);
        scheduler.runTask(runner, testOwner, false);
        assertEquals(0, runner.getRunCount());
    }

    @Test public void runTaskMultipleTasks() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        RunCountTestRunner runner = new RunCountTestRunner(4);
        scheduler.runTask(runner, testOwner, false);
        assertEquals(3, runner.getRunCount());
        scheduler.runTask(runner, testOwner, false);
        assertEquals(2, runner.getRunCount());
        scheduler.runTask(runner, testOwner, false);
        assertEquals(1, runner.getRunCount());
        scheduler.runTask(runner, testOwner, false);
        assertEquals(0, runner.getRunCount());
    }

    @Test public void runTaskWithRetry() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        RetryTestRunner runner = new RetryTestRunner();
        scheduler.runTask(runner, testOwner, true);
        assertTrue(runner.isFinished());
    }

    @Test (expected=TaskRejectedException.class)
        public void runTaskWithoutRetry() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        RetryTestRunner runner = new RetryTestRunner();
        scheduler.runTask(runner, testOwner, false);
    }

    @Test (expected=RuntimeException.class)
        public void runNonRetriedTaskWithRetry() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        NonRetryTestRunner runner = new NonRetryTestRunner();
        scheduler.runTask(runner, testOwner, true);
    }

    @Test public void runTaskTransactional() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        final TransactionProxy proxy = MinimalTestKernel.getTransactionProxy();
        TestRunner runner = new TestRunner(new Runnable() {
                public void run() {
                    proxy.getCurrentTransaction();
                    proxy.getCurrentOwner();
                }
            });
        scheduler.runTask(new TransactionRunner(runner), testOwner, false);
    }

    /**
     * Test runTransactionalTask.
     */

    @Test public void runNewTransaction() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        final TransactionProxy proxy = MinimalTestKernel.getTransactionProxy();
        scheduler.runTransactionalTask(new TestRunner(new Runnable() {
                public void run() {
                    proxy.getCurrentTransaction();
                }
            }), testOwner);
    }

    @Test public void runNewTransactionExistingTask() throws Exception {
        final MasterTaskScheduler scheduler = getScheduler(0);
        final TransactionProxy proxy = MinimalTestKernel.getTransactionProxy();
        TestRunner runner = new TestRunner(new Runnable() {
                public void run() {
                    try {
                        scheduler.
                            runTransactionalTask(new TestRunner(new Runnable() {
                                    public void run() {
                                        proxy.getCurrentTransaction();
                                    }
                                }), testOwner);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        scheduler.runTask(runner, testOwner, false);
    }

    @Test public void runNewTransactionScheduledTask() throws Exception {
        final MasterTaskScheduler scheduler = getScheduler(1);
        final RunCountTestRunner countRunner = new RunCountTestRunner(1);
        TestRunner runner = new TestRunner(new Runnable() {
                public void run() {
                    try {
                        scheduler.
                            runTransactionalTask(countRunner, testOwner);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        scheduler.scheduleTask(runner, testOwner);
        Thread.sleep(500L);
        assertEquals(0, countRunner.getRunCount());
    }

    @Test public void runExistingTransaction() throws Exception {
        final MasterTaskScheduler scheduler = getScheduler(0);
        final TransactionProxy proxy = MinimalTestKernel.getTransactionProxy();
        TestRunner runner = new TestRunner(new Runnable() {
                public void run() {
                    try {
                        scheduler.
                            runTransactionalTask(new TestRunner(new Runnable() {
                                    public void run() {
                                        proxy.getCurrentTransaction();
                                    }
                                }), testOwner);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        scheduler.runTask(new TransactionRunner(runner), testOwner, false);
    }

    @Test (expected=IllegalStateException.class)
        public void runTransactionNested() throws Exception {
        MasterTaskScheduler scheduler = getScheduler(0);
        TestRunner runner = new TestRunner(new Runnable() {
                public void run() {}
            });
        scheduler.runTransactionalTask(new TransactionRunner(runner),
                                       testOwner);
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
     * Utility classes.
     */

    private class TestRunner implements KernelRunnable {
        final Runnable runnable;
        TestRunner(Runnable runnable) { this.runnable = runnable; }
        public String getBaseTaskType() { return TestRunner.class.getName(); }
        public void run() throws Exception { runnable.run(); }
    }

    private class NestedTestRunner implements KernelRunnable {
        final TaskScheduler scheduler;
        NestedTestRunner(TaskScheduler scheduler) {
            this.scheduler = scheduler;
        }
        public String getBaseTaskType() {
            return NestedTestRunner.class.getName();
        }
        public void run() throws Exception {
            scheduler.runTask(new TestRunner(new Runnable() {
                    public void run() {}
                }), testOwner, false);
        }
    }

    private class RunCountTestRunner implements KernelRunnable {
        private int runCount;
        RunCountTestRunner(int initialCount) {
            this.runCount = initialCount;
        }
        public String getBaseTaskType() {
            return RunCountTestRunner.class.getName();
        }
        public void run() throws Exception {
            runCount--;
        }
        int getRunCount() {
            return runCount;
        }
    }

    private class RetryTestRunner implements KernelRunnable {
        private boolean hasRun = false;
        private boolean finished = false;
        public String getBaseTaskType() {
            return RetryTestRunner.class.getName();
        }
        public void run() throws Exception {
            if (! hasRun) {
                hasRun = true;
                throw new TaskRejectedException("test");
            }
            finished = true;
        }
        public boolean isFinished() {
            return finished;
        }
    }

    private class NonRetryTestRunner implements KernelRunnable {
        public String getBaseTaskType() {
            return NonRetryTestRunner.class.getName();
        }
        public void run() throws Exception {
            throw new RuntimeException("non-retry exception");
        }
    }

    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestMasterTaskSchedulerImpl.class);
    }

}
