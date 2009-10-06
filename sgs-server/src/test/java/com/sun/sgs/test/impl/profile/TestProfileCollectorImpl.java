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
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(FilteredNameRunner.class)
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

    @Test
    public void testLocalNodeIdReported() {
        long expectedNodeId = serverNode.getNodeId();
        SimpleTestListener test = new SimpleTestListener();
        profileCollector.addListener(test, false);
        assertEquals(expectedNodeId, test.reportedNodeId);
    }

}
