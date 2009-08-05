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
    
    /** The cache package. */
    private static final String DataStoreCachePackage =
	"com.sun.sgs.impl.service.data.store.cache";

    /**
     * The name of the host running the {@link CachingDataStoreServer}, or
     * {@code null} to create one locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the {@link CachingDataStoreServer}. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", 44540);
    
    /** The network port for the server's update queue. */
    private static final int updateQueuePort =
	Integer.getInteger("test.update.queue.port", 44542);

    /** The network port for the node's callback server. */
    private static final int nodeCallbackPort =
	Integer.getInteger("test.callback.port", 44541);

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
	props.setProperty(DataStoreCachePackage + ".server.host", host);
	props.setProperty(DataStoreCachePackage + ".server.port",
			  String.valueOf(port));
	props.setProperty(DataStoreCachePackage + ".update.queue.port",
			  String.valueOf(queuePort));
	props.setProperty(DataStoreCachePackage + ".callback.port",
			  String.valueOf(callbackPort));
	props.setProperty(DataStoreCachePackage + ".directory", dbDirectory);
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
