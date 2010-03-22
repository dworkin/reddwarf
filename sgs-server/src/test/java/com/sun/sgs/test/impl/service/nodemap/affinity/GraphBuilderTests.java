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
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.kernel.SystemIdentity;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderStats;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AbstractAffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.management.AffinityGroupFinderMXBean;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.AccessedObjectsDetail.ConflictType;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Pair;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Common tests and support for all graph builder/LPA implementations.
 */
public class GraphBuilderTests {
    private static Class<?> profileReportImplClass;
    protected static Constructor<?> profileReportImplConstructor;
    protected static Method setAccessedObjectsDetailMethod;
    protected static Field finderField;
    static {
        try {
            profileReportImplClass =
                Class.forName("com.sun.sgs.impl.profile.ProfileReportImpl");
            profileReportImplConstructor =
                UtilReflection.getConstructor(profileReportImplClass,
                    KernelRunnable.class, Identity.class,
                    long.class, int.class);
            setAccessedObjectsDetailMethod =
                UtilReflection.getMethod(profileReportImplClass,
                    "setAccessedObjectsDetail", AccessedObjectsDetail.class);
            finderField =
                UtilReflection.getField(NodeMappingServiceImpl.class, "finder");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // The name of our test application
    protected final String appName;
 
    // The listener created for each test
    protected GraphListener listener;
    // The builder used by the listener
    protected AffinityGraphBuilder builder;

    protected Properties props;

    protected SgsTestNode serverNode;
    protected SgsTestNode node;

    // The LPA drivers, one for updating graphs, the other for finding groups.
    // These can be the same instance
    protected LPADriver graphDriver;
    protected LPADriver groupDriver;

    protected int serverPort;
    /**
     * Create this test class.
     * @param appName the name of our application
     */
    public GraphBuilderTests(String appName) {
        this.appName = appName;

    }

    @Before
    public void beforeEachTest() throws Exception {
        beforeEachTest(null);
    }

    protected void beforeEachTest(Properties addProps) throws Exception {
        props = getProps(null, addProps);
        serverNode = new SgsTestNode(appName, null, props);
        groupDriver = (LPADriver)
            finderField.get(serverNode.getNodeMappingService());
        // Create a new app node
        props = getProps(serverNode, addProps);
        node = new SgsTestNode(serverNode, null, props);
        graphDriver = (LPADriver)
                finderField.get(node.getNodeMappingService());
        listener = graphDriver.getGraphListener();
        builder = graphDriver.getGraphBuilder();
    }

    protected Properties getProps(SgsTestNode serverNode) throws Exception {
        return getProps(serverNode, null);
    }

    protected Properties getProps(SgsTestNode serverNode, Properties addProps)
            throws Exception
    {
        Properties p =
                SgsTestNode.getDefaultProperties(appName, serverNode, null);
        if (serverNode == null) {
            serverPort = SgsTestNode.getNextUniquePort();
            p.setProperty(StandardProperties.NODE_TYPE,
                          NodeType.coreServerNode.toString());
        }
        p.setProperty(LabelPropagationServer.SERVER_PORT_PROPERTY,
                String.valueOf(serverPort));
        p.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "3600"); // one hour
        if (addProps != null) {
            for (Map.Entry<Object, Object> entry : addProps.entrySet()) {
                p.put(entry.getKey(), entry.getValue());
            }
        }
        return p;
    }

    protected void startNewNode(Properties addProps) throws Exception {
        if (node != null) {
            node.shutdown(false);
            node = null;
            Thread.sleep(100);
        }
        props = getProps(serverNode);
        for (Map.Entry<Object, Object> entry : addProps.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }
        node =  new SgsTestNode(serverNode, null, props);
        graphDriver = (LPADriver)
                finderField.get(node.getNodeMappingService());
        listener = graphDriver.getGraphListener();
        builder = graphDriver.getGraphBuilder();
    }

    @After
    public void afterEachTest() throws Exception {
        if (node != null) {
            node.shutdown(false);
            node = null;
        }
        if (serverNode != null) {
            serverNode.shutdown(true);
            serverNode = null;
        }
    }

    @Test
    public void testConstructor() {
        // empty graph
        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertNotNull(graph);
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
    }

    @Test
    public void testNoDetail() throws Exception {
        ProfileReport report = makeReport(new IdentityImpl("something"));
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        // no accessed objects
        Assert.assertEquals(0, graph.getVertexCount());
    }

    @Test
    public void testOneAccess() throws Exception {
        ProfileReport report = makeReport(new IdentityImpl("something"));
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(1, graph.getVertexCount());
    }

    @Test
    public void testOneEdge() throws Exception {
        // Note that the graph listener believes edges are the same
        // objects if their reported objIds are equal.
        ProfileReport report = makeReport(new IdentityImpl("something"));
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(new IdentityImpl("somethingElse"));

        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());
    }

