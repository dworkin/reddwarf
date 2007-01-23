package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.test.impl.service.data.store.TestDataStoreImpl;

public class TestDataStoreClient extends TestDataStoreImpl {
    private static boolean externalServer =
	Boolean.getBoolean("test.externalServer");
    private static String serverHost =
	System.getProperty("test.serverHost", "localhost");
    private static int serverPort =
	Integer.getInteger("test.serverPort", 54321);
    
    /** The name of the DataStoreClient class. */
    private static final String DataStoreClientClassName =
	DataStoreClient.class.getName();

    /** The name of the DataStoreServerImpl class. */
    private static final String DataStoreServerImplClassName =
	DataStoreServerImpl.class.getName();

    DataStoreServerImpl server;

    public TestDataStoreClient(String name) {
	super(name);
    }

    protected void tearDown() throws Exception {
	super.tearDown();
	try {
	    if (server != null) {
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
	}
    }

    /** Returns a DataStore for the shared database. */
    protected DataStore getDataStore() throws Exception {
	if (!externalServer) {
	    dbProps.setProperty(DataStoreServerImplClassName + ".port", "0");
	    DataStoreServerImpl serverImpl = new DataStoreServerImpl(dbProps);
	    server = serverImpl;
	    serverPort = serverImpl.getPort();
	}
	dbProps.setProperty(DataStoreClientClassName + ".host", serverHost);
	dbProps.setProperty(DataStoreClientClassName + ".port",
			    String.valueOf(serverPort));
	/*
	 * XXX: I shouldn't need to use a non-standard timeout here.  See if it
	 * can go away when I do transaction timeouts on the server.
	 * -tjb@sun.com (01/23/2007)
	 */
	dbProps.setProperty("com.sun.sgs.txnTimeout", "5000");
	return new DataStoreClient(dbProps);
    }
}
