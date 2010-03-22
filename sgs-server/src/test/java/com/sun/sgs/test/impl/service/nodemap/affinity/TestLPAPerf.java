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
import com.sun.sgs.impl.service.nodemap.affinity.LPAAffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.DLPAGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupGoodness;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.BipartiteGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.WeightedGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleLabelPropagation;
import com.sun.sgs.impl.sharedutil.Objects;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.IntegrationTest;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
    // Builder, used for dist runs
    private final String builderName;

    // server used for single node tests
    private static LabelPropagationServer lpaServer;

    // sgs test node used to start a watchdog, needed for lpaServer
    private static SgsTestNode serverNode;

    // watchdog
    private static WatchdogService wdog;

    // profile collector
    private static ProfileCollector collector;
    
    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][]
            {{1, WeightedGraphBuilder.class.getName()},
             {2, WeightedGraphBuilder.class.getName()},
             {4, WeightedGraphBuilder.class.getName()},
             {8, WeightedGraphBuilder.class.getName()},
             {16, WeightedGraphBuilder.class.getName()},
             {1, BipartiteGraphBuilder.class.getName()},
             {2, BipartiteGraphBuilder.class.getName()},
             {4, BipartiteGraphBuilder.class.getName()},
             {8, WeightedGraphBuilder.class.getName()},
             {16, BipartiteGraphBuilder.class.getName()}});
    }

    public TestLPAPerf(int numThreads, String builderName) {
        this.numThreads = numThreads;
        this.builderName = builderName;
    }

    @BeforeClass
    public static void before() throws Exception {
        Properties props = SgsTestNode.getDefaultProperties("TestLPA", null, null);
        props.put("com.sun.sgs.impl.kernel.profile.level",
                   ProfileLevel.MAX.name());
        props.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "3600"); // one hour
        // We are creating this SgsTestNode so we can get at its watchdog
        // and profile collector only - the LPAServer we are testing is
        // created outside this framework so we could easily extend the type.
        serverNode = new SgsTestNode("TestLPA", null, props);
        collector =
            serverNode.getSystemRegistry().getComponent(ProfileCollector.class);
        wdog = serverNode.getWatchdogService();
        lpaServer = new LabelPropagationServer(collector, wdog, props);
    }

    @AfterClass
    public static void after() throws Exception {
        lpaServer.shutdown();
        serverNode.shutdown(true);
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
            Set<AffinityGroup> groups = 
                    Objects.uncheckedCast(lpa.findAffinityGroups());
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
            int serverPort = SgsTestNode.getNextUniquePort();
            props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                       String.valueOf(serverPort));
            props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));
            server = new LabelPropagationServer(collector, wdog, props);

            LabelPropagation lp1 =
                new LabelPropagation(
                    new DistributedZachBuilder(DistributedZachBuilder.NODE1),
                        wdog, DistributedZachBuilder.NODE1, props);
            LabelPropagation lp2 =
                new LabelPropagation(
                    new DistributedZachBuilder(DistributedZachBuilder.NODE2),
                        wdog, DistributedZachBuilder.NODE2, props);
            LabelPropagation lp3 =
                new LabelPropagation(
                    new DistributedZachBuilder(DistributedZachBuilder.NODE3),
                        wdog, DistributedZachBuilder.NODE3, props);
        }

        for (int i = 0; i < WARMUP_RUNS; i++) {
            Set<RelocatingAffinityGroup> groups = server.findAffinityGroups();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testDistZach() throws Exception {
        // setup
        Properties props = new Properties();
        int serverPort = SgsTestNode.getNextUniquePort();
        props.put("com.sun.sgs.impl.service.nodemap.affinity.server.port",
                   String.valueOf(serverPort));
        LabelPropagationServer server = 
                new LabelPropagationServer(collector, wdog, props);
        props.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));
        props.put(LPADriver.GRAPH_CLASS_PROPERTY, builderName);

        LabelPropagation lp1 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE1),
                    wdog, DistributedZachBuilder.NODE1, props);
        LabelPropagation lp2 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE2),
                    wdog, DistributedZachBuilder.NODE2, props);
        LabelPropagation lp3 =
            new LabelPropagation(
                new DistributedZachBuilder(DistributedZachBuilder.NODE3),
                    wdog, DistributedZachBuilder.NODE3, props);

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
            Set<AffinityGroup> groups = 
                    Objects.uncheckedCast(server.findAffinityGroups());
            double mod =
                AffinityGroupGoodness.calcModularity(
                                new ZachBuilder().getAffinityGraph(), groups);

            avgMod = avgMod + mod;
            maxMod = Math.max(maxMod, mod);
            minMod = Math.min(minMod, mod);
        }
        String name;
        if (WeightedGraphBuilder.class.getName().equals(builderName)) {
            name = "DIST weighted";
        } else {
            name = "DIST bipartite";
        }
        System.out.printf(name + " (%d runs, %d threads): " +
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
        private final UndirectedGraph<LabelVertex, WeightedEdge> graph;
        private final HashMap<Identity, LabelVertex> identMap =
                new HashMap<Identity, LabelVertex>();
        private final Map<Long, Map<Object, Long>> conflictMap;
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
            graph = new UndirectedSparseGraph<LabelVertex, WeightedEdge>();
            objUseMap = new HashMap<Object, Map<Identity, Long>>();
            conflictMap = new HashMap<Long, Map<Object, Long>>();
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
                objUseMap.put("o84", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[4], 1L);
                objUseMap.put("o87", tempMap);
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
                HashMap<Object, Long> conflict = new HashMap<Object, Long>();;
                conflict.put("o1", 1L);
                conflict.put("o2", 1L);
                conflict.put("o18", 1L);
                conflict.put("o21", 1L);
                conflict.put("o41", 1L);
                conflict.put("o45", 1L);
                conflict.put("o47", 1L);
                conflictMap.put(NODE2, conflict);
                conflict = new HashMap<Object, Long>();
                conflict.put("o1", 1L);
                conflict.put("o5", 1L);
                conflict.put("o7", 1L);
                conflict.put("o11", 1L);
                conflict.put("o31", 1L);
                conflict.put("o35", 1L);
                conflict.put("o36", 1L);
                conflict.put("o42", 1L);
                conflict.put("o46", 1L);
                conflict.put("o48", 1L);
                conflict.put("o49", 1L);
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
                objUseMap.put("o1", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[14], 1L);
                objUseMap.put("o84", tempMap);
                tempMap.put(idents[2], 1L);
                tempMap.put(idents[14], 1L);
                objUseMap.put("o85", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[14], 1L);
                objUseMap.put("o86", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[14], 1L);
                objUseMap.put("o87", tempMap);
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
                tempMap.put(idents[32], 1L);
                objUseMap.put("o32", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[23], 1L);
                objUseMap.put("o38", tempMap);
                tempMap = new HashMap<Identity, Long>();
                tempMap.put(idents[32], 1L);
                objUseMap.put("o39", tempMap);
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
                HashMap<Object, Long> conflict = new HashMap<Object, Long>();
                conflict.put("o2", 1L);
                conflict.put("o8", 1L);
                conflict.put("o15", 1L);
                conflict.put("o23", 1L);
                conflict.put("o30", 1L);
                conflict.put("o32", 1L);
                conflict.put("o51", 1L);
                conflict.put("o53", 1L);
                conflict.put("o84", 1L);
                conflict.put("o87", 1L);
                conflictMap.put(NODE1, conflict);
                conflict = new HashMap<Object, Long>();
                conflict.put("o1", 1L);
                conflict.put("o10", 1L);
                conflict.put("o14", 1L);
                conflict.put("o22", 1L);
                conflict.put("o86", 1L);
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
                tempMap.put(idents[3], 1L);
                objUseMap.put("o86", tempMap);
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
                HashMap<Object, Long> conflict = new HashMap<Object, Long>();
                conflict.put("o3", 1L);
                conflict.put("o4", 1L);
                conflict.put("o16", 1L);
                conflict.put("o24", 1L);
                conflict.put("o25", 1L);
                conflict.put("o31", 1L);
                conflict.put("o52", 1L);
                conflictMap.put(NODE1, conflict);
                conflict = new HashMap<Object, Long>();
                conflict.put("o17", 1L);
                conflict.put("o27", 1L);
                conflict.put("o38", 1L);
                conflict.put("o39", 1L);
                conflictMap.put(NODE2, conflict);
            }
            for (LabelVertex v : graph.getVertices()) {
                identMap.put(v.getIdentity(), v);
            }
        }

        /** {@inheritDoc} */
        public UndirectedGraph<LabelVertex, WeightedEdge>
                getAffinityGraph()
        {
            return graph;
        }

        /** {@inheritDoc} */
        public LabelVertex getVertex(Identity id) {
            return identMap.get(id);
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

        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }

        /** {@inheritDoc} */
        public void enable() {
            // do nothing
        }
        /** {@inheritDoc} */
        public void disable() {
            // do nothing
        }
        /** {@inheritDoc} */
        public void shutdown() {
            // do nothing
        }

        /** {@inheritDoc} */
        public LPAAffinityGroupFinder getAffinityGroupFinder() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
}
