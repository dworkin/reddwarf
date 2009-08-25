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

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import static com.sun.sgs.impl.kernel.StandardProperties.NODE_TYPE;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CALLBACK_PORT_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CHECK_BINDINGS_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.DEFAULT_CALLBACK_PORT;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.DEFAULT_SERVER_PORT;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_HOST_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_PORT_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStoreServerImpl.DEFAULT_UPDATE_QUEUE_PORT;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStoreServerImpl.DIRECTORY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStoreServerImpl.UPDATE_QUEUE_PORT_PROPERTY;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.impl.service.data.store.BasicDataStoreTestEnv;
import com.sun.sgs.test.impl.service.data.store.TestDataStoreImpl;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import java.util.Properties;

/** Test the {@link CachingDataStore} class. */
public class TestCachingDataStore extends TestDataStoreImpl {
    
    /**
     * The name of the host running the {@link CachingDataStoreServer}, or
     * {@code null} to create one locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the {@link CachingDataStoreServer}. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", DEFAULT_SERVER_PORT);
    
    /** The network port for the server's update queue. */
    private static final int updateQueuePort =
	Integer.getInteger("test.update.queue.port",
			   DEFAULT_UPDATE_QUEUE_PORT);

    /** The network port for the node's callback server. */
    private static final int nodeCallbackPort =
	Integer.getInteger("test.callback.port", DEFAULT_CALLBACK_PORT);

    /** The basic test environment, or {@code null} if not set. */
    private static BasicDataStoreTestEnv staticEnv = null;

    /** Creates an instance of this class. */
    public TestCachingDataStore() {
	super(staticEnv == null
	      ? staticEnv = new BasicDataStoreTestEnv(
		  System.getProperties(),
		  LockingAccessCoordinator.class.getName())
	      : staticEnv);
    }

    /** Add client and server properties. */
    @Override
    protected Properties getProperties() throws Exception {
	Properties props = super.getProperties();
	String host = serverHost;
	int port = serverPort;
	int queuePort = updateQueuePort;
	int callbackPort = nodeCallbackPort;
        String nodeType = NodeType.appNode.toString();
	if (host == null) {
	    host = "localhost";
	    port = 0;
	    queuePort = 0;
	    callbackPort = 0;
	    nodeType = NodeType.coreServerNode.toString();
	}
        props.setProperty(NODE_TYPE, nodeType);
	props.setProperty(SERVER_HOST_PROPERTY, host);
	props.setProperty(SERVER_PORT_PROPERTY, String.valueOf(port));
	props.setProperty(UPDATE_QUEUE_PORT_PROPERTY,
			  String.valueOf(queuePort));
	props.setProperty(CALLBACK_PORT_PROPERTY,
			  String.valueOf(callbackPort));
	props.setProperty(DIRECTORY_PROPERTY, dbDirectory);
	if (props.getProperty(CHECK_BINDINGS_PROPERTY) == null) {
	    props.setProperty(CHECK_BINDINGS_PROPERTY, "TXN");
	}
	return props;
    }

    /** Create a {@link CachingDataStore}. */
    @Override
    protected DataStore createDataStore(Properties props) throws Exception {
	txnProxy.setComponent(
	    WatchdogService.class,
	    new DummyWatchdogService(props, systemRegistry, txnProxy));
	DataStore store = new DataStoreProfileProducer(
	    new CachingDataStore(props, systemRegistry, txnProxy),
	    DummyProfileCoordinator.getCollector());
	DummyProfileCoordinator.startProfiling();
	return store;
    }

    /* -- Tests -- */

    /* -- Skip tests that involve properties that don't apply -- */

    @Override
    public void testConstructorNoDirectory() throws Exception {
	System.err.println("Skipping");
    }
    @Override
    public void testConstructorNonexistentDirectory() throws Exception {
	System.err.println("Skipping");
    }
    @Override
    public void testConstructorDirectoryIsFile() throws Exception {
	System.err.println("Skipping");

    }
    @Override
    public void testConstructorDirectoryNotWritable() throws Exception {
	System.err.println("Skipping");
    }
}
