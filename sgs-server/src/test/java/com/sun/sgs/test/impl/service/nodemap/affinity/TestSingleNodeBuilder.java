/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.LPADriver;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AbstractAffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.single.SingleGraphBuilder;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for single node graph builder/ lpa implementation.
 */
@RunWith(FilteredNameRunner.class)
public class TestSingleNodeBuilder extends GraphBuilderTests {

    private final static String APP_NAME = "TestSingleNodeBuilder";

    public TestSingleNodeBuilder() {
        super(APP_NAME);
    }

    @Override
    protected void beforeEachTest(Properties addProps) throws Exception {
        // no server node required
        props = getProps(null, addProps);
        node = new SgsTestNode(appName, null, props);

        graphDriver = (LPADriver)
                finderField.get(node.getNodeMappingService());
        groupDriver = graphDriver;
        listener = graphDriver.getGraphListener();
        builder = graphDriver.getGraphBuilder();
    }

    @Override
    protected Properties getProps(SgsTestNode serverNode, Properties addProps)
            throws Exception
    {
        Properties p = super.getProps(null, addProps);
        p.setProperty(StandardProperties.NODE_TYPE, NodeType.singleNode.name());
        p.setProperty(LPADriver.GRAPH_CLASS_PROPERTY,
                      SingleGraphBuilder.class.getName());
        p.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "3600"); // one hour
        if (addProps != null) {
            for (Map.Entry<Object, Object> entry : addProps.entrySet()) {
                p.put(entry.getKey(), entry.getValue());
            }
        }
        return p;
    }

    @Override
    protected void startNewNode(Properties addProps) throws Exception {
        super.afterEachTest();
        props = getProps(null);
        for (Map.Entry<Object, Object> entry : addProps.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }
        node =  new SgsTestNode(appName, null, props);
        graphDriver = (LPADriver)
                finderField.get(node.getNodeMappingService());
        groupDriver = graphDriver;
        listener = graphDriver.getGraphListener();
        builder = graphDriver.getGraphBuilder();
    }

    @Test(expected=IllegalArgumentException.class)
    @Override
    public void testGraphBuilderBadCount() throws Exception {
        props = getProps(serverNode);
        props.setProperty(AbstractAffinityGraphBuilder.PERIOD_COUNT_PROPERTY,
                          "0");

        SgsTestNode newNode = null;
        try {
            newNode = new SgsTestNode(appName, null, props);
        } catch (InvocationTargetException e) {
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
        } finally {
            if (newNode != null) {
                newNode.shutdown(false);
            }
        }
    }
}
