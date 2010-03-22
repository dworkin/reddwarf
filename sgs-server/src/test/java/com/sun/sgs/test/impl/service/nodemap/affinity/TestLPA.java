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

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinitySet;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.DLPAGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LPAClient;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LPAServer;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Graphs;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for distributed label propagation algorithm.
 * 
 */
@RunWith(FilteredNameRunner.class)
public class TestLPA {
    /** Max number of times we'll call Thread.sleep in a loop. */
    private final static int MAX_SLEEP_COUNT = 50;

    private TestLPAServer server;
    private SgsTestNode serverNode;
    private ProfileCollector collector;
    private Properties props;
    private WatchdogService wdog;

    @Before
    public void setup() throws Exception {
        props = SgsTestNode.getDefaultProperties("TestLPA", null, null);
        props.put("com.sun.sgs.impl.kernel.profile.level", 
                   ProfileLevel.MAX.name());
        // We are creating this SgsTestNode so we can get at its watchdog
        // and profile collector only - the LPAServer we are testing is
        // created outside this framework so we could easily extend the type.
        serverNode = new SgsTestNode("TestLPA", null, props);
        int serverPort = SgsTestNode.getNextUniquePort();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                   String.valueOf(serverPort));
        props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads", "1");
        props.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "3600"); // one hour
        collector =
            serverNode.getSystemRegistry().getComponent(ProfileCollector.class);
        wdog = serverNode.getWatchdogService();
        server = new TestLPAServer(collector, wdog, props);
    }

    @After
    public void shutdown() throws Exception {
        if (serverNode != null) {
            serverNode.shutdown(true);
            serverNode = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }

    /* -- Server tests -- */
    @Test
    public void testDistributedFramework() throws Exception {
        final long generation = 1;
        Set<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        {
            HashSet<Identity> identitySet = new HashSet<Identity>();
            identitySet.add(new DummyIdentity("1"));
            identitySet.add(new DummyIdentity("2"));
            identitySet.add(new DummyIdentity("3"));
            AffinitySet a = new AffinitySet(1, generation, identitySet);
            group1.add(a);
            identitySet = new HashSet<Identity>();
            identitySet.add(new DummyIdentity("4"));
            identitySet.add(new DummyIdentity("5"));
            AffinitySet b = new AffinitySet(2, generation, identitySet);
            group1.add(b);
        }
        Set<AffinityGroup> group2 = new HashSet<AffinityGroup>();
        {
            HashSet<Identity> identitySet = new HashSet<Identity>();
            identitySet.add(new DummyIdentity("6"));
            identitySet.add(new DummyIdentity("7"));
            AffinitySet a = new AffinitySet(1, generation, identitySet);
            group2.add(a);
            identitySet = new HashSet<Identity>();
            identitySet.add(new DummyIdentity("8"));
            identitySet.add(new DummyIdentity("9"));
            AffinitySet b = new AffinitySet(3, generation, identitySet);
            
            group2.add(b);
        }
        Set<AffinityGroup> group3 = new HashSet<AffinityGroup>();
        {
            HashSet<Identity> identitySet = new HashSet<Identity>();
            identitySet.add(new DummyIdentity("10"));
            identitySet.add(new DummyIdentity("11"));
            AffinitySet a = new AffinitySet(4, generation, identitySet);
            group3.add(a);
        }

        HashSet<TestLPAClient> clients = new HashSet<TestLPAClient>();
        TestLPAClient client1 = new TestLPAClient(server, 10, 10, 3, group1);
        TestLPAClient client2 = new TestLPAClient(server, 20, 20, 4, group2);
        TestLPAClient client3 = new TestLPAClient(server, 30, 30, 2, group3);
        clients.add(client1);
        clients.add(client2);
        clients.add(client3);
        server.register(10, client1);
        server.register(20, client2);
        server.register(30, client3);

        long now = System.currentTimeMillis();
        Set<RelocatingAffinityGroup> groups = server.findAffinityGroups();
        System.out.printf("finished in %d milliseconds %n",
                          System.currentTimeMillis() - now);
        for (TestLPAClient client : clients) {
            assertFalse(client.failed);
            assertTrue(client.currentIter >= client.convergeCount);
        }
        for (AffinityGroup ag : groups) {
            Set<Identity> ids = ag.getIdentities();
            if (ag.getId() == 1) {
                assertEquals(5, ids.size());
                assertTrue(ids.contains(new DummyIdentity("1")));
                assertTrue(ids.contains(new DummyIdentity("2")));
                assertTrue(ids.contains(new DummyIdentity("3")));
                assertTrue(ids.contains(new DummyIdentity("6")));
                assertTrue(ids.contains(new DummyIdentity("7")));
            } else if (ag.getId() == 2) {
                assertEquals(2, ids.size());
                assertTrue(ids.contains(new DummyIdentity("4")));
                assertTrue(ids.contains(new DummyIdentity("5")));
            } else if (ag.getId() == 3) {
                assertEquals(2, ids.size());
                assertTrue(ids.contains(new DummyIdentity("8")));
                assertTrue(ids.contains(new DummyIdentity("9")));
            } else if (ag.getId() == 4) {
                assertEquals(2, ids.size());
                assertTrue(ids.contains(new DummyIdentity("10")));
                assertTrue(ids.contains(new DummyIdentity("11")));
            } else {
                fail("Unknown group found " + ag.getId());
            }
        }
    }

    @Test
    public void testLPAAlgorithm() throws Exception {
        // Create three clients.
        // We'll just use the server's watchdog - safe because we aren't
        // testing node failures here.
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    wdog, PartialToyBuilder.NODE2, props);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    wdog, PartialToyBuilder.NODE3, props);
        Set<RelocatingAffinityGroup> groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
    }

    @Test
    public void testLPAAlgorithmTwice() throws Exception {
        // Create three clients.
        // We'll just use the server's watchdog - safe because we aren't
        // testing node failures here.
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    wdog, PartialToyBuilder.NODE2, props);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    wdog, PartialToyBuilder.NODE3, props);
        Set<RelocatingAffinityGroup> groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
        groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
    }

    @Test
    public void testLPAAlgorithmOneClient() throws Exception {
        // We'll just use the server's watchdog - safe because we aren't
        // testing node failures here.
        //
        // We choose node 3 here because it has no reported conflicts with
        // other nodes, so it doesn't fail immediately because the other nodes
        // are down.
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    wdog, PartialToyBuilder.NODE3, props);
        Set<RelocatingAffinityGroup> groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
    }

    @Test
    public void testLPAAlgorithmNoClient() throws Exception {
        Set<RelocatingAffinityGroup> groups = server.findAffinityGroups();
        // We expect no groups to be found
        assertTrue(groups.isEmpty());
    }

    // Need to rework this test - we now log the error, should shut down
    // the local node.
    @Ignore
    @Test(expected = IOException.class)
    public void testServerShutdown() throws Throwable {
        // We'll just use the server's watchdog - safe because we aren't
        // testing node failures here.
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        server.shutdown();
        lp1.prepareAlgorithm(1);
    }

    @Test
    public void testRegister() throws Exception {
        final int nodeId = 10;
        TestLPAClient client1 = new TestLPAClient(server, nodeId, 10, 3,
               new HashSet<AffinityGroup>());
        Exporter<TestLPAClientIface> exporter =
                new Exporter<TestLPAClientIface>(TestLPAClientIface.class);
        exporter.export(client1, 0);
        try {
            server.register(nodeId, exporter.getProxy());

            TestLPAClientIface proxy =
                    (TestLPAClientIface) server.getLPAClientProxy(nodeId);
            assertFalse(proxy.finishedExchangeInfo());
            assertFalse(client1.finishedExchangeInfo);
            proxy.prepareAlgorithm(1);
            assertTrue(proxy.finishedExchangeInfo());
            assertTrue(client1.finishedExchangeInfo);
        } finally {
            exporter.unexport();
        }
    }

    @Test
    public void testRegisterTwice() throws Exception {
        final int nodeId = 10;
        TestLPAClient client1 = new TestLPAClient(server, nodeId, 10, 3,
               new HashSet<AffinityGroup>());
        Exporter<TestLPAClientIface> exporter =
                new Exporter<TestLPAClientIface>(TestLPAClientIface.class);
        exporter.export(client1, 0);
        try {
            server.register(nodeId, exporter.getProxy());
            // Should be harmless to register a second time
            server.register(nodeId, exporter.getProxy());
            TestLPAClientIface proxy =
                    (TestLPAClientIface) server.getLPAClientProxy(nodeId);
            assertFalse(proxy.finishedExchangeInfo());
            assertFalse(client1.finishedExchangeInfo);
            proxy.prepareAlgorithm(1);
            assertTrue(proxy.finishedExchangeInfo());
            assertTrue(client1.finishedExchangeInfo);
        } finally {
            exporter.unexport();
        }
    }

    @Test
    public void testGetUnknownClient() throws Exception {
        LPAClient proxy = server.getLPAClientProxy(222);
        assertNull(proxy);
    }
    
    @Test
    public void testServerShutdownTwice() throws Exception {
        // Should be no problem to call shutdown twice
        server.shutdown();
        server.shutdown();
    }

    /* -- Client tests -- */
    // Tests for idempotent behavior when key LPAClient methods are called
    // more than once by the server.
    @Test
    public void testAffinityGroupsTwice() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Set<AffinityGroup> groups1 = lp1.getAffinityGroups(1, false);
        Set<AffinityGroup> groups2 = lp1.getAffinityGroups(1, false);
        assertEquals(groups1.size(), groups2.size());
        // Because we haven't actually run the algorithm, I know the groups
        // correspond to the vertices on the node
        for (AffinityGroup g : groups1) {
            assertTrue(g.getId() == 1 || g.getId() == 2);
        }
        for (AffinityGroup g : groups2) {
            assertTrue(g.getId() == 1 || g.getId() == 2);
        }
    }

    @Test
    public void testPrepareTwice() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        lp1.prepareAlgorithm(1);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Thread.sleep(50);
        assertEquals(1, server.readyToBeginCount());
    }

    @Test
    public void testIterationTwice() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        lp1.startIteration(1);
        lp1.startIteration(1);
        count = 0;
        while (server.finishedIterationCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Thread.sleep(50); 
        assertEquals(1, server.finishedIterationCount());
    }

    // Client test:  run mismatch
    @Test(expected = IllegalArgumentException.class)
    public void testAffinityGroupsRunMismatch() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Set<AffinityGroup> groups1 = lp1.getAffinityGroups(2, false);
    }

    // Client test: iteration mismatch
    @Test
    public void testIterationMismatch() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        lp1.startIteration(1);
        count = 0;
        while (server.finishedIterationCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        lp1.startIteration(2);
        count = 0;
        while (server.finishedIterationCount() < 2) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        // this one should be ignored
        lp1.startIteration(1);
        Thread.sleep(50);
        assertEquals(2, server.finishedIterationCount());
    }

    @Test
    public void testCrossNodeData() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    wdog, PartialToyBuilder.NODE2, props);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    wdog, PartialToyBuilder.NODE3, props);

        long run = 1;
        lp1.prepareAlgorithm(run);
        lp2.prepareAlgorithm(run);
        lp3.prepareAlgorithm(run);
        // Wait until all 3 callbacks have occurred
        int count = 0;
        while (server.readyToBeginCount() < 3) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }

        // examine the node conflict map -it is public so I can get it here
        assertEquals(2, lp1.getNodeConflictMap().size());
        Set<Long> expected = new HashSet<Long>();
        expected.add(PartialToyBuilder.NODE2);
        expected.add(PartialToyBuilder.NODE3);
        assertTrue(expected.containsAll(lp1.getNodeConflictMap().keySet()));
        Map<Object, Long> map =
                lp1.getNodeConflictMap().get(PartialToyBuilder.NODE2);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj1"));
        map = lp1.getNodeConflictMap().get(PartialToyBuilder.NODE3);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj2"));

        assertEquals(1, lp2.getNodeConflictMap().size());
        map = lp2.getNodeConflictMap().get(PartialToyBuilder.NODE1);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj1"));

        assertEquals(1, lp3.getNodeConflictMap().size());
        map = lp3.getNodeConflictMap().get(PartialToyBuilder.NODE1);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj2"));

        //public Map<Long, Map<Object, Integer>> nodeConflictMap
        System.out.println("NODE1 nodeConflictMap");
        printNodeConflictMap(lp1);
        System.out.println("NODE2 nodeConflictMap");
        printNodeConflictMap(lp2);
        System.out.println("NODE3 nodeConflictMap");
        printNodeConflictMap(lp3);

        // Clear out old information
        lp1.getAffinityGroups(run, true);
        lp2.getAffinityGroups(run, true);
        lp3.getAffinityGroups(run, true);
        server.clear();
        System.out.println("Exchanging info a second time");
        run++;
        lp3.prepareAlgorithm(run);
        lp2.prepareAlgorithm(run);
        lp1.prepareAlgorithm(run);
        // Wait until all 3 callbacks have occurred
        count = 0;
        while (server.readyToBeginCount() < 3) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }

        // examine the node conflict map -it is public so I can get it here
        // Expect same result as above
        assertEquals(2, lp1.getNodeConflictMap().size());
        expected = new HashSet<Long>();
        expected.add(PartialToyBuilder.NODE2);
        expected.add(PartialToyBuilder.NODE3);
        assertTrue(expected.containsAll(lp1.getNodeConflictMap().keySet()));
        map = lp1.getNodeConflictMap().get(PartialToyBuilder.NODE2);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj1"));
        map = lp1.getNodeConflictMap().get(PartialToyBuilder.NODE3);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj2"));

        assertEquals(1, lp2.getNodeConflictMap().size());
        map = lp2.getNodeConflictMap().get(PartialToyBuilder.NODE1);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj1"));

        assertEquals(1, lp3.getNodeConflictMap().size());
        map = lp3.getNodeConflictMap().get(PartialToyBuilder.NODE1);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj2"));

        //public Map<Long, Map<Object, Integer>> nodeConflictMap
        System.out.println("NODE1 nodeConflictMap");
        printNodeConflictMap(lp1);
        System.out.println("NODE2 nodeConflictMap");
        printNodeConflictMap(lp2);
        System.out.println("NODE3 nodeConflictMap");
        printNodeConflictMap(lp3);
    }

    private void printNodeConflictMap(LabelPropagation lp) {

        for (Map.Entry<Long, Map<Object, Long>> entry :
             lp.getNodeConflictMap().entrySet())
        {
            StringBuilder sb1 = new StringBuilder();
            sb1.append(entry.getKey());
            sb1.append(":  ");
            for (Map.Entry<Object, Long> subEntry :
                 entry.getValue().entrySet())
            {
                sb1.append(subEntry.getKey() + "," + subEntry.getValue() + " ");
            }
            System.out.println(sb1.toString());
        }
    }

    @Test
    public void testCrossNodeLabels() throws Exception {
        // These match the ones from the partial toy builder
        Identity id1 = new DummyIdentity("1");
        Identity id2 = new DummyIdentity("2");
        Identity id3 = new DummyIdentity("3");
        Identity id4 = new DummyIdentity("4");
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    wdog, PartialToyBuilder.NODE1, props);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    wdog, PartialToyBuilder.NODE2, props);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    wdog, PartialToyBuilder.NODE3, props);
        lp1.prepareAlgorithm(1);
        lp2.prepareAlgorithm(1);
        lp3.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 3) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Set<Object> objIds = new HashSet<Object>();
        objIds.add("obj1");
        objIds.add("obj2");

        Map<Object, Map<Integer, List<Long>>> labelMap =
                lp1.getRemoteLabels(objIds);
        assertEquals(2, labelMap.size());
        assertTrue(labelMap.keySet().equals(objIds));
        Map<Integer, List<Long>> obj1Map = labelMap.get("obj1");
        assertEquals(2, obj1Map.size());
        for (Integer label : obj1Map.keySet()) {
            assertTrue(label.equals(1) || label.equals(2));
        }
        // Check returned weights
        List<Long> oneWeightList = obj1Map.get(1);
        assertEquals(1, oneWeightList.size());
        for (Long weight : oneWeightList) {
            assertEquals(2, weight.intValue());
        }
        List<Long> twoWeightList = obj1Map.get(2);
        assertEquals(1, twoWeightList.size());
        for (Long weight : twoWeightList) {
            assertEquals(2, weight.intValue());
        }
        Map<Integer, List<Long>> obj2Map = labelMap.get("obj2");
        assertEquals(1, obj2Map.size());
        for (Integer label : obj2Map.keySet()) {
            assertTrue(label.equals(2));
        }
        twoWeightList = obj2Map.get(2);
        assertEquals(1, twoWeightList.size());
        for (Long weight : twoWeightList) {
            assertEquals(1, weight.intValue());
        }


        // Call the private implementation to update the remote label
        // maps, and check it.  We do this, rather than running an iteration,
        // because we don't want the labels to change.
        Method updateRemoteLabelsMethod =
            UtilReflection.getMethod(LabelPropagation.class,
                                     "updateRemoteLabels");

        updateRemoteLabelsMethod.invoke(lp1);
        updateRemoteLabelsMethod.invoke(lp2);
        updateRemoteLabelsMethod.invoke(lp3);

        // node 1
        ConcurrentMap<Identity, Map<Integer, Long>> rlm =
                lp1.getRemoteLabelMap();
        assertEquals(2, rlm.size());
        for (Identity id : rlm.keySet()) {
            assertTrue (id.equals(id1) || id.equals(id2));
        }
        Map<Integer, Long> labelWeight = rlm.get(id1);
        assertEquals(1, labelWeight.size());
        Long weight = labelWeight.get(3);
        assertTrue(weight != null);
        assertEquals(1, weight.intValue());

        labelWeight = rlm.get(id2);
        assertEquals(2, labelWeight.size());
        weight = labelWeight.get(3);
        assertTrue(weight != null);
        assertEquals(1, weight.intValue());
        weight = labelWeight.get(4);
        assertTrue(weight != null);
        assertEquals(1, weight.intValue());

        // node 2
        rlm = lp2.getRemoteLabelMap();
        assertEquals(1, rlm.size());
        for (Identity id : rlm.keySet()) {
            assertEquals(id3, id);
        }
        labelWeight = rlm.get(id3);
        assertEquals(2, labelWeight.size());
        weight = labelWeight.get(1);
        assertTrue(weight != null);
        assertEquals(1, weight.intValue());
        weight = labelWeight.get(2);
        assertTrue(weight != null);
        assertEquals(1, weight.intValue());

        // node 3
        rlm = lp3.getRemoteLabelMap();
        assertEquals(1, rlm.size());
        for (Identity id : rlm.keySet()) {
            assertEquals(id4, id);
        }
        labelWeight = rlm.get(id4);
        assertEquals(1, labelWeight.size());
        weight = labelWeight.get(2);
        assertTrue(weight != null);
        assertEquals(1, weight.intValue());
    }

    public interface TestLPAClientIface extends LPAClient {
        boolean finishedExchangeInfo() throws IOException;
    }
    
    private class TestLPAClient implements TestLPAClientIface {
        private final long sleepTime;
        private final long nodeId;
        private final int convergeCount;
        private final Set<AffinityGroup> result;
        private final LPAServer server;

        boolean failed = false;
        boolean startedExchangeInfo = false;
        boolean finishedExchangeInfo = false;
        boolean startedStartIter = false;
        boolean finishedStartIter = false;
        int currentIter = -1;

        public TestLPAClient(LPAServer server, long nodeId, long sleepTime, 
                int convergeCount, Set<AffinityGroup> result)
        {
            this.server = server;
            this.nodeId = nodeId;
            this.convergeCount = convergeCount;
            this.sleepTime = sleepTime;
            this.result = result;
        }

        public boolean finishedExchangeInfo() {
            return finishedExchangeInfo;
        }
        /** {@inheritDoc} */
        public Set<AffinityGroup> getAffinityGroups(long runNumber,
                                                    boolean done)
                throws IOException
        {
            return result;
        }

        /** {@inheritDoc} */
        public void prepareAlgorithm(long runNumber) throws IOException {
            startedExchangeInfo = true;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ex) {
                throw new IOException("failed", ex);
            }
            finishedExchangeInfo = true;
            server.readyToBegin(nodeId, false);
        }

        /** {@inheritDoc} */
        public void startIteration(int iteration) throws IOException {
            // Should not be called if we haven't completed exchanging info
            failed = failed || !finishedExchangeInfo;
            // Should not be called if we are in the middle of an iteration
            failed = failed || startedStartIter;
            if (!failed) {
                currentIter = iteration;
                startedStartIter = true;
                finishedStartIter = false;
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    throw new IOException("failed", ex);
                }
                startedStartIter = false;
                finishedStartIter = true;
            }
            boolean converged = currentIter >= convergeCount;
            server.finishedIteration(nodeId, converged, failed, currentIter);
        }

        /** {@inheritDoc} */
        public void notifyCrossNodeEdges(Collection<Object> objIds, long nodeId)
                throws IOException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        /** {@inheritDoc} */
        public Map<Object, Map<Integer, List<Long>>> getRemoteLabels(
                Collection<Object> objIds) throws IOException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
        /** {@inheritDoc} */
        public void shutdown() throws IOException {
            return;
        }
        /** {@inheritDoc} */
        public void enable() throws IOException {
            return;
        }
        /** {@inheritDoc} */
        public void disable() throws IOException {
            return;
        }
    }

    /**
     * A very simple test server that counts the number of client callbacks
     * for preparation completed and finished iterations.
     */
    private static class TestLPAServer extends LabelPropagationServer {
        private AtomicInteger finishedIterationCount = new AtomicInteger(0);
        private AtomicInteger readyToBeginCount = new AtomicInteger(0);

        public TestLPAServer(ProfileCollector col, 
                             WatchdogService wdog,
                             Properties properties)
                throws IOException
        {
            super(col, wdog, properties);
        }

        /** {@inheritDoc} */
        public void finishedIteration(long nodeId, boolean converged,
                boolean failed, int iteration) throws IOException
        {
            finishedIterationCount.incrementAndGet();
            super.finishedIteration(nodeId, converged, failed, iteration);
        }

        /** {@inheritDoc} */
        public void readyToBegin(long nodeId, boolean failed) throws IOException
        {
            readyToBeginCount.incrementAndGet();
            super.readyToBegin(nodeId, failed);
        }

        public int finishedIterationCount() {
            return finishedIterationCount.get();
        }
        public int readyToBeginCount() {
            return readyToBeginCount.get();
        }
        public void clear() {
            finishedIterationCount.getAndSet(0);
            readyToBeginCount.getAndSet(0);
        }
    }
    // Simple builder spread across 3 nodes
    private class PartialToyBuilder extends AbstractTestGraphBuilder 
            implements DLPAGraphBuilder
    {
        private final ConcurrentMap<Long, Map<Object, Long>> conflictMap =
                new ConcurrentHashMap<Long, Map<Object, Long>>();
        private final ConcurrentMap<Object, Map<Identity, Long>> objUseMap =
                new ConcurrentHashMap<Object, Map<Identity, Long>>();

        static final long NODE1 = 1;
        static final long NODE2 = 2;
        static final long NODE3 = 3;
        // location of identities
        // node1: 1,2
        // node2: 3
        // node3: 4, 5
        //
        // ids 1,2,3 used obj1
        // ids 2,4 used obj2
        // ids 4,5 used obj3
        public PartialToyBuilder(long node) {
            super(createPartialToyBuilderGraph(node));
            if (node == NODE1) {
                // Create a partial graph
                Identity[] idents = {new DummyIdentity("1"),
                                     new DummyIdentity("2")};
                // Obj uses
                Map<Identity, Long> tempMap =
                        new HashMap<Identity, Long>();
                tempMap.put(idents[0], 2L);
                tempMap.put(idents[1], 2L);
                objUseMap.put("obj1", tempMap);
                tempMap =  new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("obj2", tempMap);

                // conflicts - data cache evictions due to conflict
                HashMap<Object, Long> conflict = new HashMap<Object, Long>();
                conflict.put("obj2", 1L);
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE2) {
                // Create a partial graph
                Identity ident = new DummyIdentity("3");
                
                // Obj uses
                Map<Identity, Long> tempMap = new HashMap<Identity, Long>();
                tempMap.put(ident, 1L);
                objUseMap.put("obj1", tempMap);

                // conflicts - data cache evictions due to conflict
                HashMap<Object, Long> conflict = new HashMap<Object, Long>();
                conflict.put("obj1", 1L);
                conflictMap.put(NODE1, conflict);
            } else if (node == NODE3) {
                Identity[] idents = {new DummyIdentity("4"),
                                     new DummyIdentity("5")};

                // Obj uses
                Map<Identity, Long> tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[0], 1L);
                tempMap.put(idents[1], 1L);
                objUseMap.put("obj3", tempMap);
                tempMap =  new HashMap<Identity, Long>();
                tempMap.put(idents[0], 1L);
                objUseMap.put("obj2", tempMap);
                
                // conflicts - data cache evictions due to conflict
                // none on this node
            }
        }

        /** {@inheritDoc} */
        public Map<Long, Map<Object, Long>> getConflictMap() {
            return conflictMap;
        }

        /** {@inheritDoc} */
        public void removeNode(long nodeId) {
            conflictMap.remove(nodeId);
        }

        /** {@inheritDoc} */
        public Map<Object, Map<Identity, Long>> getObjectUseMap() {
            return objUseMap;
        }
    }

    //
    private UndirectedGraph<LabelVertex, WeightedEdge>
            createPartialToyBuilderGraph(long node)
    {
        UndirectedGraph<LabelVertex, WeightedEdge> graph =
                new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

        if (node == PartialToyBuilder.NODE1) {
            // Create a partial graph
            Identity[] idents = {new DummyIdentity("1"),
                                 new DummyIdentity("2")};
            LabelVertex[] nodes = new LabelVertex[2];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = new LabelVertex(idents[i]);
                graph.addVertex(nodes[i]);
            }
            graph.addEdge(new WeightedEdge(2), nodes[0], nodes[1]);

        } else if (node == PartialToyBuilder.NODE2) {
            // Create a partial graph
            Identity ident = new DummyIdentity("3");
            LabelVertex ver = new LabelVertex(ident);
            graph.addVertex(ver);

        } else if (node == PartialToyBuilder.NODE3) {
            Identity[] idents = {new DummyIdentity("4"),
                                 new DummyIdentity("5")};
            LabelVertex[] nodes = new LabelVertex[2];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = new LabelVertex(idents[i]);
                graph.addVertex(nodes[i]);
            }
            graph.addEdge(new WeightedEdge(), nodes[0], nodes[1]);
        }
        return Graphs.unmodifiableUndirectedGraph(graph);
    }
}
