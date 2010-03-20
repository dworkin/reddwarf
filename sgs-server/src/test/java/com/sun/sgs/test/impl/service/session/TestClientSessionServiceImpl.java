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

package com.sun.sgs.test.impl.service.session;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.protocol.simple.SimpleSgsProtocolAcceptor;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.ClientSessionStatusListener;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.sun.sgs.test.util.UtilProperties.createProperties;

@RunWith(FilteredNameRunner.class)
public class TestClientSessionServiceImpl extends Assert {

    private static final String APP_NAME = "TestClientSessionServiceImpl";
    /** The ClientSession service properties. */
    private static final Properties serviceProps =
	createProperties(
	    StandardProperties.APP_NAME, APP_NAME,
            com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY,
	    "20000");

    /** The node that creates the servers. */
    private SgsTestNode serverNode;

    /** Version information from ClientSessionServiceImpl class. */
    private final String VERSION_KEY;
    private final int MAJOR_VERSION;
    private final int MINOR_VERSION;
    
    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The shared data service. */
    private DataService dataService;
    
    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }

    /** Constructs a test instance. */
    public TestClientSessionServiceImpl() throws Exception {
	Class cl = ClientSessionServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    @Before
    public void setUp() throws Exception {
        setUp(null, true);
    }

    /** Creates and configures the session service. */
    protected void setUp(Properties props, boolean clean) throws Exception {
	if (props == null) {
	    props = 
                SgsTestNode.getDefaultProperties(APP_NAME, null, null);
	}
        props.setProperty(StandardProperties.AUTHENTICATORS, 
                      "com.sun.sgs.test.util.SimpleTestIdentityAuthenticator");
	props.setProperty("com.sun.sgs.impl.service.watchdog.server.renew.interval",
			  "1000");
	serverNode = 
	    new SgsTestNode(APP_NAME, null, props, clean);

        txnScheduler = 
            serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = serverNode.getProxy().getCurrentOwner();

        dataService = serverNode.getDataService();
    }

    @After
    public void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        Thread.sleep(100);
        serverNode.shutdown(clean);
        serverNode = null;
    }

    // -- Test constructor --

    @Test
    public void testConstructorNullProperties() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		null, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNullComponentRegistry() throws Exception {
	try {
	    new ClientSessionServiceImpl(serviceProps, null,
					 serverNode.getProxy());
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNullTransactionProxy() throws Exception {
	try {
	    new ClientSessionServiceImpl(serviceProps,
					 serverNode.getSystemRegistry(), null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNoAppName() throws Exception {
	try {
	    new ClientSessionServiceImpl(
		new Properties(), serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorNoPort() throws Exception {
        Properties props =
            createProperties(StandardProperties.APP_NAME, APP_NAME);
        new ClientSessionServiceImpl(
            props, serverNode.getSystemRegistry(),
            serverNode.getProxy());
    }

    @Test
    public void testConstructorDisconnectDelayTooSmall() throws Exception {
	try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME,
                    SimpleSgsProtocolAcceptor.DISCONNECT_DELAY_PROPERTY, "199");
	    new ClientSessionServiceImpl(
		props, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorBadHighWater() throws Exception {
	try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME,
                    "com.sun.sgs.impl.service.session.login.high.water", "-1");
	    new ClientSessionServiceImpl(
		props, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
        try {
	    Properties props =
		createProperties(
		    StandardProperties.APP_NAME, APP_NAME,
                    "com.sun.sgs.impl.service.session.login.high.water",
                    String.valueOf((Integer.MAX_VALUE / 2) + 1));
	    new ClientSessionServiceImpl(
		props, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructedVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = (Version)
			dataService.getServiceBinding(VERSION_KEY);
		    if (version.getMajorVersion() != MAJOR_VERSION ||
			version.getMinorVersion() != MINOR_VERSION)
		    {
			fail("Expected service version (major=" +
			     MAJOR_VERSION + ", minor=" + MINOR_VERSION +
			     "), got:" + version);
		    }
		}}, taskOwner);
    }

    @Test
    public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new ClientSessionServiceImpl(
	    serviceProps, serverNode.getSystemRegistry(),
	    serverNode.getProxy());
    }

    @Test
    public void testConstructorWithMajorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new ClientSessionServiceImpl(
		serviceProps, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorWithMinorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new ClientSessionServiceImpl(
		serviceProps, serverNode.getSystemRegistry(),
		serverNode.getProxy());
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    // -- Test addSessionStatusListener --

    @Test
    public void testAddSessionStatusListenerNullArg() {
	try {
	    serverNode.getClientSessionService().
		addSessionStatusListener(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddSessionStatusListenerInTxn()
	throws Exception
    {
	try {
	    txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    serverNode.getClientSessionService().
			addSessionStatusListener(
			    new DummyStatusListener());
		}}, taskOwner);
	    fail("expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddSessionStatusListenerNoTxn() {
	serverNode.getClientSessionService().
	    addSessionStatusListener(new DummyStatusListener());
    }

    // -- Test getSessionProtocol --

    @Test
    public void testGetSessionProtocolNullArg() {
	try {
	    serverNode.getClientSessionService(). getSessionProtocol(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetSessionProtocolInTxn()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		serverNode.getClientSessionService().
		    getSessionProtocol(new BigInteger(1, new byte[] {0}));
	    }}, taskOwner);
    }

    @Test
    public void testGetSessionProtocolNoTxn() {
	assertNull(serverNode.getClientSessionService().
		   getSessionProtocol(new BigInteger(1, new byte[] {0})));
    }
    
    private static class DummyStatusListener
	implements ClientSessionStatusListener
    {
	public void disconnected(BigInteger sessionRefId,
				 boolean isRelocating) { }

	public void prepareToRelocate(BigInteger sessionRefId, long newNode,
				      SimpleCompletionHandler handler) { }
	
	public void relocated(BigInteger sessionRefId) { }
    }
}
