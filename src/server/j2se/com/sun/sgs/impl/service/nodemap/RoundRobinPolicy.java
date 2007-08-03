/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.service.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A very simple round robin assignment policy.
 * 
 */
class RoundRobinPolicy implements NodeAssignPolicy {
    
    private final List<Long> liveNodes = 
        Collections.synchronizedList(new ArrayList<Long>());
    private AtomicInteger nextNode = new AtomicInteger();
    
    /** Creates a new instance of RoundRobinPolicy */
    public RoundRobinPolicy(Properties props) {
    }
    
    /** {@inheritDoc} */
    public long chooseNode(Identity id) {
        if (liveNodes.size() < 1) {
            // We don't have any live nodes to assign to.
            // Let the caller figure it out.
            throw new IllegalStateException("no live nodes available");
        }
        return liveNodes.get(nextNode.getAndIncrement() % liveNodes.size());
    }
    
    /** {@inheritDoc} */
    public void nodeStarted(long nodeId) {
        liveNodes.add(nodeId);
    }

    /** {@inheritDoc} */
    public void nodeStopped(long nodeId) {
        liveNodes.remove(nodeId);
    }
    
    /** {@inheritDoc} */
    public void reset() {
        liveNodes.clear();
        nextNode.set(0);
    }
    
}
