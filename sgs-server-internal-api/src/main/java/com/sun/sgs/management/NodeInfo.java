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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
