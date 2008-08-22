/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;

import com.sun.sgs.app.util.ScalableHashMap;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.kernel.AccessedObject;
import com.sun.sgs.kernel.AccessReporter;
import com.sun.sgs.kernel.AccessReporter.AccessType;

import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.ManagedSerializable;

import com.sun.sgs.kernel.TransactionScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;

import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.test.util.SgsTestNode;

import java.util.Properties;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.junit.runner.RunWith;

import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;

/**
 * Tests the {@link AccessCoordinatorImpl} class
 */
@RunWith(NameRunner.class)
public class TestAccessCoordinatorImpl extends Assert {

    private SgsTestNode serverNode;
    private TransactionScheduler txnScheduler;
    private Identity taskOwner;
    private DataService dataService;

    private AccessCoordinator accessCoordinator;


    /**
     * Test management.
     */


    @Before public void startup() throws Exception {
        Properties properties = SgsTestNode.
	    getDefaultProperties("TestAccessCoordinatorImpl",
				 null, null);

        properties.setProperty("com.sun.sgs.finalService", "DataService");
        properties.setProperty("com.sun.sgs.txn.timeout", "1000000");

        serverNode = new SgsTestNode("TestAccessCoordinatorImpl", null, properties);

        taskOwner = serverNode.getProxy().getCurrentOwner();
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
	dataService = serverNode.getDataService();

        accessCoordinator = serverNode.getSystemRegistry().
            getComponent(AccessCoordinator.class);

    }

    @After  public void shutdown() throws Exception {
        if (serverNode != null)
            serverNode.shutdown(true);
    }


    /*
     * AccessCooridnatorImpl tests
     */
   
    @Test(expected=NullPointerException.class)
    public void testGetConflictWithNull() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    accessCoordinator.getConflictingTransaction(null);
		}
	    }, taskOwner);
    }

    @Test(expected=NullPointerException.class)
    public void testAccessSourceNullName() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    accessCoordinator.registerAccessSource(null, Object.class);
		}
	    }, taskOwner);   
    }

    @Test(expected=NullPointerException.class)
    public void testAccessSourceNullType() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    accessCoordinator.registerAccessSource("source", null);
		}
	    }, taskOwner);    
    }

    /*
     * AccessReporter tests
     */
    
    @Test public void testRegisterAccessSource() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", Integer.class);
		}
	    }, taskOwner);    
    }

    @Test(expected=NullPointerException.class)
    public void testAccessReporterNullTransaction() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", Integer.class);
		    reporter.reportObjectAccess(null, 1, AccessType.READ);
		}
	    }, taskOwner);   
    }

    @Test(expected=NullPointerException.class)
    public void testAccessReporterNullTransaction2() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", Integer.class);
		    reporter.reportObjectAccess(null, 1, AccessType.READ, "description");
		}
	    }, taskOwner);
    }

    @Test(expected=NullPointerException.class)
    public void testAccessReporterNullTransaction3() throws Exception {
	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {

		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", Integer.class);
		    reporter.setObjectDescription(null, 1, "description");
		}
	    }, taskOwner);
    }

    @Test(expected=NullPointerException.class)
    public void testAccessReporterNullValue() throws Exception {
	final AccessReporter<Integer> reporter = 
	    accessCoordinator.registerAccessSource("test", Integer.class);

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    reporter.setObjectDescription(null, "description");		    
		}
	    }, taskOwner);
    }

    @Test public void testAccessReporterNullDescription() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
 		    final AccessReporter<Integer> reporter = 
 			accessCoordinator.registerAccessSource("test", 
 							       Integer.class);
		    reporter.reportObjectAccess(1, AccessType.READ);
 		    reporter.setObjectDescription(1, null);		    
		}
	    }, taskOwner);
    }

    @Test(expected=NullPointerException.class)
    public void testAccessReporterNullId() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", 
							       Integer.class);
		    reporter.reportObjectAccess(null, AccessType.READ);		    
		}
	    }, taskOwner);
    }

    @Test(expected=NullPointerException.class)
    public void testAccessReporterNullAccessType() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test",
							       Integer.class);

		    reporter.reportObjectAccess(1, null);		    
		}
	    }, taskOwner);
    }    

    @Test public void testAccessReporterAccessWithDescription() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", 
							       Integer.class);
		    reporter.reportObjectAccess(1, AccessType.READ, "desc");
		}
	    }, taskOwner);
    }

    @Test public void testAccessReporterAccessWithDescriptionAndCurTxn() 
	throws Exception {

	final SgsTestNode node = serverNode;

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    TransactionProxy proxy = node.getProxy();
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", 
							       Integer.class);
		    reporter.reportObjectAccess(proxy.getCurrentTransaction(),
						1, AccessType.READ, "desc");
		}
	    }, taskOwner);
    }


    @Test public void testAccessReporterMultipleDescriptions() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", 
							       Integer.class);
		    reporter.reportObjectAccess(1, AccessType.READ);
		    reporter.setObjectDescription(1, "desc1");		    
		    reporter.setObjectDescription(1, "desc2");
		}
	    }, taskOwner);
    }

    @Test public void testAccessReporterMultipleAccesses() throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", 
							       Integer.class);
		    reporter.reportObjectAccess(1, AccessType.READ);
		    reporter.setObjectDescription(1, "desc1");
		    reporter.reportObjectAccess(2, AccessType.READ);    
		    reporter.setObjectDescription(2, "desc2");
		    reporter.reportObjectAccess(3, AccessType.WRITE);    
		    reporter.setObjectDescription(3, "desc3");
		}
	    }, taskOwner);
    }

    @Test public void testAccessReporterDescriptionBeforeAccess() 
	throws Exception {

	txnScheduler.runTask(
	    new AbstractKernelRunnable() {
		public void run() throws Exception {
		    AccessReporter<Integer> reporter = 
			accessCoordinator.registerAccessSource("test", 
							       Integer.class);

		    reporter.setObjectDescription(1, "desc1");		    
		}
	    }, taskOwner);
    }
    
    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestAccessCoordinatorImpl.class);
    }



}