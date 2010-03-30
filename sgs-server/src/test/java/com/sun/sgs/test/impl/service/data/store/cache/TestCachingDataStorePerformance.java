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

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import static com.sun.sgs.impl.kernel.StandardProperties.NODE_TYPE;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_HOST_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DEFAULT_SERVER_PORT;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DIRECTORY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.SERVER_PORT_PROPERTY;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.impl.service.data.store.BasicDataStoreTestEnv;
import com.sun.sgs.test.impl.service.data.store.TestDataStorePerformance;
import com.sun.sgs.test.util.DummyProfileCoordinator;

/** Test the performance of the {@link CachingDataStore} class. */
public class TestCachingDataStorePerformance extends TestDataStorePerformance {

    /**
     * The name of the host running the {@link CachingDataStoreServer}, or
     * {@code null} to create one locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the {@link CachingDataStoreServer}. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", DEFAULT_SERVER_PORT);

    /** The basic test environment, or {@code null} if not set. */
    private static BasicDataStoreTestEnv staticEnv = null;

    /** Creates an instance of this class. */
    public TestCachingDataStorePerformance() {
	super(staticEnv == null
	      ? staticEnv = new BasicDataStoreTestEnv(
		  System.getProperties(),
		  LockingAccessCoordinator.class.getName())
	      : staticEnv);
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
	props.setProperty(NODE_TYPE, nodeType);
	props.setProperty(SERVER_HOST_PROPERTY, host);
	props.setProperty(SERVER_PORT_PROPERTY, String.valueOf(port));
	props.setProperty(DIRECTORY_PROPERTY, directory);
	DataStore store = new DataStoreProfileProducer(
	    new CachingDataStore(props, env.systemRegistry, txnProxy),
	    DummyProfileCoordinator.getCollector());
	DummyProfileCoordinator.startProfiling();
	return store;
    }
}
