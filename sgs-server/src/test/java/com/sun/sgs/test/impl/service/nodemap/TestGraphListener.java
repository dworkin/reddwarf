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

package com.sun.sgs.test.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.SystemIdentity;
import com.sun.sgs.impl.service.nodemap.affinity.BipartiteGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.GraphListener;
import com.sun.sgs.impl.service.nodemap.affinity.BipartiteParallelGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedEdge;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.WeightedParallelGraphBuilder;
import com.sun.sgs.kernel.AccessReporter.AccessType;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import edu.uci.ics.jung.graph.Graph;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *  Tests for the graph listener
 *
 */
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestGraphListener {

   @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(
            new Object[][] {{"default", true}, 
                        {WeightedParallelGraphBuilder.class.getName(), true},
                        {BipartiteParallelGraphBuilder.class.getName(), true},
                        {WeightedGraphBuilder.class.getName(), false},
                        {BipartiteGraphBuilder.class.getName(), false}});
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
    GraphListener listener;

    private final String builderName;
    private boolean parallelEdges;
    
    /**
     * Create this test class.
     * @param testType the type of profile data to create
     */
    public TestGraphListener(String builderName, boolean parallelEdges) {
        if (builderName.equals("default")) {
            this.builderName = null;
        } else {
            this.builderName = builderName;
        }
        this.parallelEdges = parallelEdges;
        System.err.println("Graph builder used is: " + builderName);
        
    }
    
    @Before
    public void beforeEachTest() throws Exception {
        Properties p = new Properties();
        if (builderName != null) {
            p.setProperty(GraphListener.GRAPH_CLASS_PROPERTY, builderName);
        }
        listener = new GraphListener(p);
    }
    
    @Test
    public void testConstructor() {
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
        Assert.assertNotNull(graph);
        Assert.assertEquals(0, graph.getEdgeCount());
        Assert.assertEquals(0, graph.getVertexCount());
    }
    
    @Test
    public void testNoDetail() throws Exception {
        ProfileReport report = makeReport(new IdentityImpl("something"));
        listener.report(report);
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
        
        graph = listener.getAffinityGraph();
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
        
        graph = listener.getAffinityGraph();
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
        System.out.println(graph);
        Assert.assertEquals(4, graph.getEdgeCount());
        Assert.assertEquals(4, graph.getVertexCount());
        
        // Identity A will now use obj3, so get links with B and C
        report = makeReport(identA);
        detail = new AccessedObjectsDetailTest();
        detail.addAccess(new String("obj3"));
        setAccessedObjectsDetailMethod.invoke(report, detail);
        listener.report(report);
        
        graph = listener.getAffinityGraph();
        System.out.println("New Graph: " + graph);
        Assert.assertEquals(4, graph.getVertexCount());
        if (parallelEdges) {
            Assert.assertEquals(6, graph.getEdgeCount());
        } else {
            Assert.assertEquals(4, graph.getEdgeCount());
            Assert.assertEquals(2, graph.findEdge(identA, identB).getWeight());
            Assert.assertEquals(2, graph.findEdge(identA, identC).getWeight());
            Assert.assertEquals(1, graph.findEdge(identB, identC).getWeight());
            Assert.assertEquals(1, graph.findEdge(identC, identD).getWeight());
        }
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
        
        Graph<Identity, WeightedEdge> graph = listener.getAffinityGraph();
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
