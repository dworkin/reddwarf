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

import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.GraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagation;
import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagationServer;
import com.sun.sgs.tools.test.IntegrationTest;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.runners.Parameterized;
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

    private static LabelPropagationServer lpaServer;

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
        lpaServer = new LabelPropagationServer(new Properties());
    }

    @AfterClass
    public static void after() throws Exception {
        lpaServer.shutdown();
    }

    @Test
    public void warmup() throws Exception {
        final int node = 1;
        // Warm up the compilers
        LabelPropagation lpa =
           new LabelPropagation(new ZachBuilder(), node,
                                "localhost",
                                LabelPropagationServer.DEFAULT_SERVER_PORT,
                                false,
                                numThreads);

        for (int i = 0; i < WARMUP_RUNS; i++) {
            lpa.singleNodeFindCommunities();
        }
        lpaServer.removeNode(node);
        lpa.shutdown();
    }

    @Test
    public void testZachary() throws Exception {
        final int node = 1;
        GraphBuilder builder = new ZachBuilder();
        // second argument true:  gather statistics
        LabelPropagation lpa =
            new LabelPropagation(builder, node,
                                 "localhost",
                                 LabelPropagationServer.DEFAULT_SERVER_PORT,
                                 true, numThreads);

        long avgTime = 0;
        int avgIter = 0;
        double avgMod  = 0.0;
        double maxMod = 0.0;
        double minMod = 1.0;
        long maxTime = 0;
        long minTime = Integer.MAX_VALUE;
        for (int i = 0; i < RUNS; i++) {
            Collection<AffinityGroup> groups = lpa.singleNodeFindCommunities();
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
        System.out.printf("XXX (%d runs, %d threads): " +
                          "avg time : %4.2f ms, " +
                          " time range [%d - %d ms] " +
                          " avg iters : %4.2f, avg modularity: %.4f, " +
                          " modularity range [%.4f - %.4f] %n",
                          RUNS, numThreads,
                          avgTime/(double) RUNS,
                          minTime, maxTime,
                          avgIter/(double) RUNS,
                          avgMod/(double) RUNS,
                          minMod, maxMod);
        lpaServer.removeNode(node);
        lpa.shutdown();
    }
}
