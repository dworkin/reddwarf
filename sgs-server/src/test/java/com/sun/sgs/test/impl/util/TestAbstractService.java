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

package com.sun.sgs.test.impl.util;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.impl.util.AbstractService;
import com.sun.sgs.impl.util.IoRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;
import junit.framework.TestCase;
import static com.sun.sgs.test.util.UtilProperties.createProperties;

/** Test the AbstractService class. */
public class TestAbstractService extends TestCase {

    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(TestAbstractService.class.getName()));


    private static final Properties serviceProps =
	createProperties(StandardProperties.APP_NAME, "TestAbstractService");
    
    private SgsTestNode serverNode = null;

    /** Creates an instance. */
    public TestAbstractService(String name) {
	super(name);
    }

    /** Prints the test case and sets the service field to a new instance. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	serverNode = new SgsTestNode("TestAbstractSevice", null,  null);
    }

    /** Shuts down the server node. */
    protected void tearDown() throws Exception {
	if (serverNode != null)
	    serverNode.shutdown(true);
    }

    public void testConstructorNullProperties() {
	try {
	    new DummyService(null, serverNode.getSystemRegistry(),
			     serverNode.getProxy(), logger);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullComponentRegistry() {
	try {
	    new DummyService(serviceProps, null,
			     serverNode.getProxy(), logger);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullTransactionProxy() {
	try {
	    new DummyService(serviceProps, serverNode.getSystemRegistry(),
			     null, logger);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullLoggerWrapper() {
	try {
	    new DummyService(serviceProps, serverNode.getSystemRegistry(),
			     serverNode.getProxy(), null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCheckServiceVersionNoVersion() throws Exception {
	DummyService service = createDummyService();
	    
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);
    }

    public void testCheckServiceVersionSameVersion() throws Exception {
	DummyService service = createDummyService();
	    
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);
    }

    public void testCheckServiceVersionMismatchedMajorVersion() throws Exception {
	DummyService service = createDummyService();
	    
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);
	service.callCheckServiceVersion(2, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(true);
    }
    
    public void testCheckServiceVersionMismatchedMinorVersion() throws Exception {
	DummyService service = createDummyService();
	    
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);
	service.callCheckServiceVersion(1, 1, null);
	service.checkHandleServiceVersionMismatchInvoked(true);
    }
    
    public void testHandleServiceVersionMismatchThrowsRuntimeException()
	throws Exception
    {
	DummyService service = createDummyService();
	    
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);
	RuntimeException rte =  new RuntimeException();
	try {
	    service.callCheckServiceVersion(1, 1, rte);
	} catch (IllegalStateException e) {
	    System.err.println(e);
	    if (e.getCause() != rte) {
		fail("expected cause to be original exception");
	    }
	} catch (Exception e) {
	    System.err.println(e);
	    fail("expected IllegalStateException");
	}

	service.checkHandleServiceVersionMismatchInvoked(true);
    }
    
    public void testHandleServiceVersionMismatchThrowsIllegalStateException()
	throws Exception
    {
	DummyService service = createDummyService();
	    
	service.callCheckServiceVersion(1, 0, null);
	service.checkHandleServiceVersionMismatchInvoked(false);

	IllegalStateException ise = new IllegalStateException();
	try {
	    service.callCheckServiceVersion(1, 1, ise);
	} catch (IllegalStateException e) {
	    System.err.println(e);
	    if (e != ise) {
		fail("expected IllegalStateException to be original one");
	    }
	} catch (Exception e) {
	    System.err.println(e);
	    fail("expected IllegalStateException");
	}

	service.checkHandleServiceVersionMismatchInvoked(true);
    }

    public void testRunIoTaskThatThrowsNoExceptions() throws Exception {
	DummyService service = createDummyService();
	IoRunnableImpl ioTask = new IoRunnableImpl(0);
	service.runIoTask(ioTask, serverNode.getNodeId());
	assertEquals(ioTask.runCount, 1);
    }

    public void testRunIoTaskThatThrowsIOException() throws Exception {
	DummyService service = createDummyService();
	IoRunnableImpl ioTask = new IoRunnableImpl(4);
	service.runIoTask(ioTask, serverNode.getNodeId());
	assertEquals(ioTask.runCount, 5);
    }
    
    public void testRunIoTaskToFailedNode() throws Exception {
	DummyService service = createDummyService();
	IoRunnableImpl ioTask = new IoRunnableImpl(1);
	service.runIoTask(ioTask, serverNode.getNodeId() + 1);
	assertEquals(ioTask.runCount, 1);
    }

    private static class IoRunnableImpl implements IoRunnable {

	int runCount = 0;
	private int exceptionCount;

	IoRunnableImpl(int exceptionCount) {
	    this.exceptionCount = exceptionCount;
	}
	
	public void run() throws IOException {
	    runCount++;
	    if (exceptionCount-- > 0) {
		throw new IOException();
	    }
	}
    }
    
    private DummyService createDummyService() {
	return new DummyService(serviceProps, serverNode.getSystemRegistry(),
				serverNode.getProxy(), logger);
    }

    private class DummyService extends AbstractService {

	private final static String versionKey = "service.version";

	private RuntimeException e = null;
	
	private boolean handleServiceVersionMismatchInvoked = false;

	public DummyService(Properties properties,
			    ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy,
			    LoggerWrapper logger)
	{
	    super(properties, systemRegistry, txnProxy, logger);
	}

	protected void doReady() {}

	protected void doShutdown() {}

	protected void handleServiceVersionMismatch(
	   Version oldVersion, Version currentVersion)
	{
	    handleServiceVersionMismatchInvoked = true;
	    if (e != null) {
		throw e;
	    }
	}

	void callCheckServiceVersion(
	    final int major, final int minor, RuntimeException e)
	    throws Exception
	{
	    this.handleServiceVersionMismatchInvoked = false;
	    this.e = e;
	    transactionScheduler.runTask(
		new AbstractKernelRunnable() {
		    public void run() {
			checkServiceVersion(versionKey, major,  minor);
		    }}, taskOwner);
	}

	void checkHandleServiceVersionMismatchInvoked(boolean expectedCall) {
	    if ( expectedCall) {
		if (! handleServiceVersionMismatchInvoked) {
		    fail("expected handleServiceVersionMismatch to be invoked");
		}
	    } else if (handleServiceVersionMismatchInvoked) {
		fail("handleServiceVersionMismatchInvoked should not be invoked");
	    }
	}
    }
}
