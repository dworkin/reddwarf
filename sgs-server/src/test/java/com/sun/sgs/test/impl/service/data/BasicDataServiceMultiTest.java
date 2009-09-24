/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A base class for multi-node tests of the data service. */
@RunWith(FilteredNameRunner.class)
public abstract class BasicDataServiceMultiTest extends Assert {
    
    /** The server node or {@code null}. */
    protected static SgsTestNode serverNode = null;

    /** The application nodes. */
    protected static final List<SgsTestNode> appNodes
	= new ArrayList<SgsTestNode>();

    /** The application name. */
    protected final String appName = getClass().getName();

    /**
     * The number of application nodes to create.  Run the setup method after
     * changing the value of this field.
     */
    protected int numAppNodes = 2;

    /** Creates an instance of this class. */
    protected BasicDataServiceMultiTest() { }

    /** Creates the server node and application nodes. */
    @Before
    public void setUp() throws Exception {
	if (serverNode == null) {
	    serverNode = new SgsTestNode(
		appName, null, getServerProperties());
	}
	while (numAppNodes < appNodes.size()) {
	    appNodes.remove(0).shutdown(true);
	}
	while (numAppNodes > appNodes.size()) {
	    appNodes.add(new SgsTestNode(serverNode, null, null));
	}
    }

    /** Shuts down the application nodes and the server node. */
    @AfterClass
    public static void tearDownClass() throws Exception {
	for (SgsTestNode node : appNodes) {
	    node.shutdown(true);
	}
	appNodes.clear();
	if (serverNode != null) {
	    serverNode.shutdown(true);
	    serverNode = null;
	}
    }

    /**
     * Returns the configuration properties to pass to the {@link SgsTestNode}
     * constructor to create the server node.
     *
     * @return	the server configuration properties
     * @throws	Exception if a problem occurs
     */
    protected abstract Properties getServerProperties() throws Exception;

    /* -- Tests -- */

    /* -- Test getLocalNodeId -- */

    /**
     * Test that {@code getLocalNodeId} assigns unique IDs to multiple nodes
     * that are running simultaneously.
     */
    @Test
    public void testGetLocalNodeIdMulti() throws Exception {
	long id = serverNode.getDataService().getLocalNodeId();
	Set<Long> allIds = new HashSet<Long>();
	Iterator<SgsTestNode> appNodeIter = appNodes.iterator();
	while (true) {
	    assertTrue("Node ID should be greater than 0: " + id, id > 0);
	    assertTrue("Node ID should be unique:" +
		       "\n  ID: " + id +
		       "\n  Previous IDs: " + allIds,
		       !allIds.contains(id));
	    if (!appNodeIter.hasNext()) {
		break;
	    }
	    allIds.add(id);
	    id = appNodeIter.next().getDataService().getLocalNodeId();
	}
    }
}    

    
