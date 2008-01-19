/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.util;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import java.util.Properties;
import java.util.logging.Level;

/**
 * An abstract implementation of a service.  It manages state
 * transitions (i.e., initialized, ready, shutting down, shutdown), in
 * progress call tracking for services with embedded remote servers,
 * and shutdown support.
 *
 * <p>The {@link getName getName} method invokes the instance's {@code
 * toString} method, so a concrete subclass of {@code AbstractService}
 * should provide an implementation of the {@code toString} method.
 */
public abstract class AbstractService implements Service {

    /** Service state. */
    protected static enum State {
        /** The service is initialized. */
	INITIALIZED,
        /** The service is ready. */
        READY,
        /** The service is shutting down. */
        SHUTTING_DOWN,
        /** The service is shut down. */
        SHUTDOWN
    }

    /** The transaction proxy, or null if configure has not been called. */    
    protected static volatile TransactionProxy txnProxy = null;

    /** The application name. */
    protected final String appName;

    /** The logger for the subclass. */
    private final LoggerWrapper logger;

    /** The data service. */
    protected final DataService dataService;
    
    /** The task scheduler. */
    protected final TaskScheduler taskScheduler;

    /** The task owner. */
    protected volatile TaskOwner taskOwner;

    /** The lock for {@code state} and {@code callsInProgress} fields. */
    private final Object lock = new Object();
    
    /** The server state. */
    private State state;
    
    /** The count of calls in progress. */
    private int callsInProgress = 0;

    /** Thread for shutting down the server. */
    private volatile Thread shutdownThread;