    @Test
    public void testOneEdgeTwice() throws Exception {
        ProfileReport report = makeReport(new IdentityImpl("something"));
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(new IdentityImpl("somethingElse"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(new IdentityImpl("something"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());

        for (WeightedEdge e : graph.getEdges()) {
            Assert.assertEquals(1, e.getWeight());
            validateEdgeEndpoints(e, graph);
        }
    }

    /**
     * Checks that the endpoints of a graph edge are also the same
     * as some vertex in the graph vertices.  This is a helpful graph
     * consistency check because our LabelVertices implement equal, and
     * we want the graph vertices and edges to be the same (==).
     * @param edge
     */
    private void validateEdgeEndpoints(WeightedEdge edge, 
                                       Graph<LabelVertex, WeightedEdge> graph)
    {
        Assert.assertNotNull(edge);
        Assert.assertNotNull(graph);
        Collection<LabelVertex> allvertices = graph.getVertices();
        Pair<LabelVertex> endpoints = graph.getEndpoints(edge);
        Assert.assertNotNull(endpoints);
        LabelVertex end1 = endpoints.getFirst();
        LabelVertex end2 = endpoints.getSecond();
        boolean ok = false;
        for (LabelVertex v : allvertices) {
            if (v == end1) {
                ok = true;
                break;
            }
        }
        Assert.assertTrue("end1 check", ok);
        ok = false;
        for (LabelVertex v : allvertices) {
            if (v == end2) {
                ok = true;
                break;
            }
        }
        Assert.assertTrue("end2 check", ok);
    }

    @Test
    public void testIncEdgeWeight() throws Exception {
        ProfileReport report = makeReport(new IdentityImpl("something"));
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(new IdentityImpl("somethingElse"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());

        for (WeightedEdge e : graph.getEdges()) {
            Assert.assertEquals(1, e.getWeight());
        }

        report = makeReport(new IdentityImpl("something"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        graph = builder.getAffinityGraph();
        System.out.println("Second time: " + graph);
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());

        for (WeightedEdge e : graph.getEdges()) {
            Assert.assertEquals(2, e.getWeight());
        }

        report = makeReport(new IdentityImpl("something"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        graph = builder.getAffinityGraph();
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());

        for (WeightedEdge e : graph.getEdges()) {
            // don't expect edge weight to have been updated
            Assert.assertEquals(2, e.getWeight());
        }
    }

    @Test
    public void testIgnoreSystem() throws Exception {
        ProfileReport report = makeReport(new SystemIdentity("system"));
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(new IdentityImpl("somethingElse"));

        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(1, graph.getVertexCount());
    }

    @Test
    public void testFourReports() throws Exception {
        Identity identA = new IdentityImpl("A");
        Identity identB = new IdentityImpl("B");
        Identity identC = new IdentityImpl("C");
        Identity identD = new IdentityImpl("D");

        LabelVertex vertA = new LabelVertex(identA);
        LabelVertex vertB = new LabelVertex(identB);
        LabelVertex vertC = new LabelVertex(identC);
        LabelVertex vertD = new LabelVertex(identD);

        ProfileReport report = makeReport(identA);
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj2"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identB);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identC);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj4"));
        detail.addAccess(new String("obj2"));
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identD);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj4"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(4, graph.getVertexCount());

        // Identity A will now use obj3, so get links with B and C
        report = makeReport(identA);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        graph = builder.getAffinityGraph();
        System.out.println("New Graph: " + graph);
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(2, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertC, vertD).getWeight());
    }

    @Test
    public void testThreeIdentMultOneObject() throws Exception {
        Identity identA = new IdentityImpl("A");
        Identity identB = new IdentityImpl("B");
        Identity identC = new IdentityImpl("C");
        ProfileReport report = makeReport(identA);
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);
        report = makeReport(identA);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identB);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identC);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(3, graph.getEdgeCount());
        Assert.assertEquals(3, graph.getVertexCount());

