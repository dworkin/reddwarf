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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskQueue;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;

/**
 * An abstract implementation of a service.  It manages state
 * transitions (i.e., initialized, ready, shutting down, shutdown), in
 * progress call tracking for services with embedded remote servers,
 * and shutdown support.
 *
 * <p>The {@link #getName getName} method invokes the instance's {@code
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
    
    /** The transaction scheduler. */
    protected final TransactionScheduler transactionScheduler;

    /** The task owner. */
    protected final Identity taskOwner;

    /** The lock for {@code state} and {@code callsInProgress} fields. */
    private final Object lock = new Object();
    
    /** The server state. */
    private State state;
    
    /** The count of calls in progress. */
    private int callsInProgress = 0;

    /** Thread for shutting down the server. */
    private volatile Thread shutdownThread;

    /**
     * Constructs an instance with the specified {@code properties}, {@code
     * systemRegistry}, {@code txnProxy}, and {@code logger}.  It initializes
     * the {@code appName} field to the value of the {@code com.sun.sgs.app.name}
     * property and sets this service's state to {@code INITIALIZED}.
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
	this.transactionScheduler =
	    systemRegistry.getComponent(TransactionScheduler.class);
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
     * TODO: If shutdown is interrupted, it should be possible to
     * re-initiate shutdown.
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
     * Checks the service version.  If a version is not associated with the
     * given {@code versionKey}, then a new {@link Version} object
     * (constructed with the specified {@code majorVersion} and {@code
     * minorVersion}) is bound in the data service with the specified key.
     *
     * <p>If an old version is bound to the specified key and that old
     * version is not equal to the current version (as specified by {@code
     * majorVersion}/{@code minorVersion}), then the {@link
     * #handleServiceVersionMismatch handleServiceVersionMismatch} method is
     * invoked to convert the old version to the new version.  If the {@code
     * handleVersionMismatch} method returns normally, the old version is
     * removed and the current version is bound to the specified key.
     *
     * <p>This method must be called within a transaction.
     *
     * @param	versionKey a key for the version
     * @param	majorVersion a major version
     * @param	minorVersion a minor version
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     * @throws	IllegalStateException if {@code handleVersionMismatch} is
     *		invoked and throws a {@code RuntimeException}
     */
    protected final void checkServiceVersion(
	String versionKey, int majorVersion, int minorVersion)
    {
	if (versionKey ==  null) {
	    throw new NullPointerException("null versionKey");
	}
	Version currentVersion = new Version(majorVersion, minorVersion);
	try {
	    Version oldVersion = (Version)
		dataService.getServiceBinding(versionKey);
	    
	    if (! currentVersion.equals(oldVersion)) {
		try {
		    handleServiceVersionMismatch(oldVersion, currentVersion);
		    dataService.removeObject(oldVersion);
		    dataService.setServiceBinding(versionKey, currentVersion);
		} catch (IllegalStateException e) {
		    throw e;
		} catch (RuntimeException e) {
		    throw new IllegalStateException(
		        "exception occurred while upgrading from version: " +
		        oldVersion + ", to: " + currentVersion, e);
		}
	    }

	} catch (NameNotBoundException e) {
	    // No version exists yet; store first version in data service.
	    dataService.setServiceBinding(versionKey, currentVersion);
	}
    }
    
    /**
     * Handles conversion from the {@code oldVersion} to the {@code
     * currentVersion}.  This method is invoked by {@link #checkServiceVersion
     * checkServiceVersion} if a version mismatch is detected and is invoked from
     * within a transaction.
     *
     * @param	oldVersion the old version
     * @param	currentVersion the current version
     * @throws	IllegalStateException if the old version cannot be upgraded
     *		to the current version
     */
    protected abstract void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion);
    
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
    
    /** Creates a {@code TaskQueue} for dependent, transactional tasks. */
    public TaskQueue createTaskQueue() {
	return transactionScheduler.createTaskQueue();
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
     * Returns {@code true} if the specified exception is retryable, and
     * {@code false} otherwise.  A retryable exception is one that
     * implements {@link ExceptionRetryStatus} and invoking its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns {@code
     * true}.
     *
     * @param	e an exception
     * @return	{@code true} if the specified exception is retryable, annd
     *		{@code false} otherwise
     */
    public static boolean isRetryableException(Exception e) {
	return (e instanceof ExceptionRetryStatus) &&
	    ((ExceptionRetryStatus) e).shouldRetry();
    }
    
    /**
     * An immutable class to hold the current version of the keys
     * and data persisted by a service.
     */   
    public static class Version implements ManagedObject, Serializable {
        /** Serialization version. */
        private static final long serialVersionUID = 1L;
        
        private final int majorVersion;
        private final int minorVersion;

	/**
	 * Constructs an instance with the specified {@code major} and
	 * {@code minor} version numbers.
	 *
	 * @param major a major version number
	 * @param minor a minor version number
	 */
        public Version(int major, int minor) {
            majorVersion = major;
            minorVersion = minor;
        }
        
        /**
         * Returns the major version number.
         * @return the major version number
         */
        public int getMajorVersion() {
            return majorVersion;
        }
        
        /**
         * Returns the minor version number.
         * @return the minor version number
         */
        public int getMinorVersion() {
            return minorVersion;
        }
        
        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "Version[major:" + majorVersion + 
                    ", minor:" + minorVersion + "]";
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj.getClass() == this.getClass()) {
                Version other = (Version) obj;
                return majorVersion == other.majorVersion && 
                       minorVersion == other.minorVersion;

            }
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 17;
            result = 37*result + majorVersion;
            result = 37*result + minorVersion;
            return result;              
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
