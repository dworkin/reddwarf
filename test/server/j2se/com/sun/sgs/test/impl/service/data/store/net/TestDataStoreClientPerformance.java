/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.test.impl.service.data.store.TestDataStorePerformance;

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
    public TestDataStoreClientPerformance(String name) {
	super(name);
	count = Integer.getInteger("test.count", 20);
    }

    /**
     * Create a DataStoreClient, set any default properties, and start the
     * server, if needed.
     */
    protected DataStore getDataStore() throws Exception {
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
	return new DataStoreClient(props);
    }
}
