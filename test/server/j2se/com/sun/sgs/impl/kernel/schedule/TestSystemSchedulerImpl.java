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

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskReservation;

import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.DummyTaskOwner;
import com.sun.sgs.test.util.ParameterizedNameRunner;
import com.sun.sgs.test.util.UtilThreadGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.LinkedList;
import java.util.Properties;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.junit.runners.Parameterized;



/**
 * Tests the various <code>SystemScheduler</code> implementations. Note
 * that this is a general collection of tests that apply to any scheduler.
 * All of these tests use the default backing <code>ApplicationScheduler</code>
 * (<code>FIFOApplicationScheduler</code>).
 */
@RunWith(ParameterizedNameRunner.class)
public class TestSystemSchedulerImpl {
    @Parameterized.Parameters
        public static LinkedList<String[]> data() {
        LinkedList<String[]> params = new LinkedList<String[]>();
        params.add(new String [] {SingleAppSystemScheduler.class.getName()});
        params.add(new String [] {RoundRobinSystemScheduler.class.getName()});
        params.add(new String [] {DeficitSystemScheduler.class.getName()});
        return params;
    }

    // the default owner for tests
    private static final DummyTaskOwner testOwner = new DummyTaskOwner();

    // a second owner used to let us test multiple contexts
    private static final DummyTaskOwner testOwner2 = new DummyTaskOwner();

    // the default context used for the tests
    private static final KernelAppContext testContext =
        testOwner.getContext();

    // a second context used to make sure that we're always testing with at
    // least two contexts
    private static final KernelAppContext testContext2 =
        testOwner2.getContext();

    // a basic task that shouldn't be run but can be used for tests
    private static final ScheduledTask testTask = getNewTask();

    // a basic, recurring task that shouldn't be run but can be used for tests
    private static final ScheduledTask recurringTestTask =
        new ScheduledTask(new DummyKernelRunnable(), testOwner,
                          Priority.getDefaultPriority(), 0, 100);

    // the fully-qualified name of the scheduler we're testing
    private String systemSchedulerName;

    // the scheduler used in any given test
    private SystemScheduler systemScheduler;

    public TestSystemSchedulerImpl(String systemSchedulerName) {
        this.systemSchedulerName = systemSchedulerName;
    }

    @Before public void setupSingleTest() {
        systemScheduler = null;
    }

    @After public void teardownSingleTest() {
        if (systemScheduler != null)
            systemScheduler.shutdown();
    }

    /**
     * Constructor tests.
     */

    @Test public void constructorWithValidArgs() throws Exception {
        getSchedulerInstance();
    }

    @Test (expected=NullPointerException.class)
        public void constructorWithNullArgs() throws Exception {
        getSchedulerInstance(null);
    }

    /**
     * Application registration tests.
     */

