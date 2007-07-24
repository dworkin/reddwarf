/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.service.Node;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  Just a dummy class. DO NOT CHECK IN.
 *
 */
public class NodeImpl implements Node {

    private static AtomicLong count = new AtomicLong();
    private long id;

    public NodeImpl() {
        this(count.incrementAndGet());  
    }
    
    public NodeImpl(long id) {
        this.id = id;
    }

    public String getHostName() {
        return "local";
    }

    public long getId() {
        return id;
    }

    public boolean isAlive() {
        return true;
    }
    
    public boolean equals(Object obj) {
        if (!(obj instanceof NodeImpl)) {
            return false;
        }
        NodeImpl nobj = (NodeImpl) obj;
        return nobj.getHostName() == getHostName() &&
               nobj.getId() == id;
    }
    
    public int hashCode() {
        return (new Long(id)).hashCode();
    }
}
