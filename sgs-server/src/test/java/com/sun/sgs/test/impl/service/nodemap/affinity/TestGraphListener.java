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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.SystemIdentity;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.service.nodemap.affinity.BipartiteGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.GraphListener;
import com.sun.sgs.impl.service.nodemap.affinity.GraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedGraphBuilder;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import edu.uci.ics.jung.graph.Graph;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *  Tests for the graph listener and graph builders
 *
 */
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestGraphListener {

   @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(
            new Object[][] {{"default"},
                            {WeightedGraphBuilder.class.getName()},
                            {BipartiteGraphBuilder.class.getName()}});
    }
   
    private static Class<?> profileReportImplClass;
    private static Constructor<?> profileReportImplConstructor;
    private static Method setAccessedObjectsDetailMethod;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // The listener created for each test
    private GraphListener listener;
    // The builder used by the listener
    private GraphBuilder builder;
    // The collector used by the listener/builder
    private ProfileCollector collector;

    private final String builderName;
    
    /**
     * Create this test class.
     * @param builderName the type of graph builder to use
     */
    public TestGraphListener(String builderName) {
        if (builderName.equals("default")) {
            this.builderName = null;
        } else {
            this.builderName = builderName;
        }
        System.err.println("Graph builder used is: " + builderName);
        
    }
    
    @Before
    public void beforeEachTest() throws Exception {
        Properties p = new Properties();
        if (builderName != null) {
            p.setProperty(GraphListener.GRAPH_CLASS_PROPERTY, builderName);
        }
        collector = new ProfileCollectorImpl(ProfileLevel.MIN, p, null);
        listener = new GraphListener(collector, p);
        builder = listener.getGraphBuilder();
    }

    @After
    public void afterEachTest() {
        listener.shutdown();
        collector.shutdown();
    }
    
    @Test
    public void testConstructor() {
        // empty graph
        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertNotNull(graph);
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
        // empty obj use
        ConcurrentMap<Object, ConcurrentMap<Identity, AtomicLong>> objUse =
                builder.getObjectUseMap();
        Assert.assertNotNull(objUse);
        Assert.assertEquals(0, objUse.size());
        // empty conflicts
        ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>> conflicts =
                builder.getConflictMap();
        Assert.assertNotNull(conflicts);
        Assert.assertEquals(0, conflicts.size());

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
        }
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
    public void testGraphPruner() throws Exception {
        // First period
        testFourReports();
        builder.getPruneTask().run();
        // Second period - nothing added to graph, so expect it to empty out
        builder.getPruneTask().run();
        Graph<LabelVertex, WeightedEdge> graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
    }

    @Test
    public void testGraphPrunerCountTwo() throws Exception {
        Properties p = new Properties();
        if (builderName != null) {
            p.setProperty(GraphListener.GRAPH_CLASS_PROPERTY, builderName);
        }
        p.setProperty(GraphBuilder.PERIOD_COUNT_PROPERTY, "2");
        listener = new GraphListener(collector, p);
        builder = listener.getGraphBuilder();

        LabelVertex vertA = new LabelVertex(new IdentityImpl("A"));
        LabelVertex vertB = new LabelVertex(new IdentityImpl("B"));
        LabelVertex vertC = new LabelVertex(new IdentityImpl("C"));
        LabelVertex vertD = new LabelVertex(new IdentityImpl("D"));

        // Each update to the graph is followed by a pruning task run,
        // to simulate a pruning time period.
        testFourReports();
        builder.getPruneTask().run();
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
        builder.getPruneTask().run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        // Weights are doubled from testFourReports
        Assert.assertEquals(4, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(4, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertC, vertD).getWeight());

        // 4th period - no additions, back to single weights
        builder.getPruneTask().run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(2, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertC, vertD).getWeight());

        // 5th period - no additions, back to empty graph
        builder.getPruneTask().run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
  
        testFourReports();
        // 6th period
        builder.getPruneTask().run();
        addFourReports();

        // 7th period
        builder.getPruneTask().run();
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
        builder.getPruneTask().run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(4, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(4, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertC, vertD).getWeight());

        // 9th period
        builder.getPruneTask().run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(4, graph.getVertexCount());
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(2, graph.findEdge(vertA, vertB).getWeight());
        Assert.assertEquals(2, graph.findEdge(vertA, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertB, vertC).getWeight());
        Assert.assertEquals(1, graph.findEdge(vertC, vertD).getWeight());

        // 10th period
        builder.getPruneTask().run();
        graph = builder.getAffinityGraph();
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
    }

    // The reports added in testFourReports, without the assertions.
    private void addFourReports() throws Exception {
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

    @Test(expected=NullPointerException.class)
    public void testNoteConflictDetectedBadObjId() throws Throwable {
        if (builderName == null) {
            // We'll also test it when explicitly called - much easier this way
            throw new NullPointerException("empty test");
        }
        Class builderClass = Class.forName(builderName);
        // args:  object, node, forUpdate
        Method meth = UtilReflection.getMethod(builderClass,
            "noteConflictDetected", Object.class, long.class, boolean.class);

        Object obj = null;
        long nodeId = 99L;
        try {
            meth.invoke(builder, obj, nodeId, false);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
    
    @Test
    public void testNoteConflictDetected() throws Exception {
        if (builderName == null) {
            // We'll also test it when explicitly called - much easier this way
            return;
        }
        Class builderClass = Class.forName(builderName);
        // args:  object, node, forUpdate
        Method meth = UtilReflection.getMethod(builderClass,
            "noteConflictDetected", Object.class, long.class, boolean.class);
        
        Object obj = new String("obj");
        long nodeId = 99L;
        meth.invoke(builder, obj, nodeId, false);
        ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>> conflictMap =
                builder.getConflictMap();
        Assert.assertEquals(1, conflictMap.size());
        ConcurrentMap<Object, AtomicLong> objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        AtomicLong val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.get());

        meth.invoke(builder, obj, nodeId, false);
        Assert.assertEquals(1, conflictMap.size());
        objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(2, val.get());

        // Add a different object to the same node
        Object obj1 = new String("obj1");
        meth.invoke(builder, obj1, nodeId, true);
        Assert.assertEquals(1, conflictMap.size());
        objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(2, val.get());
        val = objMap.get(obj1);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.get());
    }

    @Test
    public void testRemoveNode() throws Exception {
        if (builderName == null) {
            // We'll also test it when explicitly called - much easier this way
            return;
        }
        Class builderClass = Class.forName(builderName);
        // args:  object, node, forUpdate
        Method meth = UtilReflection.getMethod(builderClass,
            "noteConflictDetected", Object.class, long.class, boolean.class);

        Object obj = new String("obj");
        Object obj1 = new String("obj1");
        long nodeId = 99L;
        long badNodeId = 102L;
        meth.invoke(builder, obj, nodeId, false);
        meth.invoke(builder, obj, badNodeId, false);
        meth.invoke(builder, obj1, badNodeId, false);
        meth.invoke(builder, obj1, badNodeId, true);
        ConcurrentMap<Long, ConcurrentMap<Object, AtomicLong>> conflictMap =
                builder.getConflictMap();
        Assert.assertEquals(2, conflictMap.size());
        ConcurrentMap<Object, AtomicLong> objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        AtomicLong val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.get());
        objMap = conflictMap.get(badNodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.get());
        val = objMap.get(obj1);
        Assert.assertNotNull(val);
        Assert.assertEquals(2, val.get());

        // Now invalidate badNodeId
        builder.removeNode(badNodeId);
        Assert.assertEquals(1, conflictMap.size());
        objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.get());
        objMap = conflictMap.get(badNodeId);
        Assert.assertNull(objMap);
    }

    @Test
    public void testRemoveNodeUnknownNodeId() {
        builder.removeNode(22);
    }

    @Test
    public void testRemoveNodeTwice() {
        builder.removeNode(35);
        builder.removeNode(35);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGraphBuilderBadCount() throws Exception {
        Properties p = new Properties();
        if (builderName != null) {
            p.setProperty(GraphListener.GRAPH_CLASS_PROPERTY, builderName);
        }
        p.setProperty(GraphBuilder.PERIOD_COUNT_PROPERTY, "0");
        listener = new GraphListener(collector, p);
    }

    /* Utility methods and classes. */
    private ProfileReport makeReport(Identity id) throws Exception {
        return (ProfileReport) profileReportImplConstructor.newInstance(
                    null, id, System.currentTimeMillis(), 1);
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