        System.out.println("Graph is : ");
        System.out.println(graph);

        for (WeightedEdge e : graph.getEdges()) {
            // don't expect edge weight to have been updated
            Assert.assertEquals(2, e.getWeight());
        }
    }

    @Test
    public void testGetVertexNotThere() {
        // Needed for the bipartite version
        builder.getAffinityGraph();
        LabelVertex v = builder.getVertex(new IdentityImpl("None"));
        Assert.assertNull(v);
    }

    @Test
    public void testGetVertex() throws Exception {
        Identity identA = new IdentityImpl("A");
        Identity identB = new IdentityImpl("B");
        ProfileReport report = makeReport(identA);
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);
        report = makeReport(identA);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        // Needed for the bipartite version
        builder.getAffinityGraph();
        LabelVertex v = builder.getVertex(identA);
        Assert.assertEquals(identA, v.getIdentity());
        LabelVertex v1 = builder.getVertex(identB);
        Assert.assertNull(v1);

        report = makeReport(identB);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        // Needed for the bipartite version
        builder.getAffinityGraph();
        v1 = builder.getVertex(identB);
        Assert.assertEquals(identB, v1.getIdentity());
        // can still find a as well
        v = builder.getVertex(identA);
        Assert.assertEquals(identA, v.getIdentity());
    }

    @Test
    public void testGraphPruner() throws Exception {
        // First period
        testFourReports();
        Method getPruneTaskMethod =
                UtilReflection.getMethod(builder.getClass(), "getPruneTask");
        Runnable pruneTask = (Runnable) getPruneTaskMethod.invoke(builder);
        pruneTask.run();
        // Second period - nothing added to graph, so expect it to empty out
        pruneTask.run();
        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
    }

    @Test
    public void testGraphPrunerCountTwo() throws Exception {
        node.shutdown(false);
        Properties p = new Properties();
        p.setProperty(AbstractAffinityGraphBuilder.PERIOD_COUNT_PROPERTY, "2");
        startNewNode(p);

        LabelVertex vertA = new LabelVertex(new IdentityImpl("A"));
        LabelVertex vertB = new LabelVertex(new IdentityImpl("B"));
        LabelVertex vertC = new LabelVertex(new IdentityImpl("C"));
        LabelVertex vertD = new LabelVertex(new IdentityImpl("D"));

        // Each update to the graph is followed by a pruning task run,
        // to simulate a pruning time period.
        testFourReports();
        Method getPruneTaskMethod =
                UtilReflection.getMethod(builder.getClass(), "getPruneTask");
        Runnable pruneTask = (Runnable) getPruneTaskMethod.invoke(builder);
        pruneTask.run();
        // 2nd period

        // Graph should still be intact:  not enough periods to clean up yet.
        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(2, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertC, vertD).getWeight());

        addFourReports();

        // 3rd period - no additions
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        // Weights are doubled from testFourReports
        Assert.assertEquals(4, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(4, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertC, vertD).getWeight());

        // 4th period - no additions, back to single weights
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(2, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertC, vertD).getWeight());

        // 5th period - no additions, back to empty graph
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());

        testFourReports();
        // 6th period
        pruneTask.run();
        addFourReports();

        // 7th period
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        // Weights are doubled from testFourReports
        Assert.assertEquals(4, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(4, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertC, vertD).getWeight());
        addFourReports();

        // 8th period - no addition
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(4, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(4, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertC, vertD).getWeight());

        // 9th period
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(2, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertC, vertD).getWeight());

        // 10th period
        pruneTask.run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
    }

    // The reports added in testFourReports, without the assertions.
    protected void addFourReports() throws Exception {
        Identity identA = new IdentityImpl("A");
        Identity identB = new IdentityImpl("B");
        Identity identC = new IdentityImpl("C");
        Identity identD = new IdentityImpl("D");
        ProfileReport report = makeReport(identA);
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj2"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identB);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identC);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj4"));
        detail.addAccess(new String("obj2"));
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(identD);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj4"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        // Identity A will now use obj3, so get links with B and C
        report = makeReport(identA);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGraphBuilderBadCount() throws Exception {
        Properties p = new Properties();
        props = getProps(serverNode);
        p.setProperty(AbstractAffinityGraphBuilder.PERIOD_COUNT_PROPERTY, "0");

        try {
            startNewNode(p);
        } catch (InvocationTargetException e) {
            unwrapException(e);
        }
    }

    @Test
    public void testShutdownTwice() {
        graphDriver.shutdown();
        graphDriver.shutdown();
    }

    @Test
    public void testEnableTwice() {
        graphDriver.enable();
        graphDriver.enable();
    }

    @Test
    public void testDisableTwice() {
        graphDriver.disable();
        graphDriver.disable();
    }

    @Test(expected=IllegalStateException.class)
    public void testShutdownDisable() {
        graphDriver.shutdown();
        graphDriver.disable();
    }

    @Test(expected=IllegalStateException.class)
    public void testShutdownEnable() {
        graphDriver.shutdown();
        graphDriver.enable();
    }

    @Test(expected=IllegalStateException.class)
    public void testShutdownUpdateGraph() throws Exception {
        graphDriver.shutdown();
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        graphDriver.getGraphBuilder().
                updateGraph(new IdentityImpl("something"), detail);
    }

    @Test(expected=IllegalStateException.class)
    public void testShutdownFindGroups() throws Exception {
        groupDriver.shutdown();
        groupDriver.getGraphBuilder().
                getAffinityGroupFinder().findAffinityGroups();
    }

    @Test
    public void testListenerDisable() throws Exception {
        ProfileReport report = makeReport(new IdentityImpl("something"));
        AccessedObjectsDetailTest detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        report = makeReport(new IdentityImpl("somethingElse"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());

        // Now, disable
        graphDriver.disable();

        // And add more stuff
        report = makeReport(new IdentityImpl("somethingDifferent"));
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj1"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);

        graph = builder.getAffinityGraph();
        Assert.assertEquals(1, graph.getEdgeCount());
        Assert.assertEquals(2, graph.getVertexCount());

        // Renable
        graphDriver.enable();
        listener.report(report);
        graph = builder.getAffinityGraph();
        Assert.assertEquals(3, graph.getEdgeCount());
        Assert.assertEquals(3, graph.getVertexCount());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDriverBadValue() throws Exception {
        Properties addProps = new Properties();
        addProps.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "1");
        try {
            startNewNode(addProps);
        } catch (InvocationTargetException e) {
            unwrapException(e);
        }
    }

    @Test
    public void testDriverSmallValueNoData() throws Exception {
        afterEachTest();

        Properties addProps = new Properties();
        // Add in a small update freq, 5 seconds
        addProps.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "5");
        beforeEachTest(addProps);

        ProfileCollector col;
        if (serverNode != null) {
            col = serverNode.getSystemRegistry().
                    getComponent(ProfileCollector.class);
        } else {
            col = node.getSystemRegistry().
                    getComponent(ProfileCollector.class);
        }
        // Set the profiling level because we will use the results of
        // profiling in this test
        col.getConsumer(AffinityGroupFinderStats.CONS_NAME).
                setProfileLevel(ProfileLevel.MAX);
        graphDriver.enable();
        Thread.sleep(5500);
       
        AffinityGroupFinderStats stats = (AffinityGroupFinderStats)
            col.getRegisteredMBean(AffinityGroupFinderMXBean.MXBEAN_NAME);
        Assert.assertNotNull(stats);
        Assert.assertTrue("stats should be updated", stats.getNumberRuns() > 0);
    }

    /* Utility methods and classes. */
    protected ProfileReport makeReport(Identity id) throws Exception {
        return (ProfileReport) profileReportImplConstructor.newInstance(
                    null, id, System.currentTimeMillis(), 1);
    }

    /**
     * Unwraps an InvocationTargetException to throw the root cause.
     */
    protected void unwrapException(InvocationTargetException e)
            throws Exception
    {
        // Try to unwrap any nested exceptions - they will be wrapped
        // because the kernel instantiating via reflection
        Throwable cause = e.getCause();
        while (cause instanceof InvocationTargetException) {
            cause = cause.getCause();
        }
        if (cause instanceof Exception) {
            throw (Exception) cause;
        } else {
            throw e;
        }
    }
    /**
     * Private implementation of {@code AccessedObjectsDetail}.
     * It allows adding and getting accessed objects only.
     */
    protected static class AccessedObjectsDetailTest
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
