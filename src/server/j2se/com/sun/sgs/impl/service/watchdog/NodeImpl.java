/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.util.BoundNamesUtil;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Implements the {@link Node} interface.  The state for a given
 * {@code nodeId} is bound in the datastore with the following service
 * bound name:
 *
 * <p><code>com.sun.sgs.impl.service.watchdog.NodeImpl.<i>nodeId</i></code>
 */
class NodeImpl
    implements Node, ManagedObject, Serializable, Comparable<NodeImpl>
{
    /** The serialVersionUID of this class. */
    private static final long serialVersionUID = 1L;

    private static final long INVALID_ID = -1L;

    /** The name of this class. */
    static final String CLASSNAME = NodeImpl.class.getName();

    /** The prefix for NodeImpl state. */
    private static final String NODE_PREFIX = CLASSNAME;

    /** The node id. */
    private final long id;
    
    /** The host name, or {@code null}. */
    private final String host;

    /** The watchdog client, or {@code null}. */
    private final WatchdogClient client;
    
    /** If true, this node is considered alive. */
    private boolean isAlive;

    /** The ID of the backup for this node. */
    private long backupId = INVALID_ID;

    /** The set of primaries for which this node is a backup. */
    private Set<Long> primaryIds = new HashSet<Long>();

    /**
     * The expiration time for this node. A value of {@code 0} means
     * that either the value has not been intialized or the value is
     * not meaningful because the node has failed.
     */
    private transient long expiration;

    /**
     * Constructs an instance of this class with the given {@code
     * nodeId}, {@code hostname}, and {@code client}.  This instance's
     * alive status is set to {@code true}.  The expiration time for
     * this instance should be set as soon as it is known.
     *
     * @param 	nodeId a node ID
     * @param 	hostName a host name
     * @param	client a watchdog client
     */
    NodeImpl(long nodeId, String hostName, WatchdogClient client) {
	this.id = nodeId;
	this.host = hostName;
	this.client = client;
	this.isAlive = true;
    }

    /**
     * Constructs an instance of this class with the given {@code
     * nodeId}, {@code hostname}, and {@code isAlive} status.  This
     * instance's watchdog client is set to {@code null} and its
     * backup is unassigned (backup ID is -1).
     *
     * @param 	nodeId a node ID
     * @param 	hostName a host name, or {@code null}
     * @param	isAlive if {@code true}, this node is considered alive
     */
    NodeImpl(long nodeId, String hostName, boolean isAlive) {
	this(nodeId, hostName, isAlive, INVALID_ID);
    }
	
    /**
     * Constructs an instance of this class with the given {@code
     * nodeId}, {@code hostname}, {@code backupId}, and {@code
     * isAlive} status.  This instance's watchdog client is set to
     * {@code null}.
     *
     * @param 	nodeId a node ID
     * @param 	hostName a host name, or {@code null}
     * @param	isAlive if {@code true}, this node is considered alive
     * @param	backupId the ID of the node's backup (-1 if no backup
     *		is assigned)
     */
    NodeImpl(long nodeId, String hostName, boolean isAlive, long backupId) {
	this.id = nodeId;
	this.host = hostName;
	this.client = null;
	this.isAlive = isAlive;
	this.backupId = backupId;
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
	if (difference == 0) {
	    difference = id - o.id;
	    if (difference == 0) {
		difference = compareStrings(host, o.host);
	    }
	}
	return difference < 0 ? -1 : (difference > 0 ? 1 : 0);
    }

    /* -- Implement Object -- */

    /** {@inheritDoc} */
    public boolean equals(Object obj) {
	if (obj == null) {
	    throw new NullPointerException("obj is null");
	} else if (this == obj) {
	    return true;
	} else if (obj.getClass() == this.getClass()) {
	    NodeImpl node = (NodeImpl) obj;
	    return id == node.id && compareStrings(host, node.host) == 0;
	}
	return false;
    }

    /** {@inheritDoc} */
    public int hashCode() {
	return ((int) (id >>> 32)) ^ ((int) id);
    }

    /** {@inheritDoc} */
    public String toString() {
	return getClass().getName() + "[" + id + "," +
	    (isAlive() ? "alive" : "failed") + "]@" + host;
    }

    /* -- package access methods -- */

    /**
     * Returns the watchdog client, or {@code null}.
     */
    WatchdogClient getWatchdogClient() {
	return client;
    }
    
    /**
     * Returns the expiration time.  A value of {@code 0} means that
     * either the value has not been intialized or the value is not
     * meaningful because the node has failed.  If {@link #isAlive}
     * returns {@code false} the value returned from this method is
     * not meaningful.
     */
    synchronized long getExpiration() {
	return expiration;
    }

    /**
     * Sets the expiration time for this node instance.
     *
     * @param	newExpiration the new expiration value
     */
    synchronized void setExpiration(long newExpiration) {
	expiration = newExpiration;
    }

    /**
     * Returns {@code true} if the node is expired, and {@code false}
     * otherwise.
     *
     * @return	{@code true} if the node is expired, and {@code false}
     *		otherwise
     */
    synchronized boolean isExpired() {
	return expiration <= System.currentTimeMillis();
    }
    
    /**
     * Sets the alive status of this node instance to {@code false},
     * sets this node's backup to the specified {@code backup},
     * empties the set of primaries for which this node is recovering,
     * and updates the node's state in the specified {@code
     * dataService}.  Subsequent calls to {@link #isAlive isAlive}
     * will return {@code false}
     *
     * @param	dataService a data service
     * @param	backup a chosen backup
     * @throws	ObjectNotFoundException if this node has been removed
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    synchronized void setFailed(DataService dataService, NodeImpl backup) {
	NodeImpl nodeImpl = getForUpdate(dataService);
	this.isAlive = nodeImpl.isAlive = false;
	this.backupId = nodeImpl.backupId =
	    (backup != null) ?
	    backup.getId() :
	    INVALID_ID;
	this.primaryIds.clear();
	nodeImpl.primaryIds.clear();
    }

    /**
     * Adds the specified {@code primaryId} to the list of primaries
     * for which this node is a backup, and updates the node's state
     * in the specified {@code dataService}.
     *
     * @param	dataService a data service
     * @param	primaryId the ID of a primary for which this node is a
     *		backup
     * @throws	ObjectNotFoundException if this node has been removed
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    synchronized void addPrimary(DataService dataService, long primaryId) {
	NodeImpl nodeImpl = getForUpdate(dataService);
	primaryIds.add(primaryId);
	nodeImpl.primaryIds.add(primaryId);
    }

    synchronized Set<Long> getPrimaries() {
	return primaryIds;
    }

    /** Returns {@code true} if this node has a backup. */
    synchronized boolean hasBackup() {
	return backupId != INVALID_ID;
    }

    /**
     * Returns the backup for this node.  If no backup is assigned to
     * this node, then {@code IllegalStateException} is thrown.  This
     * method should only be invoked if {@code hasBackup} returns
     * {@code true}.
     */
    synchronized long getBackupId() {
	return backupId;
    }

    /**
     * Stores this instance in the specified {@code dataService}.
     * This method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    synchronized void putNode(DataService dataService) {
	dataService.setServiceBinding(getNodeKey(id), this);
    }
    
    /**
     * Fetches this node's state from the specified {@code
     * dataService}, marked for update.
     *
     * @param	dataService a data service
     * @throws	ObjectNotFoundException if this node has been removed
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
     private NodeImpl getForUpdate(DataService dataService) {
	NodeImpl nodeImpl = getNode(dataService, id);
	if (nodeImpl == null) {
	    throw new ObjectNotFoundException("node is removed");
	}
	// update non-final fields before
	dataService.markForUpdate(nodeImpl);
	return nodeImpl;
    }

    /**
     * Removes the node with the specified {@code nodeId} and its
     * binding from the specified {@code dataService}.  If the binding
     * has already been removed from the {@code dataService} this
     * method takes no action.  This method should only be called
     * within a transaction.
     *
     * @param	dataService a data service
     * @param	nodeId a node ID
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static void removeNode(DataService dataService, long nodeId) {
	String key = getNodeKey(nodeId);
	NodeImpl node;
	try {
	    node = dataService.getServiceBinding(key, NodeImpl.class);
	    dataService.removeServiceBinding(key);
	    dataService.removeObject(node);
	} catch (NameNotBoundException e) {
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
     * Marks all nodes currently bound in the specified {@code
     * dataService} as failed, and returns a collection of those
     * nodes.  This method should only be called within a transaction.
     *
     * @param	dataService a data service
     * @return	a collection of currently bound nodes, each marked as failed
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static Collection<NodeImpl> markAllNodesFailed(DataService dataService) {
	Collection<NodeImpl> nodes = new ArrayList<NodeImpl>();
	for (String key :
	     BoundNamesUtil.getServiceBoundNamesIterable(
		dataService, NODE_PREFIX))
	{
	    NodeImpl node = dataService.getServiceBinding(key, NodeImpl.class);
	    node.setFailed(dataService, null);
	    nodes.add(node);
	}
	return nodes;
    }

    /**
     * Returns an iterator for {@code Node} instances to be retrieved
     * from the specified {@code dataService}.  The returned iterator
     * does not support the {@code remove} operation.  This method
     * should only be called within a transaction, and the returned
     * iterator should only be used within that transaction.
     *
     * @param	dataService a data service
     * @return	an iterator for nodes
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static Iterator<Node> getNodes(DataService dataService) {
	return new NodeIterator(dataService);
    }

    /* -- private methods and classes -- */

    /**
     * Compares the specified strings and returns -1, 0, or 1
     * according to whether the first string is less than, equal to,
     * or greater than the second string in a lexicographic ordering.
     * In this ordering, a string with a value of {@code null} is less
     * than any non-{@code null} string.
     *
     * @param	s1 a string, or {@code null}
     * @param	s2 a string, or {@code null}
     * @return	-1, 0, or 1 according to whether {@code s1} is less than,
     *		equal to, or greater than {@code s2}
     */
    private static int compareStrings(String s1, String s2) {
	if (s1 == null) {
	    return (s2 == null) ? 0 : -1;
	} else if (s2 == null) {
	    return 1;
	} else {
	    return s1.compareTo(s2);
	}
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
