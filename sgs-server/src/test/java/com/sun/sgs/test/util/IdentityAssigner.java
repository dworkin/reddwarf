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

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.util.KernelCallable;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.WatchdogService;
import java.lang.reflect.Method;

/**
 * A utility class used to force the node mapping service to reassign an
 * identity to a new node.
 */
public class IdentityAssigner {
    
    private final NodeMappingServerImpl nodemapServer;
    private final WatchdogService watchdogService;
    private final TransactionScheduler txnScheduler;
    private final Identity taskOwner;
    private final Method mapToNewNode;
    
    /** Creates a new instance of IdentityAssigner */
    public IdentityAssigner(SgsTestNode serverNode)
	throws Exception
    {
        this.nodemapServer = serverNode.getNodeMappingServer();
	this.watchdogService = serverNode.getWatchdogService();
	this.txnScheduler =
	    serverNode.getSystemRegistry().
	    getComponent(TransactionScheduler.class);
	this.taskOwner = serverNode.getProxy().getCurrentOwner();
	try {
	    mapToNewNode = nodemapServer.getClass().getDeclaredMethod(
 		"mapToNewNode",
		new Class[] { Identity.class, String.class,
			      Node.class, long.class });
	    mapToNewNode.setAccessible(true);
	} catch (Exception e) {
	    System.err.println("exception initializing IdentityAssigner");
	    e.printStackTrace();
	    throw new RuntimeException("failed to intialize IdentityAssigner");
	}
    }
    
    /**
     * Assigns the identity with the specified {@code name} from {@code
     * oldNode} to {@code newNode}.
     */
    public void moveIdentity(String name, final long oldNodeId, long newNodeId)
	throws Exception
    {
	Node oldNode = KernelCallable.call(
	    new KernelCallable<Node>("getNode") {
		public Node call() {
		    return watchdogService.getNode(oldNodeId);
		} },
	    txnScheduler, taskOwner);
	mapToNewNode.invoke(
	    nodemapServer,
	    new Object[] {
		new SimpleTestIdentityAuthenticator.DummyIdentity(name),
		this.getClass().getName(), oldNode, newNodeId });
    }
}
