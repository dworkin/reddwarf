/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import com.sun.sgs.test.util.DummyTaskOwner;
import com.sun.sgs.test.util.ParameterizedNameRunner;
import com.sun.sgs.test.util.UtilThreadGroup;

import java.util.LinkedList;
import java.util.Properties;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.junit.runners.Parameterized;


/**
 * Tests for the various <code>ApplicationScheduler</code> implementations.
 * Note that this is a general collection of tests that apply to any
 * scheduler.
 */
@RunWith(ParameterizedNameRunner.class)
public class TestApplicationSchedulerImpl {
    @Parameterized.Parameters 
        public static LinkedList<String[]> data() {
        LinkedList<String[]> params = new LinkedList<String[]>();
        params.add(new String [] {FIFOApplicationScheduler.class.getName()});
        params.add(new String [] {WindowApplicationScheduler.class.getName()});
        return params;
    }

    // a basic task that shouldn't be run but can be used for tests
    private static final ScheduledTask testTask = getNewTask();

    // a basic, recurring task that shouldn't be run but can be used for tests
    private static final ScheduledTask recurringTestTask =
        new ScheduledTask(new KernelRunnable() {
                public String getBaseTaskType() {
                    return getClass().getName();
                }
                public void run() throws Exception {}
            }, new DummyTaskOwner(), Priority.getDefaultPriority(), 0, 100);

    // the fully-qualified name of the scheduler we're testing
    private String appSchedulerName;

    // the scheduler used in any given test
    private ApplicationScheduler applicationScheduler;

    @BeforeClass public static void setupSystem() {
        // class-wide setup can happen here
    }

    @AfterClass public static void teardownSystem() {
        // class-wide teardown can happen here
    }

    public TestApplicationSchedulerImpl(String appSchedulerName) {
        this.appSchedulerName = appSchedulerName;
    }

    @Before public void setupSingleTest() {
        applicationScheduler = null;
    }

    @After public void teardownSingleTest() {
        if (applicationScheduler != null)
            applicationScheduler.shutdown();
    }

    /**
     * Constructor tests.
     */

    @Test public void constructorWithValidArgs() throws Exception {
        getSchedulerInstance();
    }

    @Test(expected=NullPointerException.class)
        public void constructorWithNullArgs() throws Exception {
        getSchedulerInstance(null);
    }

    /**
     * Task reservation tests.
     */

