/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.impl.kernel.schedule.ImmediateRetryPolicy;
import com.sun.sgs.kernel.schedule.ScheduledTask;
import com.sun.sgs.kernel.schedule.SchedulerQueue;
import com.sun.sgs.kernel.schedule.SchedulerRetryAction;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.Properties;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@code ImmediateRetryPolicy} in isolation.
 */
@RunWith(FilteredNameRunner.class)
public class TestImmediateRetryPolicy {

    private ImmediateRetryPolicy policy;
    private ScheduledTask task;
    private Throwable result;
    private SchedulerQueue backingQueue;
    private SchedulerQueue throttleQueue;

    @Before
    public void setup() {
        Properties emptyProps = new Properties();
        policy = new ImmediateRetryPolicy(emptyProps);

        task = EasyMock.createMock(ScheduledTask.class);
        backingQueue = EasyMock.createMock(SchedulerQueue.class);
        throttleQueue = EasyMock.createMock(SchedulerQueue.class);
    }

    @After
    public void tearDown() {
        policy = null;
    }

    private void replayMocks() {
        EasyMock.replay(task);
        EasyMock.replay(backingQueue);
        EasyMock.replay(throttleQueue);
    }

    private void verifyMocks() {
        EasyMock.verify(task);
        EasyMock.verify(backingQueue);
        EasyMock.verify(throttleQueue);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullResult() {
        result = null;
        policy.getRetryAction(task, result, backingQueue, throttleQueue);
    }

    @Test
    public void testInterruptedResultQueueOk() {
        // expect task to be added to queue
        backingQueue.addTask(task);
        replayMocks();

        // verify
        result = new InterruptedException("task interrupted");
        SchedulerRetryAction action = policy.getRetryAction(
                task, result, backingQueue, throttleQueue);
        Assert.assertEquals(SchedulerRetryAction.HANDOFF, action);
        verifyMocks();
    }

    @Test
    public void testInterruptedResultQueueFull() {
        // expect task to be added to full queue
        backingQueue.addTask(task);
        EasyMock.expectLastCall().andThrow(
                new TaskRejectedException("queue full"));
        replayMocks();

        // verify
        result = new InterruptedException("task interrupted");
        SchedulerRetryAction action = policy.getRetryAction(
                task, result, backingQueue, throttleQueue);
        Assert.assertEquals(SchedulerRetryAction.DROP, action);
        verifyMocks();
    }

    @Test
    public void testRetryableTrueResult() {
        // no expected behavior to record
        replayMocks();

        // verify
        result = new RetryableException(true);
        SchedulerRetryAction action = policy.getRetryAction(
                task, result, backingQueue, throttleQueue);
        Assert.assertEquals(SchedulerRetryAction.RETRY, action);
        verifyMocks();
    }

    @Test
    public void testRetryableFalseResult() {
        // give task needed behavior
        EasyMock.expect(task.isRecurring()).andReturn(true);
        replayMocks();

        // verify
        result = new RetryableException(false);
        SchedulerRetryAction action = policy.getRetryAction(
                task, result, backingQueue, throttleQueue);
        Assert.assertEquals(SchedulerRetryAction.DROP, action);
        verifyMocks();
    }

    @Test
    public void testNotRetryableExceptionAndRecurringTask() {
        // record recurring task
        EasyMock.expect(task.isRecurring()).andReturn(true);
        replayMocks();

        // verify
        result = new Exception();
        SchedulerRetryAction action = policy.getRetryAction(
                task, result, backingQueue, throttleQueue);
        Assert.assertEquals(SchedulerRetryAction.DROP, action);
        verifyMocks();
    }

    @Test
    public void testNotRetryableExceptionAndNotRecurringTask() {
        // record recurring task
        EasyMock.expect(task.isRecurring()).andReturn(false);
        replayMocks();

        // verify
        result = new Exception();
        SchedulerRetryAction action = policy.getRetryAction(
                task, result, backingQueue, throttleQueue);
        Assert.assertEquals(SchedulerRetryAction.DROP, action);
        verifyMocks();
    }

    private static class RetryableException extends Exception
            implements ExceptionRetryStatus {
        
        private final boolean retryable;

        public RetryableException(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean shouldRetry() {
            return retryable;
        }

    }

}
