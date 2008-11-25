/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.management;

import java.beans.ConstructorProperties;
import java.io.Serializable;

/**
 *  Management information about a single node
 * 
 */
public class NodeInfo implements Serializable {
    /** The serialVersionUID of this class. */
    private static final long serialVersionUID = 1L;
    
    private String host;
    private int port;
    private long id;
    private boolean live;
    private long backup;
    private int jmxPort;
    // an enum to say what sort of node this is?
    // some sort of health metric:  red, yellow, green?
    // whether it is recovering something (and what?)
    
    @ConstructorProperties({"host", "port", "id", "live", "backup", "jmxPort"})
    public NodeInfo(String host, int port, long id, boolean live, long backup, 
                    int jmxPort) 
    {
        this.host = host;
        this.port = port;
        this.id = id;
        this.live = live;
        this.backup = backup;
        this.jmxPort = jmxPort;
    }
    
    public String getHost() {
        return host;
    }
    public int getPort() {
        return port;
    }
    public long getId() {
        return id;
    }
    public boolean isLive() {
        return live;
    }
    public long getBackup() {
        return backup;
    }
    public int getJmxPort() {
        return jmxPort;
    }
    @Override
    public String toString() { 
        return host + ":" + port;
    }
}