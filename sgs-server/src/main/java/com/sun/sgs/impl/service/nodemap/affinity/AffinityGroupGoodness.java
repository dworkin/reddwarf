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

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.auth.Identity;
import edu.uci.ics.jung.graph.Graph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 *  Utility methods for "goodness" measurements of found groups.
 */
public final class AffinityGroupGoodness {
    
    /**
     * A private constructor:  we do not want instances of this class to
     * be constructed, as it contains only static utility methods.
     */
    private AffinityGroupGoodness() {
    }

    /**
     * Given a graph and a set of partitions of it, calculate the modularity.
     * Modularity is a quality measure for the goodness of a clustering
     * algorithm, and is, essentially, the number of edges within communities
     * subtracted by the expected number of such edges.
     * <p>
     * The modularity will be a number between 0.0 and 1.0, with a higher
     * number being better.
     * <p>
     * See "Finding community structure in networks using eigenvectors
     * of matrices" 2006 Mark Newman and "Finding community structure in
     * very large networks" 2004 Clauset, Newman, Moore.
     * <p>
     * Note that modularity can only be calculated on a complete graph.
     *
     * @param graph the graph which was divided into communities
     * @param groups the communities found in the graph
     * @return the modularity of the groups found in the graph
     */
    public static double calcModularity(Graph<LabelVertex, WeightedEdge> graph,
            Collection<AffinityGroup> groups)
    {
        // NOTE: this algorithm might need to be optimized if we use it for
        // more than goodness testing.
        // m is the sum of edge weights for all edges in the graph
        long m = 0;
        for (WeightedEdge e : graph.getEdges()) {
            m = m + e.getWeight();
        }
        final long doublem = 2 * m;
        final long doublemsquare = doublem * doublem;
        // For each pair of vertices that are in the same community,
        // compute 1/(2m) * Sum(A[i,j] - P[i,j]), where P[i,j] is k[i]k[j]/2m.
        // See equation (18) in Newman's 2006 paper. P[i,j] is the probable
        // weight of an edge between vertices i and j, and A[i,j] is the
        // actual weight.  k[i] is the sum of weights of edges connected to
        // vertex i.
        //
        // Note also that modularity can be expressed as
        // Sum(e[i,i] - a[i]*a[i]) where e[i,i] is the fraction of edges inside
        // the community i and a[i] is the fraction of ends of edges that are
        // attached to vertices in community i.
        // See equation (7) in Clauset, Newman, Moore 2004 paper.
        long sum = 0;
        for (AffinityGroup g : groups) {
            // ingroup is weighted edge count within the community
            long ingroup = 0;
            // totEdges is the total number of connections for this community
            long totEdges = 0;

            Set<Identity> ids = g.getIdentities();
            int size = ids.size();
            ArrayList<LabelVertex> groupList =
                    new ArrayList<LabelVertex>(size);
            for (Identity id : ids) {
                groupList.add(new LabelVertex(id));
            }
            for (LabelVertex vertex : groupList) {
                for (WeightedEdge edge : graph.getIncidentEdges(vertex)) {
                    totEdges = totEdges + edge.getWeight();
                }
            }

            // Look at each of the pairs in the community to find the number
            // of edges within
            for (int i = 0; i < size - 1; i++) {
                LabelVertex  v1 = groupList.get(i);
                for (int j = i + 1; j < size; j++) {
                    LabelVertex v2 = groupList.get(j);
                    // Calculate the adjacency info for v1 and v2;  each edge
                    // is counted twice to account for the two vertices it
                    // connects.
                    // We allow parallel (multiple) edges in the graph so
                    // use findEdgeSet.
                    Collection<WeightedEdge> edges = graph.findEdgeSet(v1, v2);
                    for (WeightedEdge edge : edges) {
                        ingroup = ingroup + (edge.getWeight() * 2);
                    }
                }
            }
            // ingroup is e[i,i] * doublem.
            // totEdges is a[i] * doublem.
            // Multiply ingroup by doublem here so we can, outside this loop,
            // divide the sum by doublemsquare to remove the effects of counting
            // each edge twice.
            sum = sum + (ingroup * doublem - (totEdges * totEdges));
        }
        double q = (double) sum / doublemsquare;
        // Ensure that the final value is between 0.0 and 1.0.  This number
        // can go slightly negative if we have groups with single nodes.
        q = Math.min(1.0, Math.max(0.0, q));
        return q;
    }

    /**
     * Calculates Jaccard's index for a pair of affinity group collections,
     * which is a measurement of similarity of the groups found in the two
     * collections.  The value will be between {@code 0.0} and {@code 1.0},
     * with higher values indicating stronger similarity between two samples.
     * See page 8 of "Near linear time algorithm to detect community structures
     * in large-scale networks" 2007 Raghavan, Albert, Kumara.
     * <p>
     * Because Jaccard's index uses computed groups, rather than a graph,
     * it can be useful when the graphs are distributed or incomplete.
     * <p>
     * @param sample1 the first sample
     * @param sample2 the second sample
     * @return the Jaccard index, a value between {@code 0.0} and {@code 1.0},
     *    with higher values indicating more similarity
     */
    public static double calcJaccard(Collection<AffinityGroup> sample1,
                                     Collection<AffinityGroup> sample2)
    {
        // a is number of pairs of identities in same affinity group
        //    in both samples
        // b is number of pairs that are in the same affinity gruop
        //    in the first sample only
        // c is the number of pairs that in the same affinity group
        //    in the second sample only
        long a = 0;
        long b = 0;
        long c = 0;
        for (AffinityGroup group : sample1) {
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(group.getIdentities());
            int size = groupList.size();
            for (int i = 0; i < size - 1; i++) {
                Identity v1 = groupList.get(i);
                for (int j = i + 1; j < size; j++) {
                    Identity v2 = groupList.get(j);
                    // v1 and v2 are in the same group in sample1.  Are they
                    // in the same group in sample2?
                    if (inSameGroup(v1, v2, sample2)) {
                        a++;
                    } else {
                        b++;
                    }
                }
            }
        }

        for (AffinityGroup group : sample2) {
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(group.getIdentities());
            int size = groupList.size();
            for (int i = 0; i < size - 1; i++) {
                Identity v1 = groupList.get(i);
                for (int j = i + 1; j < size; j++) {
                    Identity v2 = groupList.get(j);
                    // v1 and v2 are in the same group in sample2.  Count those
                    // that are not in the same group in sample1.
                    if (!inSameGroup(v1, v2, sample1)) {
                        c++;
                    }
                }
            }
        }

        // Jaccard's index (or coefficient) is defined as a/(a+b+c).
        return ((double) a / (double) (a + b + c));
    }

    /**
     * Returns {@code true} if two identities are in the same
     * {@code AffinityGroup} in a given affinity group collection.
     * @param id1 the first identity
     * @param id2 the second identity
     * @param sample the affinity group collection
     * @return {@code true} if {@code id1} and {@code id2} are in the
     *        same affinity group in the {@code sample} collection of affinity
     *        groups
     */
    private static boolean inSameGroup(Identity id1, Identity id2,
                                       Collection<AffinityGroup> sample)
    {
        // Note:  this method doesn't assume that affinity groups will
        // contain disjoint members - it is legal for an Identity to
        // be found in two groups.
        for (AffinityGroup g : sample) {
            Set<Identity> idents = g.getIdentities();
            if (idents.contains(id1) && idents.contains(id2)) {
                return true;
            }
        }
        return false;
    }
}
