/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.test.impl.service.data.TestDataServiceImpl;
import java.util.Properties;

/** Test the DataStoreService using a networked data store. */
public class TestDataServiceClient extends TestDataServiceImpl {

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

    /** The data store server. */
    private static DataStoreServerImpl server;

    /** Creates an instance. */
    public TestDataServiceClient(String name) {
	super(name);
    }

    /** Shuts down the server if the test failed. */
    public void tearDown() throws Exception {
	super.tearDown();
	try {
	    if (!passed && server != null) {
		server.shutdown();
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
	props.setProperty(DataServiceImplClassName + ".data.store.class",
			  DataStoreClientClassName);
	return props;
    }

    /* -- Tests -- */

    /* -- Skip these tests -- they don't apply in the network case -- */

    public void testConstructorNoDirectory() {
	System.err.println("Skipping");
    }

    public void testConstructorNoDirectoryNorRoot() {
	System.err.println("Skipping");
    }
}
