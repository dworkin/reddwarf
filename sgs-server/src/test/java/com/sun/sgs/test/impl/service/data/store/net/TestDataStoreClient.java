/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.impl.service.data.store.net.NetworkException;
import com.sun.sgs.test.impl.service.data.store.TestDataStoreImpl;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import com.sun.sgs.test.util.DummyTransaction;
import java.util.Properties;
import junit.framework.TestSuite;

/** Test the DataStoreClient class. */
public class TestDataStoreClient extends TestDataStoreImpl {

    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");

    /**
     * Specify the test suite to include all tests, or just a single method if
     * specified.
     */
    public static TestSuite suite() {
	if (testMethod == null) {
	    return new TestSuite(TestDataStoreClient.class);
	}
	TestSuite suite = new TestSuite();
	suite.addTest(new TestDataStoreClient(testMethod));
	return suite;
    }

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
    public TestDataStoreClient(String name) {
	super(name);
    }

    /** Adds client and server properties. */
    @Override
    protected Properties getProperties() throws Exception {
	Properties props = super.getProperties();
	String host = serverHost;
	int port = serverPort;
	if (host == null) {
	    host = "localhost";
	    port = 0;
	    props.setProperty(DataStoreNetPackage + ".server.start", "true");
	}
	props.setProperty(DataStoreNetPackage + ".server.host", host);
	props.setProperty(DataStoreNetPackage + ".server.port",
			  String.valueOf(port));
	return props;
    }

    /** Create a DataStoreClient. */
    @Override
    protected DataStore createDataStore(Properties props) throws Exception {
	DataStore store = new DataStoreProfileProducer(
	    new DataStoreClient(props), DummyProfileCoordinator.getRegistrar());
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

    /* -- Modify tests for differences in the network version -- */

    /** 
     * Override this test to account for the fact that there may be service
     * bindings in the data store for the data service.
     */
    @Override
    public void testNextBoundNameEmpty() {
	String first = store.nextBoundName(txn, null);
	assertTrue(first == null || first.startsWith("s."));
	assertEquals(first, store.nextBoundName(txn, ""));
	store.setBinding(txn, "", id);
	assertEquals("", store.nextBoundName(txn, null));
	assertEquals(first, store.nextBoundName(txn, ""));
    }

    /* -- Test constructor -- */

    public void testConstructorBadPort() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(DataStoreNetPackage + ".server.port", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativePort() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(DataStoreNetPackage + ".server.port", "-1");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBigPort() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(
	    DataStoreNetPackage + ".server.port", "70000");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorZeroPort() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(DataStoreNetPackage + ".server.start", "false");
	props.setProperty(DataStoreNetPackage + ".server.host", "localhost");
	props.setProperty(DataStoreNetPackage + ".server.port", "0");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadMaxTimeout() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(
	    DataStoreNetPackage + ".max.txn.timeout", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
	
    public void testConstructorNegativeMaxTimeout() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(
	    DataStoreNetPackage + ".max.txn.timeout", "-1");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorZeroMaxTimeout() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
	props.setProperty(
	    DataStoreNetPackage + ".max.txn.timeout", "0");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testConstructorAppButNoServerHost() throws Exception {
        txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	txn = new DummyTransaction();
        props.setProperty(DataStoreNetPackage + ".server.start", "false");
	props.remove(DataStoreNetPackage + ".server.host");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Other tests -- */

    /**
     * Test that the maximum transaction timeout overrides the standard
     * timeout.
     */
    public void testGetObjectMaxTxnTimeout() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	props.setProperty(DataStoreNetPackage + ".max.txn.timeout", "50");
	props.setProperty("com.sun.sgs.txn.timeout", "2000");
	store = createDataStore(props);
	txn = new DummyTransaction();
	Thread.sleep(1000);
	try {
	    store.getObject(txn, id, false);
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    txn = null;
	    System.err.println(e);
	}
    }

    /**
     * Test what happens when joining a transaction when the server has failed.
     */
    public void testJoinTxnServerFailed() throws Exception {
	txn.abort(new RuntimeException("abort"));
	store.shutdown();
	store = null;
	DataStoreServerImpl server = new DataStoreServerImpl(props);
	props.setProperty(DataStoreNetPackage + ".server.host", "localhost");
	props.setProperty(DataStoreNetPackage + ".server.port",
			  String.valueOf(server.getPort()));
	props.setProperty(DataStoreNetPackage + ".server.start", "false");
	txn = new DummyTransaction();	
	store = createDataStore(props);
	server.shutdown();
	try {
	    store.createObject(txn);
	    fail("Expected NetworkException");
	} catch (NetworkException e) {
	    System.err.println(e);
	}
	try {
	    txn.abort(new RuntimeException("abort"));
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
	txn = null;
	store.shutdown();
	store = null;
    }
}
