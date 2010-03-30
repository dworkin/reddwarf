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

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import static com.sun.sgs.impl.kernel.StandardProperties.NODE_TYPE;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CACHE_SIZE_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CHECK_BINDINGS_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_HOST_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DEFAULT_SERVER_PORT;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DIRECTORY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.SERVER_PORT_PROPERTY;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.service.DataConflictListener;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.Node.Health;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.impl.service.data.store.BasicDataStoreTestEnv;
import com.sun.sgs.test.impl.service.data.store.TestDataStoreImpl;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Properties;
import org.junit.Test;

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

    /** The basic test environment, or {@code null} if not set. */
    private static BasicDataStoreTestEnv staticEnv = null;

    /** Creates an instance of this class. */
    public TestCachingDataStore() {
	super(staticEnv == null ? staticEnv = createTestEnv() : staticEnv);
    }

    private static BasicDataStoreTestEnv createTestEnv() {
	BasicDataStoreTestEnv env = new BasicDataStoreTestEnv(
	    System.getProperties(),
	    LockingAccessCoordinator.class.getName());
	env.txnProxy.setComponent(
	    DataService.class, new DummyDataService());
	env.txnProxy.setComponent(
	    WatchdogService.class, new DummyWatchdogService());
	return env;
    }

    /** Add client and server properties. */
    @Override
    protected Properties getProperties() throws Exception {
	Properties props = super.getProperties();
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
	props.setProperty(DIRECTORY_PROPERTY, dbDirectory);
	if (props.getProperty(CHECK_BINDINGS_PROPERTY) == null) {
	    props.setProperty(CHECK_BINDINGS_PROPERTY, "TXN");
	}
	return props;
    }

    /** Create a {@link CachingDataStore}. */
    @Override
    protected DataStore createDataStore(Properties props) throws Exception {
	DataStore store = new DataStoreProfileProducer(
	    new CachingDataStore(props, systemRegistry, txnProxy),
	    DummyProfileCoordinator.getCollector());
	DummyProfileCoordinator.startProfiling();
	store.ready();
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

    /* -- Other tests -- */

    @Test
    public void testCacheReplacement() throws Exception {
	txn.commit();
	txn = null;
	store.shutdown();
	store = null;
	/* Use a smaller cache size to get more replacement */
	props.setProperty(CACHE_SIZE_PROPERTY, "1000");
	store = createDataStore();
	try {
	    /* Create objects and bindings */
	    for (int i = 0; i < 2000; i++) {
		for (int j = 0; true; j++) {
		    assertTrue("Too many iterations: " + j, j < 10);
		    txn = createTransaction();
		    try {
			id = store.createObject(txn);
			store.setObject(txn, id, new byte[] { 0 });
			store.setBinding(txn, String.valueOf(i), id);
			txn.commit();
			txn = null;
			break;
		    } catch (RuntimeException e) {
			if (!(e instanceof ExceptionRetryStatus) ||
			    !((ExceptionRetryStatus) e).shouldRetry())
			{
			    throw e;
			} else if (!txn.isAborted()) {
			    txn.abort(e);
			}
		    }
		}
	    }
	    /*
	     * Make sure cache replacement works when there has been aborted
	     * object creation.
	     */
	    txn = createTransaction();
	    id = store.createObject(txn);
	    store.setObject(txn, id, new byte[] { 0 });
	    txn.abort(new RuntimeException("abort"));
	    txn = null;
	    /* Access objects and bindings */
	    for (int repeat = 0; repeat < 3; repeat++) {
		for (int i = 0; i < 2000; i++) {
		    for (int j = 0; true; j++) {
			assertTrue("Too many iterations: " + j, j < 10);
			txn = createTransaction();
			try {
			    id = store.getBinding(txn, String.valueOf(i));
			    boolean forUpdate = (i % 2 == 0);
			    store.getObject(txn, id, forUpdate);
			    if (forUpdate) {
				store.setObject(
				    txn, id, new byte[] { (byte) i });
			    }
			    txn.commit();
			    txn = null;
			    break;
			} catch (RuntimeException e) {
			    if (!(e instanceof ExceptionRetryStatus) ||
				!((ExceptionRetryStatus) e).shouldRetry())
			    {
				throw e;
			    } else if (!txn.isAborted()) {
				txn.abort(e);
			    }
			}
		    }
		}
	    }
	    /* Clean up */
	    for (int i = 0; i < 2000; i++) {
		for (int j = 0; true; j++) {
		    assertTrue("Too many iterations: " + j, j < 10);
		    txn = createTransaction();
		    try {
			id = store.getBinding(txn, String.valueOf(i));
			store.removeObject(txn, id);
			store.removeBinding(txn, String.valueOf(i));
			txn.commit();
			txn = null;
			break;
		    } catch (RuntimeException e) {
			if (!(e instanceof ExceptionRetryStatus) ||
			    !((ExceptionRetryStatus) e).shouldRetry())
			{
			    throw e;
			} else if (!txn.isAborted()) {
			    txn.abort(e);
			}
		    }
		}
	    }
	} finally {
	    if (txn != null) {
		txn.abort(new RuntimeException("abort"));
	    }
	    store.shutdown();
	    store = null;
	}
    }

    /* -- Other classes -- */

    /** A dummy data service that just supplies the local node ID. */
    private static class DummyDataService implements DataService {
	/* -- Stubs for DataManager -- */
	public ManagedObject getBinding(String name) { return null; }
	public ManagedObject getBindingForUpdate(String name) { return null; }
	public void setBinding(String name, Object object) { }
	public void removeBinding(String name) { }
	public String nextBoundName(String name) { return null; }
	public void removeObject(Object object) { }
	public void markForUpdate(Object object) { }
	public <T> ManagedReference<T> createReference(T object) {
	    return null;
	}
	public BigInteger getObjectId(Object object) { return null; }
	/* -- Stubs for DataService -- */
	public long getLocalNodeId() { return 1; }
	public ManagedObject getServiceBinding(String name) { return null; }
	public ManagedObject getServiceBindingForUpdate(String name) {
	    return null;
	}
	public void setServiceBinding(String name, Object object) { }
	public void removeServiceBinding(String name) { }
	public String nextServiceBoundName(String name) { return null; }
	public ManagedReference<?> createReferenceForId(BigInteger id) {
	    return null;
	}
	public BigInteger nextObjectId(BigInteger objectId) { return null; }
	public void addDataConflictListener(DataConflictListener listener) { }
	/* -- Stubs for Service -- */
	public String getName() { return null; }
	public void ready() { }
	public void shutdown() { }
    }

    /**
     * A dummy watchdog service that shuts down the data service on failure.
     */
    private static class DummyWatchdogService implements WatchdogService {
	DummyWatchdogService() { }
	/* Service */
	@Override
	public String getName() { return "DummyWatchdogService"; }
	@Override
	public void ready() { }
	@Override
	public void shutdown() { }
	/* WatchdogService */
	@Override
	public Health getLocalNodeHealth() { return null; }
	@Override
	public Health getLocalNodeHealthNonTransactional() { return null; }
	@Override
	public boolean isLocalNodeAlive() { return true; }
	@Override
	public boolean isLocalNodeAliveNonTransactional() { return true; }
	@Override
	public Iterator<Node> getNodes() { return null; }
	@Override
	public Node getNode(long nodeId) { return null; }
	@Override
	public Node getBackup(long nodeId) { return null; }
	@Override
	public void addNodeListener(NodeListener listener) { }
	@Override
	public void addRecoveryListener(RecoveryListener listener) { }
	@Override
	public void reportHealth(long nodeId, Health health, String component)
	{
	    if (!health.isAlive()) {
		reportFailure(nodeId, component);
	    }
	}
	@Override
	public void reportFailure(long nodeId, String className) {
	    DataStore s = store;
	    if (s != null) {
		s.shutdown();
	    }
	}
	@Override
	public long currentAppTimeMillis() { return 0; }
	@Override
	public long getAppTimeMillis(long systemTimeMillis) { return 0; }
	@Override
	public long getSystemTimeMillis(long appTimeMillis) { return 0; }
    }
}
