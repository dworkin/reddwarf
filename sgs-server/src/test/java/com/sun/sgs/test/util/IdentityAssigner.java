/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
