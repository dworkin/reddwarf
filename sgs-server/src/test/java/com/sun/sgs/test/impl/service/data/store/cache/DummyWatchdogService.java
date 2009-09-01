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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.test.impl.service.data.store.cache;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * A dummy implementation of the watchdog service, for use in testing the
 * caching data store for a single node.
 */
public class DummyWatchdogService implements WatchdogService {

    /** The transaction proxy. */
    private final TransactionProxy txnProxy;

    /** The local node. */
    private final NodeImpl node;

    /** Node listeners. */
    private final List<NodeListener> nodeListeners =
	new ArrayList<NodeListener>();

    /**
     * Creates an instance of this class.
     *
     * @param	properties the properties for configuring this instance
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     */
    public DummyWatchdogService(Properties properties,
				ComponentRegistry systemRegistry,
				TransactionProxy txnProxy)
    {
	this.txnProxy = txnProxy;
	node = new NodeImpl(
	    txnProxy.getService(DataService.class).getLocalNodeId());
    }

    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return "DummyWatchdogService";
    }

    /** {@inheritDoc} */
    public void ready() { }

    /** {@inheritDoc} */
    public void shutdown() { }

    /* -- Implement WatchdogService -- */

    /** {@inheritDoc} */
    public boolean isLocalNodeAlive() {
	return node.isAlive();
    }

    /** {@inheritDoc} */
    public boolean isLocalNodeAliveNonTransactional() {
	return node.isAlive();
    }

    /** {@inheritDoc} */
    public Iterator<Node> getNodes() {
	return Collections.singleton((Node) node).iterator();
    }

    /** {@inheritDoc} */
    public Node getNode(long nodeId) {
	return (nodeId == node.getId()) ? node : null;
    }

    /** {@inheritDoc} */
    public Node getBackup(long nodeId) {
	return null;
    }

    /** {@inheritDoc} */
    public void addNodeListener(NodeListener listener) {
	nodeListeners.add(listener);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This method is not supported.
     */
    public void addRecoveryListener(RecoveryListener listener) {
	throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    public void reportFailure(long nodeId, String className) {
	if (nodeId == node.getId() && node.isAlive()) {
	    node.setNotAlive();
	    Thread t = new Thread() {
		public void run() {
		    for (NodeListener listener : nodeListeners) {
			listener.nodeFailed(node);
		    }
		}
	    };
	    t.start();
	}
    }

    /** Implement {@code Node}. */
    private static class NodeImpl implements Node {
	private final long id;
	private boolean alive = true;
	NodeImpl(long id) { this.id = id; }
	public long getId() { return id; }
	public String getHostName() { return "localhost"; }
	public synchronized boolean isAlive() {
	    return alive;
	}
	synchronized void setNotAlive() {
	    alive = false;
	}
    }
}
