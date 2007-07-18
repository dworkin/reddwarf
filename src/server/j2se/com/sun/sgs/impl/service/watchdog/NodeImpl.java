/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
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
 *
 * <p>This implementation is not thread-safe, and therefore must be
 * synchronized by the caller.
 */
class NodeImpl
    implements Node, ManagedObject, Serializable, Comparable<NodeImpl>
{
    
    /** The serialVersionUID of this class. */
    private static final long serialVersionUID = 1L;

    /** The name of this class. */
    private static final String CLASSNAME = NodeImpl.class.getName();

    /** The prefix for NodeImpl state. */
    private static final String NODE_PREFIX = CLASSNAME;

    /** The node id. */
    private final long id;
    
    /** The host name, or {@code null}. */
    private final String host;
    
    /** If true, this node is considered alive. */
    private boolean isAlive;

    /** The expiration time for this node. */
    private transient long expiration;

    /**
     * Constructs an instance of this class with the given {@code nodeId},
     * {@code hostname}, and {@code alive} status.
     *
     * @param 	nodeId a node ID
     * @param 	hostName a host name, or {@code null}
     * @param 	alive the alive status
     * @param	expiration the node's expiration time
     */
    NodeImpl(long nodeId, String hostName, boolean alive, long expiration) {
	this.id = nodeId;
	this.host = hostName;
	this.isAlive = alive;
	this.expiration = expiration;
    }

    /* -- Implement Node -- */

    /** {@inheritDoc} */
    public long getId() {
	return id;
    }

    /** {@inheritDoc} */
    public String getHostName() {
	return host;
    }

    /** {@inheritDoc} */
    public synchronized boolean isAlive() {
	return isAlive;
    }

    /* -- Implement Comparable -- */

    /** {@inheritDoc} */
    public int compareTo(NodeImpl o) {
	long difference = getExpiration() - o.getExpiration();
	if (difference < 0) {
	    return -1;
	} else if (difference == 0) {
	    return 0;
	} else {
	    return 1;
	}
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    NodeImpl node = (NodeImpl) obj;
	    return id == node.id && host.equals(node.host);
	}
	return false;
    }
    
    /** {@inheritDoc} */
    public int hashCode() {
	return (int) id;
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + id + "]@" + host;
    }

    /* -- package access methods -- */
    
    /**
     * Returns the expiration time.
     */
    synchronized long getExpiration() {
	return expiration;
    }

    /**
     * Sets the expiration time for this node instance.
     */
    synchronized void setExpiration(long newExpiration) {
	expiration = newExpiration;
    }
    
    /**
     * Sets the alive status of this node instance to {@code false}.
     * Subsequent calls to {@link #isAlive isAlive} will return {@code false}.
     */
    synchronized void setFailed() {
	isAlive = false;
    }

    /**
     * Stores this instance in the specified {@code dataService} if it
     * is not already there, otherwise updates the node's state in the
     * data service.  This method should only be called within a
     * transaction.
     *
     * @param	dataService a data service
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    synchronized void updateNode(DataService dataService) {
	
	NodeImpl node = getNode(dataService, id);
	if (node == null) {
	    dataService.markForUpdate(this);
	    dataService.setServiceBinding(getNodeKey(id), this);
	} else {
	    dataService.markForUpdate(node);
	    node.isAlive = isAlive;
	    node.expiration = expiration;
	}
    }

    /**
     * Returns the {@code Node} instance for the given {@code nodeId},
     * retrieved from the specified {@code dataService}, or {@code
     * null} if the node isn't bound in the data service .  This
     * method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @param	nodeId a node ID
     * @return	the node for the given {@code nodeId}, or {@code null}
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static NodeImpl getNode(DataService dataService, long nodeId) {
	String key = getNodeKey(nodeId);
	NodeImpl node = null;
	try {
	    node = dataService.getServiceBinding(key, NodeImpl.class);
	} catch (NameNotBoundException e) {
	}
	return node;
    }

    /**
     * Returns the key to access from the data service the {@code
     * Node} instance with the specified {@code nodeId}.
     *
     * @param	a node ID
     * @return	a key for acessing the {@code Node} instance
     */
    private static String getNodeKey(long nodeId) {
	return NODE_PREFIX + "." + nodeId;
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
