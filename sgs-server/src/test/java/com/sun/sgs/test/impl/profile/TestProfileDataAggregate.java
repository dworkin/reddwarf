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
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.AggregateProfileSample;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.test.util.ParameterizedNameRunner;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for profile data that will be run with data of type
 * {@code AGGREGATE} and {@code TASK_AGGREGATE}.
 */
@RunWith(ParameterizedNameRunner.class)
public class TestProfileDataAggregate {

    private final static String APP_NAME = "TestProfileDataTask";
    
    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][] {{ProfileDataType.AGGREGATE}, 
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
    public TestProfileDataAggregate(ProfileDataType testType) {
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
    
   @Test
    public void testAggregateProfileCounter() throws Exception {
        final String name = "counter";
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        // Register a counter to be noted at all profiling levels
        final AggregateProfileCounter counter = 
                (AggregateProfileCounter) 
                    cons1.createCounter(name, testType, ProfileLevel.MIN);
        
        counter.incrementCount();
        assertEquals(1, counter.getCount());
        
        counter.incrementCount(4);
        assertEquals(5, counter.getCount());
        
        counter.clearCount();
        assertEquals(0, counter.getCount());
        
        counter.incrementCount(2);
        assertEquals(2, counter.getCount());
    }
   
   @Test
   public void testAggregateProfileOperation() throws Exception {
        final String name = "operation";
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final AggregateProfileOperation op =
                (AggregateProfileOperation) 
                    cons1.createOperation(name, testType, ProfileLevel.MIN);
        
        op.report();
        assertEquals(1, op.getCount());
        
        op.report();
        assertEquals(2, op.getCount());
        
        op.clearCount();
        assertEquals(0, op.getCount());
        
        op.report();
        assertEquals(1, op.getCount());
    }
   
   @Test
   public void testAggregateProfileSample() throws Exception {
        final String name = "sample";
        ProfileRegistrar registrar = getRegistrar(serverNode);
        ProfileConsumer cons1 = registrar.registerProfileProducer("c1");
        final AggregateProfileSample samp =
                (AggregateProfileSample) 
                    cons1.createSample(name, testType, -1, ProfileLevel.MIN);
        
        assertEquals(0, samp.getNumSamples());
        for (Long sample : samp.getSamples()) {
            assertNull(sample);
        }
        
        final long[] testData = {4, 8, 2, -1, 5, 9, 11, 14};
        
        int testIndex = 0;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, 4, 4, testData);
       
        testIndex = 1;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, 4, 8, testData);
        
        
        testIndex = 2;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, 2, 8, testData);
        
        testIndex = 3;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, -1, 8, testData);
        
        testIndex = 4;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, -1, 8, testData);
        
        testIndex = 5;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, -1, 9, testData);
         
        testIndex = 6;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, -1, 11, testData);
        
        testIndex = 7;
        samp.addSample(testData[testIndex]);
        testStatistics(samp, testIndex, -1, 14, testData);
        
        samp.clearSamples();
        assertEquals(0, samp.getNumSamples());
        for (Long sample : samp.getSamples()) {
            assertNull(sample);
        }
        
        // Add all the data back in, check that min, max, values what we expect
        for (long data : testData) {
            samp.addSample(data);
        }
        testStatistics(samp, testIndex, -1, 14, testData);
    }
   
    private void testStatistics(AggregateProfileSample samp, 
                                 int testIndex,
                                 long expectedMin,
                                 long expectedMax,
                                 long[] testData) 
    {
        double currAvg = samp.getAverage();
        long currMin = samp.getMinSample();
        long currMax = samp.getMaxSample();
        System.err.println("Sample data:  " +
                "index: " + testIndex +
                " sample: " + testData[testIndex] +
                " avg: " + currAvg +
                " min: " + currMin +
                " max: " + currMax);
        
        assertEquals(testIndex + 1, samp.getNumSamples());
        assertEquals(expectedMin, currMin);
        assertEquals(expectedMax, currMax);
        assertTrue(currAvg <= currMax);
        assertTrue(currAvg >= currMin);
        
        List<Long>samples = samp.getSamples();
        assertEquals(testIndex + 1, samples.size());
        for (int i = 0; i < samp.getNumSamples(); i++) {
            assertEquals(testData[i], samples.get(i).longValue());
        }
   }
}