    @Test public void reserveTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(testTask);
    }

    @Test public void reserveTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
    }

    @Test public void reserveTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
        scheduler.reserveTask(testTask);
    }

    @Test public void reserveTasksDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(null);
    }

    @Test public void useReservedTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.use();
    }

    @Test public void useReservedTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.use();
    }

    @Test public void cancelReservedTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.cancel();
    }

    @Test public void cancelReservedTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void reuseReservedTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.use();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void recancelReservedTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.cancel();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void cancelAfterUseReservedTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.use();
        reservation.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        TaskReservation reservation = scheduler.reserveTask(testTask);
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=IllegalStateException.class)
        public void useAfterCancelReservedTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        TaskReservation reservation =
            scheduler.reserveTask(new ScheduledTask(testTask, now + 100));
        reservation.cancel();
        reservation.use();
    }

    @Test (expected=TaskRejectedException.class)
        public void reserveTaskRecurring() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.reserveTask(recurringTestTask);
    }

    /**
     * Task addition tests.
     */

    @Test public void addTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(testTask);
    }

    @Test public void addTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.addTask(new ScheduledTask(testTask, now + 100));
    }

    @Test public void addTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
        scheduler.addTask(testTask);
    }

    @Test public void addTasksDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(null);
    }

    /**
     * Recurring task addition tests.
     */

    @Test public void addTaskRecurring() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        scheduler.addRecurringTask(new ScheduledTask(recurringTestTask, now));
    }

    @Test public void addTasksRecurring() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addRecurringTask(null);
    }

    @Test (expected=IllegalArgumentException.class)
        public void addTaskRecurringNotRecurring() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addRecurringTask(testTask);
    }

    @Test public void cancelAfterStartRecurringTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.start();
        handle.cancel();
    }

    @Test public void startSleepAndCancelRecurringTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.start();
        Thread.sleep(200);
        handle.cancel();
    }

    @Test public void cancelRecurringTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.cancel();
    }

    @Test public void restartRecurringTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.cancel();
        handle.cancel();
    }

    @Test (expected=IllegalStateException.class)
        public void startAfterCancelRecurringTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        long now = System.currentTimeMillis();
        RecurringTaskHandle handle = scheduler.
            addRecurringTask(new ScheduledTask(recurringTestTask, now));
        handle.cancel();
        handle.start();
    }

    /**
     * Add and consume correctness tests.
     */

    @Test public void addAndConsumeTask() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        ScheduledTask task = getNewTask();
        scheduler.addTask(task);
        assertEquals(task, scheduler.getNextTask(false));
    }

    @Test (timeout=100)
        public void addAndConsumeTaskWaiting() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask());
        scheduler.getNextTask(true);
    }

    @Test public void addAndConsumeTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        assertNotNull(scheduler.getNextTask(false));
        assertNotNull(scheduler.getNextTask(false));
        assertNotNull(scheduler.getNextTask(false));
        assertNull(scheduler.getNextTask(false));

        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        scheduler.addTask(getNewTask());
        LinkedList<ScheduledTask> tasks = new LinkedList<ScheduledTask>();
        assertEquals(6, scheduler.getNextTasks(tasks, 10));
    }

    @Test (timeout=300)
        public void reserveAndConsumeTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        assertNotNull(scheduler.getNextTask(false));
        assertNull(scheduler.getNextTask(false));
        assertNotNull(scheduler.getNextTask(true));
        assertNotNull(scheduler.getNextTask(true));
        assertNull(scheduler.getNextTask(false));
    }

    @Test public void addAndConsumeTaskDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        ScheduledTask task = getNewTask(100);
        scheduler.addTask(task);
        assertNull(scheduler.getNextTask(false));
        Thread.sleep(200);
        assertEquals(task, scheduler.getNextTask(false));
    }

    @Test (timeout=200)
        public void addAndConsumeTaskDelayedWaiting() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask(100));
        scheduler.getNextTask(true);
    }

    @Test public void addAndConsumeTasksDelayed() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        scheduler.addTask(getNewTask(100));
        scheduler.addTask(getNewTask(100));
        scheduler.addTask(getNewTask(120));
        scheduler.addTask(getNewTask(110));
        Thread.sleep(200);
        assertNotNull(scheduler.getNextTask(false));
        assertNotNull(scheduler.getNextTask(false));
        assertNotNull(scheduler.getNextTask(false));
        assertNotNull(scheduler.getNextTask(false));
        assertNull(scheduler.getNextTask(false));

        scheduler.addTask(getNewTask(100));
        scheduler.addTask(getNewTask(100));
        scheduler.addTask(getNewTask(120));
        scheduler.addTask(getNewTask(110));
        scheduler.addTask(getNewTask(150));
        LinkedList<ScheduledTask> tasks = new LinkedList<ScheduledTask>();
        assertEquals(0, scheduler.getNextTasks(tasks, 5));
        Thread.sleep(200);
        assertEquals(3, scheduler.getNextTasks(tasks, 3));
        assertEquals(2, scheduler.getNextTasks(tasks, 3));
    }

    @Test public void addAndConsumeTasksRecurring() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        LinkedList<ScheduledTask> tasks = new LinkedList<ScheduledTask>();
        assertEquals(2, scheduler.getNextTasks(tasks, 6));
        Thread.sleep(150);
        assertNull(scheduler.getNextTask(false));
    }

    /**
     * Test scale through number of tasks and number of threads.
     */

    @Test (timeout=1000)
        public void addAndConsumeManyTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        for (int i = 0; i < 537; i++)
            scheduler.addTask(getNewTask(50));
        for (int i = 0; i < 679; i++)
            scheduler.addTask(getNewTask());
        int count = 0;
        while (scheduler.getNextTask(false) != null)
            count++;
        Thread.sleep(100);
        while (scheduler.getNextTask(false) != null)
            count++;
        assertEquals(1216, count);
    }

    @Test (timeout=5000)
        public void fewThreadsFewTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
        Runnable [] r = new Runnable[4];
        r[0] = new ProducerRunnable(scheduler, 1);
        r[1] = new ProducerRunnable(scheduler, 10);
        r[2] = new ConsumerRunnable(scheduler, 4);
        r[3] = new ConsumerRunnable(scheduler, 7);
        UtilThreadGroup threadGroup = new UtilThreadGroup(r);
        threadGroup.run();
        assertEquals(0, threadGroup.getFailureCount());
    }

    @Test (timeout=5000)
        public void manyThreadsFewTasks() throws Exception {
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        ApplicationScheduler scheduler = getSchedulerInstance();
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
        ApplicationScheduler scheduler = getSchedulerInstance();
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

    protected ApplicationScheduler getSchedulerInstance() throws Exception {
        Properties p = new Properties();
        p.setProperty(ApplicationScheduler.APPLICATION_SCHEDULER_PROPERTY,
                      appSchedulerName);
        return getSchedulerInstance(p);
    }

    protected ApplicationScheduler getSchedulerInstance(Properties p)
        throws Exception
    {
        applicationScheduler = LoaderUtil.getScheduler(p);
        if (applicationScheduler == null)
            throw new Exception("Couldn't load the app scheduler");
        return applicationScheduler;
    }

    protected static ScheduledTask getNewTask() {
        return getNewTask(0);
    }

    protected static ScheduledTask getNewTask(long delay) {
        long time = System.currentTimeMillis() + delay;
        return new ScheduledTask(new KernelRunnable() {
                public String getBaseTaskType() {
                    return getClass().getName();
                }
                public void run() throws Exception {}
            }, new DummyTaskOwner(), Priority.getDefaultPriority(), time);
    }

    /**
     * Utility classes.
     */

    private class ConsumerRunnable implements Runnable {
        private ApplicationScheduler scheduler;
        private int tasks;
        public ConsumerRunnable(ApplicationScheduler scheduler, int tasks) {
            this.scheduler = scheduler;
            this.tasks = tasks;
        }
        public void run() {
            try {
                for (int i = 0; i< tasks; i++)
                    scheduler.getNextTask(true);
            } catch (InterruptedException ie) {}
        }
    }

    private class ProducerRunnable implements Runnable {
        private ApplicationScheduler scheduler;
        private int tasks;
        public ProducerRunnable(ApplicationScheduler scheduler, int tasks) {
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
        return new JUnit4TestAdapter(TestApplicationSchedulerImpl.class);
    }

}
