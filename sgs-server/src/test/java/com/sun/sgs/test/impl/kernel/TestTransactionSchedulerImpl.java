/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.TransactionScheduler;

import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;

import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;

import java.util.Properties;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;


/**
 * Basic tests for the TransactionScheduler interface. Note that reservation
 * and scheduling methods are already tested from the tests specific to the
 * {@code ApplicationScheduler}s, so there are no duplicated tests here.
 */
@RunWith(FilteredNameRunner.class)
public class TestTransactionSchedulerImpl {

    private SgsTestNode serverNode = null;
    private TransactionScheduler txnScheduler;
    private Identity taskOwner;

    public TestTransactionSchedulerImpl() { }

    /** Per-test initialization */
    @Before public void startup() throws Exception {
        Properties properties =
            SgsTestNode.getDefaultProperties("TestTransactionSchedulerImpl",
					     null, null);
        properties.setProperty("com.sun.sgs.finalService", "DataService");
        serverNode = new SgsTestNode("TestTransactionSchedulerImpl",
                                     null, properties);
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
    }

    /** Per-test shutdown */
    @After public void shutdown() throws Exception {
        if (serverNode != null)
            serverNode.shutdown(true);
    }

    /**
     * Test runTask.
     */

    @Test (expected=NullPointerException.class)
        public void runTaskNullTask() throws Exception {
        txnScheduler.runTask(null, taskOwner);
    }

    @Test (expected=NullPointerException.class)
        public void runTaskNullOwner() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {}
            }, null);
    }

    @Test public void runTaskSingleTask() throws Exception {
        RunCountTestRunner runner = new RunCountTestRunner(1);
        txnScheduler.runTask(runner, taskOwner);
        assertEquals(0, runner.getRunCount());
    }

    @Test public void scheduleSingleTask() throws Exception {
        RunCountTestRunner runner = new RunCountTestRunner(1);
        txnScheduler.scheduleTask(runner, taskOwner);
        Thread.sleep(500L);
        assertEquals(0, runner.getRunCount());
    }

    @Test public void runTaskMultipleTasks() throws Exception {
        RunCountTestRunner runner = new RunCountTestRunner(4);
        txnScheduler.runTask(runner, taskOwner);
        assertEquals(3, runner.getRunCount());
        txnScheduler.runTask(runner, taskOwner);
        assertEquals(2, runner.getRunCount());
        txnScheduler.runTask(runner, taskOwner);
        assertEquals(1, runner.getRunCount());
        txnScheduler.runTask(runner, taskOwner);
        assertEquals(0, runner.getRunCount());
    }

    @Test public void runTaskTransactional() throws Exception {
        final TransactionProxy proxy = serverNode.getProxy();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    proxy.getCurrentTransaction();
                    proxy.getCurrentOwner();
                }
            }, taskOwner);
    }

    @Test public void runTransactionInTransaction() throws Exception {
        final TransactionProxy proxy = serverNode.getProxy();
        KernelRunnable task = new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    final Transaction t = proxy.getCurrentTransaction();
                    txnScheduler.runTask(new TestAbstractKernelRunnable() {
                            public void run() throws Exception {
                                Transaction t2 = proxy.getCurrentTransaction();
                                assertTrue(t.equals(t2));
                            }
                        }, taskOwner);
                }
            };
        txnScheduler.runTask(task, taskOwner);
    }

    @Test public void runTransactionFromScheduledTask() throws Exception {
        final RunCountTestRunner countRunner = new RunCountTestRunner(1);
        KernelRunnable task = new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    txnScheduler.runTask(countRunner, taskOwner);
                }
            };
        txnScheduler.scheduleTask(task, taskOwner);
        Thread.sleep(500L);
        assertEquals(0, countRunner.getRunCount());
    }

    /**
     * Test interruption.
     */

    @Test public void scheduleTransactionRetryAfterInterrupt()
        throws Exception
    {
        final AtomicInteger i = new AtomicInteger(0);
        final KernelRunnable r = new TestAbstractKernelRunnable() {
                public int runCount = 0;
                public void run() throws Exception {
                    if (i.getAndIncrement() == 0)
                        throw new InterruptedException("test");
                }
            };
        txnScheduler.scheduleTask(r, taskOwner);
        Thread.sleep(200L);
        assertEquals(i.get(), 2);
    }

    @Test public void runTransactionInterrupted() throws Exception {
        final AtomicInteger i = new AtomicInteger(0);
        final KernelRunnable r = new TestAbstractKernelRunnable() {
                public int runCount = 0;
                public void run() throws Exception {
                    if (i.getAndIncrement() == 0)
                        throw new InterruptedException("test");
                }
            };
        try {
            txnScheduler.runTask(r, taskOwner);
            fail("Expected Interrupted Exception");
        } catch (InterruptedException ie) {}
        assertEquals(i.get(), 1);
    }

    /**
     * Test transaction handling.
     */

    @Test public void runTaskWithRetry() throws Exception {
        RetryTestRunner runner = new RetryTestRunner();
        txnScheduler.runTask(runner, taskOwner);
        assertTrue(runner.isFinished());
    }

    @Test (expected=RuntimeException.class)
        public void runNonRetriedTask() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    throw new RuntimeException("intentionally thrown");
                }
            }, taskOwner);
    }

    @Test (expected=RuntimeException.class)
        public void runNonRetriedTaskExplicitAbort() throws Exception {
        final TransactionProxy proxy = serverNode.getProxy();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    RuntimeException re = new RuntimeException("intentional");
                    proxy.getCurrentTransaction().abort(re);
                    throw re;
                }
            }, taskOwner);
    }

    @Test (expected=Error.class)
        public void testRunTransactionThrowsError() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    throw new Error("intentionally thrown");
                }
            }, taskOwner);
    }

    /**
     * Test createTaskQueue.
     */

    @Test public void scheduleQueuedTasks() throws Exception {
        TaskQueue queue = txnScheduler.createTaskQueue();
        AtomicInteger runCount = new AtomicInteger(0);
        for (int i = 0; i < 10; i++)
            queue.addTask(new DependentTask(runCount), taskOwner);
        Thread.sleep(500L);
        assertEquals(10, runCount.get());
    }

    @Test (expected=NullPointerException.class)
        public void scheduleQueuedTasksNull() throws Exception {
        TaskQueue queue = txnScheduler.createTaskQueue();
        queue.addTask(null, taskOwner);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleQueuedTasksOwnerNull() throws Exception {
        TaskQueue queue = txnScheduler.createTaskQueue();
        queue.addTask(new DependentTask(null), null);
    }

    /**
     * Utility classes.
     */

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

    public static class DependentTask implements KernelRunnable {
        private static final Object lock = new Object();
        private static boolean isRunning = false;
        private static int objNumberSequence = 0;
        private static volatile int nextExpectedObjNumber = 0;
        private final int objNumber;
        private final AtomicInteger runCounter;
        DependentTask(AtomicInteger runCounter) {
            synchronized (lock) {
                objNumber = objNumberSequence++;
            }
            this.runCounter = runCounter;
        }
        public String getBaseTaskType() {
            return DependentTask.class.getName();
        }
        public void run() throws Exception {
            synchronized (lock) {
                if (isRunning)
                    throw new RuntimeException("another task was running");
                isRunning = true;
            }
            if (nextExpectedObjNumber != objNumber)
                throw new RuntimeException("tasks ran out-of-order");
            nextExpectedObjNumber++;
            runCounter.incrementAndGet();
            isRunning = false;
        }
    }
}
