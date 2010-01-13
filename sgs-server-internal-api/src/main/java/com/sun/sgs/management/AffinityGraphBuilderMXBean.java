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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.management;

/**
 * The management interface for the affinity graph builder.
 */
public interface AffinityGraphBuilderMXBean {
    /** The name for uniquely identifying this MBean. */
    String MXBEAN_NAME = "com.sun.sgs:type=AffinityGraphBuilder";

    /**
     * Returns the configured time, in milliseconds, for each snapshot.
     * @return the snapshot time period, in milliseconds
     */
    long getSnapshotPeriod();

    /**
     * Returns the configured number of full snapshots to consider live.
     * Dead snapshots are eventually removed.
     * @return the number of live snapshots
     */
    int getSnapshotCount();

    /**
     * Returns the number of vertices in the affinity graph.
     * @return the number of vertices in the affinity graph
     */
    long getNumberVertices();

    /**
     * Returns the number of edges in the affinity graph.
     * @return the number of edges in the affinity graph
     */
    long getNumberEdges();

    /**
     * Returns the number of updates made to the affinity graph,
     * which add graph data.
     * @return the number of updates made to the affinity graph
     */
    long getUpdateCount();

    /**
     * Returns the number of times the affinity graph has been pruned to
     * remove dead data.
     * @return the number of times the affinity graph has been pruned
     */
    long getPruneCount();

    /**
     * Returns the time, in milliseconds, spent processing the affinity
     * graph due to modifications such as updates or pruning.
     * @return the amount of time spent processing the affinity graph
     */
    long getProcessingTime();
}
