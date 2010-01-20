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

    @Before
    public void setup() {
        Properties emptyProps = new Properties();
        policy = new ImmediateRetryPolicy(emptyProps);

        task = EasyMock.createMock(ScheduledTask.class);
    }

    @After
    public void tearDown() {
        policy = null;
        task = null;
    }

    private void setupTask(Throwable result) {
        EasyMock.expect(task.getLastFailure()).andStubReturn(result);
    }

    private void replayMocks() {
        EasyMock.replay(task);
    }

    private void verifyMocks() {
        EasyMock.verify(task);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTask() {
        policy.getRetryAction(null);
    }

    @Test(expected=IllegalStateException.class)
    public void testNullResult() {
        setupTask(null);
        replayMocks();
        policy.getRetryAction(task);
    }

    @Test
    public void testRetryableTrueResult() {
        setupTask(new RetryableException(true));
        EasyMock.expect(task.getTryCount()).andStubReturn(1);
        replayMocks();

        // verify
        SchedulerRetryAction action = policy.getRetryAction(task);
        Assert.assertEquals(SchedulerRetryAction.RETRY_NOW, action);
        verifyMocks();
    }

    @Test
    public void testRetryableFalseResult() {
        setupTask(new RetryableException(false));
        EasyMock.expect(task.isRecurring()).andStubReturn(true);
        replayMocks();

        // verify
        SchedulerRetryAction action = policy.getRetryAction(task);
        Assert.assertEquals(SchedulerRetryAction.DROP, action);
        verifyMocks();
    }

    @Test
    public void testNotRetryableExceptionAndRecurringTask() {
        setupTask(new Exception());
        // record recurring task
        EasyMock.expect(task.isRecurring()).andStubReturn(true);
        replayMocks();

        // verify
        SchedulerRetryAction action = policy.getRetryAction(task);
        Assert.assertEquals(SchedulerRetryAction.DROP, action);
        verifyMocks();
    }

    @Test
    public void testNotRetryableExceptionAndNotRecurringTask() {
        setupTask(new Exception());
        // record recurring task
        EasyMock.expect(task.isRecurring()).andStubReturn(false);
        replayMocks();

        // verify
        SchedulerRetryAction action = policy.getRetryAction(task);
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
