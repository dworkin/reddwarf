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

package com.sun.sgs.impl.service.data.store.net;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.AbstractDataStore;
import com.sun.sgs.impl.service.data.store.BindingValue;
import com.sun.sgs.impl.service.data.store.NetworkException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import com.sun.sgs.service.store.DataStore;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of {@code DataStore} by communicating over the
 * network to an implementation of {@link DataStoreServer}, and optionally runs
 * the server. <p>
 *
 * The {@link #DataStoreClient constructor} supports the following properties:
 * <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.net.max.txn.timeout
 *	</b></code><br>
 *	<i>Default:</i> {@code 600000}
 *
 * <dd style="padding-top: .5em">The maximum amount of time in milliseconds
 *	that a transaction that uses the data store will be permitted to run
 *	before it is a candidate for being aborted.  This value must be greater
 *	than {@code 0}. <p>
 *
 * <dd style="padding-top: .5em">Whether to run the server by creating an
 *	instance of {@link DataStoreServerImpl}, using the properties provided
 *	to this instance's constructor. <p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.net.server.host
 *	</b></code><br>
 *	<i>Default</i> the value of the {@code com.sun.sgs.server.host}
 *	property, if present, or {@code localhost} if this node is starting the 
 *      server.
 *
 * <dd style="padding-top: .5em">The name of the host running the {@code
 *	DataStoreServer}. <p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.net.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44530}
 *
 * <dd style="padding-top: .5em">The network port for the {@code
 *	DataStoreServer}.  This value must be no less than {@code 0} and no
 *	greater than {@code 65535}.  The value {@code 0} can only be specified
 *      if the {@code com.sun.sgs.node.type} property is not {@code appNode}, 
 *      and means that an anonymous port will be chosen for running the 
 *      server. <p>
 *
 * </dl> <p>
 *
 * This class uses the {@link Logger} named {@code
 * com.sun.sgs.impl.service.data.store.net.client} to log information
 * at the following levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Problems starting the server
 * <li> {@link Level#INFO INFO} - Starting the server
 * <li> {@link Level#CONFIG CONFIG} - Constructor properties
 * <li> {@link Level#FINE FINE} - Allocating object IDs
 * <li> {@link Level#FINEST FINEST} - Object operations
 * </ul>
 */
public final class DataStoreClient extends AbstractDataStore {

    /** The package for this class. */
    private static final String PACKAGE =
	"com.sun.sgs.impl.service.data.store.net";

    /** The property that specifies the name of the server host. */
    private static final String SERVER_HOST_PROPERTY =
	PACKAGE + ".server.host";

    /** The property that specifies the server port. */
    private static final String SERVER_PORT_PROPERTY =
	PACKAGE + ".server.port";

    /** The default for the server port. */
    private static final int DEFAULT_SERVER_PORT = 44530;

    /**
     * The number of times to retry attempting to obtain the server after a
     * failure to obtain it initially.
     */
    private static final int GET_SERVER_MAX_RETRIES = 3;

    /**
     * The number of milliseconds to wait between attempts to obtain the
     * server.
     */
    private static final long GET_SERVER_WAIT = 10000;

    /**
     * Whether to replace Java(TM) RMI with an experimental, socket-based
     * facility.
     */
    private static final boolean noRmi = Boolean.getBoolean(
	PACKAGE + ".no.rmi");

    /** The property that specifies the maximum transaction timeout. */
    private static final String MAX_TXN_TIMEOUT_PROPERTY =
	PACKAGE + ".max.txn.timeout";

    /** The default maximum transaction timeout. */
    private static final long DEFAULT_MAX_TXN_TIMEOUT = 600000;

    /** The server host name. */
    private final String serverHost;

    /** The server port. */
    private final int serverPort;

    /** The local server or null. */
    private final DataStoreServerImpl localServer;

    /** The remote server. */
    private final DataStoreServer server;

    /** The local node ID. */
    private final long nodeId;

    /** The maximum transaction timeout. */
    private final long maxTxnTimeout;

    /** Provides information about the transaction for the current thread. */
    private final ThreadLocal<TxnInfo> threadTxnInfo =
	new ThreadLocal<TxnInfo>();

    /** Object to synchronize on when accessing txnCount and shuttingDown. */
    private final Object txnCountLock = new Object();

    /** The number of currently active transactions. */
    private int txnCount = 0;

    /** Whether the client is in the process of shutting down. */
    private boolean shuttingDown = false;

    /** Stores transaction information. */
    private static class TxnInfo {

	/** The transaction. */
	final Transaction txn;

	/** The associated server transaction ID. */
	final long tid;

	/** Whether preparation of the transaction has started. */
	boolean prepared;

	/** Whether the server side has already aborted. */
	boolean serverAborted;

	/** Creates an instance. */
	TxnInfo(Transaction txn, long tid) {
	    this.txn = txn;
	    this.tid = tid;
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties and access coordinator.  See the {@link DataStoreClient class
     * documentation} for a list of supported properties.
     *
     * @param	properties the properties for configuring this instance
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     * @throws	IllegalArgumentException if the {@code
     *		com.sun.sgs.impl.service.data.store.net.server.host} property
     *		is not set, or if the value of the {@code
     *		com.sun.sgs.impl.service.data.store.net.server.port} property
     *		is not a valid integer not less than {@code 0} and not greater
     *		than {@code 65535}
     * @throws	IOException if a network problem occurs
     * @throws	NotBoundException if the server is not found in the Java RMI
     *		registry
     */
    public DataStoreClient(Properties properties,
			   ComponentRegistry systemRegistry,
			   TransactionProxy txnProxy)
	throws IOException, NotBoundException
    {
	super(systemRegistry, 
	      new LoggerWrapper(Logger.getLogger(PACKAGE + ".client")),
	      new LoggerWrapper(Logger.getLogger(PACKAGE + ".client.abort")));
	logger.log(Level.CONFIG, "Creating DataStoreClient properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        NodeType nodeType = 
                wrappedProps.getEnumProperty(StandardProperties.NODE_TYPE, 
                                             NodeType.class, 
                                             NodeType.singleNode);
        boolean serverStart = nodeType != NodeType.appNode;
        if (serverStart) {
            // we default to localHost;  this is useful for starting
            // single node systems
            String localHost = InetAddress.getLocalHost().getHostName();
            serverHost = wrappedProps.getProperty(
                SERVER_HOST_PROPERTY,
                wrappedProps.getProperty(
                    StandardProperties.SERVER_HOST, localHost));
        } else {
            // a server host most be specified
            serverHost = wrappedProps.getProperty(
                SERVER_HOST_PROPERTY,
                wrappedProps.getProperty(
                    StandardProperties.SERVER_HOST));
            if (serverHost == null) {
                throw new IllegalArgumentException(
                                           "A server host must be specified");
            }
        }
	int specifiedServerPort = wrappedProps.getIntProperty(
	    SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, serverStart ? 0 : 1,
	    65535);
	maxTxnTimeout = wrappedProps.getLongProperty(
	    MAX_TXN_TIMEOUT_PROPERTY, DEFAULT_MAX_TXN_TIMEOUT, 1,
	    Long.MAX_VALUE);
	if (serverStart) {
	    try {
		localServer = new DataStoreServerImpl(
		    properties, systemRegistry, txnProxy);
		serverPort = localServer.getPort();
		logger.log(Level.INFO, "Started server: {0}", localServer);
	    } catch (IOException t) {
		logger.logThrow(Level.SEVERE, t, "Problem starting server");
		throw t;
	    } catch (RuntimeException t) {
		logger.logThrow(Level.SEVERE, t, "Problem starting server");
		throw t;
	    }
	} else {
	    localServer = null;
	    serverPort = specifiedServerPort;
	}
	server = getServer();
	nodeId = server.newNodeId();
    }

    /* -- Implement AbstractDataStore's DataStore methods -- */

    /** {@inheritDoc} */
    protected long getLocalNodeIdInternal() {
	return nodeId;
    }

    /** {@inheritDoc} */
    protected long createObjectInternal(Transaction txn) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.createObject(txnInfo.tid);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void markForUpdateInternal(Transaction txn, long oid) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    server.markForUpdate(txnInfo.tid, oid);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate)
    {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.getObject(txnInfo.tid, oid, forUpdate);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void setObjectInternal(Transaction txn, long oid, byte[] data) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    server.setObject(txnInfo.tid, oid, data);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void setObjectsInternal(
	Transaction txn, long[] oids, byte[][] dataArray)
    {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    server.setObjects(txnInfo.tid, oids, dataArray);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void removeObjectInternal(Transaction txn, long oid) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    server.removeObject(txnInfo.tid, oid);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected BindingValue getBindingInternal(Transaction txn, String name) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.getBinding(txnInfo.tid, name);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected BindingValue setBindingInternal(
	Transaction txn, String name, long oid)
    {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.setBinding(txnInfo.tid, name, oid);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected BindingValue removeBindingInternal(
	Transaction txn, String name)
    {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.removeBinding(txnInfo.tid, name);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected String nextBoundNameInternal(Transaction txn, String name) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.nextBoundName(txnInfo.tid, name);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void shutdownInternal() {
	synchronized (txnCountLock) {
	    shuttingDown = true;
	    while (txnCount > 0) {
		try {
		    logger.log(Level.FINEST,
			       "shutdown waiting for {0} transactions",
			       txnCount);
		    txnCountLock.wait();
		} catch (InterruptedException e) {
		    // loop until shutdown is complete
		    logger.log(Level.FINEST, "Interrupt ignored during" +
			       "shutdown");
		}
	    }
	    if (txnCount < 0) {
		return; // return silently
	    }
	    
	    txnCount = -1;
	    if (localServer != null) {
		localServer.shutdown();
	    }
	}
    }

    /** {@inheritDoc} */
    protected int getClassIdInternal(Transaction txn, byte[] classInfo) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.getClassId(txnInfo.tid, classInfo);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected byte[] getClassInfoInternal(Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.getClassInfo(txnInfo.tid, classId);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected long nextObjectIdInternal(Transaction txn, long oid) {
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    return server.nextObjectId(txnInfo.tid, oid);
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /* -- Implement AbstractDataStore's TransactionParticipant methods -- */

    /** {@inheritDoc} */
    protected boolean prepareInternal(Transaction txn) {
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, true);
	    checkTimeout(txn);
	    if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    boolean result = server.prepare(txnInfo.tid);
	    txnInfo.prepared = true;
	    if (result) {
		threadTxnInfo.set(null);
		decrementTxnCount();
	    }
	    return result;
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void commitInternal(Transaction txn) {
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, true);
	    if (!txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has not been prepared");
	    }
	    server.commit(txnInfo.tid);
	    threadTxnInfo.set(null);
	    decrementTxnCount();
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void prepareAndCommitInternal(Transaction txn) {
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, true);
	    checkTimeout(txn);
	    if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    server.prepareAndCommit(txnInfo.tid);
	    threadTxnInfo.set(null);
	    decrementTxnCount();
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }

    /** {@inheritDoc} */
    protected void abortInternal(Transaction txn) {
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, false);
	    if (!txnInfo.serverAborted) {
		try {
		    server.abort(txnInfo.tid);
		} catch (TransactionNotActiveException e) {
		    logger.logThrow(Level.FINEST, e,
				    "abort txn:{0} - Transaction already " +
				    "aborted by server",
				    txn);
		}
	    }
	    threadTxnInfo.set(null);
	    decrementTxnCount();
	} catch (IOException e) {
	    throw new NetworkException("", e);
	}
    }
    
    /* -- Other AbstractDataStore methods -- */

    /**
     * {@inheritDoc} <p>
     *
     * In addition to the operations performed by the superclass, this
     * implementation includes the operation in the exception message for
     * {@link NetworkException}s, converts {@link
     * TransactionNotActiveException} to {@link TransactionTimeoutException} if
     * the transaction timeout has passed, and notes in the information for the
     * transaction if the transaction has been aborted on the server side.
     */
    @Override
    protected RuntimeException handleException(Transaction txn,
					       Level level,
					       RuntimeException e,
					       String operation)
    {
	if (e instanceof NetworkException) {
	    /* Include the operation in the message */
	    Throwable cause = e.getCause();
	    e = new NetworkException(
		operation + " failed due to a communication problem: " +
		cause.getMessage(), cause);
	} else if (e instanceof TransactionNotActiveException && txn != null) {
	    /*
	     * If the transaction is not active on the server, then it may have
	     * timed out.
	     */
	    long duration = System.currentTimeMillis() - txn.getCreationTime();
	    if (duration > txn.getTimeout()) {
		e = new TransactionTimeoutException(
		    operation + " failed: Transaction timed out after " +
		    duration + " ms",
		    e);
	    }
	}
	/*
	 * If we're throwing an exception saying that the transaction was
	 * aborted, then note that the server must already know about the
	 * abort.
	 */
	if (e instanceof TransactionAbortedException) {
	    TxnInfo txnInfo = threadTxnInfo.get();
	    if (txnInfo != null) {
		txnInfo.serverAborted = true;
	    }
	}
	return super.handleException(txn, level, e, operation);
    }

    /* -- Other public methods -- */

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "DataStoreClient[" +
	    "nodeId:" + nodeId +
	    ", serverHost:" + serverHost +
	    ", serverPort:" + serverPort + "]";
    }

    /* -- Private methods -- */

    /** Obtains the server. */
    private DataStoreServer getServer() throws IOException, NotBoundException {
	boolean done = false;
	for (int i = 0; !done; i++) {
	    if (i == GET_SERVER_MAX_RETRIES) {
		done = true;
	    }
	    try {
		if (!noRmi) {
		    Registry registry = LocateRegistry.getRegistry(
			serverHost, serverPort);
		    return (DataStoreServer) registry.lookup(
			"DataStoreServer");
		} else {
		    return new DataStoreClientRemote(serverHost, serverPort);
		}
	    } catch (IOException e) {
		if (done) {
		    throw e;
		}
	    } catch (NotBoundException e) {
		if (done) {
		    throw e;
		}
	    }
	}
	throw new AssertionError();
    }

    /**
     * Checks that the correct transaction is in progress, and join if none is
     * in progress.
     */
    private TxnInfo checkTxn(Transaction txn) throws IOException {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = threadTxnInfo.get();
	if (txnInfo == null) {
	    txnInfo = joinTransaction(txn);
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException(
		"Wrong transaction: Found " + txnInfo.txn +
		", expected " + txn);
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException("Transaction has been prepared");
	}
	checkTimeout(txn);
	return txnInfo;
    }

    /**
     * Joins the specified transaction, checking first to see if the data store
     * is currently shutting down, and returning the new TxnInfo.
     */
    private TxnInfo joinTransaction(Transaction txn) throws IOException {
	synchronized (txnCountLock) {
	    if (txnCount < 0) {
		throw new IllegalStateException("Service is shut down");
	    } else if (shuttingDown) {
		throw new IllegalStateException("Service is shutting down");
	    }
	    txnCount++;
	}
	boolean joined = false;
	long tid = -1;
	try {
	    tid = server.createTransaction(txn.getTimeout());
	    txn.join(this);
	    joined = true;
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER,
			   "Created server transaction stid:{0,number,#} " +
			   "for transaction {1}",
			   tid, txn);
	    }
	} finally {
	    if (!joined) {
		decrementTxnCount();
		if (tid != -1) {
		    try {
			server.abort(tid);
		    } catch (RuntimeException e) {
			if (logger.isLoggable(Level.FINEST)) {
			    logger.logThrow(
				Level.FINEST, e,
				"Problem aborting server transaction " +
				"stid:{0,number,#} for transaction {1}",
				tid, txn);
			}
		    }
		}
	    }
	}
	TxnInfo txnInfo = new TxnInfo(txn, tid);
	threadTxnInfo.set(txnInfo);
	return txnInfo;
    }

    /**
     * Checks that the correct transaction is in progress, throwing an
     * exception if the transaction has not been joined.  If notAborting is
     * true, then checks if the store is shutting down.
     */
    private TxnInfo checkTxnNoJoin(Transaction txn, boolean notAborting) {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = threadTxnInfo.get();
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (notAborting && getTxnCount() < 0) {
	    throw new IllegalStateException("DataStore is shutting down");
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	}
	return txnInfo;
    }

    /** Returns the current transaction count. */
    private int getTxnCount() {
	synchronized (txnCountLock) {
	    return txnCount;
	}
    }

    /** Decrements the current transaction count. */
    private void decrementTxnCount() {
	synchronized (txnCountLock) {
	    txnCount--;
	    if (txnCount <= 0) {
		txnCountLock.notifyAll();
	    }
	}
    }

    /**
     * Checks that the transaction has not timed out, including if it has run
     * for longer than the maximum timeout.
     */
    private void checkTimeout(Transaction txn) {
	long max = Math.min(txn.getTimeout(), maxTxnTimeout);
	long runningTime = System.currentTimeMillis() - txn.getCreationTime();
	if (runningTime > max) {
	    throw new TransactionTimeoutException(
		"Transaction timed out: " + runningTime + " ms");
	}
    }
}
