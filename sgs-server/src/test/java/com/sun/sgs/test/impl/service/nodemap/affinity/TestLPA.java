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
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupImpl;
import com.sun.sgs.impl.service.nodemap.affinity.GraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.Graphs;
import com.sun.sgs.impl.service.nodemap.affinity.LPAClient;
import com.sun.sgs.impl.service.nodemap.affinity.LPAServer;
import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedEdge;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.tools.test.FilteredNameRunner;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * 
 */
@RunWith(FilteredNameRunner.class)
public class TestLPA {
    /** The default initial unique port for this test suite. */
    private final static int DEFAULT_PORT = 20000;

    /** The property that can be used to select an initial port. */
    private final static String PORT_PROPERTY = "test.sgs.port";

    /** The next unique port to use for this test suite. */
    private static AtomicInteger nextUniquePort;

    static {
        Integer systemPort = Integer.getInteger(PORT_PROPERTY);
        int port = systemPort == null ? DEFAULT_PORT
                                      : systemPort.intValue();
        nextUniquePort = new AtomicInteger(port);
    }

    private LabelPropagationServer server;
    private String localHost;

    @Before
    public void setup() throws Exception {
        Properties props = new Properties();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                   String.valueOf(getNextUniquePort()));
        server = new LabelPropagationServer(props);
        localHost = InetAddress.getLocalHost().getHostName();
    }

    @After
    public void shutdown() throws Exception {
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }
    /**
     * Returns a unique port number.  Note that the ports are only unique
     * within the current process.
     */
    public static int getNextUniquePort() {
        return nextUniquePort.incrementAndGet();
    }

    
    @Test
    public void testDistributedFramework() throws Exception {
        // Create a server, and add a few "nodes"
        Collection<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        {
            AffinityGroupImpl a = new AffinityGroupImpl(1);
            a.addIdentity(new DummyIdentity("1"));
            a.addIdentity(new DummyIdentity("2"));
            a.addIdentity(new DummyIdentity("3"));
            group1.add(a);
            AffinityGroupImpl b = new AffinityGroupImpl(2);
            b.addIdentity(new DummyIdentity("4"));
            b.addIdentity(new DummyIdentity("5"));
            group1.add(b);
        }
        Collection<AffinityGroup> group2 = new HashSet<AffinityGroup>();
        {
            AffinityGroupImpl a = new AffinityGroupImpl(1);
            a.addIdentity(new DummyIdentity("6"));
            a.addIdentity(new DummyIdentity("7"));
            group2.add(a);
            AffinityGroupImpl b = new AffinityGroupImpl(3);
            b.addIdentity(new DummyIdentity("8"));
            b.addIdentity(new DummyIdentity("9"));
            group2.add(b);
        }
        Collection<AffinityGroup> group3 = new HashSet<AffinityGroup>();
        {
            AffinityGroupImpl a = new AffinityGroupImpl(4);
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
    public void testCrossNodeData() throws Exception {
        // Create our server and three clients.
        int port = nextUniquePort.get();

        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, port, true, 1, 0);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, port, true, 1, 0);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, port, true, 1, 0);

        lp1.prepareAlgorithm();
        lp2.prepareAlgorithm();
        lp3.prepareAlgorithm();

        // Wait an appropriate time - want server to be called back 3 times
        Thread.sleep(1000);

        // examine the node conflict map -it is public so I can get it here
        assertEquals(2, lp1.getNodeConflictMap().size());
        Set<Long> expected = new HashSet<Long>();
        expected.add(PartialToyBuilder.NODE2);
        expected.add(PartialToyBuilder.NODE3);
        assertTrue(expected.containsAll(lp1.getNodeConflictMap().keySet()));
        Map<Object, Integer> map =
                lp1.getNodeConflictMap().get(PartialToyBuilder.NODE2);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj1"));
        map = lp1.getNodeConflictMap().get(PartialToyBuilder.NODE3);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("obj2"));
        // JANE not checking weights

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
        lp1.affinityGroups(true);
        lp2.affinityGroups(true);
        lp3.affinityGroups(true);

        System.out.println("Exchanging info a second time");
        lp3.prepareAlgorithm();
        lp2.prepareAlgorithm();
        lp1.prepareAlgorithm();

        // Wait an appropriate time - want server to be called back 3 times
        Thread.sleep(1000);

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
        // JANE not checking weights

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
        
        for (Map.Entry<Long, ConcurrentHashMap<Object, Integer>> entry :
             lp.getNodeConflictMap().entrySet())
        {
            StringBuilder sb1 = new StringBuilder();
            sb1.append(entry.getKey());
            sb1.append(":  ");
            for (Map.Entry<Object, Integer> subEntry :
                 entry.getValue().entrySet())
            {
                sb1.append(subEntry.getKey() + "," + subEntry.getValue() + " ");
            }
            System.out.println(sb1.toString());
        }
    }

    @Test
    public void testCrossNodeLabels() throws Exception {
        // Create our server and three clients.
        int port = nextUniquePort.get();

        // These match the ones from the partial toy builder
        Identity id1 = new DummyIdentity("1");
        Identity id2 = new DummyIdentity("2");
        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, port, true, 1, 0);
        lp1.initializeLPARun();

        Set<Object> objIds = new HashSet<Object>();
        objIds.add("obj1");
        objIds.add("obj2");

        Map<Object, Map<Integer, Long>> labelMap =
                lp1.getRemoteLabels(objIds);
        assertEquals(2, labelMap.size());
        assertTrue(labelMap.keySet().equals(objIds));
        Map<Integer, Long> obj1Map = labelMap.get("obj1");
        assertEquals(2, obj1Map.size());
        for (Integer label : obj1Map.keySet()) {
            assertTrue(label.equals(1) || label.equals(2));
//            assertTrue(label.equals(id1.getName().hashCode()) ||
//                       label.equals(id2.getName().hashCode()));
        }
        Map<Integer, Long> obj2Map = labelMap.get("obj2");
        assertEquals(1, obj2Map.size());
        for (Integer label : obj2Map.keySet()) {
            assertTrue(label.equals(2));
//            assertTrue(label.equals(id2.getName().hashCode()));
        }
    }

    @Test
    public void testLPAAlgorithm() throws Exception {
        // Create our server and three clients.
        int port = nextUniquePort.get();

        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, port, true, 1, 0);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, port, true, 1, 0);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, port, true, 1, 0);
        Collection<AffinityGroup> groups = server.findAffinityGroups();
    }

    @Test
    public void testLPADistributedZach() throws Exception {
        int RUNS = 500;
        int port = nextUniquePort.get();
        LabelPropagation lp1 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE1),
                    DistributedZachBuilder.NODE1, localHost, port, true, 1, 0);
        LabelPropagation lp2 =
            new LabelPropagation(new DistributedZachBuilder(
                DistributedZachBuilder.NODE2),
                    DistributedZachBuilder.NODE2, localHost, port, true, 1, 0);
        LabelPropagation lp3 =
            new LabelPropagation(new DistributedZachBuilder(
                DistributedZachBuilder.NODE3),
                    DistributedZachBuilder.NODE3, localHost, port, true, 1, 0);

        double avgMod  = 0.0;
        double maxMod = 0.0;
        double minMod = 1.0;
        for (int i = 0; i < RUNS; i++) {
            Collection<AffinityGroup> groups = server.findAffinityGroups();
            double mod =
                Graphs.calcModularity(new ZachBuilder().getAffinityGraph(),
                                      groups);
            avgMod = avgMod + mod;
            maxMod = Math.max(maxMod, mod);
            minMod = Math.min(minMod, mod);
            System.out.printf("run %d, modularity: %.4f \n", i, mod);
        }
        System.out.printf("(%d runs): " +
          " avg modularity: %.4f, " +
          " modularity range [%.4f - %.4f] %n",
          RUNS,
          avgMod/(double) RUNS,
          minMod, maxMod);
    }

    private class TestLPAClient implements LPAClient {
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

        /** {@inheritDoc} */
        public Collection<AffinityGroup> affinityGroups(boolean done) 
                throws IOException
        {
            return result;
        }

        /** {@inheritDoc} */
        public void prepareAlgorithm() throws IOException {
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
        public Collection<Object> crossNodeEdges(Collection<Object> objIds, long nodeId)
                throws IOException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        /** {@inheritDoc} */
        public Map<Object, Map<Integer, Long>> getRemoteLabels(
                Collection<Object> objIds) throws IOException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    // Simple builder spread across 3 nodes
    private class PartialToyBuilder implements GraphBuilder {
        private final Graph<LabelVertex, WeightedEdge> graph;
        private final 
            ConcurrentHashMap<Long, ConcurrentHashMap<Object, Integer>>
            conflictMap;
        private final Map<Object, Map<Identity, Long>> objUseMap;

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
            graph = new UndirectedSparseMultigraph<LabelVertex, WeightedEdge>();
            objUseMap = new ConcurrentHashMap<Object, Map<Identity, Long>>();
            conflictMap = new ConcurrentHashMap<Long, 
                                    ConcurrentHashMap<Object, Integer>>();

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
                Map<Identity, Long> tempMap =
                        new HashMap<Identity, Long>();
                tempMap.put(idents[0], 2L);
                tempMap.put(idents[1], 2L);
                objUseMap.put("obj1", tempMap);
                tempMap =  new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("obj2", tempMap);

                // conflicts - data cache evictions due to conflict
                ConcurrentHashMap<Object, Integer> conflict =
                        new ConcurrentHashMap<Object, Integer>();
                conflict.put("obj1", 1);
                conflictMap.put(NODE2, conflict);
                conflict = new ConcurrentHashMap<Object, Integer>();
                conflict.put("obj2", 1);
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE2) {
                // Create a partial graph
                Identity ident = new DummyIdentity("3");
                LabelVertex ver = new LabelVertex(ident);
                graph.addVertex(ver);

                // Obj uses
                Map<Identity, Long> tempMap =
                        new HashMap<Identity, Long>();
                tempMap.put(ident, 2L);
                objUseMap.put("obj1", tempMap);

                // conflicts - data cache evictions due to conflict
                ConcurrentHashMap<Object, Integer> conflict =
                        new ConcurrentHashMap<Object, Integer>();
                conflict.put("obj1", 1);
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
                Map<Identity, Long> tempMap =
                        new HashMap<Identity, Long>();
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
        public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
            return graph;
        }

        /** {@inheritDoc} */
        public Runnable getPruneTask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        /** {@inheritDoc} */
        public ConcurrentHashMap<Long, ConcurrentHashMap<Object, Integer>>
                getConflictMap()
        {
            return conflictMap;
        }

        /** {@inheritDoc} */
        public Map<Object, Map<Identity, Long>> getObjectUseMap() {
            return objUseMap;
        }

        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }
    }

    // A Zachary karate club which is distributed over 3 nodes, round-robin.
    private class DistributedZachBuilder implements GraphBuilder {
        private final Graph<LabelVertex, WeightedEdge> graph;
        private final 
            ConcurrentHashMap<Long, ConcurrentHashMap<Object, Integer>>
            conflictMap;
        private final Map<Object, Map<Identity, Long>> objUseMap;

        static final long NODE1 = 1;
        static final long NODE2 = 2;
        static final long NODE3 = 3;
        // location of identities
        // node1: 1,4,7,10,13,16,19,22,25,28,31,34
        // node2: 2,5,8,11,14,17,20,23,26,29,32
        // node3: 3,6,9,12,15,18,21,24,27,30,33
        // using 44 objects, making sure weights stay at one (with higher
        // weights, could use fewer objects)

        public DistributedZachBuilder(long node) {
            super();
            graph = new UndirectedSparseMultigraph<LabelVertex, WeightedEdge>();
            objUseMap = new ConcurrentHashMap<Object, Map<Identity, Long>>();
            conflictMap = new ConcurrentHashMap<Long,
                                    ConcurrentHashMap<Object, Integer>>();
            LabelVertex[] nodes = new LabelVertex[35];
            DummyIdentity[] idents = new DummyIdentity[35];
            int nodeAsInt = (int) node;
            // Create a partial graph
            for (int i = nodeAsInt; i < nodes.length; i+=3) {
                // Add identities 1, 4, etc.
                idents[i] = new DummyIdentity(String.valueOf(i));
                nodes[i] = new LabelVertex(idents[i]);
                graph.addVertex(nodes[i]);
            }
            // Obj uses
            Map<Identity, Long> tempMap = new HashMap<Identity, Long>();
            if (node == NODE1) {
                // Update edges
                graph.addEdge(new WeightedEdge(), nodes[4], nodes[1]);
                graph.addEdge(new WeightedEdge(), nodes[7], nodes[1]);
                graph.addEdge(new WeightedEdge(), nodes[13], nodes[1]);
                graph.addEdge(new WeightedEdge(), nodes[13], nodes[4]);
                graph.addEdge(new WeightedEdge(), nodes[22], nodes[1]);
                graph.addEdge(new WeightedEdge(), nodes[28], nodes[25]);
                graph.addEdge(new WeightedEdge(), nodes[34], nodes[10]);
                graph.addEdge(new WeightedEdge(), nodes[34], nodes[16]);
                graph.addEdge(new WeightedEdge(), nodes[34], nodes[19]);
                graph.addEdge(new WeightedEdge(), nodes[34], nodes[28]);
                graph.addEdge(new WeightedEdge(), nodes[34], nodes[31]);

                // Obj uses
                tempMap.put(idents[1], 1L);
                tempMap.put(idents[4], 4L);
                objUseMap.put("o1", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                tempMap.put(idents[7], 1L);
                objUseMap.put("o2", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o3", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[7], 1L);
                objUseMap.put("o4", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o5", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o6", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[10], 1L);
                objUseMap.put("o7", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o8", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o11", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                tempMap.put(idents[13], 1L);
                objUseMap.put("o12", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[4], 1L);
                tempMap.put(idents[13], 1L);
                objUseMap.put("o13", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[7], 1L);
                objUseMap.put("o15", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o16", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o18", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[22], 1L);
                tempMap.put(idents[1], 1L);
                objUseMap.put("o20", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[22], 1L);
                objUseMap.put("o21", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[25], 1L);
                objUseMap.put("o23", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[28], 1L);
                objUseMap.put("o24", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[28], 1L);
                objUseMap.put("o25", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[28], 1L);
                tempMap.put(idents[25], 1L);
                objUseMap.put("o26", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[31], 1L);
                objUseMap.put("o30", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[31], 1L);
                tempMap.put(idents[34], 1L);
                objUseMap.put("o31", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[1], 1L);
                objUseMap.put("o32", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[16], 1L);
                objUseMap.put("o35", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[19], 1L);
                objUseMap.put("o36", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                tempMap.put(idents[10], 1L);
                objUseMap.put("o40", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o41", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o42", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                tempMap.put(idents[16], 1L);
                objUseMap.put("o43", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                tempMap.put(idents[19], 1L);
                objUseMap.put("o44", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o45", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o46", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o47", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o48", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o49", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                tempMap.put(idents[28], 1L);
                objUseMap.put("o50", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o51", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o52", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                objUseMap.put("o53", tempMap);

 
                // conflicts - data cache evictions due to conflict
                // just guessing
                ConcurrentHashMap<Object, Integer> conflict =
                        new ConcurrentHashMap<Object, Integer>();
                conflict.put("o1", 1);
                conflict.put("o2", 1);
                conflict.put("o18", 1);
                conflict.put("o21", 1);
                conflict.put("o41", 1);
                conflict.put("o45", 1);
                conflict.put("o47", 1);
                conflictMap.put(NODE2, conflict);
                conflict = new ConcurrentHashMap<Object, Integer>();
                conflict.put("o1", 1);
                conflict.put("o5", 1);
                conflict.put("o7", 1);
                conflict.put("o11", 1);
                conflict.put("o31", 1);
                conflict.put("o35", 1);
                conflict.put("o36", 1);
                conflict.put("o42", 1);
                conflict.put("o46", 1);
                conflict.put("o48", 1);
                conflict.put("o49", 1);
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE2) {
                graph.addEdge(new WeightedEdge(), nodes[8], nodes[2]);
                graph.addEdge(new WeightedEdge(), nodes[11], nodes[5]);
                graph.addEdge(new WeightedEdge(), nodes[14], nodes[2]);
                graph.addEdge(new WeightedEdge(), nodes[20], nodes[2]);
                graph.addEdge(new WeightedEdge(), nodes[32], nodes[26]);
                graph.addEdge(new WeightedEdge(), nodes[32], nodes[29]);

                // Obj uses
                tempMap.put(idents[2], 1L);
                tempMap.put(idents[8], 1L);
                tempMap.put(idents[14], 1L);
                objUseMap.put("o1", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[5], 1L);
                objUseMap.put("o2", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[11], 1L);
                objUseMap.put("o8", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[11], 1L);
                tempMap.put(idents[5], 1L);
                objUseMap.put("o9", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[11], 1L);
                objUseMap.put("o10", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[17], 1L);
                objUseMap.put("o14", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[17], 1L);
                objUseMap.put("o15", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[2], 1L);
                objUseMap.put("o17", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[20], 1L);
                objUseMap.put("o18", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[20], 1L);
                tempMap.put(idents[2], 1L);
                objUseMap.put("o19", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[2], 1L);
                objUseMap.put("o21", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[26], 1L);
                objUseMap.put("o22", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[26], 1L);
                tempMap.put(idents[32], 1L);
                objUseMap.put("o23", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[29], 1L);
                objUseMap.put("o27", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[2], 1L);
                objUseMap.put("o30", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[32], 1L);
                tempMap.put(idents[29], 1L);
                objUseMap.put("o66", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[23], 1L);
                objUseMap.put("o38", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[32], 1L);
                objUseMap.put("o39", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[34], 1L);
                tempMap.put(idents[31], 1L);
                objUseMap.put("o31", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[14], 1L);
                objUseMap.put("o41", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[20], 1L);
                objUseMap.put("o45", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[23], 1L);
                objUseMap.put("o47", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[29], 1L);
                objUseMap.put("o51", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[32], 1L);
                objUseMap.put("o53", tempMap);


                // conflicts - data cache evictions due to conflict
                // just guessing
                ConcurrentHashMap<Object, Integer> conflict =
                        new ConcurrentHashMap<Object, Integer>();
                conflict.put("o2", 1);
                conflict.put("o8", 1);
                conflict.put("o15", 1);
                conflict.put("o23", 1);
                conflict.put("o30", 1);
                conflict.put("o32", 1);
                conflict.put("o51", 1);
                conflict.put("o53", 1);
                conflictMap.put(NODE1, conflict);
                conflict = new ConcurrentHashMap<Object, Integer>();
                conflict.put("o1", 1);
                conflict.put("o10", 1);
                conflict.put("o14", 1);
                conflict.put("o22", 1);
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE3) {
                graph.addEdge(new WeightedEdge(), nodes[9], nodes[3]);
                graph.addEdge(new WeightedEdge(), nodes[30], nodes[24]);
                graph.addEdge(new WeightedEdge(), nodes[30], nodes[27]);
                graph.addEdge(new WeightedEdge(), nodes[33], nodes[3]);
                graph.addEdge(new WeightedEdge(), nodes[33], nodes[9]);
                graph.addEdge(new WeightedEdge(), nodes[33], nodes[15]);
                graph.addEdge(new WeightedEdge(), nodes[33], nodes[21]);
                graph.addEdge(new WeightedEdge(), nodes[33], nodes[24]);
                graph.addEdge(new WeightedEdge(), nodes[33], nodes[30]);

                tempMap.put(idents[3], 1L);
                objUseMap.put("o1", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[6], 1L);
                objUseMap.put("o3", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[6], 1L);
                objUseMap.put("o4", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[9], 1L);
                objUseMap.put("o5", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[9], 1L);
                tempMap.put(idents[3], 1L);
                objUseMap.put("o6", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[3], 1L);
                objUseMap.put("o7", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[6], 1L);
                objUseMap.put("o10", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[12], 1L);
                objUseMap.put("o11", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[6], 1L);
                objUseMap.put("o14", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[18], 1L);
                objUseMap.put("o16", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[18], 1L);
                objUseMap.put("o17", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[24], 1L);
                objUseMap.put("o22", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[3], 1L);
                objUseMap.put("o24", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[24], 1L);
                objUseMap.put("o25", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[3], 1L);
                objUseMap.put("o27", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[30], 1L);
                tempMap.put(idents[24], 1L);
                tempMap.put(idents[33], 1L);
                objUseMap.put("o28", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[30], 1L);
                tempMap.put(idents[27], 1L);
                objUseMap.put("o29", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[9], 1L);
                tempMap.put(idents[33], 1L);
                objUseMap.put("o31", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                tempMap.put(idents[3], 1L);
                objUseMap.put("o33", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                tempMap.put(idents[15], 1L);
                objUseMap.put("o34", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                objUseMap.put("o35", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                objUseMap.put("o36", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                tempMap.put(idents[21], 1L);
                objUseMap.put("o37", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                objUseMap.put("o38", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[33], 1L);
                objUseMap.put("o39", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[15], 1L);
                objUseMap.put("o42", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[21], 1L);
                objUseMap.put("o46", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[24], 1L);
                objUseMap.put("o48", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[27], 1L);
                objUseMap.put("o49", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[30], 1L);
                objUseMap.put("o52", tempMap);

                // conflicts - data cache evictions due to conflict
                // just guessing
                ConcurrentHashMap<Object, Integer> conflict =
                        new ConcurrentHashMap<Object, Integer>();
                conflict.put("o3", 1);
                conflict.put("o4", 1);
                conflict.put("o12", 1);
                conflict.put("o16", 1);
                conflict.put("o24", 1);
                conflict.put("o25", 1);
                conflict.put("o31", 1);
                conflict.put("o52", 1);
                conflictMap.put(NODE1, conflict);
                conflict = new ConcurrentHashMap<Object, Integer>();
                conflict.put("o17", 1);
                conflict.put("o26", 1);
                conflict.put("o27", 1);
                conflict.put("o38", 1);
                conflict.put("o39", 1);
                conflictMap.put(NODE2, conflict);
            }
        }

        /** {@inheritDoc} */
        public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
            return graph;
        }

        /** {@inheritDoc} */
        public Runnable getPruneTask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        /** {@inheritDoc} */
        public ConcurrentHashMap<Long, ConcurrentHashMap<Object, Integer>>
                getConflictMap()
        {
            return conflictMap;
        }

        /** {@inheritDoc} */
        public Map<Object, Map<Identity, Long>> getObjectUseMap() {
            return objUseMap;
        }

        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }
    }
}
