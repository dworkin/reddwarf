/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
