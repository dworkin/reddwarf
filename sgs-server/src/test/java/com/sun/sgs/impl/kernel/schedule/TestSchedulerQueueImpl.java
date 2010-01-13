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

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.UtilThreadGroup;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.LinkedList;
import java.util.Properties;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.junit.runners.Parameterized;


/**
 * Tests for the various <code>SchedulerQueue</code> implementations.
 * Note that this is a general collection of tests that apply to any
 * queue.
 */
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestSchedulerQueueImpl {
    @Parameterized.Parameters 
        public static LinkedList<String[]> data() {
        LinkedList<String[]> params = new LinkedList<String[]>();
        params.add(new String [] {FIFOSchedulerQueue.class.getName()});
        params.add(new String [] {WindowSchedulerQueue.class.getName()});
        return params;
    }

    // a basic task that shouldn't be run but can be used for tests
    private static final ScheduledTaskImpl testTask = new ScheduledTaskImpl();

    // the fully-qualified name of the queue we're testing
    private String schedulerQueueName;

    // the scheduler used in any given test
    private SchedulerQueue schedulerQueue;

    @BeforeClass public static void setupSystem() {
        // class-wide setup can happen here
    }

    @AfterClass public static void teardownSystem() {
        // class-wide teardown can happen here
    }

    public TestSchedulerQueueImpl(String schedulerQueueName) {
        this.schedulerQueueName = schedulerQueueName;
    }

    @Before public void setupSingleTest() {
        schedulerQueue = null;
    }

    @After public void teardownSingleTest() {
        if (schedulerQueue != null)
            schedulerQueue.shutdown();
    }

    /**
     * Constructor tests.
     */

    @Test public void constructorWithValidArgs() throws Exception {
        getQueueInstance();
    }

    @Test(expected=NullPointerException.class)
        public void constructorWithNullArgs() throws Exception {
        try {
            getQueueInstance(null);
        } catch (InvocationTargetException ite) {
            throw (Exception)(ite.getCause());
        }
        fail("Invocation exception expected");
    }

    /**
     * Task reservation tests.
     */

    @Test public void reserveTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.reserveTask(testTask);
    }

    @Test public void reserveTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
    }

    @Test public void reserveTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.reserveTask(testTask);
        queue.reserveTask(testTask);
        queue.reserveTask(testTask);
        queue.reserveTask(testTask);
        queue.reserveTask(testTask);
        queue.reserveTask(testTask);
    }

    @Test public void reserveTasksDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        ScheduledTask delayedTask = new ScheduledTaskImpl(testTask, 100);
        queue.reserveTask(delayedTask);
        queue.reserveTask(delayedTask);
        queue.reserveTask(delayedTask);
        queue.reserveTask(delayedTask);
        queue.reserveTask(delayedTask);
        queue.reserveTask(delayedTask);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTaskNull() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.reserveTask(null);
    }

    @Test public void useReservedTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation = queue.reserveTask(testTask);
        reservation.use();
    }

    @Test public void useReservedTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =
            queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
        reservation.use();
    }

    @Test public void cancelReservedTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation = queue.reserveTask(testTask);
        reservation.cancel();
    }

    @Test public void cancelReservedTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =
            queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation = queue.reserveTask(testTask);
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =
            queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation = queue.reserveTask(testTask);
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =
            queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation = queue.reserveTask(testTask);
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =
            queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation = queue.reserveTask(testTask);
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =
            queue.reserveTask(new ScheduledTaskImpl(testTask, 100));
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=TaskRejectedException.class)
        public void reserveTaskRecurring() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.reserveTask(new ScheduledTaskImpl(0, 100));
    }

    /**
     * Task addition tests.
     */

    @Test public void addTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(testTask);
    }

    @Test public void addTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(new ScheduledTaskImpl(testTask, 100));
    }

    @Test public void addTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(testTask);
        queue.addTask(testTask);
        queue.addTask(testTask);
        queue.addTask(testTask);
        queue.addTask(testTask);
        queue.addTask(testTask);
    }

    @Test public void addTasksDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        ScheduledTask delayedTask = new ScheduledTaskImpl(testTask, 100);
        queue.addTask(delayedTask);
        queue.addTask(delayedTask);
        queue.addTask(delayedTask);
        queue.addTask(delayedTask);
        queue.addTask(delayedTask);
        queue.addTask(delayedTask);
    }

    @Test (expected=NullPointerException.class)
        public void addTaskNull() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(null);
    }

    /**
     * Recurring task addition tests.
     */

    @Test public void addTaskRecurring() throws Exception {
        getRecurringTask();
    }

    @Test public void addTasksRecurring() throws Exception {
        getRecurringTask();
        getRecurringTask();
        getRecurringTask();
        getRecurringTask();
        getRecurringTask();
        getRecurringTask();
    }

    @Test (expected=NullPointerException.class)
        public void addTaskRecurringNull() throws Exception {
        getQueueInstance().createRecurringTaskHandle(null);
    }

    @Test public void cancelAfterStartRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            getRecurringTask().getRecurringTaskHandle();
        handle.start();
        handle.cancel();
    }

    @Test public void startSleepAndCancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            getRecurringTask().getRecurringTaskHandle();
        handle.start();
        Thread.sleep(200);
        handle.cancel();
    }

    @Test public void cancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            getRecurringTask().getRecurringTaskHandle();
        handle.cancel();
    }

    @Test public void restartRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            getRecurringTask().getRecurringTaskHandle();
        handle.start();
        try {
            handle.start();
            fail("Expected an IllegalStateException");
        } catch(IllegalStateException e) {}
        finally {
            // this is to make sure that the timer doesn't keep going
            handle.cancel();
        }
    }

    @Test (expected=IllegalStateException.class)
        public void recancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            getRecurringTask().getRecurringTaskHandle();
        handle.cancel();
        handle.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void startAfterCancelRecurringTask() throws Exception {
        RecurringTaskHandle handle =
            getRecurringTask().getRecurringTaskHandle();
        handle.cancel();
        handle.start();
    }

    /**
     * Add and consume correctness tests.
     */

    @Test public void addAndConsumeTask() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        ScheduledTask task = new ScheduledTaskImpl();
        queue.addTask(task);
        assertEquals(task, queue.getNextTask(false));
    }

    @Test (timeout=100)
        public void addAndConsumeTaskWaiting() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(new ScheduledTaskImpl());
        queue.getNextTask(true);
    }

    @Test public void addAndConsumeTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        assertNotNull(queue.getNextTask(false));
        assertNotNull(queue.getNextTask(false));
        assertNotNull(queue.getNextTask(false));
        assertNull(queue.getNextTask(false));

        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        queue.addTask(new ScheduledTaskImpl());
        LinkedList<ScheduledTask> tasks = new LinkedList<ScheduledTask>();
        assertEquals(6, queue.getNextTasks(tasks, 10));
    }

    @Test (timeout=300)
        public void reserveAndConsumeTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        TaskReservation reservation =queue.reserveTask(new ScheduledTaskImpl());
        reservation.use();
        reservation = queue.reserveTask(new ScheduledTaskImpl());
        reservation.cancel();
        reservation = queue.reserveTask(new ScheduledTaskImpl(100));
        reservation.use();
        reservation = queue.reserveTask(new ScheduledTaskImpl(120));
        reservation.cancel();
        reservation = queue.reserveTask(new ScheduledTaskImpl(140));
        reservation.use();
        assertNotNull(queue.getNextTask(false));
        assertNull(queue.getNextTask(false));
        assertNotNull(queue.getNextTask(true));
        assertNotNull(queue.getNextTask(true));
        assertNull(queue.getNextTask(false));
    }

    @Test public void addAndConsumeTaskDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        ScheduledTask task = new ScheduledTaskImpl(100);
        queue.addTask(task);
        assertNull(queue.getNextTask(false));
        Thread.sleep(200);
        assertEquals(task, queue.getNextTask(false));
    }

    @Test (timeout=200)
        public void addAndConsumeTaskDelayedWaiting() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(new ScheduledTaskImpl(100));
        queue.getNextTask(true);
    }

    @Test public void addAndConsumeTasksDelayed() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        queue.addTask(new ScheduledTaskImpl(100));
        queue.addTask(new ScheduledTaskImpl(100));
        queue.addTask(new ScheduledTaskImpl(120));
        queue.addTask(new ScheduledTaskImpl(110));
        Thread.sleep(200);
        assertNotNull(queue.getNextTask(false));
        assertNotNull(queue.getNextTask(false));
        assertNotNull(queue.getNextTask(false));
        assertNotNull(queue.getNextTask(false));
        assertNull(queue.getNextTask(false));

        queue.addTask(new ScheduledTaskImpl(100));
        queue.addTask(new ScheduledTaskImpl(100));
        queue.addTask(new ScheduledTaskImpl(120));
        queue.addTask(new ScheduledTaskImpl(110));
        queue.addTask(new ScheduledTaskImpl(150));
        LinkedList<ScheduledTask> tasks = new LinkedList<ScheduledTask>();
        assertEquals(0, queue.getNextTasks(tasks, 5));
        Thread.sleep(200);
        assertEquals(3, queue.getNextTasks(tasks, 3));
        assertEquals(2, queue.getNextTasks(tasks, 3));
    }

    @Test public void addAndConsumeTasksRecurring() throws Exception {
        RecurringTaskHandle handle1 =
            getRecurringTask().getRecurringTaskHandle();
        handle1.start();
        RecurringTaskHandle handle2 =
            getRecurringTask().getRecurringTaskHandle();
        handle2.cancel();
        RecurringTaskHandle handle3 =
            getRecurringTask().getRecurringTaskHandle();
        handle3.start();
        handle1.cancel();
        handle3.cancel();
        LinkedList<ScheduledTask> tasks = new LinkedList<ScheduledTask>();
        assertEquals(2, schedulerQueue.getNextTasks(tasks, 6));
        Thread.sleep(150);
        assertNull(schedulerQueue.getNextTask(false));
    }

    /**
     * Test scale through number of tasks and number of threads.
     */

    @Test (timeout=1000)
        public void addAndConsumeManyTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        for (int i = 0; i < 537; i++)
            queue.addTask(new ScheduledTaskImpl(50));
        for (int i = 0; i < 679; i++)
            queue.addTask(new ScheduledTaskImpl());
        int count = 0;
        while (queue.getNextTask(false) != null)
            count++;
        Thread.sleep(100);
        while (queue.getNextTask(false) != null)
            count++;
        assertEquals(1216, count);
    }

    @Test (timeout=5000)
        public void fewThreadsFewTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        Runnable [] r = new Runnable[4];
        r[0] = new ProducerRunnable(queue, 1);
        r[1] = new ProducerRunnable(queue, 10);
        r[2] = new ConsumerRunnable(queue, 4);
        r[3] = new ConsumerRunnable(queue, 7);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void manyThreadsFewTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        Runnable [] r = new Runnable[128];
        for (int i = 0; i < 64; i++)
            r[i] = new ProducerRunnable(queue, 4);
        for (int i = 64; i < 128; i++)
            r[i] = new ConsumerRunnable(queue, 4);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void fewThreadsManyTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        Runnable [] r = new Runnable[4];
        r[0] = new ProducerRunnable(queue, 674);
        r[1] = new ProducerRunnable(queue, 458);
        r[2] = new ConsumerRunnable(queue, 539);
        r[3] = new ConsumerRunnable(queue, 593);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void manyThreadsManyTasks() throws Exception {
        SchedulerQueue queue = getQueueInstance();
        Runnable [] r = new Runnable[128];
        for (int i = 0; i < 64; i++)
            r[i] = new ProducerRunnable(queue, 83);
        for (int i = 64; i < 128; i++)
            r[i] = new ConsumerRunnable(queue, 83);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    /**
     * Utility methods.
     */

    protected SchedulerQueue getQueueInstance() throws Exception {
        return getQueueInstance(new Properties());
    }

    protected SchedulerQueue getQueueInstance(Properties p) throws Exception {
        Constructor<?> schedulerConstructor = null;
        Class<?> schedulerClass = Class.forName(schedulerQueueName);
        schedulerConstructor =
            schedulerClass.getConstructor(Properties.class);

        schedulerQueue = (SchedulerQueue)(schedulerConstructor.newInstance(p));
        return schedulerQueue;
    }

    protected ScheduledTaskImpl getRecurringTask() throws Exception {
        if (schedulerQueue == null)
            getQueueInstance();
        ScheduledTaskImpl task = new ScheduledTaskImpl(0, 100);
        task.setRecurringTaskHandle(schedulerQueue.
                                    createRecurringTaskHandle(task));
        return task;
    }

    /**
     * Utility classes.
     */

    private class ConsumerRunnable implements Runnable {
        private SchedulerQueue queue;
        private int tasks;
        public ConsumerRunnable(SchedulerQueue queue, int tasks) {
            this.queue = queue;
            this.tasks = tasks;
        }
        public void run() {
            try {
                for (int i = 0; i< tasks; i++)
                    queue.getNextTask(true);
            } catch (InterruptedException ie) {}
        }
    }

    private class ProducerRunnable implements Runnable {
        private SchedulerQueue queue;
        private int tasks;
        public ProducerRunnable(SchedulerQueue queue, int tasks) {
            this.queue = queue;
            this.tasks = tasks;
        }
        public void run() {
            for (int i = 0; i < tasks; i++)
                queue.addTask(new ScheduledTaskImpl());
        }
    }

    private static class ScheduledTaskImpl implements ScheduledTask {
        private final KernelRunnable task;
        private final DummyIdentity owner;
        private final long start;
        private final long period;
        private long timeout = 100;
        private Throwable lastFailure = null;
        private RecurringTaskHandle handle = null;
        private boolean cancelled = false;

        ScheduledTaskImpl() {
            this(0, NON_RECURRING);
        }
        ScheduledTaskImpl(long delay) {
            this(delay, NON_RECURRING);
        }
        ScheduledTaskImpl(long delay, long period) {
            this.task = new DummyKernelRunnable();
            this.owner = new DummyIdentity();
            this.start = System.currentTimeMillis() + delay;
            this.period = period;
        }
        ScheduledTaskImpl(ScheduledTaskImpl schedTask) {
            this(schedTask, 0);
        }
        ScheduledTaskImpl(ScheduledTaskImpl schedTask, long delay) {
            this.task = schedTask.task;
            this.owner = schedTask.owner;
            this.start = System.currentTimeMillis() + delay;
            this.period = schedTask.period;
        }
        public KernelRunnable getTask() { return task; }
        public Identity getOwner() { return owner; }
        public Priority getPriority() { return Priority.getDefaultPriority(); }
        public long getStartTime() { return start; }
        public long getPeriod() { return period; }
        public long getTimeout() { return timeout; }
        public Throwable getLastFailure() { return lastFailure; }
        public void setPriority(Priority priority) {

        }
        public void setTimeout(long timeout) {
            throw new UnsupportedOperationException("not supported");
        }
        public int getTryCount() { return 0; }
        public boolean isRecurring() { return period != NON_RECURRING; }
        void setRecurringTaskHandle(RecurringTaskHandle handle) {
            this.handle = handle;
        }
        public RecurringTaskHandle getRecurringTaskHandle() {
            return handle;
        }
        public synchronized boolean isCancelled() {
            return cancelled;
        }
        public synchronized boolean cancel(boolean allowInterrupt) {
            if (cancelled)
                return false;
            cancelled = true;
            return true;
        }
    }
}
