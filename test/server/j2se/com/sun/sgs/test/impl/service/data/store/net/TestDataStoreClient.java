/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.test.impl.service.data.store.TestDataStoreImpl;
import com.sun.sgs.test.util.DummyTransaction;
import java.util.Properties;

/** Test the DataStoreClient class. */
public class TestDataStoreClient extends TestDataStoreImpl {

    /**
     * The name of the host running the DataStoreServer, or null to create one
     * locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the DataStoreServer. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", 44530);
    
    /** The name of the DataStoreClient class. */
    private static final String DataStoreClientClassName =
	DataStoreClient.class.getName();

    /** The name of the DataStoreServerImpl class. */
    private static final String DataStoreServerImplClassName =
	DataStoreServerImpl.class.getName();

    /** The server. */
    private static DataStoreServerImpl server;

    /** Creates an instance. */
    public TestDataStoreClient(String name) {
	super(name);
    }

    /** Shuts down the server if the test failed. */
    protected void tearDown() throws Exception {
	super.tearDown();
	try {
	    if (!passed && server != null) {
		new ShutdownAction() {
		    protected boolean shutdown() {
			return server.shutdown();
		    }
		}.waitForDone();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	} finally {
	    if (!passed) {
		server = null;
	    }
	}
    }

    /** Adds client and server properties, starting the server if needed. */
    protected Properties getProperties() throws Exception {
	Properties props = super.getProperties();
	String host = serverHost;
	int port = serverPort;
	if (server == null && host == null) {
	    props.setProperty(DataStoreServerImplClassName + ".port", "0");
	    server = new DataStoreServerImpl(props);
	}
	if (host == null) {
	    host = "localhost";
	    port = server.getPort();
	}
	props.setProperty(DataStoreClientClassName + ".server.host", host);
	props.setProperty(DataStoreClientClassName + ".server.port",
			  String.valueOf(port));
	return props;
    }

    /** Create a DataStoreClient. */
    protected DataStore createDataStore(Properties props) throws Exception {
	return new DataStoreClient(props);
    }

    /* -- Tests -- */

    /* -- Skip tests that involve properties that don't apply -- */

    public void testConstructorNoDirectory() throws Exception {
	System.err.println("Skipping");
    }
    public void testConstructorNonexistentDirectory() throws Exception {
	System.err.println("Skipping");
    }
    public void testConstructorDirectoryIsFile() throws Exception {
	System.err.println("Skipping");

    }
    public void testConstructorDirectoryNotWritable() throws Exception {
	System.err.println("Skipping");
    }

    /* -- Test constructor -- */

    public void testConstructorBadAllocationBlockSize() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".allocation.block.size", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativeAllocationBlockSize() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".allocation.block.size", "-3");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadPort() throws Exception {
	props.setProperty(DataStoreClientClassName + ".server.port", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativePort() throws Exception {
	props.setProperty(DataStoreClientClassName + ".server.port", "-1");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBigPort() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".server.port", "70000");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadMaxTimeout() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".max.txn.timeout", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
	
    public void testConstructorNegativeMaxTimeout() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".max.txn.timeout", "-1");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorZeroMaxTimeout() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".max.txn.timeout", "0");
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
	txn.abort(null);
	store.shutdown();
	props.setProperty(DataStoreClientClassName + ".max.txn.timeout", "50");
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
}
