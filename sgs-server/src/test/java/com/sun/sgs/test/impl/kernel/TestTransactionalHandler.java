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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.logging.TransactionalHandler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;
import static com.sun.sgs.test.util.UtilReflection.getConstructor;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Constructor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *  Tests for the transactional logger handler.  
 */
@RunWith(FilteredNameRunner.class)
public class TestTransactionalHandler {
    private static final Constructor<TransactionalHandler>
        ctor = getConstructor(
	    TransactionalHandler.class, TransactionProxy.class, Handler.class);
    private SgsTestNode serverNode;
    private TransactionProxy txnProxy;
    private Logger logger;
    
    @Before
    public void setUp() throws Exception {
        serverNode = new SgsTestNode("TestNodeMappingServiceImpl", null, null);
        txnProxy = serverNode.getProxy();
        
        logger = Logger.getLogger("testLogger");
        // Find a parent with handlers, which we'll wrap with a transactional
        // handler.  This mimics the actions of the TransactionalLogManager.
        Logger parent = logger;
        while (parent != null) {
            parent = parent.getParent();
            if (parent.getHandlers().length != 0) {
                break;
            }
        }   
	assert (parent != null);
        for (Handler h : parent.getHandlers()) {
            Handler newHandler = ctor.newInstance(txnProxy, h);
            logger.addHandler(newHandler);
            parent.removeHandler(h);
        }
        logger.setLevel(Level.FINEST);
    }
    
    // Test for sgs-server issue #126
    @Test 
    public void runNoTransaction() throws Exception {
        logger.log(Level.SEVERE, "Not in a transaction!");
        
    }
}
