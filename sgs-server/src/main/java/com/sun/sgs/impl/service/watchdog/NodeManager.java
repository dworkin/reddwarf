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

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.management.NodeInfo;
import com.sun.sgs.management.NodesMXBean;

/**
 *
 *  The manager exposing all the nodes attached in the system.
 * 
 * JANE todo - can this be a non-public class?  An inner class of WDogServer?
 */
class NodeManager implements NodesMXBean {

    private WatchdogServerImpl watchdog;
    private NodeInfo[] nodes;
    
    NodeManager(WatchdogServerImpl watchdog) {
        this.watchdog = watchdog;
    }
    
    //this operation needs to be part of a service, as I need a transaction
    public NodeInfo[] getNodes() {
        nodes = watchdog.getAllNodeInfo();
        for (NodeInfo node : nodes) {
            System.out.println("GETNODES:  node is : " + node);
        }
        return nodes;
    }
}
