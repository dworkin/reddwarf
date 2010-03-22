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

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import com.sun.sgs.management.AffinityGraphBuilderMXBean;
import com.sun.sgs.profile.AggregateProfileCounter;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import edu.uci.ics.jung.graph.Graph;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.StandardMBean;

/**
 * The exposed management information for the affinity graph builder.
 */
public class AffinityGraphBuilderStats extends StandardMBean
        implements AffinityGraphBuilderMXBean
{
    /**
     * Our consumer name, created with at {@code ProfileLevel.MEDIUM}.
     */
    public static final String CONS_NAME = "com.sun.sgs.AffinityGraphBuilder";
    /** The graph we are building. */
    private final Graph<?, ?> graph;

    // Configuration info
    /** Snapshot count. */
    private final int snapCount;
    /** Snapshot period. */
    private final long snapPeriod;

    // Counters that are updated by the builders
    /** Time spent processing the graph. */
    private final AggregateProfileCounter processingTime;
    /** Number of graph updates. */
    private final AggregateProfileCounter updateCount;
    /** Number of graph prunes. */
    private final AggregateProfileCounter pruneCount;

    /**
     * Constructs a stats instance.
     * @param collector the profile collector
     * @param graph the graph
     * @param snapCount the configured snapshot count
     * @param snapPeriod the configured snapshot period
     */
    public AffinityGraphBuilderStats(ProfileCollector collector,
            Graph<?, ?> graph, int snapCount, long snapPeriod)
    {
        super(AffinityGraphBuilderMXBean.class, true);
        if (graph == null) {
	    throw new NullPointerException("null graph");
	}
        this.graph = graph;
        this.snapCount = snapCount;
        this.snapPeriod = snapPeriod;
        ProfileConsumer consumer = collector.getConsumer(CONS_NAME);
        ProfileLevel level = ProfileLevel.MEDIUM;
        ProfileDataType type = ProfileDataType.AGGREGATE;

        processingTime = (AggregateProfileCounter)
                consumer.createCounter("processingTime", type, level);
        updateCount = (AggregateProfileCounter)
                consumer.createCounter("updateCount", type, level);
        pruneCount = (AggregateProfileCounter)
                consumer.createCounter("pruneCount", type, level);
    }

    /** {@inheritDoc} */
    public long getNumberEdges() {
        return graph.getEdgeCount();
    }

    /** {@inheritDoc} */
    public long getNumberVertices() {
        return graph.getVertexCount();
    }

    /** {@inheritDoc} */
    public long getProcessingTime() {
        return processingTime.getCount();
    }

    /** {@inheritDoc} */
    public long getUpdateCount() {
        return updateCount.getCount();
    }

    /** {@inheritDoc} */
    public long getPruneCount() {
        return pruneCount.getCount();
    }

    /** {@inheritDoc} */
    public int getSnapshotCount() {
        return snapCount;
    }

    /** {@inheritDoc} */
    public long getSnapshotPeriod() {
        return snapPeriod;
    }

    // Overrides for StandardMBean information, giving JMX clients
    // (like JConsole) more information for better displays.

    /** {@inheritDoc} */
    protected String getDescription(MBeanInfo info) {
        return "An MXBean for examining affinity graph builders";
    }

    /** {@inheritDoc} */
    protected String getDescription(MBeanAttributeInfo info) {
        String description = null;
        if (info.getName().equals("NumberEdges")) {
            description = "The number of edges in the affinity graph";
        } else if (info.getName().equals("NumberVertices")) {
            description = "The number of vertices in the affinity graph";
        } else if (info.getName().equals("ProcessingTime")) {
            description = "The total amount of time, in milliseconds, spent " +
                    "processing (modifying) the affinity graph.";
        } else if (info.getName().equals("UpdateCount")) {
            description = "The number of updates (additions) to the graph.";
        } else if (info.getName().equals("PruneCount")) {
            description = "The number of times the graph was pruned (had dead"
                   +  " information removed)";
        } else if (info.getName().equals("SnapshotCount")) {
            description = "The configured number of live snapshots of the"
                   + " graph to keep.";
        } else if (info.getName().equals("SnapshotPeriod")) {
            description = "The configured length of time, in milliseconds,"
                    + " for each snapshot.";
        }
        return description;
    }

    // Updators
    /**
     * Increments the processing time by the input argument.
     * @param inc the amount to increment the processing time by
     */
    public void processingTimeInc(long inc) {
        processingTime.incrementCount(inc);
    }

    /**
     * Increments the update count.
     */
    public void updateCountInc() {
        updateCount.incrementCount();
    }

    /**
     * Increments the prune count.
     */
    public void pruneCountInc() {
        pruneCount.incrementCount();
    }
}
