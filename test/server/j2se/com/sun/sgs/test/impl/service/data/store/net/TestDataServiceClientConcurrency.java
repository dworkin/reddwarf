/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.impl.service.data.TestDataServiceConcurrency;
import java.util.Properties;

/**
 * Test concurrent operation of the data service using a networked data
 * store.
 */
public class TestDataServiceClientConcurrency
    extends TestDataServiceConcurrency
{
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

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Creates an instance. */
    public TestDataServiceClientConcurrency(String name) {
	super(name);
	/* Reduce the size of the test -- the networked version is slower */
	operations = Integer.getInteger("test.operations", 5000);
	objects = Integer.getInteger("test.objects", 500);
    }

    /**
     * Create a DataService, set any default properties, and start the server,
     * if needed.
     */
    protected DataService getDataService(Properties props,
					 ComponentRegistry componentRegistry)
	throws Exception
    {
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
	return new DataServiceImpl(props, componentRegistry);
    }
}
