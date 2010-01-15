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

package com.sun.sgs.test.impl.profile;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.profile.TaskProfileCounter;
import com.sun.sgs.profile.TaskProfileOperation;
import com.sun.sgs.profile.TaskProfileSample;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * Tests for both the profile consumer and the basic tests for the
 * objects created with the profile consumer factory tests.
 * <p>
 * Tests to be run with types TASK and TASK_AND_AGGREGATE are found in
 * TestProfileDataTaskImpl.java.   Tests to be run with types AGGREGATE
 * and TASK_AND_AGGREGATE are found in TestProfileDataAggregateImpl.java.  Any
 * other specialized profile data tests are collected here.
 */
@RunWith(FilteredNameRunner.class)
public class TestProfileConsumerImpl {
    private final static String APP_NAME = "TestProfileConsumer";
    
    /** A test server node */
    private SgsTestNode serverNode;  
    /** The profile collector associated with the test server node */
    private ProfileCollector profileCollector;
    /** The system registry */
    private ComponentRegistry systemRegistry;
    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;
    
    /** Any additional nodes, only used for selected tests */
    private SgsTestNode additionalNodes[];
    /** Test setup. */
    @Before
    public void setUp() throws Exception {
        // Start a partial stack.  We actually don't need any services for
        // these tests, but we cannot start up additional nodes if we don't
        // have at least the core services started.
        Properties p = SgsTestNode.getDefaultProperties(APP_NAME, null, null);
        p.setProperty(StandardProperties.NODE_TYPE, 
                      NodeType.coreServerNode.name());
        setUp(p);
    }

    protected void setUp(Properties props) throws Exception {
        serverNode = new SgsTestNode(APP_NAME, null, props);
        profileCollector = getCollector(serverNode);
        systemRegistry = serverNode.getSystemRegistry();
        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
    }
  
