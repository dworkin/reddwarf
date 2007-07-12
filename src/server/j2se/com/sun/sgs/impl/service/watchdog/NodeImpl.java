/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import java.io.Serializable;
import java.util.Iterator;

/**
 * Implements the {@link Node} interface.  The state for a given
 * {@code nodeId} is bound in the datastore with the following service
 * bound name:
 *
 * <p><code>com.sun.sgs.impl.service.watchdog.NodeImpl.<i>nodeId</i></code>
 */
class NodeImpl implements Node, ManagedObject, Serializable {
    
    /** The serialVersionUID of this class. */
    private static final long serialVersionUID = 1L;

    /** The name of this class. */
    private static final String CLASSNAME = NodeImpl.class.getName();

    /** The prefix for NodeImpl state. */
    private static final String NODE_PREFIX = CLASSNAME;

    /** The node id. */
    private final long id;
    
    /** The host name. */
    private final String host;
    
    /** If true, this node is considered alive. */
    private boolean isAlive;

    /**
     * Constructs an instance of this class with the given {@code nodeId},
     * {@code hostname}, and {@code alive} status.
     *
     * @param 	nodeId a node ID
     * @param 	hostName a host name, or {@code null}
     * @param 	alive the alive status
     */
    NodeImpl(long nodeId, String hostName, boolean alive) {
	this.id = nodeId;
	this.host = hostName;
	this.isAlive = alive;
    }

    /** {@inheritDoc} */
    public long getId() {
	return id;
    }

    /** {@inheritDoc} */
    public String getHostName() {
	return host;
    }

    /** {@inheritDoc} */
    public boolean isAlive() {
	return isAlive;
    }

    /**
     * Sets the alive status of this node instance to {@code false}.
     * Subsequent calls to {@link #isAlive isAlive} will return {@code false}.
     */
    void setFailed() {
	isAlive = false;
    }

    /**
     * Returns the {@code Node} instance for the given {@code nodeId},
     * retrieved from the specified {@code dataService}.  This method
     * should only be called within a transaction.
     *
     * @param	dataService a data service
     * @param	nodeId a node ID
     * @return	the node for the given {@code nodeId}
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static Node getNode(DataService dataService, long nodeId) {
	Node node;
	try {
	    String key = NODE_PREFIX + "." + nodeId;
	    node = dataService.getServiceBinding(key, NodeImpl.class);
	} catch (ObjectNotFoundException e) {
	    node = new NodeImpl(nodeId, null, false);
	}
	return node;
    }

    /**
     * Returns an iterator for {@code Node} instances to be retrieved
     * from the specified {@code dataService}.  The returned iterator
     * does not support the {@code remove} operation.  This method
     * should only be called within a transaction.
     *
     * @param	dataService a data service
     * @return	an iterator for nodes
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static Iterator<Node> getNodes(DataService dataService) {
	return new NodeIterator(dataService);
    }

    /**
     * An iterator for node state.
     */
    private static class NodeIterator implements Iterator<Node> {

	/** The data service. */
	private final DataService dataService;

	/** The underlying iterator for service bound names. */
	private Iterator<String> iterator;

	/**
	 * Constructs an instance of this class with the specified
	 * {@code dataService}.
	 */
	NodeIterator(DataService dataService) {
	    this.dataService = dataService;
	    this.iterator =
		BoundNamesUtil.getServiceBoundNamesIterator(
		    dataService, NODE_PREFIX);
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    return iterator.hasNext();
	}

	/** {@inheritDoc} */
	public Node next() {
	    String key = iterator.next();
	    return dataService.getServiceBinding(key, NodeImpl.class);
	}

	/** {@inheritDoc} */
	public void remove() {
	    throw new UnsupportedOperationException("remove is not supported");
	}
    }
    
    
}
