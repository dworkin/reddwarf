/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.test.impl.service.data.TestDataServiceImpl;
import java.util.Properties;
import junit.framework.TestSuite;

/** Test the DataStoreService using a networked data store. */
public class TestDataServiceClient extends TestDataServiceImpl {

    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");

    /**
     * Specify the test suite to include all tests, or just a single method if
     * specified.
     */
    public static TestSuite suite() {
	if (testMethod == null) {
	    return new TestSuite(TestDataServiceImpl.class);
	}
	TestSuite suite = new TestSuite();
	suite.addTest(new TestDataServiceClient(testMethod));
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
    
    /** The name of the DataStoreClient class. */
    private static final String DataStoreClientClassName =
	DataStoreClient.class.getName();

    /** The name of the DataStoreClient package. */
    private static final String DataStoreNetPackage =
	"com.sun.sgs.impl.service.data.store.net";

    /** Creates an instance. */
    public TestDataServiceClient(String name) {
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
	    props.setProperty(DataStoreNetPackage + ".server.run", "true");
	}
	props.setProperty(DataStoreNetPackage + ".server.host", host);
	props.setProperty(DataStoreNetPackage + ".server.port",
			  String.valueOf(port));
	props.setProperty(DataServiceImplClassName + ".data.store.class",
			  DataStoreClientClassName);
	return props;
    }

    /* -- Tests -- */

    /* -- Skip these tests -- they don't apply in the network case -- */

    @Override
    public void testConstructorNoDirectory() {
	System.err.println("Skipping");
    }

    @Override
    public void testConstructorNoDirectoryNorRoot() {
	System.err.println("Skipping");
    }
}