    @Test (expected=NullPointerException.class)
        public void registerNullContext() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.registerApplication(null, new Properties());
    }

    @Test (expected=NullPointerException.class)
        public void registerNullProperties() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.registerApplication(testContext, null);
    }

    @Test (expected=IllegalArgumentException.class)
        public void reregisterContext() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.registerApplication(testContext, new Properties());
    }

    /**
     * Task reservation tests.
     */

    @Test public void reserveTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(testTask);
    }

    @Test public void reserveTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
    }

    @Test public void reserveTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
    }

    @Test public void reserveTasksDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        ScheduledTask delayedTask = new ScheduledTask(testTask, now + 100);
        scheduler.reserveTask(delayedTask);
        scheduler.reserveTask(delayedTask);
        scheduler.reserveTask(delayedTask);
        scheduler.reserveTask(delayedTask);
        scheduler.reserveTask(delayedTask);
        scheduler.reserveTask(delayedTask);
    }

    @Test (expected=NullPointerException.class)
        public void reserveTaskNull() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(null);
    }

    @Test (expected=TaskRejectedException.class)
        public void reserveTaskUnknownContext() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(getNewTask(new DummyTaskOwner()));
    }

    @Test public void useReservedTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.use();
    }

    @Test public void useReservedTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.use();
    }

    @Test public void cancelReservedTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.cancel();
    }

    @Test public void cancelReservedTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=TaskRejectedException.class)
        public void reserveTaskRecurring() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(recurringTestTask);
    }

    /**
     * Task addition tests.
     */

    @Test public void addTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(testTask);
    }

    @Test public void addTaskDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.addTask(new ScheduledTask(testTask, now + 100));
    }

    @Test public void addTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
    }

    @Test public void addTasksDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        ScheduledTask delayedTask = new ScheduledTask(testTask, now + 100);
        scheduler.addTask(delayedTask);
        scheduler.addTask(delayedTask);
        scheduler.addTask(delayedTask);
        scheduler.addTask(delayedTask);
        scheduler.addTask(delayedTask);
        scheduler.addTask(delayedTask);
    }

    @Test (expected=NullPointerException.class)
        public void addTaskNull() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(null);
    }

    @Test (expected=TaskRejectedException.class)
        public void addTaskUnknownContext() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask(new DummyTaskOwner()));
    }

    /**
     * Recurring task addition tests.
     */

    @Test public void addTaskRecurring() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
    }

    @Test public void addTasksRecurring() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
    }

    @Test (expected=NullPointerException.class)
        public void addTaskRecurringNull() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addRecurringTask(null);
    }

    @Test (expected=TaskRejectedException.class)
        public void addTaskRecurringUnknownContext() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addRecurringTask(getNewTask(new DummyTaskOwner()));
    }

    @Test (expected=IllegalArgumentException.class)
        public void addTaskRecurringNotRecurring() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addRecurringTask(testTask);
    }

    @Test public void cancelAfterStartRecurringTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.start();
        handle.cancel();
    }

    @Test public void startSleepAndCancelRecurringTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.start();
        Thread.sleep(200);
        handle.cancel();
    }

    @Test public void cancelRecurringTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.cancel();
    }

    @Test public void restartRecurringTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
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
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.cancel();
        handle.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void startAfterCancelRecurringTask() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.cancel();
        handle.start();
    }

    /**
     * Add and consume correctness tests.
     */

    @Test (timeout=100)
        public void addAndConsumeTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
    }

    @Test (timeout=250)
        public void addAndConsumeTasksDelayed() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask(100));
        scheduler.addTask(getNewTask(120));
        scheduler.addTask(getNewTask(140));
        scheduler.addTask(getNewTask(130));
        scheduler.addTask(getNewTask(115));
        scheduler.addTask(getNewTask(107));
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
    }

    @Test (timeout=300)
        public void reserveAndConsumeTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(getNewTask());
        reservation.use();
        reservation = scheduler.reserveTask(getNewTask());
        reservation.cancel();
        reservation = scheduler.reserveTask(getNewTask(100));
        reservation.use();
        reservation = scheduler.reserveTask(getNewTask(120));
        reservation.cancel();
        reservation = scheduler.reserveTask(getNewTask(140));
        reservation.use();
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
    }

    @Test (timeout=100)
        public void addAndConsumeTasksRecurring() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        long startTime = System.currentTimeMillis();
        RecurringTaskHandle handle1 =
            scheduler.addRecurringTask(new ScheduledTask(recurringTestTask,
                                                         startTime));
        handle1.start();
        RecurringTaskHandle handle2 =
            scheduler.addRecurringTask(new ScheduledTask(recurringTestTask,
                                                         startTime));
        handle2.cancel();
        RecurringTaskHandle handle3 =
            scheduler.addRecurringTask(new ScheduledTask(recurringTestTask,
                                                         startTime));
        handle3.start();
        handle1.cancel();
        handle3.cancel();
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
    }

    @Test (timeout=100)
        public void addTasksDifferentContexts() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask(testOwner2));
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask(testOwner2));
        scheduler.addTask(getNewTask(testOwner2));
        scheduler.addTask(getNewTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
        assertNotNull(scheduler.getNextTask());
    }

    /**
     * Test scale through number of tasks and number of threads.
     */

    @Test (timeout=1000)
        public void addAndConsumeManyTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        for (int i = 0; i < 543; i++)
            scheduler.addTask(getNewTask(50));
        for (int i = 0; i < 672; i++)
            scheduler.addTask(getNewTask());
        for (int i = 0; i < 1215; i++)
            scheduler.getNextTask();
    }

    @Test (timeout=5000)
        public void fewThreadsFewTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        Runnable [] r = new Runnable[4];
        r[0] = new ProducerRunnable(scheduler, 6);
        r[1] = new ProducerRunnable(scheduler, 7);
        r[2] = new ConsumerRunnable(scheduler, 5);
        r[3] = new ConsumerRunnable(scheduler, 8);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void manyThreadsFewTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        Runnable [] r = new Runnable[128];
        for (int i = 0; i < 64; i++)
            r[i] = new ProducerRunnable(scheduler, 4);
        for (int i = 64; i < 128; i++)
            r[i] = new ConsumerRunnable(scheduler, 4);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void fewThreadsManyTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        Runnable [] r = new Runnable[4];
        r[0] = new ProducerRunnable(scheduler, 674);
        r[1] = new ProducerRunnable(scheduler, 458);
        r[2] = new ConsumerRunnable(scheduler, 539);
        r[3] = new ConsumerRunnable(scheduler, 593);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void manyThreadsManyTasks() throws Exception {
        SystemScheduler scheduler = getSchedulerInstance();
        Runnable [] r = new Runnable[128];
        for (int i = 0; i < 64; i++)
            r[i] = new ProducerRunnable(scheduler, 83);
        for (int i = 64; i < 128; i++)
            r[i] = new ConsumerRunnable(scheduler, 83);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    /**
     * Utility methods.
     */

    protected SystemScheduler getSchedulerInstance() throws Exception {
        return getSchedulerInstance(new Properties());
    }

    protected SystemScheduler getSchedulerInstance(Properties p)
        throws Exception
    {
        Class<?> schedulerClass = Class.forName(systemSchedulerName);
        Constructor<?> schedulerConstructor =
            schedulerClass.getConstructor(Properties.class);
        try {
            SystemScheduler scheduler = 
                (SystemScheduler)(schedulerConstructor.newInstance(p));
            scheduler.registerApplication(testContext, p);
            scheduler.registerApplication(testContext2, p);
            systemScheduler = scheduler;
            return scheduler;
        } catch (InvocationTargetException e) {
            throw (Exception)(e.getCause());
        }
    }

    protected static ScheduledTask getNewTask() {
        return getNewTask(0, testOwner);
    }

    protected static ScheduledTask getNewTask(long delay) {
        return getNewTask(delay, testOwner);
    }

    protected static ScheduledTask getNewTask(TaskOwner owner) {
        return getNewTask(0, owner);
    }

    protected static ScheduledTask getNewTask(long delay, TaskOwner owner) {
        long time = System.currentTimeMillis() + delay;
        return new ScheduledTask(new DummyKernelRunnable(), owner,
                                 Priority.getDefaultPriority(), time);
    }

    /**
     * Utility classes.
     */

    private class ConsumerRunnable implements Runnable {
        private SystemScheduler scheduler;
        private int tasks;
        public ConsumerRunnable(SystemScheduler scheduler, int tasks) {
            this.scheduler = scheduler;
            this.tasks = tasks;
        }
        public void run() {
            try {
                for (int i = 0; i< tasks; i++)
                    scheduler.getNextTask();
            } catch (InterruptedException ie) {}
        }
    }

    private class ProducerRunnable implements Runnable {
        private SystemScheduler scheduler;
        private int tasks;
        public ProducerRunnable(SystemScheduler scheduler, int tasks) {
            this.scheduler = scheduler;
            this.tasks = tasks;
        }
        public void run() {
            for (int i = 0; i < tasks; i++)
                scheduler.addTask(getNewTask());
        }
    }

    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestSystemSchedulerImpl.class);
    }

}
