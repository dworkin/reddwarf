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
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupGoodness;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.tools.test.FilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedGraph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *  Tests for static AffinityGroupGoodness methods.
 */
@RunWith(FilteredNameRunner.class)
public class TestAffinityGroupGoodness {

    @Test
    public void testToyModularityAndJaccard() {
        AffinityGraphBuilder builder = new TestToyBuilder();

        final long gen = 1;
        Collection<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        HashSet<Identity> identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("1"));
        identitySet.add(new DummyIdentity("2"));
        identitySet.add(new DummyIdentity("3"));
        AffinitySet a = new AffinitySet(1, gen, identitySet);
        group1.add(a);
        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("4"));
        identitySet.add(new DummyIdentity("5"));
        AffinitySet b = new AffinitySet(2, gen, identitySet);
        group1.add(b);

        double modularity = 
            AffinityGroupGoodness.calcModularity(builder.getAffinityGraph(),
                                                 group1);
        Assert.assertEquals(0.22, modularity, .001);

        Collection<AffinityGroup> group2 = new HashSet<AffinityGroup>();
        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("1"));
        identitySet.add(new DummyIdentity("3"));
        a = new AffinitySet(3, gen, identitySet);
        group2.add(a);
        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("2"));
        identitySet.add(new DummyIdentity("4"));
        identitySet.add(new DummyIdentity("5"));
        b = new AffinitySet(4, gen, identitySet);
        group2.add(b);

        modularity = 
            AffinityGroupGoodness.calcModularity(builder.getAffinityGraph(),
                                                 group2);
        Assert.assertEquals(0.08, modularity, .001);

        double jaccard = AffinityGroupGoodness.calcJaccard(group1, group2);
        System.out.println("Jaccard index is " + jaccard);
        Assert.assertEquals(0.333, jaccard, .001);
    }

    @Test
    public void testToyModularityAndJaccardWithWeights() {
        AffinityGraphBuilder builder = new TestToyWeightBuilder();

        final long gen = 1;
        Collection<AffinityGroup> group1 = new HashSet<AffinityGroup>();
        HashSet<Identity> identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("1"));
        identitySet.add(new DummyIdentity("2"));
        identitySet.add(new DummyIdentity("3"));
        AffinitySet a = new AffinitySet(1, gen, identitySet);
        group1.add(a);
        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("4"));
        identitySet.add(new DummyIdentity("5"));
        AffinitySet b = new AffinitySet(2, gen, identitySet);
        group1.add(b);

        double modularity =
            AffinityGroupGoodness.calcModularity(builder.getAffinityGraph(),
                                                 group1);
        Assert.assertEquals(0.22, modularity, .001);

        Collection<AffinityGroup> group2 = new HashSet<AffinityGroup>();
        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("1"));
        identitySet.add(new DummyIdentity("3"));
        a = new AffinitySet(3, gen, identitySet);
        group2.add(a);
        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("2"));
        identitySet.add(new DummyIdentity("4"));
        identitySet.add(new DummyIdentity("5"));
        b = new AffinitySet(4, gen, identitySet);
        
        group2.add(b);

        modularity =
            AffinityGroupGoodness.calcModularity(builder.getAffinityGraph(),
                                                 group2);
        Assert.assertEquals(0.08, modularity, .001);

        double jaccard = AffinityGroupGoodness.calcJaccard(group1, group2);
        System.out.println("Jaccard index is " + jaccard);
        Assert.assertEquals(0.333, jaccard, .001);
    }


    @Test
    public void testZachModularity() {
        final long gen = 1;
        AffinityGraphBuilder builder = new ZachBuilder();
        Collection<AffinityGroup> groups = new HashSet<AffinityGroup>();

        HashSet<Identity> identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("1"));
        identitySet.add(new DummyIdentity("2"));
        identitySet.add(new DummyIdentity("3"));
        identitySet.add(new DummyIdentity("4"));
        identitySet.add(new DummyIdentity("5"));
        identitySet.add(new DummyIdentity("6"));
        identitySet.add(new DummyIdentity("7"));
        identitySet.add(new DummyIdentity("8"));
        identitySet.add(new DummyIdentity("11"));
        identitySet.add(new DummyIdentity("12"));
        identitySet.add(new DummyIdentity("13"));
        identitySet.add(new DummyIdentity("14"));
        identitySet.add(new DummyIdentity("17"));
        identitySet.add(new DummyIdentity("18"));
        identitySet.add(new DummyIdentity("20"));
        identitySet.add(new DummyIdentity("22"));
        AffinitySet a = new AffinitySet(1, gen, identitySet);
        groups.add(a);

        identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("9"));
        identitySet.add(new DummyIdentity("10"));
        identitySet.add(new DummyIdentity("15"));
        identitySet.add(new DummyIdentity("16"));
        identitySet.add(new DummyIdentity("19"));
        identitySet.add(new DummyIdentity("21"));
        identitySet.add(new DummyIdentity("23"));
        identitySet.add(new DummyIdentity("24"));
        identitySet.add(new DummyIdentity("25"));
        identitySet.add(new DummyIdentity("26"));
        identitySet.add(new DummyIdentity("27"));
        identitySet.add(new DummyIdentity("28"));
        identitySet.add(new DummyIdentity("29"));
        identitySet.add(new DummyIdentity("30"));
        identitySet.add(new DummyIdentity("31"));
        identitySet.add(new DummyIdentity("32"));
        identitySet.add(new DummyIdentity("33"));
        identitySet.add(new DummyIdentity("34"));
        AffinitySet b = new AffinitySet(2, gen, identitySet);
        groups.add(b);

        double modularity =
            AffinityGroupGoodness.calcModularity(builder.getAffinityGraph(),
                                                 groups);
        System.out.println("Modularity for correct Zachary's karate club is " +
                modularity);
    }

    @Test
    public void testModularityCalc() {
        final long gen = 1;
        AffinityGraphBuilder builder = new ZachBuilder();
        Collection<AffinityGroup> groups = new HashSet<AffinityGroup>();
        AffinitySet a = new AffinitySet(49, gen, new DummyIdentity("17"));
        groups.add(a);

        HashSet<Identity> identitySet = new HashSet<Identity>();
        identitySet.add(new DummyIdentity("1"));
        identitySet.add(new DummyIdentity("2"));
        identitySet.add(new DummyIdentity("3"));
        identitySet.add(new DummyIdentity("4"));
        identitySet.add(new DummyIdentity("5"));
        identitySet.add(new DummyIdentity("6"));
        identitySet.add(new DummyIdentity("7"));
        identitySet.add(new DummyIdentity("8"));
        identitySet.add(new DummyIdentity("9"));
        identitySet.add(new DummyIdentity("10"));
        identitySet.add(new DummyIdentity("11"));
        identitySet.add(new DummyIdentity("12"));
        identitySet.add(new DummyIdentity("13"));
        identitySet.add(new DummyIdentity("14"));
        identitySet.add(new DummyIdentity("15"));
        identitySet.add(new DummyIdentity("16"));
//        identitySet.add(new DummyIdentity("17"));
        identitySet.add(new DummyIdentity("18"));
        identitySet.add(new DummyIdentity("19"));
        identitySet.add(new DummyIdentity("20"));
        identitySet.add(new DummyIdentity("21"));
        identitySet.add(new DummyIdentity("22"));
        identitySet.add(new DummyIdentity("23"));
        identitySet.add(new DummyIdentity("24"));
        identitySet.add(new DummyIdentity("25"));
        identitySet.add(new DummyIdentity("26"));
        identitySet.add(new DummyIdentity("27"));
        identitySet.add(new DummyIdentity("28"));
        identitySet.add(new DummyIdentity("29"));
        identitySet.add(new DummyIdentity("30"));
        identitySet.add(new DummyIdentity("31"));
        identitySet.add(new DummyIdentity("32"));
        identitySet.add(new DummyIdentity("33"));
        identitySet.add(new DummyIdentity("34"));
        AffinitySet b = new AffinitySet(2, gen, identitySet);
        groups.add(b);

        double modularity =
            AffinityGroupGoodness.calcModularity(builder.getAffinityGraph(),
                                                 groups);
        System.out.println("Modularity test club partition is " +
                modularity);
    }

    /**
     * A graph builder that returns a pre-made graph for a very simple toy
     * graph.
     */
    private static class TestToyBuilder extends AbstractTestGraphBuilder {

        public TestToyBuilder() {
            super(createGraph());
        }
        static private UndirectedGraph<LabelVertex, WeightedEdge> createGraph()
        {
            UndirectedGraph<LabelVertex, WeightedEdge> graph =
                    new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

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
            return graph;
        }
    }

    /**
     * A graph builder that returns a pre-made graph for a very simple toy
     * graph.
     */
    private static class TestToyWeightBuilder extends AbstractTestGraphBuilder {

        public TestToyWeightBuilder() {
            super(createGraph());
        }
        static private UndirectedGraph<LabelVertex, WeightedEdge> createGraph()
        {
            UndirectedGraph<LabelVertex, WeightedEdge> graph =
                    new UndirectedSparseGraph<LabelVertex, WeightedEdge>();

            LabelVertex[] nodes = new LabelVertex[6];
            for (int i = 1; i < nodes.length; i++) {
                // Add identities 1-5
                nodes[i] =
                        new LabelVertex(new DummyIdentity(String.valueOf(i)));
                graph.addVertex(nodes[i]);
            }

            graph.addEdge(new WeightedEdge(2), nodes[1], nodes[2]);
            graph.addEdge(new WeightedEdge(2), nodes[1], nodes[3]);
            graph.addEdge(new WeightedEdge(2), nodes[2], nodes[3]);
            graph.addEdge(new WeightedEdge(2), nodes[2], nodes[4]);
            graph.addEdge(new WeightedEdge(2), nodes[4], nodes[5]);

            Assert.assertEquals(5, graph.getVertexCount());
            Assert.assertEquals(5, graph.getEdgeCount());
            return graph;
        }
    }
}
