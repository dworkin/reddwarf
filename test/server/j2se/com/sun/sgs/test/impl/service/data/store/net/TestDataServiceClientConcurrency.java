package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.impl.service.data.TestDataServiceConcurrency;
import java.util.Properties;

public class TestDataServiceClientConcurrency
    extends TestDataServiceConcurrency
{
    private static boolean externalServer =
	Boolean.getBoolean("test.external.server");
    private static String serverHost =
	System.getProperty("test.server.host", "localhost");
    private static int serverPort =
	Integer.getInteger("test.server.port", 54321);
    
    /** The name of the DataStoreClient class. */
    private static final String DataStoreClientClassName =
	DataStoreClient.class.getName();

    /** The name of the DataStoreServerImpl class. */
    private static final String DataStoreServerImplClassName =
	DataStoreServerImpl.class.getName();

    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    DataStoreServerImpl server;

    public TestDataServiceClientConcurrency(String name) {
	super(name);
	operations = Integer.getInteger("test.operations", 5000);
	objects = Integer.getInteger("test.objects", 500);
    }

    protected void tearDown() throws Exception {
	super.tearDown();
	try {
	    if (server != null) {
		server.shutdown();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	}
    }

    protected DataService getDataService(Properties props,
					 ComponentRegistry componentRegistry)
	throws Exception
    {
	if (!externalServer) {
	    props.setProperty(DataStoreServerImplClassName + ".port",
			      "0");
	    DataStoreServerImpl serverImpl = new DataStoreServerImpl(props);
	    server = serverImpl;
	    serverPort = serverImpl.getPort();
	}
	props.setProperty(DataStoreClientClassName + ".server.host",
			  serverHost);
	props.setProperty(DataStoreClientClassName + ".server.port",
			  String.valueOf(serverPort));
	props.setProperty(DataServiceImplClassName + ".data.store.class",
			    DataStoreClientClassName);
	return new DataServiceImpl(props, componentRegistry);
    }
}
