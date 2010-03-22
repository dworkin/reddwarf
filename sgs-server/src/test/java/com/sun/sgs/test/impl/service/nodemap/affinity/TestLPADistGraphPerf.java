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

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupGoodness;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.dgb.DistGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dgb.DistGraphBuilderServerImpl;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.sharedutil.Objects;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.IntegrationTest;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedGraph;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
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
public class TestLPADistGraphPerf {
    private static final String APP_NAME = "TestLPADistGraph";
    private static final int WARMUP_RUNS = 100;
    private static final int RUNS = 500;

    private static Field finderField;
    static {
        try {
            finderField =
                UtilReflection.getField(NodeMappingServiceImpl.class, "finder");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Number of threads, set with data below for each run
    private int numThreads;

    @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(new Object[][]
            {{1}, {2}, {4}, {8}, {16}});
    }

    private SgsTestNode serverNode;
    private int serverPort;

    @Before
    public void beforeEachTest() throws Exception {
        serverNode = new SgsTestNode(APP_NAME, null, getProps(null));
    }

    protected Properties getProps(SgsTestNode serverNode) throws Exception {
        Properties p =
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        if (serverNode == null) {
            serverPort = SgsTestNode.getNextUniquePort();
            p.setProperty(StandardProperties.NODE_TYPE,
                          NodeType.coreServerNode.toString());
        }
        p.setProperty(DistGraphBuilderServerImpl.SERVER_PORT_PROPERTY,
                String.valueOf(serverPort));
        p.setProperty(LPADriver.GRAPH_CLASS_PROPERTY,
                      DistGraphBuilder.class.getName());
        p.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                    String.valueOf(numThreads));
        p.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "3600"); // one hour
        return p;
    }

    @After
    public void afterEachTest() throws Exception {
        if (serverNode != null) {
            serverNode.shutdown(true);
        }
    }

    public TestLPADistGraphPerf(int numThreads) {
        this.numThreads = numThreads;
    }

    @Test
    public void warmupZach() throws Exception {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            LPADriver driver = (LPADriver)
                finderField.get(
                            serverNode.getNodeMappingService());
            Set<RelocatingAffinityGroup> groups =
                driver.getGraphBuilder().
                    getAffinityGroupFinder().findAffinityGroups();
        }

        // There's no graph, so we expect an exception to be thrown
    }

    @Test
    public void testDistGraphZach() throws Exception {
        SgsTestNode node1 = null;
        SgsTestNode node2 = null;
        SgsTestNode node3 = null;
        try {
            // Create three app nodes         
            node1 = createNode();
            node2 = createNode();
            node3 = createNode();

            // Send updates to each of the node's graph listeners
            LPADriver driver1 = (LPADriver)
                finderField.get(node1.getNodeMappingService());
            LPADriver driver2 = (LPADriver)
                finderField.get(node2.getNodeMappingService());
            LPADriver driver3 = (LPADriver)
                finderField.get(node3.getNodeMappingService());

            DummyIdentity[] idents = new DummyIdentity[35];
            // Create identities for zach karate club
            // location of identities for the accesses below
            // node1: 1,4,7,10,13,16,19,22,25,28,31,34
            // node2: 2,5,8,11,14,17,20,23,26,29,32
            // node3: 3,6,9,12,15,18,21,24,27,30,33
            // Actual round robin policy might assign these differently...
            NodeMappingService nms = node1.getNodeMappingService();
            for (int i = 1; i < 35; i++) {
                idents[i] = new DummyIdentity(String.valueOf(i));
                nms.assignNode(this.getClass(), idents[i]);
            }
            
            // Node 1 uses.
            AffinityGraphBuilder builder1 = driver1.getGraphBuilder();
            AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o1"));
            detail.addAccess(new String("o2"));
            detail.addAccess(new String("o3"));
            detail.addAccess(new String("o5"));
            detail.addAccess(new String("o8"));
            detail.addAccess(new String("o11"));
            detail.addAccess(new String("o12"));
            detail.addAccess(new String("o16"));
            detail.addAccess(new String("o18"));
            detail.addAccess(new String("o20"));
            detail.addAccess(new String("o32"));
            detail.addAccess(new String("o84"));
            builder1.updateGraph(idents[1], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o1"));
            detail.addAccess(new String("o13"));
            detail.addAccess(new String("o87"));
            builder1.updateGraph(idents[4], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o2"));
            detail.addAccess(new String("o4"));
            detail.addAccess(new String("o15"));
            builder1.updateGraph(idents[7], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o7"));
            detail.addAccess(new String("o40"));
            builder1.updateGraph(idents[10], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o12"));
            detail.addAccess(new String("o13"));
            builder1.updateGraph(idents[13], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o35"));
            detail.addAccess(new String("o43"));
            builder1.updateGraph(idents[16], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o36"));
            detail.addAccess(new String("o44"));
            builder1.updateGraph(idents[19], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o20"));
            detail.addAccess(new String("o21"));
            builder1.updateGraph(idents[22], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o23"));
            detail.addAccess(new String("o26"));
            builder1.updateGraph(idents[25], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o24"));
            detail.addAccess(new String("o25"));
            detail.addAccess(new String("o26"));
            detail.addAccess(new String("o50"));
            builder1.updateGraph(idents[28], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o30"));
            detail.addAccess(new String("o31"));
            builder1.updateGraph(idents[31], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o31"));
            detail.addAccess(new String("o40"));
            detail.addAccess(new String("o41"));
            detail.addAccess(new String("o42"));
            detail.addAccess(new String("o43"));
            detail.addAccess(new String("o44"));
            detail.addAccess(new String("o45"));
            detail.addAccess(new String("o46"));
            detail.addAccess(new String("o47"));
            detail.addAccess(new String("o48"));
            detail.addAccess(new String("o49"));
            detail.addAccess(new String("o50"));
            detail.addAccess(new String("o51"));
            detail.addAccess(new String("o52"));
            detail.addAccess(new String("o53"));
            builder1.updateGraph(idents[34], detail);

            // Node 2
            AffinityGraphBuilder builder2 = driver2.getGraphBuilder();
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o1"));
            detail.addAccess(new String("o17"));
            detail.addAccess(new String("o19"));
            detail.addAccess(new String("o21"));
            detail.addAccess(new String("o30"));
            detail.addAccess(new String("o85"));
            builder2.updateGraph(idents[2], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o2"));
            detail.addAccess(new String("o9"));
            builder2.updateGraph(idents[5], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o1"));
            builder2.updateGraph(idents[8], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o8"));
            detail.addAccess(new String("o9"));
            detail.addAccess(new String("o10"));
            builder2.updateGraph(idents[11], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o84"));
            detail.addAccess(new String("o85"));
            detail.addAccess(new String("o86"));
            detail.addAccess(new String("o87"));
            detail.addAccess(new String("o41"));
            builder2.updateGraph(idents[14], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o14"));
            detail.addAccess(new String("o15"));
            builder2.updateGraph(idents[17], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o18"));
            detail.addAccess(new String("o19"));
            detail.addAccess(new String("o45"));
            builder2.updateGraph(idents[20], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o38"));
            detail.addAccess(new String("o47"));
            builder2.updateGraph(idents[23], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o22"));
            detail.addAccess(new String("o23"));
            builder2.updateGraph(idents[26], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o27"));
            detail.addAccess(new String("o51"));
            detail.addAccess(new String("o66"));
            builder2.updateGraph(idents[29], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o23"));
            detail.addAccess(new String("o66"));
            detail.addAccess(new String("o32"));
            detail.addAccess(new String("o39"));
            detail.addAccess(new String("o53"));
            builder2.updateGraph(idents[32], detail);

            // Node 3
            AffinityGraphBuilder builder3 = driver3.getGraphBuilder();
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o1"));
            detail.addAccess(new String("o6"));
            detail.addAccess(new String("o7"));
            detail.addAccess(new String("o24"));
            detail.addAccess(new String("o27"));
            detail.addAccess(new String("o33"));
            detail.addAccess(new String("o86"));
            builder3.updateGraph(idents[3], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o3"));
            detail.addAccess(new String("o4"));
            detail.addAccess(new String("o10"));
            detail.addAccess(new String("o14"));
            builder3.updateGraph(idents[6], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o5"));
            detail.addAccess(new String("o6"));
            detail.addAccess(new String("o31"));
            builder3.updateGraph(idents[9], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o11"));
            builder3.updateGraph(idents[12], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o34"));
            detail.addAccess(new String("o42"));
            builder3.updateGraph(idents[15], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o16"));
            detail.addAccess(new String("o17"));
            builder3.updateGraph(idents[18], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o37"));
            detail.addAccess(new String("o46"));
            builder3.updateGraph(idents[21], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o22"));
            detail.addAccess(new String("o25"));
            detail.addAccess(new String("o28"));
            detail.addAccess(new String("o48"));
            builder3.updateGraph(idents[24], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o29"));
            detail.addAccess(new String("o49"));
            builder3.updateGraph(idents[27], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o28"));
            detail.addAccess(new String("o29"));
            detail.addAccess(new String("o52"));
            builder3.updateGraph(idents[30], detail);
            detail = new AccessedObjectsDetailTest();
            detail.addAccess(new String("o28"));
            detail.addAccess(new String("o31"));
            detail.addAccess(new String("o33"));
            detail.addAccess(new String("o34"));
            detail.addAccess(new String("o35"));
            detail.addAccess(new String("o36"));
            detail.addAccess(new String("o37"));
            detail.addAccess(new String("o38"));
            detail.addAccess(new String("o39"));
            builder3.updateGraph(idents[33], detail);

            UndirectedGraph<LabelVertex, WeightedEdge> graphModel =
                    new ZachBuilder().getAffinityGraph();
            System.out.println("MODEL GRAPH IS " + graphModel);
            LPADriver driver = (LPADriver)
                finderField.get(serverNode.getNodeMappingService());
            GraphListener serverListener = driver.getGraphListener();
            AffinityGraphBuilder builder = driver.getGraphBuilder();
            // The graph can only be found on the server side.  Let's make
            // sure it looks like the expected Zachary graph.
            // The core server graph listener builds a DistGraphBuilder,
            // which (on the core node) creates the DistGraphBuilderServerImpl.
            // We can find it if we look for the group finder.
            AffinityGraphBuilder serverBuilder =
                    (AffinityGraphBuilder) builder.getAffinityGroupFinder();
            UndirectedGraph<LabelVertex, WeightedEdge> graph =
                    serverBuilder.getAffinityGraph();
            System.out.println("GRAPH IS " + graph);
            Assert.assertEquals(34, graph.getVertexCount());
            Assert.assertEquals(78, graph.getEdgeCount());
            ProfileCollector col =
                serverNode.getSystemRegistry().
                    getComponent(ProfileCollector.class);
            AffinityGroupFinderMXBean bean = (AffinityGroupFinderMXBean)
                col.getRegisteredMBean(AffinityGroupFinderMXBean.MXBEAN_NAME);
            assertNotNull(bean);
            bean.clear();

            // Be sure the consumer is turned on
            col.getConsumer(AffinityGroupFinderStats.CONS_NAME).
                    setProfileLevel(ProfileLevel.MAX);
            double avgMod = 0.0;
            double maxMod = 0.0;
            double minMod = 1.0;
            for (int i = 0; i < RUNS; i++) {
                Set<AffinityGroup> groups =
                    Objects.uncheckedCast(
                        builder.getAffinityGroupFinder().findAffinityGroups());    
                double mod =
                    AffinityGroupGoodness.calcModularity(graphModel, groups);

                avgMod = avgMod + mod;
                maxMod = Math.max(maxMod, mod);
                minMod = Math.min(minMod, mod);
            }
            System.out.printf("DIST (%d runs, %d threads): " +
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
        } finally {
            if (node1 != null) {
                node1.shutdown(false);
            }
            if (node2 != null) {
                node2.shutdown(false);
            }
            if (node3 != null) {
                node3.shutdown(false);
            }
        }
    }


    // Utility
    private SgsTestNode createNode() throws Exception {
        Properties p =
                SgsTestNode.getDefaultProperties("PerfTest", serverNode, null);
            p.setProperty(DistGraphBuilderServerImpl.SERVER_PORT_PROPERTY,
                    String.valueOf(serverPort));
            p.setProperty(LPADriver.GRAPH_CLASS_PROPERTY,
                          DistGraphBuilder.class.getName());
            p.put("com.sun.sgs.impl.service.nodemap.affinity.numThreads",
                        String.valueOf(numThreads));
            return new SgsTestNode(serverNode, null, p);
    }
    /**
     * Private implementation of {@code AccessedObjectsDetail}.
     * It allows adding and getting accessed objects only.
     */
    private static class AccessedObjectsDetailTest
        implements AccessedObjectsDetail
    {
        private final LinkedHashSet<AccessedObject> accessList =
             new LinkedHashSet<AccessedObject>();

        void addAccess(Object obj) {
            accessList.add(new AccessedObjectImpl(obj));
        }

        public List<AccessedObject> getAccessedObjects() {
            return new ArrayList<AccessedObject>(accessList);
        }

        public ConflictType getConflictType() {
            throw new UnsupportedOperationException("Not supported.");
        }

        public byte[] getConflictingId() {
            throw new UnsupportedOperationException("Not supported.");
        }

    }

    /**
     * Private implementation of {@code AccessedObject}. It supports
     * getting object ids only.
     */
    private static class AccessedObjectImpl implements AccessedObject {
        private final Object objId;

        /** Creates an instance of {@code AccessedObjectImpl}. */
        AccessedObjectImpl(Object objId) {
            this.objId = objId;
        }

        /* Implement AccessedObject. */

        /** {@inheritDoc} */
        public Object getObjectId() {
            return objId;
        }
        /** {@inheritDoc} */
        public AccessType getAccessType() {
            throw new UnsupportedOperationException("Not supported.");
        }
        /** {@inheritDoc} */
        public Object getDescription() {
            throw new UnsupportedOperationException("Not supported.");
        }
        /** {@inheritDoc} */
        public String getSource() {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
