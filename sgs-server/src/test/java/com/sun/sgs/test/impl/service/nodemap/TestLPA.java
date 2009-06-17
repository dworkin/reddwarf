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
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runners.Parameterized;
/**
 *
 * 
 */
//@RunWith(FilteredNameRunner.class)
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestLPA {

    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(
            new Object[][] {{true, false},
                            {true, true},
                            {false, false},
                            {false, true}});

    }
    private final boolean checkConverge;
    private final boolean includeSelf;

    /**
     * Create this test class.
     * @param testType the type of profile data to create
     */
    public TestLPA(boolean checkConverge, boolean includeSelf) {
        this.checkConverge = checkConverge;
        this.includeSelf = includeSelf;
    }

    @BeforeClass
    public static void warmup() {
        // Warm up the compilers
        LabelPropagation lpa =
                new LabelPropagation(new TestZachBuilder(), true, false);

        for (int i = 0; i < 100; i++) {
            lpa.findCommunities();
        }
    }

    @Test
    public void testZachary() {
        GraphBuilder builder = new TestZachBuilder();
        LabelPropagation lpa =
                new LabelPropagation(builder, checkConverge, includeSelf);
        
        final int RUNS = 100;
        long avgTime = 0;
        int avgIter = 0;
        double avgMod  = 0.0;
        for (int i = 0; i < RUNS; i++) {
            Collection<AffinityGroup> groups = lpa.findCommunities();
            avgTime = avgTime + lpa.getTime();
            avgIter = avgIter + lpa.getIterations();
            avgMod = avgMod + lpa.getModularity();
        }
        String con = checkConverge ? "checkConverge" : "noChange";
        String self = includeSelf ? "includeSelf" : "noSelf";
        System.out.printf("XXX (%s, %s, %d runs): avg time : %4.2f ms, " +
                          " avg iters : %4.2f, avg modularity: %.4f %n",
                          con, self, RUNS,
                          avgTime/(double) RUNS,
                          avgIter/(double) RUNS,
                          avgMod/(double) RUNS);
    }

    // Really only needs to be run once.  Need to figure out where
    // the modularity calculation really belongs.
    @Test
    public void testToyModularity() {
        GraphBuilder builder = new TestToyBuilder();

        Collection<AffinityGroup> groups = new HashSet<AffinityGroup>();
        AffinityGroupImpl a = new AffinityGroupImpl();
        a.addIdentity(new DummyIdentity("1"));
        a.addIdentity(new DummyIdentity("2"));
        a.addIdentity(new DummyIdentity("3"));
        groups.add(a);
        AffinityGroupImpl b = new AffinityGroupImpl();
        b.addIdentity(new DummyIdentity("4"));
        b.addIdentity(new DummyIdentity("5"));
        groups.add(b);

        double modularity =
            LabelPropagation.calcModularity(builder.getAffinityGraph(), groups);
        Assert.assertEquals(0.22, modularity, .001);

        Collection<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        a = new AffinityGroupImpl();
        a.addIdentity(new DummyIdentity("1"));
        a.addIdentity(new DummyIdentity("3"));
        group1.add(a);
        b = new AffinityGroupImpl();
        b.addIdentity(new DummyIdentity("2"));
        b.addIdentity(new DummyIdentity("4"));
        b.addIdentity(new DummyIdentity("5"));
        group1.add(b);

        modularity =
            LabelPropagation.calcModularity(builder.getAffinityGraph(), group1);
        Assert.assertEquals(0.08, modularity, .001);

        // JANE need to test with graph with weighted edges!
    }


    /**
     * A graph builder that returns a pre-made graph for the Zachary karate
     * club network.
     */
    private static class TestZachBuilder implements GraphBuilder {
        private final Graph<Identity, WeightedEdge> graph;

        public TestZachBuilder() {
            graph = new UndirectedSparseMultigraph<Identity, WeightedEdge> ();

            // Create a graph for the Zachary network:
            // W. W. Zachary, An information flow model for conflict and
            // fission in small groups,
            // Journal of Anthropological Research 33, 452-473 (1977)
            Identity[] nodes = new Identity[35];
            for (int i = 1; i < nodes.length; i++) {
                // Add identities 1-34
                nodes[i] = new DummyIdentity(String.valueOf(i));
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
        public Graph<Identity, WeightedEdge> getAffinityGraph() {
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
        private final Graph<Identity, WeightedEdge> graph;

        public TestToyBuilder() {
            graph = new UndirectedSparseMultigraph<Identity, WeightedEdge> ();

            Identity[] nodes = new Identity[6];
            for (int i = 1; i < nodes.length; i++) {
                // Add identities 1-5
                nodes[i] = new DummyIdentity(String.valueOf(i));
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
        public Graph<Identity, WeightedEdge> getAffinityGraph() {
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
