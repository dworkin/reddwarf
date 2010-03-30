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

package com.sun.sgs.impl.service.data.store.cache.server;

import com.sun.sgs.impl.util.lock.LockRequest;

/**
 * A lock request from a node, which also tracks whether the associated item
 * has been called back.
 */
class NodeRequest extends LockRequest<Object> {

    /**
     * Whether the cache ownership of the item represented by this request
     * has been, or is in the process of being, called back.  Synchronize
     * on this instance when accessing this field.
     */
    private boolean calledBack;

    /**
     * Creates an instance of this class.
     *
     * @param	nodeInfo information about the requesting node
     * @param	key the key identifying the item to lock
     * @param	forWrite whether the item should be locked for write
     * @param	upgrade whether the item should be upgraded to write
     *		access from read access
     */
    NodeRequest(
	NodeInfo nodeInfo, Object key, boolean forWrite, boolean upgrade)
    {
	super(nodeInfo, key, forWrite, upgrade);
    }

    /**
     * Notes that the cache ownership of the item represented by this
     * request should be called back.  Returns {@code true} if the callback
     * should be made, and {@code false} if the callback is already
     * underway.
     *
     * @return	whether the callback should be performed
     */
    synchronized boolean noteCallback() {
	if (!calledBack) {
	    calledBack = true;
	    return true;
	} else {
	    return false;
	}
    }

    /**
     * Returns the node information for this request.
     *
     * @return	the node information for this request
     */
    NodeInfo getNodeInfo() {
	return (NodeInfo) getLocker();
    }
}
