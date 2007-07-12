/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.service.DataService;
import java.io.Serializable;

/**
 * Represents the state for the next node ID.  This state is bound in
 * the datastore with the following service bound name:
 *
 * <p>{@code com.sun.sgs.impl.service.watchdog.NodeId.value}
 */
class NodeId implements ManagedObject, Serializable {

    /** The serialVersionUID of this class. */
    private static final long serialVersionUID = 1L;

    /** The name of this class. */
    private static final String CLASSNAME = NodeId.class.getName();

    /** The name of the NodeId state. */
    private static final String NODE_ID_KEY = CLASSNAME + ".value";

    /** The value of the id. */
    private long value = 0;

    /**
     * Constructs an instance with the initial value of zero.
     */
    private NodeId() {}

    /**
     * Returns the current node ID value, and increments the value.
     *
     * @return the current node ID value, and increments the value.
     */
    private long getAndIncrement() {
	return value++;
    }

    /**
     * Returns the next node ID, retrieved from the specified {@code
     * dataService}.  This method should only be called within a
     * transaction.
     *
     * @param	dataService a data service
     * @return	the next node ID
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     */
    static long nextNodeId(DataService dataService) {
	NodeId nodeId;
	try {
	    nodeId = dataService.getServiceBinding(NODE_ID_KEY, NodeId.class);
	} catch (ObjectNotFoundException e) {
	    nodeId = new NodeId();
	    dataService.setServiceBinding(NODE_ID_KEY, nodeId);
	}
	dataService.markForUpdate(nodeId);
	return nodeId.getAndIncrement();
    }
}
