/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
