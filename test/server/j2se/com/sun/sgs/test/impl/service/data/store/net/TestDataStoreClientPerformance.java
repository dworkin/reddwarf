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

import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
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
    
    /** The name of the DataStoreClient class. */
    private static final String DataStoreClientClassName =
	DataStoreClient.class.getName();

    /** The name of the DataStoreServerImpl class. */
    private static final String DataStoreServerImplClassName =
	DataStoreServerImpl.class.getName();

    /** The server. */
    DataStoreServerImpl server;

    /** Creates an instance. */
    public TestDataStoreClientPerformance(String name) {
	super(name);
	count = Integer.getInteger("test.count", 20);
    }

    /** Shutdown the server. */
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

    /**
     * Create a DataStoreClient, set any default properties, and start the
     * server, if needed.
     */
    protected DataStore getDataStore() throws Exception {
	String host = serverHost;
	int port = serverPort;
	if (host == null) {
	    props.setProperty(DataStoreServerImplClassName + ".port", "0");
	    DataStoreServerImpl serverImpl = new DataStoreServerImpl(props);
	    server = serverImpl;
	    host = "localhost";
	    port = serverImpl.getPort();
	}
	props.setProperty(DataStoreClientClassName + ".server.host", host);
	props.setProperty(DataStoreClientClassName + ".server.port",
			  String.valueOf(port));
	return new DataStoreClient(props);
    }
}
