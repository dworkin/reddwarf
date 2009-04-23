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
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
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
 * {@code AGGREGATE} and {@code TASK_AND_AGGREGATE}.
 */
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestProfileDataAggregateImpl {

    private final static String APP_NAME = "TestProfileDataAggregateTask";
    
    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(
                new Object[][] {{ProfileDataType.AGGREGATE}, 
                                {ProfileDataType.TASK_AND_AGGREGATE}});
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
    
    private final ProfileDataType testType;
    /**
     * Create this test class.
     * @param testType the type of profile data to create
     */
    public TestProfileDataAggregateImpl(ProfileDataType testType) {
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
        p.setProperty(StandardProperties.NODE_TYPE, 
                      NodeType.coreServerNode.name());
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
    
   @Test
    public void testAggregateProfileCounter() throws Exception {
        final String name = "counter";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
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
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
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
   
   @Test(expected=IllegalArgumentException.class)
   public void testAggregateProfileNegSmoothing() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final AggregateProfileSample samp =
                (AggregateProfileSample) 
                    cons1.createSample("foo", testType, ProfileLevel.MIN);
        samp.setSmoothingFactor(-1.1);
   }
   
   @Test(expected=IllegalArgumentException.class)
   public void testAggregateProfileBadSmoothing() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final AggregateProfileSample samp =
                (AggregateProfileSample) 
                    cons1.createSample("foo", testType, ProfileLevel.MIN);
        samp.setSmoothingFactor(5.0);
   }
   
   @Test
   public void testSetSmoothingFactor() throws Exception {
       ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final AggregateProfileSample samp =
                (AggregateProfileSample) 
                    cons1.createSample("bar", testType, ProfileLevel.MIN);
        double smooth = 0.5;
        samp.setSmoothingFactor(smooth);
        assertTrue(smooth == samp.getSmoothingFactor());
        
        smooth = 0.9;
        samp.setSmoothingFactor(smooth);
        assertTrue(smooth == samp.getSmoothingFactor());
   }
   
   @Test
   public void testAggregateProfileSample() throws Exception {
        final String name = "sample";
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final AggregateProfileSample samp =
                (AggregateProfileSample) 
                    cons1.createSample(name, testType, ProfileLevel.MIN);
        
        assertEquals(0, samp.getNumSamples());
        for (Long sample : samp.getSamples()) {
            assertNull(sample);
        }
        

        final long[] testData = {4, 8, 2, -1, 5, 9, 11, 14};
        // capacity defaults to zero, so size should be zero
        samp.addSample(3);
        testStatistics(samp, /*size*/0, /*min*/3, /*max*/3, testData);
        
        samp.setCapacity(100);
        
        samp.addSample(testData[0]);
        testStatistics(samp, /*size*/1, /*min*/3, /*max*/4, testData);
       
        samp.addSample(testData[1]);
        testStatistics(samp, 2, 3, 8, testData);
        
        samp.addSample(testData[2]);
        testStatistics(samp, 3, 2, 8, testData);
        
        samp.addSample(testData[3]);
        testStatistics(samp, 4, -1, 8, testData);
        
        samp.addSample(testData[4]);
        testStatistics(samp, 5, -1, 8, testData);
        
        samp.addSample(testData[5]);
        testStatistics(samp, 6, -1, 9, testData);
         
        samp.addSample(testData[6]);
        testStatistics(samp, 7, -1, 11, testData);
        
        samp.addSample(testData[7]);
        testStatistics(samp, 8, -1, 14, testData);
        
        samp.clearSamples();
        assertEquals(0, samp.getNumSamples());
        for (Long sample : samp.getSamples()) {
            assertNull(sample);
        }
        
        // Add all the data back in, check that min, max, values what we expect
        for (long data : testData) {
            samp.addSample(data);
        }
        testStatistics(samp, 8, -1, 14, testData);
   }
   
   @Test
   public void testAggregateProfileSampleCapacity() throws Exception {
        ProfileCollector collector = getCollector(serverNode);
        ProfileConsumer cons1 = collector.getConsumer("c1");
        final AggregateProfileSample samp1 =
                (AggregateProfileSample) 
                    cons1.createSample("s1", testType, ProfileLevel.MIN);
        // default capacity is zero
        assertEquals(0, samp1.getCapacity());
        
        final AggregateProfileSample samp2 =
                (AggregateProfileSample) 
                    cons1.createSample("s2", testType, ProfileLevel.MIN);
        samp2.setCapacity(5);
        assertEquals(5, samp2.getCapacity());
        
        // Add 5 samples, then make sure 6th causes the first to go away
        final long[] testData = {1, 2, 3, 4, 5};
        for (int i = 0; i < testData.length; i++) {
            samp2.addSample(testData[i]);
        }
        
        testStatistics(samp2, /*size*/5, /*min*/1, /*max*/5, testData);
        
        final long[] expectedData = {2, 3, 4, 5, 6};
        samp2.addSample(6);
        testStatistics(samp2, 5, 1, 6, expectedData);
        
        final long[] emptyData = {};
        samp2.clearSamples();
        samp2.setCapacity(0);
        for (int i = 0; i < testData.length; i++) {
            samp2.addSample(testData[i]);
        }
        testStatistics(samp2, /*size*/0, /*min*/1, /*max*/5, emptyData);
        samp2.addSample(6);
        testStatistics(samp2, 0, 1, 6, emptyData);
   }
   
   private void testStatistics(AggregateProfileSample samp, 
                                 int expectedSize,
                                 long expectedMin,
                                 long expectedMax,
                                 long[] testData) 
   {
        double currAvg = samp.getAverage();
        long currMin = samp.getMinSample();
        long currMax = samp.getMaxSample();
        long currSize = samp.getNumSamples();
        System.err.println("Sample data:  " +
                "size: " + currSize +
                " avg: " + currAvg +
                " min: " + currMin +
                " max: " + currMax);


        assertEquals(expectedSize, currSize);
        assertEquals(expectedMin, currMin);
        assertEquals(expectedMax, currMax);
        assertTrue(currAvg <= currMax);
        assertTrue(currAvg >= currMin);

        List<Long>samples = samp.getSamples();
        assertEquals(currSize, samples.size());
        for (int i = 0; i < samp.getNumSamples(); i++) {
            assertEquals(testData[i], samples.get(i).longValue());
        }
   }
}
