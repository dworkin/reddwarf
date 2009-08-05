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

package com.sun.sgs.test.impl.profile;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.profile.TransactionListenerDetail;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Simple test to verify that in normal oparation a known
 * {@code TransactionListener} is reported correctly in the profiling
 * stream.
 * <p>
 * Note that this is really a place-holder to support new profiling
 * functionality. In the future there should be a more robust set of
 * tests for {@code TransactionListener}s and {@code TransactionParticipant}s.
 * This involves implementing both interfaces and using a custom
 * {@code Service} to include them in transactions.
 */
@RunWith(FilteredNameRunner.class)
public class TestReportedTxnMembers {

    final String LISTENER_NAME = "com.sun.sgs.impl.service.data.Context";
    final String TASK_NS =
        "com.sun.sgs.test.impl.profile.TestReportedTxnMembers";

    // the test node
    private SgsTestNode serverNode;
    // the transaction scheduler
    private TransactionScheduler txnScheduler;
    // the profile collector
    private ProfileCollector profileCollector;

    @Before
    public void setUp() throws Exception {
        // Start a partial stack.
        Properties p =
            SgsTestNode.getDefaultProperties("TestTxnMembers", null, null);
        p.setProperty(StandardProperties.NODE_TYPE, 
                      NodeType.coreServerNode.name());
        p.setProperty("com.sun.sgs.impl.kernel.profile.level", "MEDIUM");
        serverNode = new SgsTestNode("TextTxnMembers", null, p);
        ComponentRegistry registry = serverNode.getSystemRegistry();
        profileCollector = registry.getComponent(ProfileCollector.class);
        txnScheduler = registry.getComponent(TransactionScheduler.class);
    }

    @After
    public void tearDown() throws Exception {
        serverNode.shutdown(true);
    }

    @Test
    public void testDataServiceListenerIncluded() throws Exception {
        final AtomicReference<RuntimeException> throwableRef =
            new AtomicReference<RuntimeException>();
        final AtomicBoolean beforeShouldBeCalled = new AtomicBoolean(true);
        final Semaphore sem = new Semaphore(0);

        // listener that only looks at the transactions run from this test,
        // and verifies that the Context class was correctly reported
        SimpleTestListener listener = new SimpleTestListener(new Runnable() {
                public void run() {
                    ProfileReport r = SimpleTestListener.report;
                    // only proceed for transactions that were run from
                    // this test class
                    if ((! r.wasTaskTransactional()) ||
                        (! r.getTask().getBaseTaskType().startsWith(TASK_NS)))
                    {
                        return;
                    }
                    boolean foundDataListener = false;
                    for (TransactionListenerDetail detail :
                             SimpleTestListener.report.getListenerDetails())
                    {
                        // look for the known data Context listener
                        if (detail.getListenerName().equals(LISTENER_NAME)) {
                            foundDataListener = true;
                            // check that before completion was reported
                            // correctly for successful and failed tasks
                            if (beforeShouldBeCalled.get() !=
                                detail.calledBeforeCompletion())
                            {
                                throwableRef.
                                    set(new RuntimeException("wrong before " +
                                                             "status"));
                            }
                            break;
                        }
                    }
                    if (! foundDataListener) {
                        throwableRef.set(new RuntimeException("data listener " +
                                                              "not reported"));
                    }
                    sem.release();
                }
            });
        profileCollector.addListener(listener, true);

        // run a successful task that uses the DataService
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    AppContext.getDataManager().
                        createReference(new DummyObject());
                }
            }, serverNode.getProxy().getCurrentOwner());
        if (! sem.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Listener never reported");
        }
        if (throwableRef.get() != null) {
            throw new RuntimeException("listener failed", throwableRef.get());
        }

        // re-set and run another task that uses the DataService but then
        // fails before commit
        throwableRef.set(null);
        beforeShouldBeCalled.set(false);
        try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                    public void run() {
                        AppContext.getDataManager().
                            createReference(new DummyObject());
                        throw new ExpectedException();
                    }
                }, serverNode.getProxy().getCurrentOwner());
        } catch (ExpectedException ee) {}
        if (! sem.tryAcquire(500, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Listener never reported");
        }
        if (throwableRef.get() != null) {
            throw new RuntimeException("listener failed", throwableRef.get());
        }
    }

    /** A dummy class used so the DataService can be invoked. */
    private static final class DummyObject
        implements ManagedObject, Serializable {}

    /** A custom exception that the tests can catch deliberately. */
    private static final class ExpectedException extends RuntimeException {
        ExpectedException() {
            super("expected failure");
        }
    }

}
