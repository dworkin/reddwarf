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
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.DLPAGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupGoodness;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleLabelPropagation;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.tools.test.IntegrationTest;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertNotNull;
/**
 * Test of single node performance of label propagation.
 * This is useful for modifying parameters before integrating
 * into the distributed version of the algorithm.
 *
 */

@IntegrationTest
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestLPAPerf {

    private static final int WARMUP_RUNS = 100;
    private static final int RUNS = 500;

    // Number of threads, set with data below for each run
    private int numThreads;

    // server used for single node tests
    private static LabelPropagationServer lpaServer;

    // profile collector
    private static ProfileCollector collector;

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
    
    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][]
            {{1}, {2}, {4}, {8}, {16}});
    }

    public TestLPAPerf(int numThreads) {
        this.numThreads = numThreads;
    }

    @BeforeClass
    public static void before() throws Exception {
        Properties props = new Properties();
        collector = new ProfileCollectorImpl(ProfileLevel.MAX, props, null);
        lpaServer = new LabelPropagationServer(collector, props);
    }

    @AfterClass
    public static void after() throws Exception {
        lpaServer.shutdown();
        collector.shutdown();
    }

    @Test
    public void warmupZach() throws Exception {
        // Warm up the compilers
        Properties props = new Properties();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));
        SingleLabelPropagation lpa =
           new SingleLabelPropagation(new ZachBuilder(), collector, props);

        for (int i = 0; i < WARMUP_RUNS; i++) {
            lpa.findAffinityGroups();
        }
        lpa.shutdown();
    }

    @Test
    public void testZachary() throws Exception {
        AffinityGraphBuilder builder = new ZachBuilder();
        Properties props = new Properties();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));
        // third argument true:  gather statistics
        SingleLabelPropagation lpa =
            new SingleLabelPropagation(builder, collector, props);

        AffinityGroupFinderMXBean bean = (AffinityGroupFinderMXBean)
            collector.getRegisteredMBean(AffinityGroupFinderMXBean.MXBEAN_NAME);
        assertNotNull(bean);
        bean.clear();
        // Be sure the consumer is turned on
        collector.getConsumer(AffinityGroupFinderStats.CONS_NAME).
                    setProfileLevel(ProfileLevel.MAX);
        
        double avgMod  = 0.0;
        double maxMod = 0.0;
        double minMod = 1.0;
        for (int i = 0; i < RUNS; i++) {
            Collection<AffinityGroup> groups = lpa.findAffinityGroups();
            double mod =
                AffinityGroupGoodness.calcModularity(
                                new ZachBuilder().getAffinityGraph(), groups);

            avgMod = avgMod + mod;
            maxMod = Math.max(maxMod, mod);
            minMod = Math.min(minMod, mod);
        }
        System.out.printf("SING (%d runs, %d threads): " +
                  "avg time : %4.2f ms, " +
                  " time range [%d - %d ms] " +
                  " avg iters : %4.2f, avg modularity: %.4f, " +
                  " modularity range [%.4f - %.4f] %n",
                  RUNS, numThreads,
                  bean.getAvgRunTime(),
                  bean.getMinRunTime(),
                  bean.getMaxRunTime(),
                  bean.getAvgIterations(),
                  avgMod/(double) RUNS,
                  minMod, maxMod);
        lpa.shutdown();
    }

    @Test
    public void warmupDistZach() throws Exception {
        // setup
        LabelPropagationServer server = null;
        if (WARMUP_RUNS > 0) {
            Properties props = new Properties();
            int serverPort = nextUniquePort.incrementAndGet();
            props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                       String.valueOf(serverPort));
            props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));
            server = new LabelPropagationServer(collector, props);

            LabelPropagation lp1 =
                new LabelPropagation(
                    new DistributedZachBuilder(DistributedZachBuilder.NODE1),
                        DistributedZachBuilder.NODE1, props);
            LabelPropagation lp2 =
                new LabelPropagation(
                    new DistributedZachBuilder(DistributedZachBuilder.NODE2),
                        DistributedZachBuilder.NODE2, props);
            LabelPropagation lp3 =
                new LabelPropagation(
                    new DistributedZachBuilder(DistributedZachBuilder.NODE3),
                        DistributedZachBuilder.NODE3, props);
        }

        for (int i = 0; i < WARMUP_RUNS; i++) {
            Collection<AffinityGroup> groups = server.findAffinityGroups();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testDistZach() throws Exception {
        // setup
        Properties props = new Properties();
        int serverPort = nextUniquePort.incrementAndGet();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                   String.valueOf(serverPort));
        LabelPropagationServer server = 
                new LabelPropagationServer(collector, props);
        props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));

        LabelPropagation lp1 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE1),
                    DistributedZachBuilder.NODE1, props);
        LabelPropagation lp2 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE2),
                    DistributedZachBuilder.NODE2, props);
        LabelPropagation lp3 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE3),
                    DistributedZachBuilder.NODE3, props);

        AffinityGroupFinderMXBean bean = (AffinityGroupFinderMXBean)
            collector.getRegisteredMBean(AffinityGroupFinderMXBean.MXBEAN_NAME);
        assertNotNull(bean);
        bean.clear();
        // Be sure the consumer is turned on
        collector.getConsumer(AffinityGroupFinderStats.CONS_NAME).
                    setProfileLevel(ProfileLevel.MAX);
        
        double avgMod  = 0.0;
        double maxMod = 0.0;
        double minMod = 1.0;
        for (int i = 0; i < RUNS; i++) {
            Collection<AffinityGroup> groups = server.findAffinityGroups();
            double mod =
                AffinityGroupGoodness.calcModularity(
                                new ZachBuilder().getAffinityGraph(), groups);

            avgMod = avgMod + mod;
            maxMod = Math.max(maxMod, mod);
            minMod = Math.min(minMod, mod);
        }
        System.out.printf("DIST (%d runs, %d threads): " +
                  "avg time : %4.2f ms, " +
                  " time range [%d - %d ms] " +
                  " avg iters : %4.2f, avg modularity: %.4f, " +
                  " modularity range [%.4f - %.4f] %n",
                  RUNS, numThreads,
                  bean.getAvgRunTime(),
                  bean.getMinRunTime(),
                  bean.getMaxRunTime(),
                  bean.getAvgIterations(),
                  avgMod/(double) RUNS,
                  minMod, maxMod);
        server.shutdown();
    }
    
    // A Zachary karate club which is distributed over 3 nodes, round-robin.
    private class DistributedZachBuilder implements DLPAGraphBuilder {
        private final UndirectedSparseGraph<LabelVertex, WeightedEdge> graph;
        private final ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>>
            conflictMap;
        private final ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>>
            objUseMap;

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
            graph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();
            objUseMap = new ConcurrentHashMap<Object, 
                                    ConcurrentMap<Identity, AtomicLong>>();
            conflictMap = new ConcurrentHashMap<Long, 
                                    ConcurrentMap<Object, AtomicLong>>();
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
            ConcurrentMap<Identity, AtomicLong> tempMap =
                    new ConcurrentHashMap<Identity, AtomicLong>();
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
                tempMap.put(idents[1], new AtomicLong(1));
                tempMap.put(idents[4], new AtomicLong(4));
                objUseMap.put("o1", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                tempMap.put(idents[7], new AtomicLong(1));
                objUseMap.put("o2", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o3", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[7], new AtomicLong(1));
                objUseMap.put("o4", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o5", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o84", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[4], new AtomicLong(1));
                objUseMap.put("o87", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[10], new AtomicLong(1));
                objUseMap.put("o7", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o8", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o11", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                tempMap.put(idents[13], new AtomicLong(1));
                objUseMap.put("o12", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[4], new AtomicLong(1));
                tempMap.put(idents[13], new AtomicLong(1));
                objUseMap.put("o13", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[7], new AtomicLong(1));
                objUseMap.put("o15", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o16", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o18", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[22], new AtomicLong(1));
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o20", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[22], new AtomicLong(1));
                objUseMap.put("o21", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[25], new AtomicLong(1));
                objUseMap.put("o23", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[28], new AtomicLong(1));
                objUseMap.put("o24", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[28], new AtomicLong(1));
                objUseMap.put("o25", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[28], new AtomicLong(1));
                tempMap.put(idents[25], new AtomicLong(1));
                objUseMap.put("o26", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[31], new AtomicLong(1));
                objUseMap.put("o30", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[31], new AtomicLong(1));
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o31", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[1], new AtomicLong(1));
                objUseMap.put("o32", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[16], new AtomicLong(1));
                objUseMap.put("o35", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[19], new AtomicLong(1));
                objUseMap.put("o36", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                tempMap.put(idents[10], new AtomicLong(1));
                objUseMap.put("o40", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o41", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o42", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                tempMap.put(idents[16], new AtomicLong(1));
                objUseMap.put("o43", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                tempMap.put(idents[19], new AtomicLong(1));
                objUseMap.put("o44", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o45", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o46", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o47", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o48", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o49", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                tempMap.put(idents[28], new AtomicLong(1));
                objUseMap.put("o50", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o51", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o52", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[34], new AtomicLong(1));
                objUseMap.put("o53", tempMap);


                // conflicts - data cache evictions due to conflict
                // just guessing
                ConcurrentHashMap<Object, AtomicLong> conflict =
                        new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("o1", new AtomicLong(1));
                conflict.put("o2", new AtomicLong(1));
                conflict.put("o18", new AtomicLong(1));
                conflict.put("o21", new AtomicLong(1));
                conflict.put("o41", new AtomicLong(1));
                conflict.put("o45", new AtomicLong(1));
                conflict.put("o47", new AtomicLong(1));
                conflictMap.put(NODE2, conflict);
                conflict = new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("o1", new AtomicLong(1));
                conflict.put("o5", new AtomicLong(1));
                conflict.put("o7", new AtomicLong(1));
                conflict.put("o11", new AtomicLong(1));
                conflict.put("o31", new AtomicLong(1));
                conflict.put("o35", new AtomicLong(1));
                conflict.put("o36", new AtomicLong(1));
                conflict.put("o42", new AtomicLong(1));
                conflict.put("o46", new AtomicLong(1));
                conflict.put("o48", new AtomicLong(1));
                conflict.put("o49", new AtomicLong(1));
                conflictMap.put(NODE3, conflict);
            } else if (node == NODE2) {
                graph.addEdge(new WeightedEdge(), nodes[8], nodes[2]);
                graph.addEdge(new WeightedEdge(), nodes[11], nodes[5]);
                graph.addEdge(new WeightedEdge(), nodes[14], nodes[2]);
                graph.addEdge(new WeightedEdge(), nodes[20], nodes[2]);
                graph.addEdge(new WeightedEdge(), nodes[32], nodes[26]);
                graph.addEdge(new WeightedEdge(), nodes[32], nodes[29]);

                // Obj uses
                tempMap.put(idents[2], new AtomicLong(1));
                tempMap.put(idents[8], new AtomicLong(1));
                objUseMap.put("o1", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[14], new AtomicLong(1));
                objUseMap.put("o84", tempMap);
                tempMap.put(idents[2], new AtomicLong(1));
                tempMap.put(idents[14], new AtomicLong(1));
                objUseMap.put("o85", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[14], new AtomicLong(1));
                objUseMap.put("o86", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[14], new AtomicLong(1));
                objUseMap.put("o87", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[5], new AtomicLong(1));
                objUseMap.put("o2", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[11], new AtomicLong(1));
                objUseMap.put("o8", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[11], new AtomicLong(1));
                tempMap.put(idents[5], new AtomicLong(1));
                objUseMap.put("o9", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[11], new AtomicLong(1));
                objUseMap.put("o10", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[17], new AtomicLong(1));
                objUseMap.put("o14", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[17], new AtomicLong(1));
                objUseMap.put("o15", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[2], new AtomicLong(1));
                objUseMap.put("o17", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[20], new AtomicLong(1));
                objUseMap.put("o18", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[20], new AtomicLong(1));
                tempMap.put(idents[2], new AtomicLong(1));
                objUseMap.put("o19", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[2], new AtomicLong(1));
                objUseMap.put("o21", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[26], new AtomicLong(1));
                objUseMap.put("o22", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[26], new AtomicLong(1));
                tempMap.put(idents[32], new AtomicLong(1));
                objUseMap.put("o23", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[29], new AtomicLong(1));
                objUseMap.put("o27", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[2], new AtomicLong(1));
                objUseMap.put("o30", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[32], new AtomicLong(1));
                tempMap.put(idents[29], new AtomicLong(1));
                objUseMap.put("o66", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[32], new AtomicLong(1));
                objUseMap.put("o32", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[23], new AtomicLong(1));
                objUseMap.put("o38", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[32], new AtomicLong(1));
                objUseMap.put("o39", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[14], new AtomicLong(1));
                objUseMap.put("o41", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[20], new AtomicLong(1));
                objUseMap.put("o45", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[23], new AtomicLong(1));
                objUseMap.put("o47", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[29], new AtomicLong(1));
                objUseMap.put("o51", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[32], new AtomicLong(1));
                objUseMap.put("o53", tempMap);


                // conflicts - data cache evictions due to conflict
                // just guessing
                ConcurrentHashMap<Object, AtomicLong> conflict =
                        new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("o2", new AtomicLong(1));
                conflict.put("o8", new AtomicLong(1));
                conflict.put("o15", new AtomicLong(1));
                conflict.put("o23", new AtomicLong(1));
                conflict.put("o30", new AtomicLong(1));
                conflict.put("o32", new AtomicLong(1));
                conflict.put("o51", new AtomicLong(1));
                conflict.put("o53", new AtomicLong(1));
                conflict.put("o84", new AtomicLong(1));
                conflict.put("o87", new AtomicLong(1));
                conflictMap.put(NODE1, conflict);
                conflict = new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("o1", new AtomicLong(1));
                conflict.put("o10", new AtomicLong(1));
                conflict.put("o14", new AtomicLong(1));
                conflict.put("o22", new AtomicLong(1));
                conflict.put("o86", new AtomicLong(1));
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

                tempMap.put(idents[3], new AtomicLong(1));
                objUseMap.put("o1", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[3], new AtomicLong(1));
                objUseMap.put("o86", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[6], new AtomicLong(1));
                objUseMap.put("o3", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[6], new AtomicLong(1));
                objUseMap.put("o4", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[9], new AtomicLong(1));
                objUseMap.put("o5", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[9], new AtomicLong(1));
                tempMap.put(idents[3], new AtomicLong(1));          
                objUseMap.put("o6", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[3], new AtomicLong(1));
                objUseMap.put("o7", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[6], new AtomicLong(1));
                objUseMap.put("o10", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[12], new AtomicLong(1));
                objUseMap.put("o11", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[6], new AtomicLong(1));
                objUseMap.put("o14", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[18], new AtomicLong(1));
                objUseMap.put("o16", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[18], new AtomicLong(1));
                objUseMap.put("o17", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[24], new AtomicLong(1));
                objUseMap.put("o22", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[3], new AtomicLong(1));
                objUseMap.put("o24", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[24], new AtomicLong(1));
                objUseMap.put("o25", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[3], new AtomicLong(1));
                objUseMap.put("o27", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[30], new AtomicLong(1));
                tempMap.put(idents[24], new AtomicLong(1));
                tempMap.put(idents[33], new AtomicLong(1));
                objUseMap.put("o28", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[30], new AtomicLong(1));
                tempMap.put(idents[27], new AtomicLong(1));
                objUseMap.put("o29", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[9], new AtomicLong(1));
                tempMap.put(idents[33], new AtomicLong(1));
                objUseMap.put("o31", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                tempMap.put(idents[3], new AtomicLong(1));
                objUseMap.put("o33", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                tempMap.put(idents[15], new AtomicLong(1));
                objUseMap.put("o34", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                objUseMap.put("o35", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                objUseMap.put("o36", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                tempMap.put(idents[21], new AtomicLong(1));
                objUseMap.put("o37", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                objUseMap.put("o38", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[33], new AtomicLong(1));
                objUseMap.put("o39", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[15], new AtomicLong(1));
                objUseMap.put("o42", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[21], new AtomicLong(1));
                objUseMap.put("o46", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[24], new AtomicLong(1));
                objUseMap.put("o48", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[27], new AtomicLong(1));
                objUseMap.put("o49", tempMap);
                tempMap = new ConcurrentHashMap<Identity, AtomicLong>();
                tempMap.put(idents[30], new AtomicLong(1));
                objUseMap.put("o52", tempMap);

                // conflicts - data cache evictions due to conflict
                // just guessing
                ConcurrentHashMap<Object, AtomicLong> conflict =
                        new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("o3", new AtomicLong(1));
                conflict.put("o4", new AtomicLong(1));
                conflict.put("o16", new AtomicLong(1));
                conflict.put("o24", new AtomicLong(1));
                conflict.put("o25", new AtomicLong(1));
                conflict.put("o31", new AtomicLong(1));
                conflict.put("o52", new AtomicLong(1));
                conflictMap.put(NODE1, conflict);
                conflict = new ConcurrentHashMap<Object, AtomicLong>();
                conflict.put("o17", new AtomicLong(1));
                conflict.put("o27", new AtomicLong(1));
                conflict.put("o38", new AtomicLong(1));
                conflict.put("o39", new AtomicLong(1));
                conflictMap.put(NODE2, conflict);
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

        /** {@inheritDoc} */
        public void shutdown() {
            // do nothing
        }

        /** {@inheritDoc} */
        public AffinityGroupFinder getAffinityGroupFinder() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
}
