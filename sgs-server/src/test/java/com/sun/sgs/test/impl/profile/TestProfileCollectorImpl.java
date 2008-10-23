/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.profile.TaskProfileCounter;
import com.sun.sgs.profile.TaskProfileOperation;
import com.sun.sgs.profile.TaskProfileSample;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;


@RunWith(NameRunner.class)
public class TestProfileCollectorImpl {
    private final static String APP_NAME = "TestProfileCollectorImpl";
    
    /** A test server node */
    private SgsTestNode serverNode;  
    /** The profile collector associated with the test server node */
    private ProfileCollector profileCollector;
    /** The system registry */
    private ComponentRegistry systemRegistry;
    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;
    /** The owner for tasks I initiate. */
    private Identity taskOwner;
    
    /** Any additional nodes, only used for selected tests */
    private SgsTestNode additionalNodes[];
    
    /** Test setup. */
    @Before
    public void setUp() throws Exception {
        // Start a partial stack.  We actually don't need any services for
        // these tests, but we cannot start up additional nodes if we don't
        // have at least the core services started.
        Properties p = SgsTestNode.getDefaultProperties(APP_NAME, null, null);
        p.setProperty("com.sun.sgs.finalService", "NodeMappingService");
        setUp(p);
    }

    protected void setUp(Properties props) throws Exception {
        serverNode = new SgsTestNode(APP_NAME, null, props);
        profileCollector = getCollector(serverNode);
        systemRegistry = serverNode.getSystemRegistry();
        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();
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

        ////////     The tests     /////////
    
    /*-- Global profile level tests --*/
    @Test
    public void testDefaultKernel() {
        // The profile collector must not be null and the level must be "min"
        assertNotNull(profileCollector);
        assertSame(ProfileLevel.MIN, profileCollector.getDefaultProfileLevel());
    }

    @Test
    public void testKernelNoProfile() throws Exception {
        // Even if the user specifies no profiling at startup, the collector
        // must not be null.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "MIN");
        addNodes(serviceProps, 1);

        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MIN, collector.getDefaultProfileLevel());

    }

    @Test
    public void testKernelBadProfileLevel() throws Exception {
        // The kernel won't start if a bad profile level is provided.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "JUNKJUNK");
        try {
            addNodes(serviceProps, 1);
            fail("Excpected kernel to not start up");
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            assertEquals("Expected IllegalArgumentException",
                         IllegalArgumentException.class.getName(), 
                         t.getClass().getName());
        }
    }

