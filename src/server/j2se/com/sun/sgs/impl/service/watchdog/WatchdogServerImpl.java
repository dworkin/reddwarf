/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link WatchdogServer} implementation. <p>
 *
 * The {@link #WatchdogServerImpl constructor} supports the following
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
 *	Specifies the network port for the server.  This value must be greater 
 *	than or equal to {@code 0} and no greater than {@code 65535}.  If the
 *	value specified is {@code 0}, then an anonymous port will be chosen.
 *	The value chosen will be logged, and can also be accessed with the
 *	{@link #getPort getPort} method. <p>
 *
 * <li> <i>Key:</i> {@code
 *	com.sun.sgs.impl.service.watchdog.WatchdogServerImpl.ping.interval} <br>
 *	<i>Default:</i> {@code 1000} (one second)<br>
 *	Specifies the ping interval which is returned by the {@link #ping ping}
 *	method). The interval must be greater than or equal to  {@code 5}
 *	milliseconds.<p>
 * </ul> <p>

 */
public class WatchdogServerImpl implements WatchdogServer, Service {

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServerImpl.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The property name for the server port. */
    static final String PORT_PROPERTY = CLASSNAME + ".port";

    /** The default value of the server port. */
    static final int DEFAULT_PORT = 44533;

    /** The property name for the ping interval. */
    private static final String PING_INTERVAL_PROPERTY =
	CLASSNAME + ".ping.interval";

    /** The default value of the ping interval. */
    private static final int DEFAULT_PING_INTERVAL = 1000;

    /** The lower bound for the ping interval. */
    private static final int PING_INTERVAL_LOWER_BOUND = 5;

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock. */
    private final Object lock = new Object();
    
    /** The server port. */
    private final int port;

    /** The ping interval. */
    private final long pingInterval;

    /** The exporter for this server. */
    private final Exporter<WatchdogServer> exporter;

    /** The data service. */
    private DataService dataService;

    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;
    
    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServerImpl class documentation} for a list
     * of supported properties.
     */
    public WatchdogServerImpl(Properties properties,
			      ComponentRegistry systemRegistry)
	throws IOException, Exception
    {
	logger.log(Level.CONFIG, "Creating WatchdogServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	int requestedPort = wrappedProps.getIntProperty(
	    PORT_PROPERTY, DEFAULT_PORT);
	if (requestedPort < 0 || requestedPort > 65535) {
	    throw new IllegalArgumentException(
		"The " + PORT_PROPERTY + " property value must be " +
		"greater than or equal to 0 and less than 65535: " +
		requestedPort);
	}
	exporter = new Exporter<WatchdogServer>();
	port = exporter.export(this, "WatchdogServer", requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	pingInterval = wrappedProps.getLongProperty(
	    PING_INTERVAL_PROPERTY, DEFAULT_PING_INTERVAL);
	if (pingInterval < PING_INTERVAL_LOWER_BOUND) {
	    throw new IllegalArgumentException(
		"The " + PING_INTERVAL_PROPERTY + " property value must be " +
		"greater than or equal to " + PING_INTERVAL_LOWER_BOUND +
		": " + pingInterval);
	}
    }

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {
	
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Configuring WatchdogServerImpl");
	}
	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    
	    synchronized (WatchdogServerImpl.class) {
		if (WatchdogServerImpl.txnProxy == null) {
		    WatchdogServerImpl.txnProxy = proxy;
		} else {
		    assert WatchdogServerImpl.txnProxy == proxy;
		}
	    }
	    
	    synchronized (lock) {
		if (dataService != null) {
		    throw new IllegalStateException("already configured");
		}
		dataService = registry.getComponent(DataService.class);
	    }
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure WatchdogServerImpl");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	synchronized (lock) {
	    if (shuttingDown) {
		throw new IllegalStateException("already shutting down");
	    }
	    shuttingDown = true;
	}
	exporter.unexport();
	return true;
    }
    
    /* -- Implement WatchdogServer -- */

    /**
     * {@inheritDoc}
     */
    public long registerNode(long nodeId, String hostname) throws IOException {
	throw new AssertionError("not implemented");
    }

    /**
     * {@inheritDoc}
     */
    public boolean ping(long nodeId) throws IOException {  
	throw new AssertionError("not implemented");
   }

    /**
     * {@inheritDoc}
     */
    public boolean isAlive(long nodeId) throws IOException { 
	throw new AssertionError("not implemented");
    }

    /* -- other methods -- */

    /**
     * Returns the port being used for this server.
     *
     * @return	the port
     */
    public int getPort() {
	return port;
    }

    /**
     * Resets the server to an uninitialized state so that configure
     * can be reinvoked.  This method is invoked by the {@code
     * WatchdogServiceImpl} if its {@code configure} method aborts.
     */
    void configureAborted() {
	dataService = null;
    }
    
    /*
    private void runTransactionally(Task task) throws Exception {
	// TBD: should this check to see if there is already a
	// transaction set?
	TransactionHandle handle = txnCoordinator.createTransaction(true);
	Transaction txn = handle.getTransaction();
	txnProxy.setCurrentTransaction(txn);
	try {
	    task.run();
	    handle.commit();
	    return;
	    
	} catch (Throwable t) {
	    try {
		txn.abort(t);
	    } catch (TransactionNotActiveException e) {
		// ignore; transaction already aborted
	    }

	    if (t instanceof Exception) {
		throw (Exception) t;
	    } else if (t instanceof Error) {
		throw (Error) t;
	    }
	    
	} finally {
	    txnProxy.setCurrentTransaction(null);
	}
    }

    */
}
