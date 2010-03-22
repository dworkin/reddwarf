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
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.LabelPropagationServer;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.BipartiteGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.DLPAGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dlpa.graph.WeightedGraphBuilder;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *  Tests for the graph listener and graph builders
 *
 */
@RunWith(ParameterizedFilteredNameRunner.class)
public class TestDistLPABuilder extends GraphBuilderTests {

   @Parameterized.Parameters
    public static Collection data() {
        return Arrays.asList(
            new Object[][] {{WeightedGraphBuilder.class.getName()},
                            {BipartiteGraphBuilder.class.getName()}});
        // TBD:  if default is changed from NONE
//            new Object[][] {{"default"},
//                            {WeightedGraphBuilder.class.getName()},
//                            {BipartiteGraphBuilder.class.getName()}});
    }
   
    private final static String APP_NAME = "TestDistLPABuilders";

    // Passed into each test run
    private final String builderName;

    private DLPAGraphBuilder graphBuilder;
    /**
     * Create this test class.
     * @param builderName the type of graph builder to use
     */
    public TestDistLPABuilder(String builderName) {
        super(APP_NAME);
        if (builderName.equals("default")) {
            this.builderName = null;
        } else {
            this.builderName = builderName;
        }
        System.err.println("Graph builder used is: " + builderName);
        
    }

    @Override
    public void beforeEachTest(Properties addProps) throws Exception {
        super.beforeEachTest(addProps);
        graphBuilder = (DLPAGraphBuilder) builder;
    }

    protected Properties getProps(SgsTestNode serverNode, Properties addProps)
            throws Exception
    {
        Properties p =
                SgsTestNode.getDefaultProperties(APP_NAME, serverNode, null);
        if (builderName == null) {
            p.remove(LPADriver.GRAPH_CLASS_PROPERTY);
        } else {
            p.setProperty(LPADriver.GRAPH_CLASS_PROPERTY, builderName);
        }
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
    
    // TBD change if the default changes
    @Test
    public void testDefault() throws Exception {
        SgsTestNode newNode = null;
        try {
            Properties p = getProps(serverNode);
            p.remove(LPADriver.GRAPH_CLASS_PROPERTY);
            newNode =  new SgsTestNode(serverNode, null, p);
            LPADriver newDriver = (LPADriver)
                finderField.get(newNode.getNodeMappingService());
            Assert.assertNull(newDriver.getGraphBuilder());
            Assert.assertNull(newDriver.getGraphListener());
        } finally {
            if (newNode != null) {
                newNode.shutdown(false);
            }
        }
    }

    @Test
    public void testNoListener() throws Exception {
        SgsTestNode newNode = null;
        try {
            Properties p = getProps(serverNode);
            p.setProperty(LPADriver.GRAPH_CLASS_PROPERTY,
                          LPADriver.GRAPH_CLASS_NONE);
            newNode =  new SgsTestNode(serverNode, null, p);
            LPADriver newDriver = (LPADriver)
                finderField.get(newNode.getNodeMappingService());
            Assert.assertNull(newDriver.getGraphBuilder());
            Assert.assertNull(newDriver.getGraphListener());
        } finally {
            if (newNode != null) {
                newNode.shutdown(false);
            }
        }
    }

    @Test
    public void testConstructor1() {
        // empty obj use
        Map<Object, Map<Identity, Long>> objUse =
                graphBuilder.getObjectUseMap();
        Assert.assertNotNull(objUse);
        Assert.assertEquals(0, objUse.size());
        // empty conflicts
        Map<Long, Map<Object, Long>> conflicts =
                graphBuilder.getConflictMap();
        Assert.assertNotNull(conflicts);
        Assert.assertEquals(0, conflicts.size());

    }

    @Test(expected=IllegalArgumentException.class)
    public void testBadConfig() throws Exception {
        afterEachTest();

        Properties addProps = new Properties();
        addProps.setProperty(StandardProperties.NODE_TYPE,
                             NodeType.singleNode.toString());
        try {
            beforeEachTest(addProps);
        } catch (InvocationTargetException e) {
            unwrapException(e);
        }
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
        Map<Long, Map<Object, Long>> conflictMap =
                graphBuilder.getConflictMap();
        Assert.assertEquals(1, conflictMap.size());
        Map<Object, Long> objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        Long val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.intValue());

        meth.invoke(builder, obj, nodeId, false);
        Assert.assertEquals(1, conflictMap.size());
        objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(2, val.intValue());

        // Add a different object to the same node
        Object obj1 = new String("obj1");
        meth.invoke(builder, obj1, nodeId, true);
        Assert.assertEquals(1, conflictMap.size());
        objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(2, val.intValue());
        val = objMap.get(obj1);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.intValue());
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
        Map<Long, Map<Object, Long>> conflictMap =
                graphBuilder.getConflictMap();
        Assert.assertEquals(2, conflictMap.size());
        Map<Object, Long> objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        Long val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.intValue());
        objMap = conflictMap.get(badNodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.intValue());
        val = objMap.get(obj1);
        Assert.assertNotNull(val);
        Assert.assertEquals(2, val.intValue());

        // Now invalidate badNodeId
        graphBuilder.removeNode(badNodeId);
        Assert.assertEquals(1, conflictMap.size());
        objMap = conflictMap.get(nodeId);
        Assert.assertNotNull(objMap);
        val = objMap.get(obj);
        Assert.assertNotNull(val);
        Assert.assertEquals(1, val.intValue());
        objMap = conflictMap.get(badNodeId);
        Assert.assertNull(objMap);
    }

    @Test
    public void testRemoveNodeUnknownNodeId() {
        graphBuilder.removeNode(22);
    }

    @Test
    public void testRemoveNodeTwice() {
        graphBuilder.removeNode(35);
        graphBuilder.removeNode(35);
    }
}
