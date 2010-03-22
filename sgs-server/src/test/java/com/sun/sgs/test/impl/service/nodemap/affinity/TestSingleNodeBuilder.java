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
