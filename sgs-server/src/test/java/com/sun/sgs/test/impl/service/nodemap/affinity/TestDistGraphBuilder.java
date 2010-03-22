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
import com.sun.sgs.impl.service.nodemap.affinity.dgb.DistGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.dgb.DistGraphBuilderServerImpl;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
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

    @Override
    public void beforeEachTest(Properties addProps) throws Exception {
        props = getProps(null, addProps);
        serverNode = new SgsTestNode(appName, null, props);
        // Create a new app node
        props = getProps(serverNode, addProps);
        node = new SgsTestNode(serverNode, null, props);
        // The listener we care about is the one that is given reports
        graphDriver = (LPADriver)
            finderField.get(node.getNodeMappingService());
        listener = graphDriver.getGraphListener();
        // The builder, though, is the one that has access to the graphs
        // For this combo, that lives on the server
        groupDriver = (LPADriver)
            finderField.get(serverNode.getNodeMappingService());
        builder = (AffinityGraphBuilder)
            serverImplField.get(groupDriver.getGraphBuilder());
    }

    @Override
    protected void startNewNode(Properties addProps) throws Exception {
        // Our "node" is really the core server node, so we need to stop
        // and start it.
        super.afterEachTest();
        props = getProps(null);
        for (Map.Entry<Object, Object> entry : addProps.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }
        serverNode = new SgsTestNode(appName, null, props);
        super.startNewNode(addProps);
        graphDriver = (LPADriver)
            finderField.get(node.getNodeMappingService());
        listener = graphDriver.getGraphListener();
        // The builder, though, is the one that has access to the graphs
        // For this combo, that lives on the server
        groupDriver = (LPADriver)
            finderField.get(serverNode.getNodeMappingService());
        builder = (AffinityGraphBuilder)
            serverImplField.get(groupDriver.getGraphBuilder());
    }

    @Override
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
        p.setProperty(DistGraphBuilderServerImpl.SERVER_PORT_PROPERTY,
                String.valueOf(serverPort));
        p.setProperty(LPADriver.GRAPH_CLASS_PROPERTY,
                      DistGraphBuilder.class.getName());
        p.setProperty(LPADriver.UPDATE_FREQ_PROPERTY, "3600"); // one hour
        if (addProps != null) {
            for (Map.Entry<Object, Object> entry : addProps.entrySet()) {
                p.put(entry.getKey(), entry.getValue());
            }
        }
        return p;
    }
}
