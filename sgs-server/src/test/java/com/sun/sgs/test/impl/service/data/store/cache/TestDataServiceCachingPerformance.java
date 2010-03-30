/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.impl.kernel.LockingAccessCoordinator;
import static com.sun.sgs.impl.kernel.StandardProperties.NODE_TYPE;
import static com.sun.sgs.impl.service.data.
    DataServiceImpl.DATA_STORE_CLASS_PROPERTY;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.cache.CachingDataStore;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.CHECK_BINDINGS_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.
    CachingDataStore.SERVER_HOST_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DEFAULT_SERVER_PORT;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.DIRECTORY_PROPERTY;
import static com.sun.sgs.impl.service.data.store.cache.server.
    CachingDataStoreServerImpl.SERVER_PORT_PROPERTY;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.test.impl.service.data.TestDataServicePerformance;
import com.sun.sgs.test.util.SgsTestNode;
import java.util.Properties;

/** Test {@code DataService} performance using a caching data store. */
public class TestDataServiceCachingPerformance
    extends TestDataServicePerformance
{
    /** The configuration property for specifying the access coordinator. */
    private static final String ACCESS_COORDINATOR_PROPERTY =
	"com.sun.sgs.impl.kernel.access.coordinator";

    /**
     * The name of the host running the {@link CachingDataStoreServer}, or
     * {@code null} to create one locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the {@link CachingDataStoreServer}. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", DEFAULT_SERVER_PORT);

    /** Creates an instance. */
    public TestDataServiceCachingPerformance() { }

    /** Adds client and server properties. */
    @Override
    protected Properties getNodeProps() throws Exception {
	Properties props = super.getNodeProps();
	String host = serverHost;
	int port = serverPort;
	String nodeType = NodeType.appNode.toString();
	if (host == null) {
	    host = "localhost";
	    port = 0;
	    nodeType = NodeType.coreServerNode.toString();
	}
	if (port == 0) {
	    port = SgsTestNode.getNextUniquePort();
	}
	props.setProperty(NODE_TYPE, nodeType);
	props.setProperty(SERVER_HOST_PROPERTY, host);
	props.setProperty(SERVER_PORT_PROPERTY, String.valueOf(port));
	props.setProperty(DIRECTORY_PROPERTY,
			  props.getProperty(DataStoreImpl.DIRECTORY_PROPERTY));
	if (props.getProperty(CHECK_BINDINGS_PROPERTY) == null) {
	    props.setProperty(CHECK_BINDINGS_PROPERTY, "TXN");
	}
	props.setProperty(DATA_STORE_CLASS_PROPERTY,
			  CachingDataStore.class.getName());
	props.setProperty(ACCESS_COORDINATOR_PROPERTY,
			  LockingAccessCoordinator.class.getName());
	return props;
    }
}
