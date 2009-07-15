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
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.After;
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

    private LabelPropagationServer server;

    @After
    public void shutdown() throws Exception {
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }
    
    @Test
    public void testDistributedFramework() throws Exception {
        // Create a server, and add a few "nodes"
        server = new LabelPropagationServer();
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
        server = new LabelPropagationServer();
        int port = LabelPropagationServer.DEFAULT_SERVER_PORT;
        String localHost = InetAddress.getLocalHost().getHostName();

        LabelPropagation lp1 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE1),
                    PartialToyBuilder.NODE1, localHost, port, true, 1, 0);
        server.register(PartialToyBuilder.NODE1, lp1);
        LabelPropagation lp2 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE2),
                    PartialToyBuilder.NODE2, localHost, port, true, 1, 0);
        server.register(PartialToyBuilder.NODE2, lp2);
        LabelPropagation lp3 =
            new LabelPropagation(new PartialToyBuilder(PartialToyBuilder.NODE3),
                    PartialToyBuilder.NODE3, localHost, port, true, 1, 0);
        server.register(PartialToyBuilder.NODE3, lp3);

        lp1.exchangeCrossNodeInfo();
        lp2.exchangeCrossNodeInfo();
        lp3.exchangeCrossNodeInfo();

        // Wait an appropriate time - want server to be called back 3 times
        Thread.sleep(1000);

        // examine the node conflict map -it is public so I can get it here
        //public Map<Long, Map<Object, Integer>> nodeConflictMap
        System.out.println("NODE1 nodeConflictMap");
        printNodeConflictMap(lp1);
        System.out.println("NODE2 nodeConflictMap");
        printNodeConflictMap(lp2);
        System.out.println("NODE3 nodeConflictMap");
        printNodeConflictMap(lp3);

        lp1.affinityGroups(true);
        lp2.affinityGroups(true);
        lp3.affinityGroups(true);

        System.out.println("Exchanging info a second time");
        lp3.exchangeCrossNodeInfo();
        lp2.exchangeCrossNodeInfo();
        lp1.exchangeCrossNodeInfo();

        // Wait an appropriate time - want server to be called back 3 times
        Thread.sleep(1000);

        // examine the node conflict map -it is public so I can get it here
        //public Map<Long, Map<Object, Integer>> nodeConflictMap
        System.out.println("NODE1 nodeConflictMap");
        printNodeConflictMap(lp1);
        System.out.println("NODE2 nodeConflictMap");
        printNodeConflictMap(lp2);
        System.out.println("NODE3 nodeConflictMap");
        printNodeConflictMap(lp3);
    }

    private void printNodeConflictMap(LabelPropagation lp) {
        
        for (Map.Entry<Long, Map<Object, Integer>> entry :
             lp.nodeConflictMap.entrySet())
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
        public Collection<AffinityGroup> affinityGroups(boolean done) throws IOException {
            return result;
        }

        /** {@inheritDoc} */
        public void exchangeCrossNodeInfo() throws IOException {
            startedExchangeInfo = true;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ex) {
                throw new IOException("failed", ex);
            }
            finishedExchangeInfo = true;
            server.readyToBegin(nodeId);
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
            server.finishedIteration(nodeId, converged, currentIter);
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
        public Map<Object, Set<Integer>> getRemoteLabels(
                Collection<Object> objIds) throws IOException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    // Simple builder spread across 3 nodes
    private class PartialToyBuilder implements GraphBuilder {
        private final Graph<LabelVertex, WeightedEdge> graph;
        private final Map<Long, Map<Object, Integer>> conflictMap;
        private final Map<Object, Map<Identity, Integer>> objUseMap;

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
            objUseMap = new ConcurrentHashMap<Object, Map<Identity, Integer>>();
            conflictMap = new ConcurrentHashMap<Long, Map<Object, Integer>>();

            if (node == NODE1) {
                // Create a partial graph
                Identity[] idents = {new DummyIdentity("1"),
                                     new DummyIdentity("2")};
                LabelVertex[] nodes = new LabelVertex[2];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = new LabelVertex(idents[i]);
                    graph.addVertex(nodes[i]);
                }
                graph.addEdge(new WeightedEdge(), nodes[0], nodes[1]);

                // Obj uses
                Map<Identity, Integer> tempMap =
                        new HashMap<Identity, Integer>();
                tempMap.put(idents[0], 1);
                tempMap.put(idents[1], 1);
                objUseMap.put("obj1", tempMap);
                tempMap =  new HashMap<Identity, Integer>();
                tempMap.put(idents[1], 1);
                objUseMap.put("obj2", tempMap);

                // conflicts - data cache evictions due to conflict
                Map<Object, Integer> conflict = new HashMap<Object, Integer>();
                conflict.put("obj1", 1);
                conflictMap.put(NODE2, conflict);
                conflict = new HashMap<Object, Integer>();
                conflict.put("obj2", 1);
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE2) {
                // Create a partial graph
                Identity ident = new DummyIdentity("3");
                LabelVertex ver = new LabelVertex(ident);
                graph.addVertex(ver);

                // Obj uses
                Map<Identity, Integer> tempMap =
                        new HashMap<Identity, Integer>();
                tempMap.put(ident, 1);

                // conflicts - data cache evictions due to conflict
                Map<Object, Integer> conflict = new HashMap<Object, Integer>();
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
                Map<Identity, Integer> tempMap =
                        new HashMap<Identity, Integer>();
                tempMap.put(idents[0], 1);
                tempMap.put(idents[1], 1);
                objUseMap.put("obj3", tempMap);
                tempMap =  new HashMap<Identity, Integer>();
                tempMap.put(idents[0], 1);
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
        public Map<Long, Map<Object, Integer>> getConflictMap() {
            return conflictMap;
        }

        /** {@inheritDoc} */
        public Map<Object, Map<Identity, Integer>> getObjectUseMap() {
            return objUseMap;
        }

        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }
    }
}
