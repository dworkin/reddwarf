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
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderFailedException;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import edu.uci.ics.jung.graph.UndirectedGraph;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.NavigableSet;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestGroupCoordinator {

    private static Constructor<?> GCConstructor;
    private static Method enableMethod;
    static {
        try {
            Class<?> GCClass = UtilReflection.getClass(
                    "com.sun.sgs.impl.service.nodemap.GroupCoordinator");
            GCConstructor = UtilReflection.getConstructor(GCClass,
                                                          Properties.class,
                                                          ComponentRegistry.class,
                                                          TransactionProxy.class,
                                                          NodeMappingServerImpl.class,
                                                          AffinityGraphBuilder.class);
            enableMethod = UtilReflection.getMethod(GCClass, "enable");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object newGroupCoordinator(Properties properties,
                                             ComponentRegistry systemRegistry,
                                             TransactionProxy txnProxy,
                                             NodeMappingServerImpl server,
                                             AffinityGraphBuilder builder)
        throws Exception
    {
        try {
            return GCConstructor.newInstance(properties,
                                             systemRegistry,
                                             txnProxy,
                                             server,
                                             builder);
        } catch (InvocationTargetException ie) {
            throw (Exception)ie.getCause();
        }
    }
    
    /** The node that creates the servers */
    private SgsTestNode serverNode;
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];
    
    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
    private Properties serviceProps;

    @Before
    public void setUp() throws Exception {
        setUp(null);
    }

    protected void setUp(Properties props) throws Exception {        
        serverNode = new SgsTestNode("TestGroupCoordinator", null, props);
        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
    }
    
        
    /** Shut down the nodes. */
    @After
    public void tearDown() throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(true);
    }

        ////////     The tests     /////////

    @Test(expected=IllegalArgumentException.class)
    public void testBadUpdateFeq() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("com.sun.sgs.impl.service.nodemap.update.freq", "4");
        newGroupCoordinator(properties, systemRegistry, txnProxy,
                            null,  new DummyBuilder());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBadCollocateDelay() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("com.sun.sgs.impl.service.nodemap.collocate.delay", "90");
        newGroupCoordinator(properties, systemRegistry, txnProxy,
                            null,  new DummyBuilder());
    }

    private class DummyBuilder implements AffinityGraphBuilder {

        @Override
        public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public UndirectedGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void enable() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void disable() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public LabelVertex getVertex(Identity id) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public AffinityGroupFinder getAffinityGroupFinder() {
            return new DummyFinder();
        }
    }
    
    private class DummyFinder implements AffinityGroupFinder {

        @Override
        public NavigableSet<RelocatingAffinityGroup> findAffinityGroups()
                throws AffinityGroupFinderFailedException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void enable() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void disable() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
