/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.impl.util.KernelCallable;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the KernelCallable class
 */
@RunWith(FilteredNameRunner.class)
public class TestKernelCallable {

    private static SgsTestNode serverNode;
    private static TransactionScheduler txnScheduler;
    private static Identity taskOwner;
    private static DataService dataService;

    @Before
    public void setUp() throws Exception {
	serverNode = new SgsTestNode("TestKernelCallable", null, null);
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
        dataService = serverNode.getDataService();
    }

    @After
    public void tearDown() throws Exception {
        serverNode.shutdown(true);
    }

    @Test
    public void testCall() throws Exception {
        Integer result = KernelCallable.call(
                new  KernelCallable<Integer> ("TestKernelCallable") {
                    public Integer call() {
                        return new Integer(1);
                    }
                }, txnScheduler, taskOwner);

        Assert.assertEquals(new Integer(1), result);
    }

    @Test
    public void testCallThrowsRetryableException() throws Exception {
        Integer result = KernelCallable.call(
                new  KernelCallable<Integer> ("TestKernelCallable") {
                    boolean tried = false;
                    public Integer call() throws Exception {
                        if (!tried) {
                            tried = true;
                            throw new RetryableException();
                        }
                        return new Integer(1);
                    }
                }, txnScheduler, taskOwner);

        Assert.assertEquals(new Integer(1), result);
    }

    @Test(expected=RuntimeException.class)
    public void testCallThrowsNonRetryableException() throws Exception {
        KernelCallable.call(
                new KernelCallable<Integer>("TestKernelCallable") {
                    public Integer call() throws Exception {
                        throw new RuntimeException();
                    }
                }, txnScheduler, taskOwner);
    }

    @Test
    public void testCallMoreThanOnce() throws Exception {
        KernelCallable<Integer> callable =
                new KernelCallable<Integer>("TestKernelCallable") {
                    public Integer call() {
                        return new Integer(1);
                    }
                };

        Integer result = KernelCallable.call(
                callable, txnScheduler, taskOwner);
        Assert.assertEquals(new Integer(1), result);

        try {
            KernelCallable.call(
                    callable, txnScheduler, taskOwner);
            Assert.fail("Should have thrown IllegalStateException");
        } catch (IllegalStateException ise) {
            Assert.assertEquals("already completed", ise.getMessage());
        }
    }

    @Test
    public void testCallTimesOutOnce() throws Exception {
        Integer result = KernelCallable.call(
                new  KernelCallable<Integer> ("TestKernelCallable") {
                    boolean tried = false;
                    public Integer call() throws Exception {
                        ManagedInteger i = new ManagedInteger(1);
                        dataService.setBinding("Name", i);
                        if (!tried) {
                            tried = true;
                            Thread.sleep(150);
                        }
                        return i.getValue();
                    }
                }, txnScheduler, taskOwner);

        Assert.assertEquals(new Integer(1), result);
    }

    private static class RetryableException extends Exception
            implements ExceptionRetryStatus {
        
        @Override
        public boolean shouldRetry() {
            return true;
        }
    }

    private static class ManagedInteger implements ManagedObject, Serializable {
        int i = 0;
        public ManagedInteger(int i) {
            this.i = i;
        }

        public Integer getValue() {
            return i;
        }
    }
}
