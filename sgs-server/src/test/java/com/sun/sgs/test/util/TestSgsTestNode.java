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

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.util.Properties;
import junit.framework.TestCase;
import org.junit.runner.RunWith;

@RunWith(FilteredJUnit3TestRunner.class)
public class TestSgsTestNode extends TestCase {
    /** The node that creates the servers */
    private SgsTestNode serverNode;
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];
    
    /** Test setup. */
    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());
        setUp(null);
    }

    protected void setUp(Properties props) throws Exception {
        
        serverNode = new SgsTestNode("TestNodeMappingServiceImpl", null, props);
        
    }
   
    /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param props properties for node creation, or {@code null} if default
     *     properties should be used
     *
     * @param numNodes the number of additional nodes to create
     */
    private void addNodes(Properties props, int numNodes) throws Exception {
        // Create the other nodes
        additionalNodes = new SgsTestNode[numNodes];
        
        for (int i = 0; i < numNodes; i++) {
            SgsTestNode node =  new SgsTestNode(serverNode, null, props);
            additionalNodes[i] = node;
        }
    }
        
    /** Shut down the nodes. */
    protected void tearDown() throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        serverNode.shutdown(true);
    }
    
    /* Test that we get different data managers when we try to
     * get them from two different test nodes.  This ensures that
     * our context is correct.
     */
    public void testContext() throws Exception {
        addNodes(null, 1);
        TransactionScheduler ts1 =
            serverNode.
                getSystemRegistry().getComponent(TransactionScheduler.class);
        TransactionScheduler ts2 =
            additionalNodes[0].
                getSystemRegistry().getComponent(TransactionScheduler.class);
        
        GetManagerTask task = new GetManagerTask();
        ts1.runTask(task, new DummyIdentity("first"));
        Object o1 = task.getManager();
        ts2.runTask(task, new DummyIdentity("second"));
        Object o2 = task.getManager();
        assertNotSame("expected different managers!", o1, o2);
        
        // Now reset to the first manager
        ts1.runTask(task, new DummyIdentity("first"));
        assertSame("expected same object", o1, task.getManager());
    }
    
    private class GetManagerTask extends TestAbstractKernelRunnable {
        private Object manager = null;
        public void run() throws Exception {
            manager = AppContext.getManager(DataManager.class);
        }
        public Object getManager() { return manager; }
    }
}
