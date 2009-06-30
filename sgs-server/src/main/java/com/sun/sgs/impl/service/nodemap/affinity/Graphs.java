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

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import edu.uci.ics.jung.graph.Graph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 *  Utility methods for use with affinity graphs.
 */
public final class Graphs {
    
    /**
     * A private constructor:  we do not want instances of this class to
     * be constructed, as it contains only static utility methods.
     */
    private Graphs() {
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
     *
     * @param graph the graph which was devided into communities
     * @param groups the communities found in the graph
     * @return the modularity of the groups found in the graph
     */
    public static double calcModularity(Graph<LabelVertex, WeightedEdge> graph,
            Collection<AffinityGroup> groups)
    {
        // NOTE: this algorithm might need to be optimized if we use it for
        // more than goodness testing.
        long m = 0;
        for (WeightedEdge e : graph.getEdges()) {
            m = m + e.getWeight();
        }
        final long doublem = 2 * m;

        // For each pair of vertices that are in the same community,
        // compute 1/(2m) * Sum(Aij - Pij), where Pij is kikj/2m.
        // See equation (18) in Newman's 2006 paper.
        //
        // Note also that modularity can be expressed as
        // Sum(eii - ai*ai) where eii is the fraction of edges in the group i
        // and ai is the fraction of ends of edges that are attached to
        // vertices in community i.
        // See equation (7) in Clauset, Newman, Moore 2004 paper.
        double q = 0;

        for (AffinityGroup g : groups) {
            // ingroup is weighted edge count within the community
            long ingroup = 0;
            // totEdges is the total number of connections for this community
            long totEdges = 0;

            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(g.getIdentities());
            for (Identity id : groupList) {
                for (WeightedEdge edge :
                    graph.getIncidentEdges(new LabelVertex(id))) {
                    totEdges = totEdges + edge.getWeight();
                }
            }
            // Look at each of the pairs in the community to find the number
            // of edges within
            while (!groupList.isEmpty()) {
                // Get the first identity
                Identity v1 = groupList.remove(0);
                for (Identity v2 : groupList) {
                    Collection<WeightedEdge> edges =
                        graph.findEdgeSet(new LabelVertex(v1),
                                          new LabelVertex(v2));
                    // Calculate the adjacency info for v1 and v2
                    // We allow parallel, weighted edges
                    for (WeightedEdge edge : edges) {
                        ingroup = ingroup + (edge.getWeight() * 2);
                    }
                }
            }

            double ai = (double) totEdges / doublem;
            q = q + (((double) ingroup / doublem) - (ai * ai));
        }
        // Ensure that the final value is between 0.0 and 1.0.  This number
        // can go slightly negative if we have groups with single nodes.
        q = Math.min(1.0, Math.max(0.0, q));
        return q;
    }

    /**
     * Calculates Jaccard's index for a pair of affinity groups, which is
     * a measurement of similarity.  The value will be between {@code 0.0}
     * and {@code 1.0}, with higher values indicating stronger similarity
     * between two samples.
     *
     * @param sample1 the first sample
     * @param sample2 the second sample
     * @return the Jaccard index, a value between {@code 0.0} and {@code 1.0},
     *    with higer values indicating more similarity
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
            while (!groupList.isEmpty()) {
                Identity v1 = groupList.remove(0);
                for (Identity v2 : groupList) {
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
            while (!groupList.isEmpty()) {
                Identity v1 = groupList.remove(0);
                for (Identity v2 : groupList) {
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

    private static boolean inSameGroup(Identity id1, Identity id2,
                                       Collection<AffinityGroup> group)
    {
        for (AffinityGroup g : group) {
            Set<Identity> idents = g.getIdentities();
            if (idents.contains(id1) && idents.contains(id2)) {
                return true;
            }
        }
        return false;
    }
}
