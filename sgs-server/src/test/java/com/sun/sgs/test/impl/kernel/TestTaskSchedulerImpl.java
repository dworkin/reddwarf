/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.impl.kernel.TestTransactionSchedulerImpl.DependentTask;

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


/** Basic tests for the TaskScheduler interface. */
@RunWith(FilteredNameRunner.class)
public class TestTaskSchedulerImpl {

    private SgsTestNode serverNode = null;
    private TaskScheduler taskScheduler;
    private Identity taskOwner;

    // an empty task that does nothing
    private static final KernelRunnable testTask =
        new TestAbstractKernelRunnable() {
            public void run() throws Exception {}
        };

    // a counter used in some task test
    private volatile int taskCount;

    public TestTaskSchedulerImpl() {}

    /** Per-test initialization */
    @Before public void startup() throws Exception {
        taskCount = 0;
        Properties properties =
            SgsTestNode.getDefaultProperties("TestTaskSchedulerImpl",
					     null, null);
        properties.setProperty(StandardProperties.NODE_TYPE, 
                               NodeType.coreServerNode.name());
        serverNode = new SgsTestNode("TestTaskSchedulerImpl", null, properties);
        taskScheduler = serverNode.getSystemRegistry().
            getComponent(TaskScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
    }

    /** Per-test shutdown */
    @After public void shutdown() throws Exception {
        if (serverNode != null)
            serverNode.shutdown(true);
    }

    /**
     * Task reservation tests.
     */

    @Test public void reserveTask() throws Exception {
        taskScheduler.reserveTask(testTask, taskOwner);
    }

    @Test public void reserveTaskDelayed() throws Exception {
        taskScheduler.reserveTask(testTask, taskOwner,
				  System.currentTimeMillis() + 100);
    }

    @Test public void reserveTasks() throws Exception {
        taskScheduler.reserveTask(testTask, taskOwner);
        taskScheduler.reserveTask(testTask, taskOwner);
        taskScheduler.reserveTask(testTask, taskOwner);
        taskScheduler.reserveTask(testTask, taskOwner);
        taskScheduler.reserveTask(testTask, taskOwner);
        taskScheduler.reserveTask(testTask, taskOwner);
    }

    @Test public void reserveTasksDelayed() throws Exception {
        long time = System.currentTimeMillis() + 100;
        taskScheduler.reserveTask(testTask, taskOwner, time);
        taskScheduler.reserveTask(testTask, taskOwner, time);
        taskScheduler.reserveTask(testTask, taskOwner, time);
        taskScheduler.reserveTask(testTask, taskOwner, time);
        taskScheduler.reserveTask(testTask, taskOwner, time);
        taskScheduler.reserveTask(testTask, taskOwner, time);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTaskNull() throws Exception {
        taskScheduler.reserveTask(null, taskOwner);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTaskNullOwner() throws Exception {
        taskScheduler.reserveTask(testTask, null);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTaskDelayedNull() throws Exception {
        taskScheduler.reserveTask(null, taskOwner, System.currentTimeMillis());
    }

    @Test (expected=NullPointerException.class)
        public void reserveTaskDelayedNullOwner() throws Exception {
        taskScheduler.reserveTask(testTask, null, System.currentTimeMillis());
    }

    @Test public void reserveTaskDelayedTimepassed() throws Exception {
        taskScheduler.reserveTask(testTask, taskOwner,
                                  System.currentTimeMillis() - 50);
    }

    @Test public void useReservedTask() throws Exception {
        TaskReservation reservation = 
            taskScheduler.reserveTask(new IncrementRunner(), taskOwner);
        reservation.use();
        Thread.sleep(200L);
        assertEquals(1, taskCount);
    }

    @Test public void useReservedTaskDelayed() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(new IncrementRunner(), taskOwner,
                                      System.currentTimeMillis() + 50);
        reservation.use();
        Thread.sleep(300L);
        assertEquals(1, taskCount);
    }

    @Test public void cancelReservedTask() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(new IncrementRunner(), taskOwner);
        reservation.cancel();
        Thread.sleep(200L);
        assertEquals(0, taskCount);
    }

    @Test public void cancelReservedTaskDelayed() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(new IncrementRunner(), taskOwner,
                                      System.currentTimeMillis() + 50);
        reservation.cancel();
        Thread.sleep(300L);
        assertEquals(0, taskCount);
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTask() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner);
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTaskDelayed() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner,
                                      System.currentTimeMillis() + 50);
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTask() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner);
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTaskDelayed() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner,
                                      System.currentTimeMillis() + 50);
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTask() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner);
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTaskDelayed() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner,
                                      System.currentTimeMillis() + 50);
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTask() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner);
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTaskDelayed() throws Exception {
        TaskReservation reservation =
            taskScheduler.reserveTask(testTask, taskOwner,
                                      System.currentTimeMillis() + 50);
        reservation.cancel();
        reservation.use();
    }

    /**
     * Task scheduling tests.
     */

    @Test public void scheduleTask() throws Exception {
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        Thread.sleep(200L);
        assertEquals(1, taskCount);
    }

    @Test public void scheduleTaskDelayed() throws Exception {
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner,
                                   System.currentTimeMillis() + 50);
        Thread.sleep(300L);
        assertEquals(1, taskCount);
    }

    @Test public void scheduleTasks() throws Exception {
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner);
        Thread.sleep(400L);
        assertEquals(6, taskCount);
    }

    @Test public void scheduleTasksDelayed() throws Exception {
        long time = System.currentTimeMillis() + 50;
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner, time);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner, time);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner, time);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner, time);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner, time);
        taskScheduler.scheduleTask(new IncrementRunner(), taskOwner, time);
        Thread.sleep(1000L);
        assertEquals(6, taskCount);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleTaskNull() throws Exception {
        taskScheduler.scheduleTask(null, taskOwner);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleTaskOwnerNull() throws Exception {
        taskScheduler.scheduleTask(testTask, null);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleTaskDelayedNull() throws Exception {
        taskScheduler.scheduleTask(null, taskOwner, System.currentTimeMillis());
    }

    @Test (expected=NullPointerException.class)
        public void scheduleTaskDelayedOwnerNull() throws Exception {
        taskScheduler.scheduleTask(testTask, null, System.currentTimeMillis());
    }

    /**
     * Recurring task scheduling tests.
     */

    @Test public void scheduleTaskRecurring() throws Exception {
        taskScheduler.scheduleRecurringTask(testTask, taskOwner,
                                            System.currentTimeMillis(), 50);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleTaskRecurringNull() throws Exception {
        taskScheduler.scheduleRecurringTask(null, taskOwner,
                                            System.currentTimeMillis(), 50);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleTaskRecurringNullOwner() throws Exception {
        taskScheduler.scheduleRecurringTask(testTask, null,
                                            System.currentTimeMillis(), 50);
    }

    @Test (expected=IllegalArgumentException.class)
        public void scheduleTaskRecurringIllegalPeriod() throws Exception {
        taskScheduler.scheduleRecurringTask(testTask, taskOwner,
                                            System.currentTimeMillis(), -1);
    }

    @Test public void cancelAfterStartRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(new IncrementRunner(),
                                                taskOwner,
                                                System.currentTimeMillis() + 50,
                                                50);
        handle.start();
        handle.cancel();
        Thread.sleep(200L);
        assertEquals(0, taskCount);
    }

    @Test public void startSleepAndCancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(new IncrementRunner(),
                                                taskOwner,
                                                System.currentTimeMillis(),
                                                200);
        handle.start();
        Thread.sleep(300L);
        assertEquals(2, taskCount);
        handle.cancel();
        Thread.sleep(200L);
        assertEquals(2, taskCount);
    }

    @Test public void cancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(new IncrementRunner(),
                                                taskOwner,
                                                System.currentTimeMillis(),
                                                50);
        handle.cancel();
        Thread.sleep(100L);
        assertEquals(0, taskCount);
    }

    @Test (expected=IllegalStateException.class)
        public void restartRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(testTask, taskOwner,
                                                System.currentTimeMillis(),
                                                100);
        handle.start();
        try {
            handle.start();
        } finally {
            handle.cancel();
        }
    }

    @Test (expected=IllegalStateException.class)
        public void recancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(testTask, taskOwner,
                                                System.currentTimeMillis(),
                                                100);
        handle.cancel();
        handle.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void startAfterCancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            taskScheduler.scheduleRecurringTask(testTask, taskOwner,
                                                System.currentTimeMillis(),
                                                100);
        handle.cancel();
        handle.start();
    }

    /**
     * Test createTaskQueue.
     */

    @Test public void scheduleQueuedTasks() throws Exception {
        TaskQueue queue = taskScheduler.createTaskQueue();
        AtomicInteger runCount = new AtomicInteger(0);
        for (int i = 0; i < 10; i++)
            queue.addTask(new DependentTask(runCount), taskOwner);
        Thread.sleep(500L);
        assertEquals(10, runCount.get());
    }

    @Test (expected=NullPointerException.class)
        public void scheduleQueuedTasksNull() throws Exception {
        TaskQueue queue = taskScheduler.createTaskQueue();
        queue.addTask(null, taskOwner);
    }

    @Test (expected=NullPointerException.class)
        public void scheduleQueuedTasksOwnerNull() throws Exception {
        TaskQueue queue = taskScheduler.createTaskQueue();
        queue.addTask(new DependentTask(null), null);
    }

    /**
     * Utility classes.
     */

    private class IncrementRunner extends TestAbstractKernelRunnable {
        public void run() {
            taskCount++;
        }
    }
}
