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

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.dgb.DistGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dgb.DistGraphBuilderServerImpl;
import com.sun.sgs.impl.service.nodemap.affinity.graph.BasicGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.GraphListener;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Field;
import java.util.Properties;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for distributed graph builder + single node LPA
 */
@RunWith(FilteredNameRunner.class)
public class TestDistGraphBuilder extends GraphBuilderTests {

    private final static String APP_NAME = "TestDistGraphBuilder";

    private static Field serverImplField;
    static {
        try {
            serverImplField =
                UtilReflection.getField(DistGraphBuilder.class, "serverImpl");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TestDistGraphBuilder() {
        super(APP_NAME);
    }

    @Before
    @Override
    public void beforeEachTest() throws Exception {
        props = getProps(null);
        serverNode = new SgsTestNode(appName, null, props);
        // Create a new app node
        props = getProps(serverNode);
        node = new SgsTestNode(serverNode, null, props);
        // The listener we care about is the one that is given reports
        listener = (GraphListener)
                graphListenerField.get(node.getNodeMappingService());
        // The builder, though, is the one that has access to the graphs
        // For this combo, that lives on the server
        GraphListener coreListener = (GraphListener)
                graphListenerField.get(serverNode.getNodeMappingService());
        builder = (BasicGraphBuilder)
                serverImplField.get(coreListener.getGraphBuilder());
    }

    @Override
    protected void startNewNode(Properties addProps) throws Exception {
        super.startNewNode(addProps);
        GraphListener coreListener = (GraphListener)
                graphListenerField.get(serverNode.getNodeMappingService());
        builder = (BasicGraphBuilder)
                serverImplField.get(coreListener.getGraphBuilder());
    }

    @Override
    protected Properties getProps(SgsTestNode serverNode) throws Exception {
        Properties p =
                SgsTestNode.getDefaultProperties(appName, serverNode, null);
        if (serverNode == null) {
            serverPort = SgsTestNode.getNextUniquePort();
            p.setProperty(StandardProperties.NODE_TYPE,
                          NodeType.coreServerNode.toString());
        }
        p.setProperty(DistGraphBuilderServerImpl.SERVER_PORT_PROPERTY,
                String.valueOf(serverPort));
        p.setProperty(GraphListener.GRAPH_CLASS_PROPERTY,
                      DistGraphBuilder.class.getName());
        return p;
    }

    @Ignore
    @Test
    @Override
    public void testGraphPruner() throws Exception {
        // this variation doesn't support pruning yet
        super.testGraphPruner();
    }

    @Ignore
    @Test
    @Override
    public void testGraphPrunerCountTwo() throws Exception {
        // this variation doesn't support pruning yet
        super.testGraphPrunerCountTwo();
    }

    @Ignore
    @Test
    @Override
    public void testGraphBuilderBadCount() throws Exception {
        // this variation doesn't support pruning yet so we don't parse the arg
        super.testGraphBuilderBadCount();
    }
}
