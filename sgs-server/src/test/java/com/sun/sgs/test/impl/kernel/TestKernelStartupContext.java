/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.internal.InternalContext;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A regression test for sgs-server issue 129.  It's desirable
 * to allow Services to be able to use our app utilities in
 * their constructors.
 */
@RunWith(FilteredNameRunner.class)
public class TestKernelStartupContext {
    @Before
    public void clearManagerLocator() {
        // Ensure that the manager locator has not been set before
        // running the tests.
        InternalContext.setManagerLocator(null);
    } 
    
    @Test 
    public void startupContext() throws Exception {
        // Ensure that we have a context during startup
        Properties properties =
            SgsTestNode.getDefaultProperties("TestStartupContext", null, null);
        properties.setProperty(StandardProperties.SERVICES, 
                               TestService.class.getName());
        properties.setProperty(StandardProperties.MANAGERS, "");
        
        SgsTestNode node = null;
        try {
            node = new SgsTestNode("TestStartupContext", null, properties);
        } finally {
            if (node != null) {
                node.shutdown(false);
            }
        }
    }
    
    /**
     * A service used for testing.  This service is a system extension
     * and has no associated manager.
     */
    public static final class TestService implements Service {
       
        /** 
         * Constructs a TestService instance, using the Data Manager.
         * Because the data service is constructed by the kernel before
         * this service, we expect to be able to reach the data manager.
         * 
         * @param properties the properties
         * @param systemRegistry the component registry
         * @param txnProxy the proxy
         * @throws Exception if there is an error during construction
         */
        public TestService(Properties properties, 
                    ComponentRegistry systemRegistry,
                    TransactionProxy txnProxy)
            throws Exception
        {
            TransactionScheduler transactionScheduler =
                systemRegistry.getComponent(TransactionScheduler.class);

            transactionScheduler.runTask(
		new AbstractKernelRunnable("UseStartupContext") {
		    public void run() {
                        DataManager data = AppContext.getDataManager();
                        System.out.println("Data manager is " + data);
		    } },  txnProxy.getCurrentOwner());
        }
       
        /** {@inheritDoc} */
        public String getName() {
            return "test service";
        }

        /** {@inheritDoc} */
        public void ready() throws Exception {
            // do nothing
        }

        /** {@inheritDoc} */
        public void shutdown() {
            //do nothing
        }
    }
}