    /**
     * Constructs an instance with the specified {@code properties},
     * {@code systemRegistry}, {@code txnProxy} and {@code logger}.
     * It initializes the {@code appName} field to the value of the
     * {@code com.sun.sgs.app.name} property and sets this service's
     * state to {@code INITIALIZED}.
     *
     * @param	properties service properties
     * @param	systemRegistry system registry
     * @param	txnProxy transaction proxy
     * @param	logger the service's logger
     *
     * @throws	IllegalArgumentException if the {@code com.sun.sgs.app.name}
     *		property is not defined in {@code properties}
     */
    protected AbstractService(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy,
			      LoggerWrapper logger)
    {
	if (properties == null) {
	    throw new NullPointerException("null properties");
	} else if (systemRegistry == null) {
	    throw new NullPointerException("null systemRegistry");
	} else if (txnProxy == null) {
	    throw new NullPointerException("null txnProxy");
	} else if (logger == null) {
	    throw new NullPointerException("null logger");
	}
	synchronized (AbstractService.class) {
	    if (AbstractService.txnProxy == null) {
		AbstractService.txnProxy = txnProxy;
	    } else {
		assert AbstractService.txnProxy == txnProxy;
	    }
	}
	appName = properties.getProperty(StandardProperties.APP_NAME);
	if (appName == null) {
	    throw new IllegalArgumentException(
		"The " + StandardProperties.APP_NAME +
		" property must be specified");
	}	
	
	this.logger = logger;
	this.taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	this.dataService = txnProxy.getService(DataService.class);
	this.taskOwner = txnProxy.getCurrentOwner();
	setState(State.INITIALIZED);
    }

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>If this service is in the {@code INITIALIZED} state, this
     * method sets the state to {@code READY} and invokes the {@link
     * #doReady doReady} method.  If this service is already in the
     * {@code READY} state, this method performs no actions.  If this
     * service is shutting down, or is already shut down, this method
     * throws {@code IllegalStateException}.
     *
     * @throws	IllegalStateException if this service is shutting down
     *		or is already shut down
     */
    public void ready() {
	logger.log(Level.FINEST, "ready");
	synchronized (lock) {
	    switch (state) {
		
	    case INITIALIZED:
		setState(State.READY);
		break;
		
	    case READY:
		return;
		
	    case SHUTTING_DOWN:
	    case SHUTDOWN:
		throw new IllegalStateException("service shutting down");
	    }
	}
	taskOwner = txnProxy.getCurrentOwner();
	doReady();
    }

    /**
     * Performs ready operations.  This method is invoked by the
     * {@link #ready ready} method only once so that the subclass can
     * perform any operations necessary during the "ready" phase.
     */
    protected abstract void doReady();

    /**
     * {@inheritDoc}
     *
     * <p>If this service is in the {@code INITIALIZED} state, this
     * method throws {@code IllegalStateException}.  If this service
     * is in the {@code READY} state, this method sets the state to
     * {@code SHUTTING_DOWN}, waits for all calls in progress to
     * complete, then starts a thread to invoke the {@link #doShutdown
     * doShutdown} method, waits for that thread to complete, and
     * returns {@code true}.  If the service is in the {@code
     * SHUTTING_DOWN} state, this method waits for the shutdown thread
     * to complete, and returns {@code true}.  If this service is in
     * the {@code SHUTDOWN} state, then this method returns {@code
     * true}.
     *
     * <p>If the current thread is interrupted while waiting for calls
     * to complete or while waiting for the shutdown thread to finish,
     * this method returns {@code false}.
     *
     */
    public boolean shutdown() {
	logger.log(Level.FINEST, "shutdown");
	
	synchronized (lock) {
	    switch (state) {
		
	    case INITIALIZED:
	    case READY:
		logger.log(Level.FINEST, "initiating shutdown");
		setState(State.SHUTTING_DOWN);
		while (callsInProgress > 0) {
		    try {
			lock.wait();
		    } catch (InterruptedException e) {
			return false;
		    }
		}
		shutdownThread = new ShutdownThread();
		shutdownThread.start();
		break;

	    case SHUTTING_DOWN:
		break;
		
	    case SHUTDOWN:
		return true;
	    }
	}

	try {
	    shutdownThread.join();
	} catch (InterruptedException e) {
	    return false;
	}

	return true;
    }

    /**
     * Performs shutdown operations.  This method is invoked by the
     * {@link #shutdown shutdown} method only once so that the
     * subclass can perfom any operations necessary to shutdown the
     * service.
     */
    protected abstract void doShutdown();

    /**
     * Returns this service's state.
     *
     * @return this service's state
     */
    protected State getState() {
	synchronized (lock) {
	    return state;
	}
    }
    
    /**
     * Increments the number of calls in progress.  This method should
     * be invoked by remote methods to both increment in progress call
     * count and to check the state of this server.  When the call has
     * completed processing, the remote method should invoke {@link
     * #callFinished callFinished} before returning.
     *
     * @throws	IllegalStateException if this service is shutting down
     */
    protected void callStarted() {
	synchronized (lock) {
	    if (shuttingDown()) {
		throw new IllegalStateException("service is shutting down");
	    }
	    callsInProgress++;
	}
    }

    /**
     * Decrements the in progress call count, and if this server is
     * shutting down and the count reaches 0, then notifies the waiting
     * shutdown thread that it is safe to continue.  A remote method
     * should invoke this method when it has completed processing.
     */
    protected void callFinished() {
	synchronized (lock) {
	    callsInProgress--;
	    if (state == State.SHUTTING_DOWN && callsInProgress == 0) {
		lock.notifyAll();
	    }
	}
    }

    /**
     * Returns {@code true} if this service is shutting down.
     *
     * @return	{@code true} if this service is shutting down
     */
    protected boolean shuttingDown() {
	synchronized (lock) {
	    return
		state == State.SHUTTING_DOWN ||
		state == State.SHUTDOWN;
	}
    }
    
    /**
     * Returns the data service relevant to the current context.
     *
     * @return the data service relevant to the current context
     */
    public synchronized static DataService getDataService() {
	if (txnProxy == null) {
	    throw new IllegalStateException("Service not initialized");
	} else {
	    return txnProxy.getService(DataService.class);
	}
    }

    /**
     * Sets this service's state to {@code newState}.
     *
     * @param	newState a new state.
     */
    private void setState(State newState) {
	synchronized (lock) {
	    state = newState;
	}
    }

    /**
     * Thread for shutting down service/server.
     */
    private final class ShutdownThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	ShutdownThread() {
	    super(ShutdownThread.class.getName());
	    setDaemon(true);
	}

	/** {@inheritDoc} */
	public void run() {
	    try {
		doShutdown();
	    } catch (RuntimeException e) {
		logger.logThrow(
		    Level.WARNING, e, "shutting down service throws");
		// swallow exception
	    }
	    setState(AbstractService.State.SHUTDOWN);
	}
    }
}
