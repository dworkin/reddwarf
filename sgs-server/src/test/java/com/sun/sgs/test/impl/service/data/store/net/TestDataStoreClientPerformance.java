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

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.impl.service.data.store.TestDataStorePerformance;
import com.sun.sgs.test.util.DummyProfileCoordinator;

/** Test the performance of the DataStoreClient class. */
public class TestDataStoreClientPerformance extends TestDataStorePerformance {
    /**
     * The name of the host running the DataStoreServer, or null to create one
     * locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the DataStoreServer. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", 44530);
    
    /** The name of the DataStoreClient package. */
    private static final String DataStoreNetPackage =
	"com.sun.sgs.impl.service.data.store.net";

    /** Creates an instance. */
    public TestDataStoreClientPerformance() {
	count = Integer.getInteger("test.count", 20);
    }

    /**
     * Create a DataStoreClient, set any default properties, and start the
     * server, if needed.
     */
    @Override
    protected DataStore getDataStore() throws Exception {
	String host = serverHost;
	int port = serverPort;
        String nodeType = NodeType.appNode.toString();
	if (host == null) {
	    host = "localhost";
	    port = 0;
	    nodeType = NodeType.coreServerNode.toString();
	}
        props.setProperty(StandardProperties.NODE_TYPE, nodeType);
	props.setProperty(DataStoreNetPackage + ".server.host", host);
	props.setProperty(DataStoreNetPackage + ".server.port",
			  String.valueOf(port));
	DataStore store = new DataStoreProfileProducer(
	    new DataStoreClient(props, env.systemRegistry, txnProxy),
	    DummyProfileCoordinator.getCollector());
	DummyProfileCoordinator.startProfiling();
	return store;
    }
}
