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

package com.sun.sgs.test.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupImpl;
import com.sun.sgs.impl.service.nodemap.affinity.GraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedEdge;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Graphs;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import org.junit.runner.RunWith;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized;
/**
 *
 * 
 */
//@RunWith(FilteredNameRunner.class)
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestLPA {

    private static int numThreads;
    private static final int WARMUP_RUNS = 100;
    private static final int RUNS = 500;

    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][] {{1}, {2}, {4}, {8}, {16}});

    }

    public TestLPA(int numThreads) {
        this.numThreads = numThreads;
    }

    @Test
    public void warmup() {
        // Warm up the compilers
        LabelPropagation lpa =
                new LabelPropagation(new TestZachBuilder(), false, numThreads);

        for (int i = 0; i < WARMUP_RUNS; i++) {
            lpa.findCommunities();
        }
        lpa.shutdown();
    }

    @Test
    public void testZachary() {
        GraphBuilder builder = new TestZachBuilder();
        // second argument true:  gather statistics
        LabelPropagation lpa = new LabelPropagation(builder, true, numThreads);
        
        long avgTime = 0;
        int avgIter = 0;
        double avgMod  = 0.0;
        double maxMod = 0.0;
        double minMod = 1.0;
        long maxTime = 0;
        long minTime = 1;
        for (int i = 0; i < RUNS; i++) {
            Collection<AffinityGroup> groups = lpa.findCommunities();
            long time = lpa.getTime();
            avgTime = avgTime + time;
            maxTime = Math.max(maxTime, time);
            minTime = Math.min(minTime, time);

            avgIter = avgIter + lpa.getIterations();
            double mod = lpa.getModularity();
            avgMod = avgMod + mod;
            maxMod = Math.max(maxMod, mod);
            minMod = Math.min(minMod, mod);
        }
        System.out.printf("XXX (%d runs, %d threads): avg time : %4.2f ms, " +
                          " time range [%d - %d ms] " +
                          " avg iters : %4.2f, avg modularity: %.4f, " +
                          " modularity range [%.4f - %.4f] %n",
                          RUNS, numThreads,
                          avgTime/(double) RUNS,
                          minTime, maxTime,
                          avgIter/(double) RUNS,
                          avgMod/(double) RUNS,
                          minMod, maxMod);
        lpa.shutdown();
    }

    // Need to figure out where the modularity calculation really belongs.
    @Test
    public void testToyModularity() {
        GraphBuilder builder = new TestToyBuilder();

        Collection<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        AffinityGroupImpl a = new AffinityGroupImpl(1);
        a.addIdentity(new DummyIdentity("1"));
        a.addIdentity(new DummyIdentity("2"));
        a.addIdentity(new DummyIdentity("3"));
        group1.add(a);
        AffinityGroupImpl b = new AffinityGroupImpl(2);
        b.addIdentity(new DummyIdentity("4"));
        b.addIdentity(new DummyIdentity("5"));
        group1.add(b);

        double modularity =
            LabelPropagation.calcModularity(builder.getAffinityGraph(), group1);
        Assert.assertEquals(0.22, modularity, .001);

        Collection<AffinityGroup> group2 = new HashSet<AffinityGroup>();
        a = new AffinityGroupImpl(3);
        a.addIdentity(new DummyIdentity("1"));
        a.addIdentity(new DummyIdentity("3"));
        group2.add(a);
        b = new AffinityGroupImpl(4);
        b.addIdentity(new DummyIdentity("2"));
        b.addIdentity(new DummyIdentity("4"));
        b.addIdentity(new DummyIdentity("5"));
        group2.add(b);

        modularity =
            LabelPropagation.calcModularity(builder.getAffinityGraph(), group2);
        Assert.assertEquals(0.08, modularity, .001);

        // JANE need to test with graph with weighted edges!

        double jaccard = LabelPropagation.calcJaccard(group1, group2);
        System.out.println("Jaccard index is " + jaccard);
        Assert.assertEquals(0.333, jaccard, .001);
    }

    @Test
    public void testZachModularity() {
        GraphBuilder builder = new TestZachBuilder();
        Collection<AffinityGroup> groups = new HashSet<AffinityGroup>();
        AffinityGroupImpl a = new AffinityGroupImpl(1);
        a.addIdentity(new DummyIdentity("1"));
        a.addIdentity(new DummyIdentity("2"));
        a.addIdentity(new DummyIdentity("3"));
        a.addIdentity(new DummyIdentity("4"));
        a.addIdentity(new DummyIdentity("5"));
        a.addIdentity(new DummyIdentity("6"));
        a.addIdentity(new DummyIdentity("7"));
        a.addIdentity(new DummyIdentity("8"));
        a.addIdentity(new DummyIdentity("11"));
        a.addIdentity(new DummyIdentity("12"));
        a.addIdentity(new DummyIdentity("13"));
        a.addIdentity(new DummyIdentity("14"));
        a.addIdentity(new DummyIdentity("17"));
        a.addIdentity(new DummyIdentity("18"));
        a.addIdentity(new DummyIdentity("20"));
        a.addIdentity(new DummyIdentity("22"));
        groups.add(a);

        AffinityGroupImpl b = new AffinityGroupImpl(2);
        b.addIdentity(new DummyIdentity("9"));
        b.addIdentity(new DummyIdentity("10"));
        b.addIdentity(new DummyIdentity("15"));
        b.addIdentity(new DummyIdentity("16"));
        b.addIdentity(new DummyIdentity("19"));
        b.addIdentity(new DummyIdentity("21"));
        b.addIdentity(new DummyIdentity("23"));
        b.addIdentity(new DummyIdentity("24"));
        b.addIdentity(new DummyIdentity("25"));
        b.addIdentity(new DummyIdentity("26"));
        b.addIdentity(new DummyIdentity("27"));
        b.addIdentity(new DummyIdentity("28"));
        b.addIdentity(new DummyIdentity("29"));
        b.addIdentity(new DummyIdentity("30"));
        b.addIdentity(new DummyIdentity("31"));
        b.addIdentity(new DummyIdentity("32"));
        b.addIdentity(new DummyIdentity("33"));
        b.addIdentity(new DummyIdentity("34"));
        groups.add(b);

        double modularity =
            LabelPropagation.calcModularity(builder.getAffinityGraph(), groups);
        System.out.println("Modularity for correct Zachary's karate club is " +
                modularity);
    }

    @Test
    public void testModularityCalc() {
        GraphBuilder builder = new TestZachBuilder();
        Collection<AffinityGroup> groups = new HashSet<AffinityGroup>();
        AffinityGroupImpl a = new AffinityGroupImpl(49);
        a.addIdentity(new DummyIdentity("17"));
        groups.add(a);

        AffinityGroupImpl b = new AffinityGroupImpl(2);
        b.addIdentity(new DummyIdentity("1"));
        b.addIdentity(new DummyIdentity("2"));
        b.addIdentity(new DummyIdentity("3"));
        b.addIdentity(new DummyIdentity("4"));
        b.addIdentity(new DummyIdentity("5"));
        b.addIdentity(new DummyIdentity("6"));
        b.addIdentity(new DummyIdentity("7"));
        b.addIdentity(new DummyIdentity("8"));
        b.addIdentity(new DummyIdentity("9"));
        b.addIdentity(new DummyIdentity("10"));
        b.addIdentity(new DummyIdentity("11"));
        b.addIdentity(new DummyIdentity("12"));
        b.addIdentity(new DummyIdentity("13"));
        b.addIdentity(new DummyIdentity("14"));
        b.addIdentity(new DummyIdentity("15"));
        b.addIdentity(new DummyIdentity("16"));
//        b.addIdentity(new DummyIdentity("17"));
        b.addIdentity(new DummyIdentity("18"));
        b.addIdentity(new DummyIdentity("19"));
        b.addIdentity(new DummyIdentity("20"));
        b.addIdentity(new DummyIdentity("21"));
        b.addIdentity(new DummyIdentity("22"));
        b.addIdentity(new DummyIdentity("23"));
        b.addIdentity(new DummyIdentity("24"));
        b.addIdentity(new DummyIdentity("25"));
        b.addIdentity(new DummyIdentity("26"));
        b.addIdentity(new DummyIdentity("27"));
        b.addIdentity(new DummyIdentity("28"));
        b.addIdentity(new DummyIdentity("29"));
        b.addIdentity(new DummyIdentity("30"));
        b.addIdentity(new DummyIdentity("31"));
        b.addIdentity(new DummyIdentity("32"));
        b.addIdentity(new DummyIdentity("33"));
        b.addIdentity(new DummyIdentity("34"));
        groups.add(b);

        double modularity =
            LabelPropagation.calcModularity(builder.getAffinityGraph(), groups);
        System.out.println("Modularity test club partition is " +
                modularity);
    }

    /**
     * A graph builder that returns a pre-made graph for the Zachary karate
     * club network.
     */
    private static class TestZachBuilder implements GraphBuilder {
        private final Graph<LabelVertex, WeightedEdge> graph;

        public TestZachBuilder() {
            graph = new UndirectedSparseMultigraph<LabelVertex, WeightedEdge>();

            // Create a graph for the Zachary network:
            // W. W. Zachary, An information flow model for conflict and
            // fission in small group1,
            // Journal of Anthropological Research 33, 452-473 (1977)
            LabelVertex[] nodes = new LabelVertex[35];
            for (int i = 1; i < nodes.length; i++) {
                // Add identities 1-34
                nodes[i] =
                        new LabelVertex(new DummyIdentity(String.valueOf(i)));
                graph.addVertex(nodes[i]);
            }

            graph.addEdge(new WeightedEdge(), nodes[2], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[3], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[3], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[4], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[4], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[4], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[5], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[6], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[7], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[7], nodes[5]);
            graph.addEdge(new WeightedEdge(), nodes[7], nodes[6]);
            graph.addEdge(new WeightedEdge(), nodes[8], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[8], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[8], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[8], nodes[4]);
            graph.addEdge(new WeightedEdge(), nodes[9], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[9], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[10], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[11], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[11], nodes[5]);
            graph.addEdge(new WeightedEdge(), nodes[11], nodes[6]);
            graph.addEdge(new WeightedEdge(), nodes[12], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[13], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[13], nodes[4]);
            graph.addEdge(new WeightedEdge(), nodes[14], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[14], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[14], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[14], nodes[4]);
            graph.addEdge(new WeightedEdge(), nodes[17], nodes[6]);
            graph.addEdge(new WeightedEdge(), nodes[17], nodes[7]);
            graph.addEdge(new WeightedEdge(), nodes[18], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[18], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[20], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[20], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[22], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[22], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[26], nodes[24]);
            graph.addEdge(new WeightedEdge(), nodes[26], nodes[25]);
            graph.addEdge(new WeightedEdge(), nodes[28], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[28], nodes[24]);
            graph.addEdge(new WeightedEdge(), nodes[28], nodes[25]);
            graph.addEdge(new WeightedEdge(), nodes[29], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[30], nodes[24]);
            graph.addEdge(new WeightedEdge(), nodes[30], nodes[27]);
            graph.addEdge(new WeightedEdge(), nodes[31], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[31], nodes[9]);
            graph.addEdge(new WeightedEdge(), nodes[32], nodes[1]);
            graph.addEdge(new WeightedEdge(), nodes[32], nodes[25]);
            graph.addEdge(new WeightedEdge(), nodes[32], nodes[26]);
            graph.addEdge(new WeightedEdge(), nodes[32], nodes[29]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[9]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[15]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[16]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[19]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[21]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[23]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[24]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[30]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[31]);
            graph.addEdge(new WeightedEdge(), nodes[33], nodes[32]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[9]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[10]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[14]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[15]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[16]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[19]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[20]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[21]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[23]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[24]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[27]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[28]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[29]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[30]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[31]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[32]);
            graph.addEdge(new WeightedEdge(), nodes[34], nodes[33]);

            Assert.assertEquals(34, graph.getVertexCount());
            Assert.assertEquals(78, graph.getEdgeCount());
        }

        /** {@inheritDoc} */
        public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
            return Graphs.unmodifiableGraph(graph);
        }
        /** {@inheritDoc} */
        public Runnable getPruneTask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }
    }

    /**
     * A graph builder that returns a pre-made graph for a very simple toy
     * graph.
     */
    private static class TestToyBuilder implements GraphBuilder {
        private final Graph<LabelVertex, WeightedEdge> graph;

        public TestToyBuilder() {
            graph = new UndirectedSparseMultigraph<LabelVertex, WeightedEdge>();

            LabelVertex[] nodes = new LabelVertex[6];
            for (int i = 1; i < nodes.length; i++) {
                // Add identities 1-5
                nodes[i] =
                        new LabelVertex(new DummyIdentity(String.valueOf(i)));
                graph.addVertex(nodes[i]);
            }

            graph.addEdge(new WeightedEdge(), nodes[1], nodes[2]);
            graph.addEdge(new WeightedEdge(), nodes[1], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[2], nodes[3]);
            graph.addEdge(new WeightedEdge(), nodes[2], nodes[4]);
            graph.addEdge(new WeightedEdge(), nodes[4], nodes[5]);

            Assert.assertEquals(5, graph.getVertexCount());
            Assert.assertEquals(5, graph.getEdgeCount());
        }

        /** {@inheritDoc} */
        public Graph<LabelVertex, WeightedEdge> getAffinityGraph() {
            return Graphs.unmodifiableGraph(graph);
        }
        /** {@inheritDoc} */
        public Runnable getPruneTask() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        /** {@inheritDoc} */
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            return;
        }
    }
}
