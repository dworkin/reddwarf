/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.test.impl.service.data.TestDataServiceImpl;
import com.sun.sgs.tools.test.ParameterizedFilteredNameRunner;
import java.util.Properties;
import org.junit.Ignore;
import org.junit.runner.RunWith;

/** Test the DataStoreService using a networked data store. */
@SuppressWarnings("hiding")
@RunWith(ParameterizedFilteredNameRunner.class)
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

    /** The name of the DataStoreClient package. */
    private static final String DataStoreNetPackage =
	"com.sun.sgs.impl.service.data.store.net";

    /** Creates an instance. */
    public TestDataServiceClient(boolean durableParticipant) {
	super(durableParticipant);
    }

    /** Adds client and server properties. */
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
        props.setProperty(StandardProperties.NODE_TYPE, nodeType);
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
    @Ignore
    public void testConstructorNoDirectory() {
	System.err.println("Skipping");
    }

    @Override
    @Ignore
    public void testConstructorNoDirectoryNorRoot() {
	System.err.println("Skipping");
    }
}
