
package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskReservation;

import com.sun.sgs.test.util.DummyTaskOwner;

import java.util.Collection;
import java.util.Properties;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;


/** Tests for the utility structures shared between schedulers. */
@RunWith(NameRunner.class)
public class TestInternalStructuresImpl {

    //
    private static final ApplicationScheduler scheduler =
        new DummyApplicationScheduler();

    // a basic KernelRunnable that does nothing
    private static final KernelRunnable testRunnable =
        new KernelRunnable() {
            public void run() throws Exception {}
        };

    // a basic task that shouldn't be run but can be used for tests
    private static final ScheduledTask testTask =
        new ScheduledTask(testRunnable, new DummyTaskOwner(),
                          Priority.getDefaultPriority(), 0);

    // a basic, recurring task that shouldn't be run but can be used for tests
    private static final ScheduledTask recurringTestTask =
        new ScheduledTask(testRunnable, new DummyTaskOwner(),
                          Priority.getDefaultPriority(), 0, 100);

    public TestInternalStructuresImpl() {

    }

    @Before public void setupSingleTest() {
        // setup for each individual test can happen here
    }

    @After public void teardownSingleTest() {
        // teardown for each individual test can happen here
    }

    /**
     * RecurringTaskHandleImpl tests.
     * 
     * Note that there are no start/cancel tests here because they are
     * already done in the ApplicationScheduler tests, where this class'
     * interface is exposed and it must be tested since we don't know
     * what implementation a given scheduler will choose to use.
     */

    @Test public void constructRecurringHandle() throws Exception {
        new RecurringTaskHandleImpl(scheduler, recurringTestTask);
    }

    @Test (expected=NullPointerException.class)
        public void constructRecurringHandleNullScheduler() throws Exception {
        new RecurringTaskHandleImpl(null, recurringTestTask);
    }

    @Test (expected=NullPointerException.class)
        public void constructRecurringHandleNullTask() throws Exception {
        new RecurringTaskHandleImpl(scheduler, null);
    }

    @Test (expected=IllegalArgumentException.class)
        public void constructRecurringHandleNonRecurringTask()
        throws Exception
    {
        new RecurringTaskHandleImpl(scheduler, testTask);
    }

    @Test public void scheduleNextRecurrence() throws Exception {
        RecurringTaskHandleImpl handle =
            new RecurringTaskHandleImpl(scheduler, recurringTestTask);
        handle.scheduleNextRecurrence();
    }

    @Test (expected=NullPointerException.class)
        public void setTimerTaskNull() throws Exception {
        RecurringTaskHandleImpl handle =
            new RecurringTaskHandleImpl(scheduler, recurringTestTask);
        handle.setTimerTask(null);
    }

    /**
     * ScheduledTask tests.
     */

    @Test public void constructScheduledTaskFromScheduledTask() {
        new ScheduledTask(recurringTestTask, 0);
    }

    @Test (expected=NullPointerException.class)
        public void constructScheduledTaskNullScheduledTask()
        throws Exception
    {
        new ScheduledTask(null, 0);
    }

    @Test (expected=NullPointerException.class)
        public void constructScheduledTaskNullTask() throws Exception {
        new ScheduledTask(null, new DummyTaskOwner(),
                          Priority.getDefaultPriority(), 0);
    }

    @Test (expected=NullPointerException.class)
        public void constructScheduledTaskNullOwner() throws Exception {
        new ScheduledTask(testRunnable, null,
                          Priority.getDefaultPriority(), 0);
    }

    @Test (expected=NullPointerException.class)
        public void constructScheduledTaskNullPriority() throws Exception {
        new ScheduledTask(testRunnable, new DummyTaskOwner(), null, 0);
    }

    @Test public void assertIsRecurring() {
        assertTrue(recurringTestTask.isRecurring());
    }

    @Test public void assertIsNotRecurring() {
        assertFalse(testTask.isRecurring());
    }

    @Test public void assertIsStillRecurring() {
        assertTrue((new ScheduledTask(recurringTestTask, 10)).isRecurring());
    }

    @Test public void setRecurringHandleNonRecurringTask() {
        RecurringTaskHandleImpl handle =
            new RecurringTaskHandleImpl(scheduler, recurringTestTask);
        assertFalse(testTask.setRecurringTaskHandle(handle));
    }

    @Test public void setRecurringHandleOnceOnly() {
        RecurringTaskHandleImpl handle =
            new RecurringTaskHandleImpl(scheduler, recurringTestTask);
        assertTrue(recurringTestTask.setRecurringTaskHandle(handle));
        assertFalse(recurringTestTask.setRecurringTaskHandle(handle));
    }

    @Test public void getRecurringHandle() {
        assertNotNull(recurringTestTask.getRecurringTaskHandle());
    }

    /**
     * SimpleTaskReservation tests.
     * 
     * Note that there are no use/cancel tests here because they are
     * already done in the ApplicationScheduler tests, where this class'
     * interface is exposed and it must be tested since we don't know
     * what implementation a given scheduler will choose to use.
     */

    @Test (expected=NullPointerException.class)
        public void constructSimpleTaskReservationNullScheduler()
        throws Exception {
        new SimpleTaskReservation(null, testTask);
    }

    @Test (expected=NullPointerException.class)
        public void constructSimpleTaskReservationNullTask()
        throws Exception {
        new SimpleTaskReservation(scheduler, null);
    }

    /**
     * TimedTaskHandler tests.
     */

    @Test (expected=NullPointerException.class)
        public void constructTimedTaskHandleNullConsumer() throws Exception {
        new TimedTaskHandler(null);
    }

    @Test public void timedTaskHandlerRejects() {
        assertFalse((new TimedTaskHandler(new TimedTaskConsumerImpl())).
                    runDelayed(testTask));
    }

    @Test public void timedTaskHandlerAccepts() {
        ScheduledTask task =
            new ScheduledTask(testRunnable, new DummyTaskOwner(),
                              Priority.getDefaultPriority(),
                              System.currentTimeMillis() +
                              TimedTaskHandler.FUTURE_THRESHOLD + 50);
        assertTrue((new TimedTaskHandler(new TimedTaskConsumerImpl())).
                   runDelayed(task));
    }

    /**
     * Utility classes.
     */
    
    private static class DummyApplicationScheduler
        implements ApplicationScheduler
    {
        public ScheduledTask getNextTask(boolean wait)
            throws InterruptedException {
            return null;
        }
        public int getNextTasks(Collection<ScheduledTask> tasks, int max) {
            return 0;
        }
        public TaskReservation reserveTask(ScheduledTask task) {
            return new SimpleTaskReservation(this, task);
        }
        public void addTask(ScheduledTask task) {}
        public RecurringTaskHandle addRecurringTask(ScheduledTask task) {
            return new RecurringTaskHandleImpl(this, task);
        }
        public void notifyCancelled(ScheduledTask task) {}
    }

    private static class TimedTaskConsumerImpl implements TimedTaskConsumer {
        public void timedTaskReady(ScheduledTask task) {

        }
    }

    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestInternalStructuresImpl.class);
    }

}
