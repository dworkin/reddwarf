/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link WatchdogService} implementation. <p>
 *
 * In addition to the properties supported by the {@link DataServiceImpl}
 * class, the {@link #WatchdogServiceImpl constructor} supports the following
 * properties: <p>
 *
 * <ul>
 * <li> <i>Key:</i> {@code com.sun.sgs.app.name} <br>
 *	<i>No default &mdash; required</i> <br>
 *	Specifies the app name. <p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.port} <br>
 *	<i>Default:</i> {@code 44533} <br>
 *	Specifies the network port for the watchdog server that this service
 *	contacts.  This value must non-zero, positive, and no greater than
 *	{@code 65535}.<p>
 * </ul> <p>

 */
public class WatchdogServiceImpl implements WatchdogService {

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServiceImpl.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The property name for the server port. */
    private static final String PORT_PROPERTY =
	WatchdogServerImpl.PORT_PROPERTY;

    /** The default value of the server port. */
    private static final int DEFAULT_PORT = WatchdogServerImpl.DEFAULT_PORT;

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock. */
    private final Object lock = new Object();
    
    /** The application name */
    private final String appName;

    /** The watchdog server impl. */
    private final WatchdogServerImpl serverImpl;

    /** The watchdog server proxy. */
    private final WatchdogServer serverProxy;
    
    /** The server port. */
    private final int port;

    /** The ping interval. */
    private long pingInterval;

    /** The data service. */
    private DataServiceImpl dataService;

    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServiceImpl class documentation} for a list
     * of supported properties.
     */
    public WatchdogServiceImpl(
	Properties properties, ComponentRegistry systemRegistry)
	throws Exception
    {
	logger.log(Level.CONFIG, "Creating WatchdogServiceImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    }
	    appName = wrappedProps.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }

	    // For now, always create the WatchdogServer...
	    serverImpl = new WatchdogServerImpl(properties);
	    port = serverImpl.getPort();
	    Registry rmiRegistry = LocateRegistry.getRegistry(port);
	    serverProxy = (WatchdogServer) rmiRegistry.lookup("WatchdogServer");
	    
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create WatchdogServiceImpl");
	    }
	    throw e;
	}
	
    }

    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {
	
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Configuring WatchdogServiceImpl");
	}
	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    
	    synchronized (WatchdogServiceImpl.class) {
		if (WatchdogServiceImpl.txnProxy == null) {
		    WatchdogServiceImpl.txnProxy = proxy;
		} else {
		    assert WatchdogServiceImpl.txnProxy == proxy;
		}
	    }
	    
	    synchronized (lock) {
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure WatchdogServiceImpl");
	    }
	    throw e;
	}
    }
    
    /** {@inheritDoc} */
    public boolean shutdown() {
	// TBI
	return true;
    }
	
    /* -- Implement WatchdogService -- */

    /** {@inheritDoc} */
    public long getLocalNodeId() {
	throw new AssertionError("not implemented");
    }

    /** {@inheritDoc} */
    public boolean isLocalNodeAlive(boolean checkTransactionally) {
	throw new AssertionError("not implemented");
    }

    /** {@inheritDoc} */
    public Iterator<Node> getNodes() {
	throw new AssertionError("not implemented");
    }

    /** {@inheritDoc} */
    public Node getNode(long nodeId) {
	throw new AssertionError("not implemented");
    }

    /** {@inheritDoc} */
    public void addNodeListener(NodeListener listener) {
	throw new AssertionError("not implemented");
    }

    /** {@inheritDoc} */
    public void addNodeListener(long nodeId, NodeListener listener) {
	throw new AssertionError("not implemented");
    }

    /* -- other methods -- */

}
