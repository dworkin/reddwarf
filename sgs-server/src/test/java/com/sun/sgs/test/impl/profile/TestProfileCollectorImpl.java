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
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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
        TestListener test = new TestListener();
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
        
        TestListener test = new TestListener(
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
        TestListener test = new TestListener();
        profileCollector.addListener(test, true);
        assertEquals(initialSize + 1, profileCollector.getListeners().size());
        profileCollector.addListener(test, true);
        assertEquals(initialSize + 1, profileCollector.getListeners().size());
    }
    
    @Test
    public void testListenerShutdown() throws Exception {
        TestListener test = new TestListener();
        profileCollector.addListener(test, true);
        // The profile collector should shut down all listeners added with
        // argument of true
        profileCollector.shutdown();
        assertTrue(test.shutdownCalls > 0);
    }
    
    @Test
    public void testListenerNoShutdown() throws Exception {
        TestListener test = new TestListener();
        profileCollector.addListener(test, false);
        // The profile collector should not shut down listeners added with
        // argument of false
        profileCollector.shutdown();
        assertEquals(0, test.shutdownCalls);
    }
    
    @Test
    public void testListenerRemove() throws Exception {
        int initialSize = profileCollector.getListeners().size();
        TestListener test = new TestListener();
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
        TestListener test = new TestListener();
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
        TestListener test = new TestListener();
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
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter counter1 = 
                cons1.registerCounter(name, true, ProfileLevel.MAX);
        assertEquals(name, counter1.getCounterName());
    }
    
    @Test
    public void testCounterTwice() throws Exception {
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter counter1 = 
                cons1.registerCounter(name, true, ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileCounter counter2 =
                cons1.registerCounter(name, true, ProfileLevel.MAX);
        assertSame(counter1, counter2);
        
        // Try creating with same name and different parameters
        // Note we expect this behavior to change with upcoming API changes
        ProfileCounter op3 =
                cons1.registerCounter(name, false, ProfileLevel.MAX);
        assertSame(counter1, op3);
        
        // Try creating with a different name
        ProfileCounter counter4 =
                cons1.registerCounter("somethingelse", true, ProfileLevel.MAX);
        assertNotSame(counter1, counter4);
    }
    
    @Test
    public void testCounterType() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileCounter counter = 
                cons1.registerCounter("counter", true, ProfileLevel.MIN);
        
        assertTrue(counter.isTaskLocal());
        
        ProfileCounter counter1 = 
                cons1.registerCounter("other", false, ProfileLevel.MIN);
        assertFalse(counter1.isTaskLocal());
    }
    
    // NOTE not bothering to write tests for aggregate types now, as
    // aggregation within profile reports will be going away soon
    
    @Test
    public void testCounter() throws Exception {
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.registerCounter(name, true, ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("owner");
        TestListener test = new TestListener(
            new TestCounterReport(name, positiveOwner, errorExchanger, 1));
        profileCollector.addListener(test, true);

        // We run with the myOwner because we expect to see the
        // value in the test report.
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

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                }
            }, taskOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void testCounterLevel() throws Exception {
        final String name = "MyCounter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be updated only at the max level
        final ProfileCounter counter = 
                cons1.registerCounter(name, true, ProfileLevel.MAX);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("counterlevel");
        TestListener test = new TestListener( 
            new TestCounterReport(name, positiveOwner, errorExchanger, 1));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // The default profile level is MIN so we don't expect
                    // to see the counter incremented.
                    counter.incrementCount();
                }
            }, taskOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        cons1.setProfileLevel(ProfileLevel.MAX);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    // Because we bumped the consumer's profile level,
                    // we expect the counter
                    counter.incrementCount();
                }
            }, positiveOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    @Test
    public void testCounterIncrement() throws Exception {
        final String name = "counter";
        final int incValue = 5;
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.registerCounter(name, true, ProfileLevel.MIN);

        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("counterinc");
        TestListener test = new TestListener(
            new TestCounterReport(name, positiveOwner, 
                                  errorExchanger, incValue));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    counter.incrementCount(incValue);
                }
            }, positiveOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    @Test
    public void testCounterMultiple() throws Exception {
        final String name = "counterforstuff";
        final int incValue = 5;
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.registerCounter(name, true, ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("countermult");
        TestListener test = new TestListener(
            new TestCounterReport(name, positiveOwner, 
                                  errorExchanger, incValue));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We expect to see the counter incremented by 5 total
                    counter.incrementCount(2);
                    counter.incrementCount(3);
                }
            }, positiveOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
        
    /* -- Operation tests -- */
    @Test
    public void testOperationName() throws Exception {
        final String name = "myOperation";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation op1 = 
                cons1.registerOperation(name, ProfileLevel.MAX);
        assertEquals(name, op1.getOperationName());
    }
    
    @Test
    public void testOperationTwice() throws Exception {
        final String name = "myOperation";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileOperation op1 = 
                cons1.registerOperation(name, ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileOperation op2 =
                cons1.registerOperation(name, ProfileLevel.MAX);
        assertSame(op1, op2);
        
        // Try creating with same name and different parameters
        // Note we expect this behavior to change with upcoming API changes
        ProfileOperation op3 =
                cons1.registerOperation(name, ProfileLevel.MIN);
        assertSame(op1, op3);
        
        // Try creating with a different name
        ProfileOperation op4 =
                cons1.registerOperation("somethingelse", ProfileLevel.MAX);
        assertNotSame(op1, op4);
    }
    
    // NOTE will need type tests soon, right now there is only one type
    // of operation
    @Ignore
    public void testOperationType() {
        
    }
    
    @Test
    public void testOperation() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileOperation op =
                cons1.registerOperation("something", ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opowner");
        TestListener test = new TestListener(
            new TestOperationReport(op, positiveOwner, errorExchanger));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    op.report();
                }
            }, positiveOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                }
            }, taskOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void testOperationMediumLevel() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileOperation op =
                cons1.registerOperation("something", ProfileLevel.MEDIUM);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opmed");
        TestListener test = new TestListener(
            new TestOperationReport(op, positiveOwner, errorExchanger));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We do not expect to see this reported.
                    op.report();
                }
            }, taskOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        cons1.setProfileLevel(ProfileLevel.MEDIUM);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    op.report();
                }
            }, positiveOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    @Test
    public void testOperationMediumToMaxLevel() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileOperation op =
                cons1.registerOperation("something", ProfileLevel.MEDIUM);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opmedtomax");
        TestListener test = new TestListener(
            new TestOperationReport(op, positiveOwner, errorExchanger));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We do not expect to see this reported.
                    op.report();
                }
            }, taskOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        cons1.setProfileLevel(ProfileLevel.MAX);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    op.report();
                }
            }, positiveOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    @Test
    public void testOperationMaxLevel() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileOperation op =
                cons1.registerOperation("something", ProfileLevel.MAX);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opmax");
        TestListener test = new TestListener(
            new TestOperationReport(op, positiveOwner, errorExchanger));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We do not expect to see this reported.
                    op.report();
                }
            }, taskOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        cons1.setProfileLevel(ProfileLevel.MEDIUM);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    // No report expected:  the level is still too low
                    op.report();
                }
            }, taskOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
        
        cons1.setProfileLevel(ProfileLevel.MAX);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    op.report();
                }
            }, positiveOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
       
    @Test
    public void testOperationMultiple() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileOperation op =
                cons1.registerOperation("something", ProfileLevel.MIN);
        final ProfileOperation op1 =
                cons1.registerOperation("else", ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final Identity myOwner = new DummyIdentity("me");
        TestListener test = new TestListener(
            new Runnable() {
                public void run() {
                    AssertionError error = null;
                    ProfileReport report = TestListener.report;
                    if (report.getTaskOwner().equals(myOwner)) {
                        try {
                            List<ProfileOperation> ops =
                                TestListener.report.getReportedOperations();
                            for (ProfileOperation po : ops) {
                                System.err.println(po);
                            }
                            int opIndex1 = ops.indexOf(op);
                            int opIndex2 = ops.lastIndexOf(op);
                            int op1Index1 = ops.indexOf(op1);
                            int op1Index2 = ops.lastIndexOf(op1);

                            // We expect to see op twice, and op1 once
                            assertTrue(opIndex1 != -1);
                            assertTrue(opIndex2 != -1);
                            assertTrue(op1Index1 != -1);
                            assertTrue(op1Index2 == op1Index1);

                            // We expect the op ordering to be maintained
                            assertTrue(opIndex1 < op1Index1);
                            assertTrue(op1Index1 < opIndex2);
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

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We expect to see the operation in the profile report
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
    }
     
    /* -- Sample tests -- */
    @Test
    public void testSampleName() throws Exception {
        final String name = "SomeSamples";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample sample1 = 
                cons1.registerSampleSource(name, true, -1, ProfileLevel.MAX);
        assertEquals(name, sample1.getSampleName());
    }
    
    @Test
    public void testSampleTwice() throws Exception {
        final String name = "mySamples";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
                cons1.registerSampleSource(name, true, -1, ProfileLevel.MAX);

        // Try creating with same name and parameters
        ProfileSample s2 =
                cons1.registerSampleSource(name, true, -1, ProfileLevel.MAX);
        assertSame(s1, s2);
        
        // Try creating with same name and different parameters
        // Note we expect this behavior to change with upcoming API changes
        ProfileSample s3 =
                cons1.registerSampleSource(name, false, -1, ProfileLevel.MIN);
        assertSame(s1, s3);     
        ProfileSample s4 =
                cons1.registerSampleSource(name, true, 25, ProfileLevel.MIN);
        assertSame(s1, s4);
        
        // Try creating with a different name
        ProfileSample s5 =
            cons1.registerSampleSource("somethingelse", 
                                        true, -1, ProfileLevel.MAX);
        assertNotSame(s1, s5);
    }
    

    @Test
    public void testSampleType() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        ProfileSample s1 = 
            cons1.registerSampleSource("samples", true, -1, ProfileLevel.MAX);
        
        assertTrue(s1.isTaskLocal());
        
        ProfileSample s2 = 
            cons1.registerSampleSource("other", false, -1, ProfileLevel.MAX);
        assertFalse(s2.isTaskLocal());
    }
     
    // NOTE not bothering to write tests for aggregate types now, as
    // aggregation within profile reports will be going away soon
    
    @Test
    public void testSample() throws Exception {
        final String name = "sample";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileSample sample = 
            cons1.registerSampleSource(name, true, -1, ProfileLevel.MIN);

        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final List<Long> testValues = new ArrayList<Long>();
        testValues.add(1L);
        testValues.add(5L);
        testValues.add(-22L);
        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("sampleowner");
        TestListener test = new TestListener(
            new TestSampleReport(name, positiveOwner, 
                                 errorExchanger, testValues));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // We expect to see the test values in listener
                    for (Long v : testValues) {
                        sample.addSample(v);
                    }
                }
            }, positiveOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                }
            }, taskOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void testSampleLevel() throws Exception {
        final String name = "MySamples";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("cons1");
        final ProfileSample sample = 
            cons1.registerSampleSource(name, true, -1, ProfileLevel.MAX);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final List<Long> testValues = new ArrayList<Long>();
        testValues.add(101L);
        testValues.add(-22L);
        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("samplelevel");
        TestListener test = new TestListener(
            new TestSampleReport(name, positiveOwner, 
                                 errorExchanger, testValues));
        profileCollector.addListener(test, true);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // The default profile level is MIN so we don't expect
                    // to see the samples.
                    for (Long v : testValues) {
                        sample.addSample(v);
                    }
                }
            }, taskOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        cons1.setProfileLevel(ProfileLevel.MAX);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    // Because we bumped the consumer's profile level,
                    // we expect the samples to appear
                    for (Long v : testValues) {
                        sample.addSample(v);
                    }
                }
            }, positiveOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    @Test
    public void testSampleLevelChange() throws Exception {
        final String name = "samples";
        ProfileCollector collector = getCollector(serverNode);
        final ProfileConsumer cons1 = collector.getConsumer("c1");
        final ProfileSample sample = 
            cons1.registerSampleSource(name, true, -1, ProfileLevel.MAX);

        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        final List<Long> testValues = new ArrayList<Long>();
        testValues.add(101L);
        testValues.add(-22L);
        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("samplechange");
        TestListener test = new TestListener(
            new TestSampleReport(name, positiveOwner, 
                                 errorExchanger, testValues));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    // The default profile level is MIN so we don't expect
                    // to see the samples.
                    for (Long v : testValues) {
                        sample.addSample(v);
                    }
                }
            }, taskOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() {
                    // We don't expect to see this sample in the report;
                    // the level was still too low.
                    sample.addSample(999L);
                    cons1.setProfileLevel(ProfileLevel.MAX);
                    for (Long v : testValues) {
                        sample.addSample(v);
                    }
                    cons1.setProfileLevel(ProfileLevel.MIN);
                    // Should not see this one, either
                    sample.addSample(-22L);
                }
            }, positiveOwner);
            
        error = errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    // NOTE: no test for maxSamples argument into consumer when creating
    // samples, as this is only used for the aggregate sample case (tasks
    // always saw all the samples)
    
    
    /* -- HELPER CLASSES -- */

    /** A simple profile listener that notes calls to the public APIs */
    private static class TestListener implements ProfileListener {
        int propertyChangeCalls = 0;
        int reportCalls = 0;
        int shutdownCalls = 0;
        final Runnable doReport;
        // Make the profile report available to the doReport runnable.
        static ProfileReport report;
        
        TestListener() {
            this.doReport = null;
        }
        TestListener(Runnable doReport) {
            this.doReport = doReport;
        }
        
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            propertyChangeCalls++;
        }

        @Override
        public void report(ProfileReport profileReport) {
            reportCalls++;
            if (doReport != null) {
                TestListener.report = profileReport;
                doReport.run();
            }
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
        }
    }
    
    /**
     * Helper class for counter tests.  This runnable is run during
     * the profile listener's report method.  It checks for a known
     * operation to know if a counter should have been incremented,
     * otherwise the counter should not be in the profile report.
     * Synchronization with the test case is performed through an
     * Exchanger. If an AssertionError is thrown, it is assumed to 
     * have come from the JUnit framework and is passed back to the 
     * test thread so it can be reported there.  Otherwise, JUnit
     * does not note that the test has failed.
     * <p>
     * Note that this class assumes the counter will only be updated once.
     */
    private static class TestCounterReport implements Runnable {
        final String name;
        final Identity positiveOwner;
        final Exchanger<AssertionError> errorExchanger;
        final int incrementValue;
        
        public TestCounterReport(String name,
                                 Identity positiveOwner, 
                                 Exchanger<AssertionError> errorExchanger,
                                 int incrementValue)
        {
            this.name = name;
            this.positiveOwner = positiveOwner;
            this.errorExchanger = errorExchanger;
            this.incrementValue = incrementValue;
        }
        
        public void run() {
            AssertionError error = null;
            ProfileReport report = TestListener.report;

            // Check to see if we expected the counter value to be
            // updated in this report.
            boolean update = report.getTaskOwner().equals(positiveOwner);
            if (update) {    
                try {
                    // Find the counter, make sure it was incremented
                    Long value = 
                        report.getUpdatedTaskCounters().get(name);
                    System.err.println("got counter value of " + value);
                    assertEquals(incrementValue, value.intValue());
                } catch (AssertionError e) {
                    error = e;
                }
            } else {
                try {
                    Long value =
                        report.getUpdatedTaskCounters().get(name);
                    assertNull("expected no value", value);
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
    }
    
    /**
     * Helper class for testing operations in ProfileReports
     */
    private static class TestOperationReport implements Runnable {
        final ProfileOperation operation;
        final Identity positiveOwner;
        final Exchanger<AssertionError> errorExchanger;
        
        public TestOperationReport(ProfileOperation operation,
                                   Identity positiveOwner,
                                   Exchanger<AssertionError> errorExchanger) 
        {
            this.operation = operation;
            this.positiveOwner = positiveOwner;
            this.errorExchanger = errorExchanger;
        }
        
        public void run() {
            AssertionError error = null;
            ProfileReport report = TestListener.report;
            // Check to see if we expected the operation to be in this report.
            boolean update = report.getTaskOwner().equals(positiveOwner);
            boolean found = report.getReportedOperations().contains(operation);
            try {
                assertEquals(update, found);
            } catch (AssertionError e) {
                error = e;
            }
            
            // Signal that we're done, and return the exception
            try { 
                errorExchanger.exchange(error);
            } catch (InterruptedException ignored) {
                // do nothing
            }
        }
    }
    
    /**
     * Helper class for sample tests.  This runnable is run during
     * the profile listener's report method.  It checks for a known
     * operation to know if a sample should have been added,
     * otherwise the sample should not be in the profile report.
     * Synchronization with the test case is performed through an
     * Exchanger. If an AssertionError is thrown, it is assumed to 
     * have come from the JUnit framework and is passed back to the 
     * test thread so it can be reported there.  Otherwise, JUnit
     * does not note that the test has failed.
     * <p>
     * Note that this class assumes the sample will only be updated once.
     */
    private static class TestSampleReport implements Runnable {
        final String name;
        final Identity positiveOwner;
        final Exchanger<AssertionError> errorExchanger;
        final List<Long> expectedValues;
        
        public TestSampleReport(String sampleName,
                                Identity positiveIdentity,
                                Exchanger<AssertionError> errorExchanger,
                                List<Long> expectedValues)
        {
            this.name = sampleName;
            this.positiveOwner = positiveIdentity;
            this.errorExchanger = errorExchanger;
            this.expectedValues = expectedValues;
        }
        
        public void run() {
            AssertionError error = null;
            ProfileReport report = TestListener.report;
            // Check to see if we expected the sample values to be
            // updated in this report.
            boolean update = report.getTaskOwner().equals(positiveOwner);
            List<Long> values = report.getUpdatedTaskSamples().get(name);
            
            try {
                if (update) {
                    assertEquals(expectedValues.size(), values.size());
                    for (int i = 0; i < expectedValues.size(); i++) {
                        Long found = values.get(i);
                        System.err.println("found value: " + found);
                        assertEquals(expectedValues.get(i), found);
                    }
                } else {
                    assertNull(values);
                }
            } catch (AssertionError e) {
                    error = e;
            }

            // Signal that we're done, and return the exception
            try { 
                errorExchanger.exchange(error);
            } catch (InterruptedException ignored) {
                // do nothing
            }
        }
    }
}
