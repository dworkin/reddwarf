/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.management;

import com.sun.sgs.service.Node.Health;
import java.beans.ConstructorProperties;
import java.io.Serializable;

/**
 *  Management information about a single node.
 */
public class NodeInfo implements Serializable {
    /** The serialVersionUID of this class. */
    private static final long serialVersionUID = 1L;
    
    private String host;
    private long id;
    private Health health;
    private long backup;
    private int jmxPort;
    
    // Maybe combine this with the ConfigMXBean?
    //
    // some sort of health metric:  red, yellow, green?
    // whether it is recovering something (and what?)
    // coordinator for any channels?
    // time booted/time failed?
    // method to shut down the node?
    
    // A note about jmxPort - it would be nice if we could add a jmxHost,
    // as well, allowing for a clean separation of a management network
    // from a client network.  We're not sure how to support that with
    // JMX, so this issue can be examined later.
    
    /**
     * Creates a NodeInfo object.
     * 
     * @param host the host name of the machine
     * @param id   the unique identifier for this node
     * @param health the node's health
     * @param backup the backup node for this node
     * @param jmxPort the port for JMX remote connections
     */
    @ConstructorProperties({"host", "id", "health", "backup", "jmxPort" })
    public NodeInfo(String host, long id, Health health, long backup,
                    int jmxPort) 
    {
        this.host = host;
        this.id = id;
        this.health = health;
        this.backup = backup;
        this.jmxPort = jmxPort;
    }
    
    /**
     * Returns the host name the node is running on.
     * @return host name of the machine the node is running on
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Returns the unique node identifier, which is assigned internally.
     * 
     * @return the unique node identifier
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the node health.
     *
     * @return the node health
     */
    public Health getHealth() {
        return health;
    }

    /**
     * Returns whether the node is alive or failed.  Once a node has
     * failed, it is never considered alive again and is removed from
     * the system when any required fail-over procedures have completed.
     *
     * @return {@code true} if the node is alive
     */
    public boolean isLive() {
        return health.isAlive();
    }

    /**
     * Returns the node id of the backup node for this node, or {@code -1} if
     * no backup is assigned.
     * 
     * @return the node id of the backup node, or {@code -1} if no backup
     *         is assigned.
     */
    public long getBackup() {
        return backup;
    }
    
    /**
     * Returns the port JMX is listening on for remote connections, or 
     * {@code -1} if only local JMX connections are allowed.
     * 
     * @return the port JMX is listening on for remote connections, or 
     *         {@code -1} if only local JMX connections are allowed
     */
    public int getJmxPort() {
        return jmxPort;
    }
    
    /** {@inheritDoc} */
    public String toString() { 
        return host + ":" + id;
    }
}
