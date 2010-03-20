/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
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

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for the {@code NowOrLaterRetryPolicy} class when it is plugged in to
 * the {@code TransactionScheduler}.
 */
@RunWith(FilteredNameRunner.class)
@IntegrationTest
public class TestNowOrLaterRetryPolicyIntegration {

    private SgsTestNode serverNode = null;
    private TransactionScheduler txnScheduler;
    private Identity taskOwner;

    /** Per-test initialization */
    @Before public void startup() throws Exception {
        Properties properties =
            SgsTestNode.getDefaultProperties("TestNowOrLaterRetryPolicyIntegration",
					     null, null);
        properties.setProperty(StandardProperties.NODE_TYPE,
                               NodeType.coreServerNode.name());
        properties.setProperty("com.sun.sgs.impl.kernel.scheduler.retry",
                               NowOrLaterRetryPolicy.class.getName());
        properties.setProperty(NowOrLaterRetryPolicy.RETRY_BACKOFF_THRESHOLD_PROPERTY,
                               "5");
        serverNode = new SgsTestNode("TestNowOrLaterRetryPolicyIntegration",
                                     null, properties);
        txnScheduler = (TransactionScheduler) serverNode.
                getSystemRegistry().getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
    }

    /** Per-test shutdown */
    @After public void shutdown() throws Exception {
        if (serverNode != null)
            serverNode.shutdown(true);
    }


    @Test
    public void testRetryableTrueResult() throws Exception {
        CountTestRunner c = new CountTestRunner(2, true);
        txnScheduler.runTask(c, taskOwner);
        Assert.assertEquals(0, c.getCount());
    }

    @Test
    public void testRetryableFalseResult() throws Exception {
        CountTestRunner c = new CountTestRunner(2, false);
        try {
            txnScheduler.runTask(c, taskOwner);
            Assert.fail("Expected RetryableException");
        } catch (RetryableException r) {
            Assert.assertFalse(r.shouldRetry());
        }
        Assert.assertEquals(1, c.getCount());
    }

    @Test
    public void testRetryableAboveBackoffThreshold() throws Exception {
        LongTransactionRunner l = new LongTransactionRunner(150);
        txnScheduler.runTask(l, taskOwner);
        Assert.assertTrue(l.isFinished());
        Assert.assertTrue(l.getRunCount() > 5);
    }

    @Test
    public void testRetryableAboveDoubleBackoffThreshold() throws Exception {
        LongTransactionRunner l = new LongTransactionRunner(250);
        txnScheduler.runTask(l, taskOwner);
        Assert.assertTrue(l.isFinished());
        Assert.assertTrue(l.getRunCount() > 6);
    }

    private class CountTestRunner implements KernelRunnable {
        private int count;
        private boolean retryable;
        CountTestRunner(int initialCount, boolean retryable) {
            this.count = initialCount;
            this.retryable = retryable;
        }
        public String getBaseTaskType() {
            return CountTestRunner.class.getName();
        }
        public void run() throws Exception {
            count--;
            if (count > 0) {
                throw new RetryableException(retryable);
            }
        }
        public int getCount() {
            return count;
        }
    }

    private class LongTransactionRunner implements KernelRunnable {
        private boolean finished = false;
        private int runCount = 0;
        private long sleep = 0;
        LongTransactionRunner(long sleep) {
            this.sleep = sleep;
        }
        public String getBaseTaskType() {
            return LongTransactionRunner.class.getName();
        }
        public void run() throws Exception {
            runCount++;
            if (runCount > 10) {
                throw new RuntimeException("ran too many times");
            }
            Thread.sleep(sleep);
            serverNode.getProxy().getCurrentTransaction();
            finished = true;
        }
        public boolean isFinished() {
            return finished;
        }
        public int getRunCount() {
            return runCount;
        }
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

