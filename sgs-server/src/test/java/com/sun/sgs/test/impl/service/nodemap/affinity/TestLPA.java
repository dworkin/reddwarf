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

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.AffinitySet;
import com.sun.sgs.impl.service.nodemap.affinity.graph.dlpa.GraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LPAClient;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LPAServer;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    /** The default initial unique port for this test suite. */
    private final static int DEFAULT_PORT = 20000;

    /** The property that can be used to select an initial port. */
    private final static String PORT_PROPERTY = "test.sgs.port";

    /** Max number of times we'll call Thread.sleep in a loop. */
    private final static int MAX_SLEEP_COUNT = 50;

    /** The next unique port to use for this test suite. */
    private final static AtomicInteger nextUniquePort;

    static {
        Integer systemPort = Integer.getInteger(PORT_PROPERTY);
        int port = systemPort == null ? DEFAULT_PORT
                                      : systemPort.intValue();
        nextUniquePort = new AtomicInteger(port);
    }

    private TestLPAServer server;
    private String localHost;
    private int serverPort;
    private ProfileCollector collector;

    @Before
    public void setup() throws Exception {
        Properties props = new Properties();
        serverPort = getNextUniquePort();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                   String.valueOf(serverPort));
        collector = new ProfileCollectorImpl(ProfileLevel.MAX, props, null);
        server = new TestLPAServer(collector, props);
        localHost = InetAddress.getLocalHost().getHostName();
    }

    @After
    public void shutdown() throws Exception {
        if (server != null) {
            server.shutdown();
            server = null;
            collector.shutdown();
        }
    }
    /**
     * Returns a unique port number.  Note that the ports are only unique
     * within the current process.
     */
    public static int getNextUniquePort() {
        return nextUniquePort.incrementAndGet();
    }

    /* -- Server tests -- */
    @Test
    public void testDistributedFramework() throws Exception {
        Collection<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        {
            AffinitySet a = new AffinitySet(1);
            a.addIdentity(new DummyIdentity("1"));
            a.addIdentity(new DummyIdentity("2"));
            a.addIdentity(new DummyIdentity("3"));
            group1.add(a);
            AffinitySet b = new AffinitySet(2);
            b.addIdentity(new DummyIdentity("4"));
            b.addIdentity(new DummyIdentity("5"));
            group1.add(b);
        }
        Collection<AffinityGroup> group2 = new HashSet<AffinityGroup>();
        {
            AffinitySet a = new AffinitySet(1);
            a.addIdentity(new DummyIdentity("6"));
            a.addIdentity(new DummyIdentity("7"));
            group2.add(a);
            AffinitySet b = new AffinitySet(3);
            b.addIdentity(new DummyIdentity("8"));
            b.addIdentity(new DummyIdentity("9"));
            group2.add(b);
        }
        Collection<AffinityGroup> group3 = new HashSet<AffinityGroup>();
        {
            AffinitySet a = new AffinitySet(4);
            a.addIdentity(new DummyIdentity("10"));
            a.addIdentity(new DummyIdentity("11"));
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
        Collection<AffinityGroup> groups = server.findAffinityGroups();
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
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, serverPort, true, 1);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, serverPort, true, 1);
        Collection<AffinityGroup> groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
    }

    @Test
    public void testLPAAlgorithmTwice() throws Exception {
        // Create three clients.
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, serverPort, true, 1);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, serverPort, true, 1);
        Collection<AffinityGroup> groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
        groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
    }

    @Test
    public void testLPAAlgorithmOneClient() throws Exception {
        // We choose node 3 here because it has no reported conflicts with
        // other nodes, so it doesn't fail immediately because the other nodes
        // are down.
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, serverPort, true, 1);
        Collection<AffinityGroup> groups = server.findAffinityGroups();
        assertTrue(groups.size() != 0);
    }

    @Test
    public void testLPAAlgorithmNoClient() throws Exception {
        Collection<AffinityGroup> groups = server.findAffinityGroups();
        assertEquals(0, groups.size());
    }

    // Need to rework this test - we now log the error, should shut down
    // the local node.
    @Ignore
    @Test(expected = IOException.class)
    public void testServerShutdown() throws Throwable {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
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
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Collection<AffinityGroup> groups1 = lp1.getAffinityGroups(1, false);
        Collection<AffinityGroup> groups2 = lp1.getAffinityGroups(1, false);
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
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
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
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
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
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
        lp1.prepareAlgorithm(1);
        int count = 0;
        while (server.readyToBeginCount() < 1) {
            Thread.sleep(5);
            if (++count > MAX_SLEEP_COUNT) {
                fail("Too much time sleeping");
            }
        }
        Collection<AffinityGroup> groups1 = lp1.getAffinityGroups(2, false);
    }

    // Client test: iteration mismatch
    @Test
    public void testIterationMismatch() throws Exception {
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
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
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, serverPort, true, 1);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, serverPort, true, 1);

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
        Map<Object, AtomicLong> map =
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

        for (Map.Entry<Long, ConcurrentMap<Object, AtomicLong>> entry :
             lp.getNodeConflictMap().entrySet())
        {
            StringBuilder sb1 = new StringBuilder();
            sb1.append(entry.getKey());
            sb1.append(":  ");
            for (Map.Entry<Object, AtomicLong> subEntry :
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
                    PartialToyBuilder.NODE1, localHost, serverPort, true, 1);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, serverPort, true, 1);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, serverPort, true, 1);
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
//            assertTrue(label.equals(id1.getName().hashCode()) ||
//                       label.equals(id2.getName().hashCode()));
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
//            assertTrue(label.equals(id2.getName().hashCode()));
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
        private final Collection<AffinityGroup> result;
        private final LPAServer server;

        boolean failed = false;
        boolean startedExchangeInfo = false;
        boolean finishedExchangeInfo = false;
        boolean startedStartIter = false;
        boolean finishedStartIter = false;
        int currentIter = -1;

        public TestLPAClient(LPAServer server, long nodeId, long sleepTime, 
                int convergeCount, Collection<AffinityGroup> result)
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
        public Collection<AffinityGroup> getAffinityGroups(long runNumber,
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
        public void removeNode(long nodeId) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        /** {@inheritDoc} */
        public void crossNodeEdges(Collection<Object> objIds, long nodeId)
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
    }

    /**
     * A very simple test server that counts the number of client callbacks
     * for preparation completed and finished iterations.
     */
    private static class TestLPAServer extends LabelPropagationServer {
        private AtomicInteger finishedIterationCount = new AtomicInteger(0);
        private AtomicInteger readyToBeginCount = new AtomicInteger(0);

        public TestLPAServer(ProfileCollector col, Properties properties)
                throws IOException
        {
            super(col, properties);
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
    private class PartialToyBuilder implements GraphBuilder {
        private final UndirectedSparseGraph<LabelVertex, WeightedEdge> graph;
        private final ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
                conflictMap;
        private final ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
                objUseMap;

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
            super();
            graph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();
            objUseMap = new ConcurrentHashMap<Object, 
                                ConcurrentMap<Identity, AtomicLong>>();
            conflictMap = new ConcurrentHashMap<Long,
                                ConcurrentMap<Object, AtomicLong>>();

            if (node == NODE1) {
                // Create a partial graph
                Identity[] idents = {new DummyIdentity("1"),
                                     new DummyIdentity("2")};
                LabelVertex[] nodes = new LabelVertex[2];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = new LabelVertex(idents[i]);
                    graph.addVertex(nodes[i]);
                }
                graph.addEdge(new WeightedEdge(2), nodes[0], nodes[1]);


                // Obj uses
                ConcurrentMap<Identity, AtomicLong> tempMap =
                        new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[0], new AtomicLong(2));
                tempMap.put(idents[1], new AtomicLong(2));
                objUseMap.put("obj1", tempMap);
                tempMap =  new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("obj2", tempMap);

                // conflicts - data cache evictions due to conflict
                ConcurrentHashMap<Object, AtomicLong> conflict =
                        new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("obj2", new AtomicLong(1));
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE2) {
                // Create a partial graph
                Identity ident = new DummyIdentity("3");
                LabelVertex ver = new LabelVertex(ident);
                graph.addVertex(ver);

                // Obj uses
                ConcurrentMap<Identity, AtomicLong> tempMap =
                        new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(ident, new AtomicLong(1));
                objUseMap.put("obj1", tempMap);

                // conflicts - data cache evictions due to conflict
                ConcurrentHashMap<Object, AtomicLong> conflict =
                        new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("obj1", new AtomicLong(1));
                conflictMap.put(NODE1, conflict);
            } else if (node == NODE3) {
                Identity[] idents = {new DummyIdentity("4"),
                                     new DummyIdentity("5")};
                LabelVertex[] nodes = new LabelVertex[2];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = new LabelVertex(idents[i]);
                    graph.addVertex(nodes[i]);
                }
                graph.addEdge(new WeightedEdge(), nodes[0], nodes[1]);

                // Obj uses
                ConcurrentMap<Identity, AtomicLong> tempMap =
                        new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[0], new AtomicLong(1));
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("obj3", tempMap);
                tempMap =  new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[0], new AtomicLong(1));
                objUseMap.put("obj2", tempMap);

                // conflicts - data cache evictions due to conflict
                // none on this node
            }
        }

        /** {@inheritDoc} */
        public UndirectedSparseGraph<LabelVertex, WeightedEdge>
                getAffinityGraph()
        {
            return graph;
        }

        /** {@inheritDoc} */
        public Runnable getPruneTask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        /** {@inheritDoc} */
        public ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
                getConflictMap()
        {
            return conflictMap;
        }

        /** {@inheritDoc} */
        public void removeNode(long nodeId) {
            conflictMap.remove(nodeId);
        }

        /** {@inheritDoc} */
        public ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
                getObjectUseMap()
        {
            return objUseMap;
        }

        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }
    }
}