    /** Shut down the nodes. */
    @After
    public void tearDown() throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                if (node != null) {
                    node.shutdown(false);
                }
            }
            additionalNodes = null;
        }
        serverNode.shutdown(true);
    }
    
    /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param props properties for node creation, or {@code null} if default
     *     properties should be used
     * @parm num the number of nodes to add
     */
    private void addNodes(Properties props, int num) throws Exception {
        // Create the other nodes
        additionalNodes = new SgsTestNode[num];

        for (int i = 0; i < num; i++) {
            SgsTestNode node = new SgsTestNode(serverNode, null, props); 
            additionalNodes[i] = node;
        }
    }
    
    /** Returns the profile collector for a given node */
    private ProfileCollector getCollector(SgsTestNode node) throws Exception {
        return node.getSystemRegistry().getComponent(ProfileCollector.class);
    }
    
    /* -- Consumer tests -- */
    @Test
    public void testConsumerName() throws Exception {
        final String name = "consumer1";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons = collector.getConsumer(name);
        assertEquals(name, cons.getName());
    }
    
    @Test
    public void testConsumerSetLevel() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        assertEquals(profileCollector.getDefaultProfileLevel(), 
                     cons1.getProfileLevel());
        
        cons1.setProfileLevel(ProfileLevel.MIN);
        assertEquals(ProfileLevel.MIN, cons1.getProfileLevel());
        cons1.setProfileLevel(ProfileLevel.MEDIUM);
        assertEquals(ProfileLevel.MEDIUM, cons1.getProfileLevel());
        cons1.setProfileLevel(ProfileLevel.MAX);
        assertEquals(ProfileLevel.MAX, cons1.getProfileLevel());
    }
    
    @Test
    public void testConsumerSetCollectorLevel() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileLevel cons1Level = cons1.getProfileLevel();
        assertEquals(profileCollector.getDefaultProfileLevel(), cons1Level);

        // Change default level from what the kernel set, make sure it
        // affects later consumers.
        profileCollector.setDefaultProfileLevel(ProfileLevel.MIN);
        ProfileConsumer cons2 = collector.getConsumer("c2");
        assertEquals(profileCollector.getDefaultProfileLevel(), 
                     cons2.getProfileLevel());
        // and make sure other consumers aren't affected
        assertEquals(cons1Level, cons1.getProfileLevel());
    }
    
    /* -- Counter tests -- */
    @Test(expected=NullPointerException.class)
    public void testCounterTaskBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter c1 = 
            cons1.createCounter(null, ProfileDataType.TASK, ProfileLevel.MIN);
    }
    @Test(expected=NullPointerException.class)
    public void testCounterAggregateBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter c1 = 
            cons1.createCounter(null, 
                                ProfileDataType.AGGREGATE, ProfileLevel.MIN);
    }
    @Test(expected=NullPointerException.class)
    public void testCounterTaskAggregateBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter c1 = 
            cons1.createCounter(null, 
                                ProfileDataType.TASK_AND_AGGREGATE, 
                                ProfileLevel.MIN);
    }
    
    @Test
    public void testCounterName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileConsumer cons2 = collector.getConsumer("c2");
        String name = "taskcounter";
        {
            ProfileCounter counter1 = 
                    cons1.createCounter(name, 
                                        ProfileDataType.TASK, ProfileLevel.MAX);
            ProfileCounter counter2 =
                    cons2.createCounter(name, 
                                        ProfileDataType.TASK, ProfileLevel.MAX);
            assertFalse(counter1.getName().equals(counter2.getName()));
            assertTrue(counter1.getName().contains(name));
            assertTrue(counter2.getName().contains(name));
        }
        
        name = "aggregateCounter";
        {
            ProfileCounter counter1 = 
                    cons1.createCounter(name, ProfileDataType.AGGREGATE, 
                                        ProfileLevel.MAX);
            ProfileCounter counter2 = 
                    cons2.createCounter(name, ProfileDataType.AGGREGATE, 
                                        ProfileLevel.MAX);
            assertFalse(counter1.getName().equals(counter2.getName()));
            assertTrue(counter1.getName().contains(name));
            assertTrue(counter2.getName().contains(name));
        }
        
        name = "bothCounter";
        {
            ProfileCounter counter1 = 
                cons1.createCounter(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                    ProfileLevel.MAX);
            ProfileCounter counter2 = 
                cons2.createCounter(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                    ProfileLevel.MAX);
            assertFalse(counter1.getName().equals(counter2.getName()));
            assertTrue(counter1.getName().contains(name));
            assertTrue(counter2.getName().contains(name));
        }
    }
    
    @Test
    public void testCounterTwice() throws Exception {
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter counter1 = 
                cons1.createCounter(name, 
                                    ProfileDataType.TASK, ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileCounter counter2 =
                cons1.createCounter(name, 
                                    ProfileDataType.TASK, ProfileLevel.MAX);
        assertSame(counter1, counter2);
        
        // Try creating with same name and different parameters
        try {
            ProfileCounter op3 =
                cons1.createCounter(name, 
                                    ProfileDataType.AGGREGATE, 
                                    ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileCounter op3 =
                cons1.createCounter(name, 
                                    ProfileDataType.TASK_AND_AGGREGATE, 
                                    ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileCounter op3 =
                cons1.createCounter(name, 
                                    ProfileDataType.TASK, 
                                    ProfileLevel.MIN);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileCounter op3 =
                cons1.createCounter(name, 
                                    ProfileDataType.TASK, 
                                    ProfileLevel.MEDIUM);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        
        // Try creating with a different name
        ProfileCounter counter4 =
                cons1.createCounter("somethingelse", 
                                    ProfileDataType.TASK, ProfileLevel.MAX);
        assertNotSame(counter1, counter4);
    }
    
    @Test
    public void testCounterType() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter counter = 
                cons1.createCounter("counter", 
                                    ProfileDataType.TASK, ProfileLevel.MIN);
        assertTrue(counter instanceof TaskProfileCounter);
        assertFalse(counter instanceof AggregateProfileCounter);
        
        ProfileCounter counter1 = 
                cons1.createCounter("counter1", 
                                    ProfileDataType.AGGREGATE, 
                                    ProfileLevel.MIN);
        assertFalse(counter1 instanceof TaskProfileCounter);
        assertTrue(counter1 instanceof AggregateProfileCounter);
        
        ProfileCounter counter2 = 
                cons1.createCounter("counter2", 
                                    ProfileDataType.TASK_AND_AGGREGATE, 
                                    ProfileLevel.MIN);
        assertTrue(counter2 instanceof TaskProfileCounter);
        assertTrue(counter2 instanceof AggregateProfileCounter);
        
    }

    @Test(expected=IllegalStateException.class)
    public void testTaskCounterIncrementNoTransaction() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileCounter counter = 
                cons1.createCounter("my counter", 
                                    ProfileDataType.TASK, ProfileLevel.MIN);
        
        counter.incrementCount();
    }
    
    @Test(expected=IllegalStateException.class)
    public void testTaskCounterIncrementValueNoTransaction() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileCounter counter = 
                cons1.createCounter("my counter", 
                                    ProfileDataType.TASK, ProfileLevel.MIN);
        
        counter.incrementCount(55);
    }
    
    @Test
    public void testAggregateProfileCounterNotInTaskReport() throws Exception {
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be noted at all profiling levels
        final AggregateProfileCounter counter = 
                (AggregateProfileCounter) 
                    cons1.createCounter(name, 
                                        ProfileDataType.AGGREGATE, 
                                        ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final Identity positiveOwner = new DummyIdentity("never-used");
        final Identity negativeOwner = new DummyIdentity("neg-owner");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, negativeOwner, positiveOwner, 
                                      errorExchanger, 1));
        profileCollector.addListener(test, true);

        // We don't expect to see the counter updated in the task report
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount();
                }
            }, negativeOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            // Rethrow with the original error as the cause so we see
            // both stack traces.
            throw new AssertionError(error);
        }
        
        // But we do expect to see the counter updated globally!
        assertEquals(1L, counter.getCount());
    }
    
    @Test
    public void testTaskAggregateProfileCounter() throws Exception {
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be noted at all profiling levels
        final AggregateProfileCounter counter = 
                (AggregateProfileCounter) 
                    cons1.createCounter(name, 
                                        ProfileDataType.TASK_AND_AGGREGATE, 
                                        ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // Set up a couple of test listeners, each listening for a different
        // task owner
        final Identity positiveOwner = new DummyIdentity("hello");
        final Identity negativeOwner = new DummyIdentity("neg-hello");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(counter.getName(), 
                                      negativeOwner, positiveOwner, 
                                      errorExchanger, 1));
        profileCollector.addListener(test, true);
        
        // We expect to see the counter updated in the task report
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount();
                }
            }, positiveOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            // Rethrow with the original error as the cause so we see
            // both stack traces.
            throw new AssertionError(error);
        }
        
        // We expect to see the counter updated in the task report,
        // and it should be independent of the last report.
        // Note that we assume the profile listener for the last task
        // has already been called at this point.  This is safe, because
        // we're using the ErrorExchanger to ensure the profile report for 
        // the above task has been seen.
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount();
                }
            }, positiveOwner);

        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            // Rethrow with the original error as the cause so we see
            // both stack traces.
            throw new AssertionError(error);
        }
        
        // And we expect to see the counter aggregated
        assertEquals(2, counter.getCount());
    }
    
    
    /* -- Operation tests -- */
    @Test(expected=NullPointerException.class)
    public void testOperationTaskBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation o1 = 
            cons1.createOperation(null, ProfileDataType.TASK, ProfileLevel.MIN);
    }
    @Test(expected=NullPointerException.class)
    public void testOperationAggregateBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation o1 = 
            cons1.createOperation(null, 
                                  ProfileDataType.AGGREGATE, ProfileLevel.MIN);
    }
    @Test(expected=NullPointerException.class)
    public void testOperationTaskAggregateBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation o1 = 
            cons1.createOperation(null, 
                                  ProfileDataType.TASK_AND_AGGREGATE, 
                                  ProfileLevel.MIN);
    }
    
    @Test
    public void testOperationName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileConsumer cons2 = collector.getConsumer("c2");
        String name = "myOperation";
        {
            ProfileOperation op1 = 
                cons1.createOperation(name, 
                                      ProfileDataType.TASK, ProfileLevel.MAX);
            ProfileOperation op2 = 
                cons2.createOperation(name, 
                                      ProfileDataType.TASK, ProfileLevel.MAX);
            assertFalse(op1.getName().equals(op2.getName()));
            assertTrue(op1.getName().contains(name));
            assertTrue(op2.getName().contains(name));
        }
        
        name = "aggOp";
        {
            ProfileOperation op1 = 
                cons1.createOperation(name, ProfileDataType.AGGREGATE, 
                                      ProfileLevel.MAX);
            ProfileOperation op2 = 
                cons2.createOperation(name, ProfileDataType.AGGREGATE, 
                                      ProfileLevel.MAX);
            assertFalse(op1.getName().equals(op2.getName()));
            assertTrue(op1.getName().contains(name));
            assertTrue(op2.getName().contains(name));
        }
        
        name = "bothOp";
        {
            ProfileOperation op1 = 
                cons1.createOperation(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                      ProfileLevel.MAX);
            ProfileOperation op2 = 
                cons2.createOperation(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                      ProfileLevel.MAX);
            assertFalse(op1.getName().equals(op2.getName()));
            assertTrue(op1.getName().contains(name));
            assertTrue(op2.getName().contains(name));
        }
    }
    
    @Test
    public void testOperationTwice() throws Exception {
        final String name = "myOperation";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation op1 = 
                cons1.createOperation(name, 
                                      ProfileDataType.TASK, ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileOperation op2 =
                cons1.createOperation(name, 
                                      ProfileDataType.TASK, ProfileLevel.MAX);
        assertSame(op1, op2);
        
        // Try creating with same name and different parameters
        try {
            ProfileOperation op3 =
                cons1.createOperation(name, 
                                      ProfileDataType.AGGREGATE, 
                                      ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileOperation op3 =
                cons1.createOperation(name, 
                                      ProfileDataType.TASK_AND_AGGREGATE, 
                                      ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileOperation op3 =
                cons1.createOperation(name, ProfileDataType.TASK, 
                                      ProfileLevel.MIN);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileOperation op3 =
                cons1.createOperation(name, ProfileDataType.TASK, 
                                      ProfileLevel.MEDIUM);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        
        // Try creating with a different name
        ProfileOperation op4 =
                cons1.createOperation("somethingelse", 
                                      ProfileDataType.TASK, ProfileLevel.MAX);
        assertNotSame(op1, op4);
    }
    

    @Test
    public void testOperationType() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation op1 = 
            cons1.createOperation("op1", ProfileDataType.TASK, 
                                  ProfileLevel.MAX);
        assertTrue(op1 instanceof TaskProfileOperation);
        assertFalse(op1 instanceof AggregateProfileOperation);
 
        ProfileOperation op2 = 
            cons1.createOperation("op2", ProfileDataType.AGGREGATE, 
                                  ProfileLevel.MAX);
        assertFalse(op2 instanceof TaskProfileOperation);
        assertTrue(op2 instanceof AggregateProfileOperation);
        
        ProfileOperation op3 = 
            cons1.createOperation("op3", ProfileDataType.TASK_AND_AGGREGATE, 
                                  ProfileLevel.MAX);
        assertTrue(op3 instanceof TaskProfileOperation);
        assertTrue(op3 instanceof AggregateProfileOperation);
    }
        
    @Test
    public void testTaskAggregateOperationUnique() throws Exception {
        final String opName = "something";
        final String op1Name = "else";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileOperation op =
                cons1.createOperation(opName, 
                                      ProfileDataType.TASK_AND_AGGREGATE, 
                                      ProfileLevel.MIN);
        final ProfileOperation op1 =
                cons1.createOperation(op1Name, 
                                      ProfileDataType.TASK_AND_AGGREGATE, 
                                      ProfileLevel.MIN);
        final AggregateProfileOperation opAgg = (AggregateProfileOperation) op;
        final AggregateProfileOperation op1Agg = 
                (AggregateProfileOperation) op1;
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final Identity myOwner = new DummyIdentity("me");
        SimpleTestListener test = new SimpleTestListener(
            new Runnable() {
                public void run() {
                    AssertionError error = null;
                    ProfileReport report = SimpleTestListener.report;
                    if (report.getTaskOwner().equals(myOwner)) {
                        try {
                            List<String> ops =
                                report.getReportedOperations();
                            System.err.println("+++");
                            for (String name : ops) {
                                assertTrue(name.contains(opName) 
                                        || name.contains(op1Name));
                                
                                System.err.println("+ " + name);
                            }
                            System.err.println("+++");
                            
                            // Our aggregate counter knows that it was updated
                            assertEquals(4, opAgg.getCount());
                            assertEquals(2, op1Agg.getCount());
                            
                            
                            
                        } catch (AssertionError e) {
                            error = e;
                        }
                    }

                    // Signal that we're done, and return the exception
                    try { 
                        errorExchanger.exchange(error);
                    } catch (InterruptedException ignored) {
                        // do nothing
                    }
                }
        });
        profileCollector.addListener(test, true);

        op.report();
        op1.report();
        assertEquals(1, opAgg.getCount());
        assertEquals(1, op1Agg.getCount());
        op.report();
        assertEquals(2, opAgg.getCount());
        
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We expect to see the operations in the profile report
                    op.report();
                    op1.report();
                    op.report();
                }
            }, myOwner);
            
        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
        assertEquals(4, opAgg.getCount());
        assertEquals(2, op1Agg.getCount());
    }
    
    /* -- Sample tests -- */
    @Test(expected=NullPointerException.class)
    public void testSampleTaskBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
            cons1.createSample(null, ProfileDataType.TASK, ProfileLevel.MIN);
    }
    @Test(expected=NullPointerException.class)
    public void testSampleAggregateBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
            cons1.createSample(null, 
                               ProfileDataType.AGGREGATE, ProfileLevel.MIN);
    }
    @Test(expected=NullPointerException.class)
    public void testSampleTaskAggregateBadName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
            cons1.createSample(null, 
                               ProfileDataType.TASK_AND_AGGREGATE, 
                               ProfileLevel.MIN);
    }
    @Test(expected=IllegalArgumentException.class)
    public void testSampleTaskAggregateNegCapacity() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        AggregateProfileSample s1 = (AggregateProfileSample)
            cons1.createSample("foo", 
                               ProfileDataType.TASK_AND_AGGREGATE,
                               ProfileLevel.MIN);
        s1.setCapacity(-1);
    }
    
    @Test
    public void testSampleName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileConsumer cons2 = collector.getConsumer("c2");
        String name = "mySample";
        {
            ProfileSample samp1 = 
                cons1.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MAX);
            ProfileSample samp2 = 
                cons2.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MAX);
            assertFalse(samp1.getName().equals(samp2.getName()));
            assertTrue(samp1.getName().contains(name));
            assertTrue(samp2.getName().contains(name));
        }
        
        name = "aggSample";
        {
            ProfileSample samp1 = 
                cons1.createSample(name, ProfileDataType.AGGREGATE, 
                                   ProfileLevel.MAX);
            ProfileSample samp2 = 
                cons2.createSample(name, ProfileDataType.AGGREGATE, 
                                   ProfileLevel.MAX);
            assertFalse(samp1.getName().equals(samp2.getName()));
            assertTrue(samp1.getName().contains(name));
            assertTrue(samp2.getName().contains(name));
        }
        
        name = "bothSample";
        {
            ProfileSample samp1 = 
                cons1.createSample(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                   ProfileLevel.MAX);
            ProfileSample samp2 = 
                cons2.createSample(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                   ProfileLevel.MAX);
            assertFalse(samp1.getName().equals(samp2.getName()));
            assertTrue(samp1.getName().contains(name));
            assertTrue(samp2.getName().contains(name));
        }
    }
    
    @Test
    public void testSampleTwice() throws Exception {
        final String name = "mySamples";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
                cons1.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileSample s2 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MAX);
        assertSame(s1, s2);
        
        // Try creating with same name and different parameters
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.AGGREGATE, 
                                   ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        } 
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK_AND_AGGREGATE, 
                                   ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MIN);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MEDIUM);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   ProfileLevel.MAX);
            assertSame(s1, s3);   
        }
        
        final String aggName = "aggregateSample";
        {
            ProfileSample s3 =
                 cons1.createSample(aggName, ProfileDataType.AGGREGATE,
                                    ProfileLevel.MAX);
            
            AggregateProfileSample s4 = (AggregateProfileSample)
                 cons1.createSample(aggName, ProfileDataType.AGGREGATE,
                                    ProfileLevel.MAX);
            assertSame(s3, s4);
        }
        
        final String taskAggName = "task aggregate sample";
        {
            ProfileSample s3 =
                cons1.createSample(taskAggName, 
                                   ProfileDataType.TASK_AND_AGGREGATE, 
                                   ProfileLevel.MAX);
            ProfileSample s4 =
                 cons1.createSample(taskAggName, 
                                    ProfileDataType.TASK_AND_AGGREGATE,
                                    ProfileLevel.MAX);
            assertSame(s3, s4);
        }
        
        // Try creating with a different name
        ProfileSample s5 =
            cons1.createSample("somethingelse", ProfileDataType.TASK, 
                               ProfileLevel.MAX);
        assertNotSame(s1, s5);
    }
    

    @Test
    public void testSampleType() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
            cons1.createSample("samples1", ProfileDataType.TASK, 
                               ProfileLevel.MAX);
        assertTrue(s1 instanceof TaskProfileSample);
        assertFalse(s1 instanceof AggregateProfileSample);
 
        ProfileSample s2 = 
            cons1.createSample("samples2", ProfileDataType.AGGREGATE, 
                               ProfileLevel.MAX);
        assertFalse(s2 instanceof TaskProfileSample);
        assertTrue(s2 instanceof AggregateProfileSample);
        
        ProfileSample s3 = 
            cons1.createSample("samples3", ProfileDataType.TASK_AND_AGGREGATE, 
                               ProfileLevel.MAX);
        assertTrue(s3 instanceof TaskProfileSample);
        assertTrue(s3 instanceof AggregateProfileSample);
    }
    
    @Test
    public void testTaskAggregateSampleZeroCapacity() throws Exception {
        final List<Long> expected = new LinkedList<Long>();
        expected.add(Long.valueOf(5));
        expected.add(Long.valueOf(-1));
        expected.add(Long.valueOf(2));
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final AggregateProfileSample samp = (AggregateProfileSample)
                cons1.createSample("sample1", 
                                   ProfileDataType.TASK_AND_AGGREGATE, 
                                   ProfileLevel.MIN);
        final String sampleName = samp.getName();
        // Ensure that a zero capacity aggregate sample sends the sample
        // values to the task listener, but does not accumulate any
        // samples in the global aggregation.
        samp.setCapacity(0);
        
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final Identity myOwner = new DummyIdentity("me");
        SimpleTestListener test = new SimpleTestListener(
            new Runnable() {
                public void run() {
                    AssertionError error = null;
                    ProfileReport report = SimpleTestListener.report;
                    if (report.getTaskOwner().equals(myOwner)) {
                        try {
                            List<Long> samples =
                                report.getUpdatedTaskSamples().get(sampleName);
                            assertEquals(expected, samples);    
                        } catch (AssertionError e) {
                            error = e;
                        }
                    }

                    // Signal that we're done, and return the exception
                    try { 
                        errorExchanger.exchange(error);
                    } catch (InterruptedException ignored) {
                        // do nothing
                    }
                }
        });
        profileCollector.addListener(test, true);
        
        assertEquals(0, samp.getNumSamples());
        assertNotNull(samp.getSamples());
        for (Long sample : samp.getSamples()) {
            fail("didn't expect to find a sample " + sample);
        }
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    for (long value : expected) {
                        samp.addSample(value);
                    }
                }
            }, myOwner);
            
        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
 
        // No global samples, yet statistics are maintained
        assertEquals(0, samp.getNumSamples());
        assertNotNull(samp.getSamples());
        for (Long sample : samp.getSamples()) {
            fail("didn't expect to find a sample " + sample);
        }
        assertEquals(5, samp.getMaxSample());
        assertEquals(-1, samp.getMinSample());
    }
}
