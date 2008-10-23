/*
 * Copyright 2008 Sun Microsystems, Inc.
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
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.profile.ProfileSample;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.ParameterizedNameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.test.util.UtilReflection;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertTrue;

/**
 * Tests for profile data that will be run with data of type
 * {@code TASK} and {@code TASK_AGGREGATE}.
 */
@RunWith(ParameterizedNameRunner.class)
public class TestProfileDataTask {

    private final static String APP_NAME = "TestProfileDataTask";
    
    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][] {{ProfileDataType.TASK}, 
                                             {ProfileDataType.TASK_AGGREGATE}});
    }
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
    
    private Field profileCollectorField = 
            UtilReflection.getField(
                com.sun.sgs.impl.profile.ProfileRegistrarImpl.class, 
                "profileCollector");
    
    private final ProfileDataType testType;
    /**
     * Create this test class.
     * @param testType the type of profile data to create
     */
    public TestProfileDataTask(ProfileDataType testType) {
        this.testType = testType;
        System.err.println("Test type is " + testType);
    }
    
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
        ProfileRegistrar registrar = getRegistrar(node);
        return (ProfileCollector) profileCollectorField.get(registrar);
    }
    
    /** Returns the profile registrar for a given node */
    private ProfileRegistrar getRegistrar(SgsTestNode node) {
        return  node.getSystemRegistry().getComponent(ProfileRegistrar.class);
    }
    
    /* -- counter tests -- */
    @Test
    public void testCounter() throws Exception {
        final String name = "counter";
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.createCounter(name, testType, ProfileLevel.MIN);   
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("owner");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, positiveOwner, errorExchanger, 1));
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be updated only at the max level
        final ProfileCounter counter = 
                cons1.createCounter(name, testType, ProfileLevel.MAX);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("counterlevel");
        SimpleTestListener test = new SimpleTestListener( 
            new CounterReportRunnable(name, positiveOwner, errorExchanger, 1));
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
    
   @Test(expected=IllegalArgumentException.class)
    public void testCounterIncrementValueBad() {
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileCounter counter = 
                cons1.createCounter("my counter", 
                                    testType, ProfileLevel.MIN);
        
        counter.incrementCount(-1);
    }
       
    @Test
    public void testCounterIncrementValue() throws Exception {
        final String name = "counter";
        final int incValue = 5;
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.createCounter(name, testType, ProfileLevel.MIN);

        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("counterinc");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, positiveOwner, 
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
    public void testCounterIncrementMultiple() throws Exception {
        final String name = "counterforstuff";
        final int incValue = 3;
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.createCounter(name, testType, ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("countermult");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, positiveOwner, 
                                      errorExchanger, incValue));
        profileCollector.addListener(test, true);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
		public void run() { 
                    for (int i = 0; i < incValue; i++) {
                        counter.incrementCount();
                    }
                }
            }, positiveOwner);

        AssertionError error = 
                errorExchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        if (error != null) {
            throw new AssertionError(error);
        }
    }
    
    @Test
    public void testCounterIncrementValueMultiple() throws Exception {
        final String name = "counterforstuff";
        final int incValue = 5;
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileCounter counter = 
                cons1.createCounter(name, testType, ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("countermult");
        SimpleTestListener test = new SimpleTestListener(
            new CounterReportRunnable(name, positiveOwner, 
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
    
    @Test
    public void testOperation() throws Exception {
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileOperation op =
                cons1.createOperation("something", testType, ProfileLevel.MIN);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opowner");
        SimpleTestListener test = new SimpleTestListener(
            new OperationReportRunnable(op, positiveOwner, errorExchanger));
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileOperation op =
                cons1.createOperation("something", testType, 
                                      ProfileLevel.MEDIUM);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opmed");
        SimpleTestListener test = new SimpleTestListener(
            new OperationReportRunnable(op, positiveOwner, errorExchanger));
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileOperation op =
            cons1.createOperation("something", testType,ProfileLevel.MEDIUM);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opmedtomax");
        SimpleTestListener test = new SimpleTestListener(
            new OperationReportRunnable(op, positiveOwner, errorExchanger));
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileOperation op =
                cons1.createOperation("something", testType, ProfileLevel.MAX);
        
        // Because the listener is running in a different thread, JUnit
        // is not able to report the assertions and failures.
        // Use an exchanger to synchronize between the threads and communicate
        // any problems.
        final Exchanger<AssertionError> errorExchanger = 
                new Exchanger<AssertionError>();

        // The owner for our positive test.  The listener uses this owner
        // to find the ProfileReport for the task in this test.
        final Identity positiveOwner = new DummyIdentity("opmax");
        SimpleTestListener test = new SimpleTestListener(
            new OperationReportRunnable(op, positiveOwner, errorExchanger));
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileOperation op =
                cons1.createOperation("something", testType, ProfileLevel.MIN);
        final ProfileOperation op1 =
                cons1.createOperation("else", testType, ProfileLevel.MIN);
        
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
                            List<ProfileOperation> ops =
                                SimpleTestListener.report.getReportedOperations();
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
        
            @Test
    public void testSample() throws Exception {
        final String name = "sample";
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be noted at all profiling levels
        final ProfileSample sample = 
            cons1.createSample(name, testType, -1, ProfileLevel.MIN);

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
        SimpleTestListener test = new SimpleTestListener(
            new SampleReportRunnable(name, positiveOwner, 
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("cons1");
        final ProfileSample sample = 
            cons1.createSample(name, testType, -1, ProfileLevel.MAX);
        
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
        SimpleTestListener test = new SimpleTestListener(
            new SampleReportRunnable(name, positiveOwner, 
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
        ProfileRegistrar registrar = getRegistrar(serverNode);
        final ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final ProfileSample sample = 
            cons1.createSample(name, testType, -1, ProfileLevel.MAX);

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
        SimpleTestListener test = new SimpleTestListener(
            new SampleReportRunnable(name, positiveOwner, 
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
}