    @Test
    public void testKernelLowerCaseLevel() throws Exception {
        // The profiling level is case insensitive.
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "medium");
        addNodes(serviceProps, 1);
        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MEDIUM, collector.getDefaultProfileLevel());
    }
    
    // need tests to check that listeners added with boolean false (addListener)
    // are not shut down, and tests for consumer profile levels being 
    // independent and changable.
    @Test
    public void testLocale() throws Exception {
        Locale.setDefault(Locale.JAPANESE);
        Properties serviceProps = 
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        serviceProps.setProperty(
                "com.sun.sgs.impl.kernel.profile.level", "medium");
        addNodes(serviceProps, 1);
        ProfileCollector collector = getCollector(additionalNodes[0]);
        assertNotNull(collector);
        assertSame(ProfileLevel.MEDIUM, collector.getDefaultProfileLevel());
    }
    
    /* -- consumer creation tests -- */
    @Test
    public void testConsumerMapAdd() throws Exception {
        // Create a ProfileConsumer, and make sure it appears in the consumer
        // map.
        ProfileCollector collector = getCollector(serverNode);
        
        Map<String, ProfileConsumer> consumerMap = 
                profileCollector.getConsumers();
        int count = consumerMap.size();
        
        ProfileConsumer pc1 = collector.getConsumer("Cons1");
        ProfileConsumer pc2 = collector.getConsumer("Cons2");
        
        consumerMap = profileCollector.getConsumers();
        assertSame(count+2, consumerMap.size());
        assertEquals(pc1, consumerMap.get("Cons1"));
        assertEquals(pc2, consumerMap.get("Cons2"));
    }
    
    @Test
    public void testConsumerMapBadAdd() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        collector.getConsumer("Cons1");
        collector.getConsumer("Cons2");
        
        Map<String, ProfileConsumer> consumerMap =
                profileCollector.getConsumers();
        int count = consumerMap.size();
        ProfileConsumer pc1 = consumerMap.get("Cons2");
        
        // Test that the map isn't modified if we try adding the same
        // named consumer a second time
        ProfileConsumer pc2 = collector.getConsumer("Cons2");
        assertSame(count, consumerMap.size());
        assertSame(pc2, pc1);
    }
    
    @Test(expected=UnsupportedOperationException.class)
    public void testGetConsumersReadOnly() {
        Map<String, ProfileConsumer> consumerMap =
                profileCollector.getConsumers();
        consumerMap.put("Foo", null);
    }
    
    /* -- Listener tests -- */
    @Test
    public void testNoListener() {
        // By default, there is one listener in the system, 
        // the TransactionScheduler
        assertEquals(1, profileCollector.getListeners().size());
    }
    
    @Test
    public void testGetListener() throws Exception {
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, true);
        List<ProfileListener> listeners = 
                profileCollector.getListeners();
        assertEquals(2, listeners.size());
        assertTrue(listeners.contains(test));
    }
    
    @Test(expected=UnsupportedOperationException.class)
    public void testGetListenersReadOnly() {
        List<ProfileListener> listeners = 
                profileCollector.getListeners();
        listeners.add(null);
    }
    
    
    @Test
    public void testAddListenerCalled() throws Exception {
        final Semaphore flag = new Semaphore(1);
        
        SimpleTestListener test = new SimpleTestListener(
            new Runnable() {
                public void run() {
                    flag.release();
                }
        });
        profileCollector.addListener(test, true);
        
        flag.acquire();
        // Run a task, to be sure our listener gets called at least once
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                // empty task
		public void run() { 
                }
            }, taskOwner);
            
        // Calling reports is asynchronous
        flag.tryAcquire(100, TimeUnit.MILLISECONDS);
        assertTrue(test.reportCalls > 0);
    }
    
    @Test
    public void testAddListenerTwice() throws Exception {
        int initialSize = profileCollector.getListeners().size();
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, true);
        assertEquals(initialSize + 1, profileCollector.getListeners().size());
        profileCollector.addListener(test, true);
        assertEquals(initialSize + 1, profileCollector.getListeners().size());
    }
    
    @Test
    public void testListenerShutdown() throws Exception {
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, true);
        // The profile collector should shut down all listeners added with
        // argument of true
        profileCollector.shutdown();
        assertTrue(test.shutdownCalls > 0);
    }
    
    @Test
    public void testListenerNoShutdown() throws Exception {
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, false);
        // The profile collector should not shut down listeners added with
        // argument of false
        profileCollector.shutdown();
        assertEquals(0, test.shutdownCalls);
    }
    
    @Test
    public void testListenerRemove() throws Exception {
        int initialSize = profileCollector.getListeners().size();
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, true);
        assertEquals(initialSize  + 1, profileCollector.getListeners().size());
        // Remove should cause shutdown
        profileCollector.removeListener(test);
        assertTrue(test.shutdownCalls > 0);
        assertEquals(initialSize, profileCollector.getListeners().size());
    }
    
    @Test
    public void testListenerNoRemove() throws Exception {
        int initialSize = profileCollector.getListeners().size();
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, false);
        assertEquals(initialSize  + 1, profileCollector.getListeners().size());
        // Cannot remove and no shutdown call
        profileCollector.removeListener(test);
        assertEquals(0, test.shutdownCalls);
        assertEquals(initialSize  + 1, profileCollector.getListeners().size());
    }
    
    @Test(expected=NullPointerException.class)
    public void testListenerNullRemove() {
        profileCollector.removeListener(null);
    }
    
    @Test
    public void testListenerRemoveNotAdded() {
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.removeListener(test);
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
    @Test
    public void testCounterName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        String name = "taskcounter";
        ProfileCounter counter1 = 
                cons1.createCounter(name, 
                                    ProfileDataType.TASK, ProfileLevel.MAX);
        assertEquals(name, counter1.getName());
        
        name = "aggregateCounter";
        ProfileCounter counter2 = 
                cons1.createCounter(name, ProfileDataType.AGGREGATE, 
                                    ProfileLevel.MAX);
        assertEquals(name, counter2.getName());
        
        name = "bothCounter";
        ProfileCounter counter3 = 
                cons1.createCounter(name, ProfileDataType.TASK_AGGREGATE, 
                                    ProfileLevel.MAX);
        assertEquals(name, counter3.getName());
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
                                    ProfileDataType.TASK_AGGREGATE, 
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
                                    ProfileDataType.TASK_AGGREGATE, 
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
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, positiveOwner, errorExchanger, 1));
        profileCollector.addListener(test, true);

        // We don't expect to see the counter updated in the task report
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount();
                }
            }, taskOwner);

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
                                        ProfileDataType.TASK_AGGREGATE, 
                                        ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // Set up a couple of test listeners, each listening for a different
        // task owner
        final Identity owner1 = new DummyIdentity("hello");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, owner1, errorExchanger, 1));
        profileCollector.addListener(test, true);
        
        // We expect to see the counter updated in the task report
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount();
                }
            }, owner1);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            // Rethrow with the original error as the cause so we see
            // both stack traces.
            throw new AssertionError(error);
        }
        
        // We expect to see the counter updated in the task report,
        // and it should be independent of the last report
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount();
                }
            }, owner1);

        error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            // Rethrow with the original error as the cause so we see
            // both stack traces.
            throw new AssertionError(error);
        }
        
        // And we expect to see the counter aggregated
        assertEquals(2, counter.getCount());
    }
    
    
    /* -- Operation tests -- */
    @Test
    public void testOperationName() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        String name = "myOperation";
        ProfileOperation op1 = 
                cons1.createOperation(name, 
                                      ProfileDataType.TASK, ProfileLevel.MAX);
        assertEquals(name, op1.getName());
        
        name = "aggOp";
        ProfileOperation op2 = 
                cons1.createOperation(name, ProfileDataType.AGGREGATE, 
                                      ProfileLevel.MAX);
        assertEquals(name, op2.getName());
        
        name = "bothOp";
        ProfileOperation op3 = 
                cons1.createOperation(name, ProfileDataType.TASK_AGGREGATE, 
                                      ProfileLevel.MAX);
        assertEquals(name, op3.getName());
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
                                      ProfileDataType.TASK_AGGREGATE, 
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
            cons1.createOperation("op3", ProfileDataType.TASK_AGGREGATE, 
                                  ProfileLevel.MAX);
        assertTrue(op3 instanceof TaskProfileOperation);
        assertTrue(op3 instanceof AggregateProfileOperation);
    }
     
    /* -- Sample tests -- */
    @Test
    public void testSampleName() throws Exception {
        final String name = "SomeSamples";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample sample1 = 
                cons1.createSample(name, ProfileDataType.TASK, 
                                   -1, ProfileLevel.MAX);
        assertEquals(name, sample1.getName());
    }
    
    @Test
    public void testSampleTwice() throws Exception {
        final String name = "mySamples";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
                cons1.createSample(name, ProfileDataType.TASK, 
                                   -1, ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileSample s2 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   -1, ProfileLevel.MAX);
        assertSame(s1, s2);
        
        // Try creating with same name and different parameters
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.AGGREGATE, 
                                   -1, ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        } 
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK_AGGREGATE, 
                                   -1, ProfileLevel.MAX);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   -1, ProfileLevel.MIN);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        try {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   -1, ProfileLevel.MEDIUM);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            System.err.println(expected);
        }
        {
            ProfileSample s3 =
                cons1.createSample(name, ProfileDataType.TASK, 
                                   25, ProfileLevel.MAX);
            assertSame(s1, s3);
        }
        
        final String aggName = "aggregateSample";
        {
            ProfileSample s3 =
                 cons1.createSample(aggName, ProfileDataType.AGGREGATE,
                                    -1, ProfileLevel.MAX);
            try {
                ProfileSample s4 =
                     cons1.createSample(aggName, ProfileDataType.AGGREGATE,
                                        75, ProfileLevel.MAX);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                System.err.println(expected);
            }
        }
        
        final String taskAggName = "task aggregate sample";
        {
            ProfileSample s3 =
                cons1.createSample(taskAggName, ProfileDataType.TASK_AGGREGATE, 
                                   -1, ProfileLevel.MAX);
            try {
                ProfileSample s4 =
                     cons1.createSample(taskAggName, 
                                        ProfileDataType.TASK_AGGREGATE,
                                        25, ProfileLevel.MAX);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                System.err.println(expected);
            }
        }
        
        // Try creating with a different name
        ProfileSample s5 =
            cons1.createSample("somethingelse", ProfileDataType.TASK, 
                               -1, ProfileLevel.MAX);
        assertNotSame(s1, s5);
    }
    

    @Test
    public void testSampleType() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
            cons1.createSample("samples1", ProfileDataType.TASK, 
                               -1, ProfileLevel.MAX);
        assertTrue(s1 instanceof TaskProfileSample);
        assertFalse(s1 instanceof AggregateProfileSample);
 
        ProfileSample s2 = 
            cons1.createSample("samples2", ProfileDataType.AGGREGATE, 
                               -1, ProfileLevel.MAX);
        assertFalse(s2 instanceof TaskProfileSample);
        assertTrue(s2 instanceof AggregateProfileSample);
        
        ProfileSample s3 = 
            cons1.createSample("samples3", ProfileDataType.TASK_AGGREGATE, 
                               -1, ProfileLevel.MAX);
        assertTrue(s3 instanceof TaskProfileSample);
        assertTrue(s3 instanceof AggregateProfileSample);
    }
}
