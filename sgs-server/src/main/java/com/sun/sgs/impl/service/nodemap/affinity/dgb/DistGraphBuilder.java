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

package com.sun.sgs.impl.service.nodemap.affinity.dgb;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.BasicGraphBuilder;
import com.sun.sgs.impl.service.nodemap.affinity.graph.LabelVertex;
import com.sun.sgs.impl.service.nodemap.affinity.graph.WeightedEdge;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.profile.AccessedObjectsDetail;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TransactionProxy;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;

/**
 *  The portion of the distributed affinity graph builder which resides on
 *  a local node.  This code forwards graph information to its server,
 *  which builds a single large graph for all information in the system.
 */
public class DistGraphBuilder implements BasicGraphBuilder {
    // Our package name 
    private static final String PKG_NAME =
            "com.sun.sgs.impl.service.nodemap.affinity";
    // The property name for the server host
    private static final String SERVER_HOST_PROPERTY =
            PKG_NAME + ".server.host";

    // The remote server, or null if we're on the core server node
    private final DistGraphBuilderServer server;
    // The server implementation, or null if we're on an app node
    private final DistGraphBuilderServerImpl serverImpl;

    /**
     * Creates the client side of a distributed graph builder.
     * @param systemRegistry the registry of available system components
     * @param txnProxy the transaction proxy
     * @param nms the node mapping service currently being created
     * @param properties  application properties
     * @param nodeId the local node id
     * @throws Exception if an error occurs
     */
    public DistGraphBuilder(ComponentRegistry systemRegistry,
                            TransactionProxy txnProxy,
                            NodeMappingService nms,
                            Properties properties, long nodeId)
        throws Exception
    {
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        NodeType nodeType =
                wrappedProps.getEnumProperty(StandardProperties.NODE_TYPE,
                                             NodeType.class,
                                             NodeType.singleNode);
        if (nodeType == NodeType.coreServerNode) {
            serverImpl = 
                new DistGraphBuilderServerImpl(systemRegistry,
                                               txnProxy, nms, properties);
            server = null;
        } else {
            String host = wrappedProps.getProperty(SERVER_HOST_PROPERTY,
                            wrappedProps.getProperty(
                                StandardProperties.SERVER_HOST));
            if (host == null) {
                // None specified, use local host
                host = InetAddress.getLocalHost().getHostName();
            }
            int port = wrappedProps.getIntProperty(
                    DistGraphBuilderServerImpl.SERVER_PORT_PROPERTY,
                    DistGraphBuilderServerImpl.DEFAULT_SERVER_PORT, 0, 65535);
            // Look up our server
            Registry registry = LocateRegistry.getRegistry(host, port);
            server = (DistGraphBuilderServer) registry.lookup(
                             DistGraphBuilderServerImpl.SERVER_EXPORT_NAME);
            serverImpl = null;
        }
    }

    /** {@inheritDoc} */
    public void updateGraph(Identity owner, AccessedObjectsDetail detail) {
        Object[] ids = new Object[detail.getAccessedObjects().size()];
        int index = 0;
        for (AccessedObject access : detail.getAccessedObjects()) {
            ids[index++] = access.getObjectId();
        }
        try {
            server.updateGraph(owner, ids);
        } catch (IOException e) {
            // jane retry
            System.out.println(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation will always throw an
     * {@code UnsupportedOperationException}, because the graph is not
     * held on the local node.
     */
    public UndirectedSparseGraph<LabelVertex, WeightedEdge> getAffinityGraph() {
        throw new UnsupportedOperationException(
                "Cannot obtain the affinity graph from a local node");
    }

    /** {@inheritDoc} */
    public void shutdown() {
        if (serverImpl != null) {
            serverImpl.shutdown();
        }
    }

    /** {@inheritDoc} */
    public AffinityGroupFinder getAffinityGroupFinder() {
        return serverImpl;
    }

    /** {@inheritDoc} */
    public Runnable getPruneTask() {
        throw new UnsupportedOperationException("pruning not yet implemented");
    }
}
