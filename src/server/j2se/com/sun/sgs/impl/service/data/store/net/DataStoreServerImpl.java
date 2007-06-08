/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.net;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.ClassInfoNotFoundException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of {@code DataStoreServer}, using the {@link
 * DataStoreImpl} class to support the same database format which that class
 * supports. <p>
 *
 * In addition to those supported by the {@link DataStoreImpl} class, the
 * {@link #DataStoreServerImpl constructor} supports the following properties:
 * <p>
 *
 * <ul>
 *
 * <li> <i>Key:</i> {@code 
 *	com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.max.txn.timeout}
 *	<br>
 *      <i>Default:</i> {@code 500} <br>
 *	The maximum amount of time in milliseconds that a transaction will be
 *	permitted to run before it is a candidate for being aborted. <p>
 *
 * <li> <i>Key:</i> {@code 
 *	com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.reap.delay}
 *	<br>
 *      <i>Default:</i> {@code 500} <br>
 *	The delay in milliseconds between attempts to reap timed out
 *	transactions. <p>
 *
 * <li> <i>Key:</i> {@code 
 *	com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.port}
 *	<br>
 *      <i>Default:</i> {@code 44530} <br>
 *	The network port for running the server. This value must be greater
 *	than or equal to {@code 0} and no greater than {@code 65535}.  If the
 *	value specified is {@code 0}, then an anonymous port will be chosen.
 *	The value chosen will be logged, and can also be accessed with the
 *	{@link #getPort getPort} method. <p>
 * </ul> <p>
 *
 * In addition to any logging performed by the {@code DataStoreImpl} class,
 * this class uses the {@link Logger} named {@code
 * com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl} to log
 * information at the following levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - problems starting the server from {@link
 *	#main main} 
 * <li> {@link Level#INFO INFO} - starting the server from {@code main},
 *	actual port if anonymous port was requested
 * <li> {@link Level#CONFIG CONFIG} - server properties
 * <li> {@link Level#FINE FINE} - allocation transaction IDs, problems
 *	unexporting the server, reaping expired transactions, problems
 *	the specified transaction ID
 * <li> {@link Level#FINER FINER} - create transactions
 * </ul> <p>
 */
public class DataStoreServerImpl implements DataStoreServer {

    /** The name of this class. */
    private static final String CLASSNAME =
	DataStoreServerImpl.class.getName();

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The property that specifies the maximum transaction timeout. */
    private static final String MAX_TXN_TIMEOUT_PROPERTY =
	CLASSNAME + ".max.txn.timeout";

    /** The default maximum transaction timeout in milliseconds. */
    private static final long DEFAULT_MAX_TXN_TIMEOUT = 600000;

    /**
     * The property that specifies the delay in milliseconds between attempts
     * to reap timed out transactions.
     */
    private static final String REAP_DELAY_PROPERTY = CLASSNAME +
	".reap.delay";

    /** The default reap delay. */
    private static final long DEFAULT_REAP_DELAY = 500;

    /**
     * The name of the property for specifying the port for running the server.
     */
    private static final String PORT_PROPERTY = CLASSNAME + ".port";

    /** The default value of the port for running the server. */
    private static final int DEFAULT_PORT = 44530;

    /** The number of transactions to allocate at a time. */
    private static final int TXN_ALLOCATION_BLOCK_SIZE = 100;

    /**
     * The name of the undocumented property that controls whether to replace
     * Java RMI with an experimental, socket-based facility.
     */
    private static final boolean noRmi = Boolean.getBoolean(
	CLASSNAME + ".no.rmi");

    /** Set by main to make sure that the server is reachable. */
    private static DataStoreServerImpl server;

    /** The underlying data store. */
    private final CustomDataStoreImpl store;

    /** The maximum transaction timeout in milliseconds. */
    private final long maxTxnTimeout;

    /** The object used to export the server. */
    private final Exporter exporter;

    /** The port for running the server. */
    private final int port;

    /** Used to execute the expired transaction reaper. */
    private final ScheduledExecutorService executor;

    /** Stores information about transactions. */
    TxnTable<?> txnTable;

    /** Implement Transactions using a long for the transaction ID. */
    private static class Txn implements Transaction {

	/** The state value for when the transaction is not in use. */
	private static final int IDLE = 1;

	/** The state value for when the transaction is currently in use. */
	private static final int IN_USE = 2;

	/**
	 * The state value for when the transaction is being reaped because it
	 * is expired.  Once this state is reached, it never changes.
	 */
	private static final int REAPING = 3;

	/** The transaction ID. */
	private final long tid;

	/** The creation time. */
	private final long creationTime;

	/** The timeout value. */
	private final long timeout;

	/** The information associated with this transaction, or null. */
	private Object txnInfo;

	/** The current state, one of IDLE, IN_USE, or REAPING. */
	private final AtomicInteger state = new AtomicInteger(IDLE);

	/** The transaction participant or null. */
	private TransactionParticipant participant;

	/** Whether the transaction has already started aborting. */
	private boolean aborting;

	/**
	 * The exception that caused the transaction to be aborted, or null if
	 * no cause was provided or if no abort occurred.
	 */
	private Throwable abortCause = null;

	/** Creates an instance with the specified ID and timeout. */
	Txn(long tid, long timeout) {
	    this.tid = tid;
	    this.timeout = timeout;
	    creationTime = System.currentTimeMillis();
	}

	/** Returns the associated ID as a long. */
	long getTid() {
	    return tid;
	}

	/**
	 * Returns the information associated with this transaction, or
	 * null.
	 */
	Object getTxnInfo() {
	    return txnInfo;
	}

	/** Sets the information associated with this transaction. */
	void setTxnInfo(Object txnInfo) {
	    this.txnInfo = txnInfo;
	}

	/**
	 * Sets whether this transaction is in use, doing nothing if the state
	 * is REAPING.  Returns whether the attempt to set the state was
	 * successful.  The attempt succeeds if the state is REAPING or if it
	 * is the opposite of the requested state.
	 */
	boolean setInUse(boolean inUse) {
	    int expect = inUse ? IDLE : IN_USE;
	    int update = inUse ? IN_USE : IDLE;
	    return state.compareAndSet(expect, update) ||
		state.get() == REAPING;
	}

	/**
	 * Sets this transaction as being reaped.  Returns whether the attempt
	 * to set the state was successful.  The attempt fails if the state was
	 * IN_USE.
	 */
	boolean setReaping() {
	    boolean success = state.compareAndSet(IDLE, REAPING);
	    return success || state.get() == REAPING;
	}

	/* -- Implement Transaction -- */

	public byte[] getId() {
	    return longToBytes(tid);
	}

	public long getCreationTime() {
	    return creationTime;
	}

	public long getTimeout() {
	    return timeout;
	}

	public void checkTimeout() {
	    long runningTime = System.currentTimeMillis() - getCreationTime();
	    if (runningTime > getTimeout()) {
		throw new TransactionTimeoutException(
		    "Transaction timed out: " + runningTime + " ms");
	    }
	}

	public void join(TransactionParticipant participant) {
	    this.participant = participant;
	}

	public void abort(Throwable cause) {
	    if (!aborting) {
		aborting = true;
		abortCause = cause;
		participant.abort(this);
	    }
	}

	public boolean isAborted() {
	    return aborting;
	}

	public Throwable getAbortCause() {
	    return abortCause;
	}

	/* -- Other methods -- */

	public String toString() {
	    return "Txn[stid:" + tid + "]";
	}

	public boolean equals(Object object) {
	    return (object instanceof Txn) && tid == ((Txn) object).tid;
	}

	public int hashCode() {
	    return (int) (tid >>> 32) ^ (int) tid;
	}

	/** Returns a byte array that represents the specified long. */
	private byte[] longToBytes(long l) {
	    return new byte[] {
		(byte) (l >>> 56), (byte) (l >>> 48), (byte) (l >>> 40),
		(byte) (l >>> 32), (byte) (l >>> 24), (byte) (l >>> 16),
		(byte) (l >>> 8), (byte) l };
	}
    }

    /**
     * A table for storing transactions that uses a sorted map keyed on
     * transaction IDs.  Since transaction IDs are allocated in order, the
     * transaction IDs will order the transactions by creation time.
     */
    private static class TxnTable<T> {

	/**
	 * Maps transaction IDs to transactions and their associated
	 * information.
	 */
	final SortedMap<Long, Txn> table =
	    Collections.synchronizedSortedMap(new TreeMap<Long, Txn>());

	/** Creates an instance. */
	TxnTable() { }

	/**
	 * Gets the transaction associated with the specified ID, and marks it
	 * in use.
	 */
	Txn get(long tid) {
	    Txn txn = table.get(tid);
	    if (txn != null) {
		if (!txn.setInUse(true)) {
		    throw new IllegalStateException(
			"Multiple simultaneous accesses to transaction: " +
			txn);
		}
		return txn;
	    }
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}

	/** Marks the transaction as not in use. */
	void notInUse(Txn txn) {
	    boolean ok = txn.setInUse(false);
	    /*
	     * Only the single transaction that placed the transaction in-use
	     * should be setting it not in-use, unless it is marked REAPING,
	     * which causes the call to always return true.
	     * -tjb@sun.com (02/14/2007)
	     */
	    assert ok : "Clearing transaction in-use flag failed";
	}
	
	/**
	 * Returns all expired transactions that are not in use, marking their
	 * states as REAPING.
	 */
	Collection<Transaction> getExpired() {
	    long now = System.currentTimeMillis();
	    Collection<Transaction> result = new ArrayList<Transaction>();
	    Long nextId;
	    /* Get the first key */
	    try {
		nextId = table.firstKey();
	    } catch (NoSuchElementException e) {
		nextId = null;
	    }
	    /* Loop while there is another potentially expired entry */
	    while (nextId != null) {
		Txn txn = table.get(nextId);
		if (txn != null
		    && txn.getCreationTime() + txn.getTimeout() < now 
		    && txn.setReaping())
		{
		    result.add(txn);
		}
		/* Search for the next entry */
		Long startingId = Long.valueOf(nextId + 1);
		nextId = null;
		synchronized (table) {
		    Iterator<Long> iter =
			table.tailMap(startingId).keySet().iterator();
		    if (iter.hasNext()) {
			nextId = iter.next();
		    }
		}
	    }
	    return result;
	}
    }

    /**
     * Customize DataStoreImpl to use a different transaction table and to
     * create transactions explicitly.
     */
    private class CustomDataStoreImpl extends DataStoreImpl {

	/** Object to synchronize on when accessing nextTxnId and lastTxnId. */
	private final Object tidLock = new Object();

	/**
	 * The next transaction ID to use for allocation Transaction IDs.
	 * Valid if not greater than lastTxnId.
	 */
	private long nextTxnId = 0;

	/**
	 * The last transaction ID that is free for allocating an transaction
	 * ID before needing to obtain more IDs from the database.
	 */
	private long lastTxnId = -1;

	/**
	 * An implementation of TxnInfoTable that uses TxnTable's map keyed on
	 * transaction IDs.
	 */
	private class CustomTxnInfoTable<T> extends TxnTable<T>
	    implements TxnInfoTable<T>
	{
	    /** Creates an instance. */
	    CustomTxnInfoTable() { }

	    /* -- Implement TxnInfoTable -- */

	    public T get(Transaction txn) {
		assert txn instanceof Txn;
		Txn t = (Txn) txn;
		/* All transactions will have information of the right type */
		@SuppressWarnings("unchecked")
		    T info = (T) t.getTxnInfo();
		if (info != null) {
		    return info;
		}
		throw new TransactionNotActiveException(
		    "Transaction is not active");
	    }

	    public T remove(Transaction txn) {
		assert txn instanceof Txn;
		Txn t = (Txn) txn;
		@SuppressWarnings("unchecked")
		    T info = (T) t.getTxnInfo();
		long tid = t.getTid();
		Txn t2 = table.remove(tid);
		if (t2 != null) {
		    if (!t2.equals(t)) {
			throw new IllegalStateException("Wrong transaction");
		    }
		    t.setTxnInfo(null);
		    return info;
		}
		throw new TransactionNotActiveException(
		    "Transaction is not active");
	    }

	    public void set(Transaction txn, T info) {
		assert txn instanceof Txn;
		Txn t = (Txn) txn;
		t.setTxnInfo(info);
		table.put(t.getTid(), t);
	    }
	}

	/** Creates an instance. */
	CustomDataStoreImpl(Properties properties) {
	    super(properties);
	}

	/**
	 * Create an alternative transaction table, and store it in the
	 * txnTable field of the containing server.
	 */
	protected <T> TxnInfoTable<T> getTxnInfoTable(Class<T> txnInfoType) {
	    CustomTxnInfoTable<T> table = new CustomTxnInfoTable<T>();
	    txnTable = table;
	    return table;
	}

	/** Creates a new transaction. */
	long createTransaction(long timeout) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER,
			   "createTransaction timeout:{0,number,#}",
			   timeout);
	    }
	    try {
		long tid;
		synchronized (tidLock) {
		    if (nextTxnId > lastTxnId) {
			logger.log(
			    Level.FINE, "Allocate more transaction IDs");
			nextTxnId = getNextTxnId(
			    TXN_ALLOCATION_BLOCK_SIZE, timeout);
			lastTxnId = nextTxnId + TXN_ALLOCATION_BLOCK_SIZE - 1;
		    }
		    tid = nextTxnId++;
		}
		joinNewTransaction(new Txn(tid, timeout));
		if (logger.isLoggable(Level.FINER)) {
		    logger.log(
			Level.FINER,
			"createTransaction timeout:{0,number,#} returns " +
			"stid:{1,number,#}",
			timeout, tid);
		}
		return tid;
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.FINER)) {
		    logger.logThrow(
			Level.FINER, e,
			"createTransaction timeout:{0,number,#} throws",
			timeout);
		}
		throw e;
	    }
	}
    }

    /**
     * Provides for making the server available on the network, and removing it
     * from the network during shutdown.
     */
    private static class Exporter {

	/** The server for handling inbound requests. */
	private DataStoreServer server;

	/** The RMI registry for advertising the server. */
	private Registry registry;

	/** Creates an instance. */
	Exporter() { }

	/**
	 * Makes the server available on the network on the specified port.  If
	 * the port is 0, chooses an anonymous port.  Returns the actual port
	 * on which the server is available.
	 */
	int export(DataStoreServer server, int port) throws IOException {
	    this.server = server;
	    assert server != null;
	    ServerSocketFactory ssf = new ServerSocketFactory();
	    registry = LocateRegistry.createRegistry(port, null, ssf);
	    registry.rebind(
		"DataStoreServer",
		UnicastRemoteObject.exportObject(server, port, null, ssf));
	    return ssf.getLocalPort();
	}

	/**
	 * Removes the server from the network, returning true if successful.
	 * Throws IllegalStateException if the server has already been removed
	 * from the network.
	 */
	boolean unexport() {
	    if (registry == null) {
		throw new IllegalStateException(
		    "The server is already shut down");
	    }
	    if (server != null) {
		try {
		    UnicastRemoteObject.unexportObject(server, true);
		    server = null;
		} catch (NoSuchObjectException e) {
		    logger.logThrow(
			Level.FINE, e, "Problem unexporting server");
		    return false;
		}
	    }
	    try {
		UnicastRemoteObject.unexportObject(registry, true);
		registry = null;
	    } catch (NoSuchObjectException e) {
		logger.logThrow(
		    Level.FINE, e, "Problem unexporting registry");
		return false;
	    }
	    return true;
	}
    }   

    /**
     * Defines a server socket factory that provides access to the server
     * socket's local port.
     */
    private static class ServerSocketFactory
	implements RMIServerSocketFactory
    {
	/** The last server socket created. */
	private ServerSocket serverSocket;

	/** Creates an instance. */
	ServerSocketFactory() { }

	/** {@inheritDoc} */
	public ServerSocket createServerSocket(int port) throws IOException {
	    serverSocket = new ServerSocket(port);
	    return serverSocket;
	}

	/** Returns the local port of the last server socket created. */
	int getLocalPort() {
	    return (serverSocket == null) ? -1 : serverSocket.getLocalPort();
	}
    }

    /**
     * An alternative exporter that uses an experimental socket-based facility
     * instead of Java RMI.
     */
    private static class SocketExporter extends Exporter {
	private DataStoreServerRemote remote;
	SocketExporter() { }
	int export(DataStoreServer server, int port) throws IOException {
	    remote = new DataStoreServerRemote(server, port);
	    return remote.serverSocket.getLocalPort();
	}
	boolean unexport() {
	    if (remote == null) {
		throw new IllegalStateException(
		    "The server is already shut down");
	    }
	    try {
		remote.shutdown();
		remote = null;
	    } catch (IOException e) {
		logger.logThrow(
		    Level.FINE, e, "Problem shutting down server");
		return false;
	    }
	    return true;
	}
    }

    /**
     * Starts the server.  The current system properties supplied to the
     * constructor.  Exits with a non-zero status value if a problem occurs.
     *
     * @param	args ignored
     */
    public static void main(String[] args) {
	try {
	    server = new DataStoreServerImpl(System.getProperties());
	    logger.log(Level.INFO, "Server started: {0}", server);
	} catch (Throwable t) {
	    logger.logThrow(Level.SEVERE, t, "Problem starting server");
	    System.exit(1);
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.  See the {@link DataStoreServerImpl class documentation} for
     * a list of supported properties.
     *
     * @param	properties the properties for configuring this instance
     * @throws	DataStoreException if there is a problem with the database
     * @throws	IllegalArgumentException if the value of the {@code
     *		com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.port}
     *		property is less than {@code 0} or greater than {@code 65535},
     *		or if thrown by the {@link
     *		DataStoreImpl#DataStoreImpl DataStoreImpl constructor}
     * @throws	IOException if a network problem occurs
     */
    public DataStoreServerImpl(Properties properties) throws IOException {
	logger.log(Level.CONFIG, "Creating DataStoreServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	store = new CustomDataStoreImpl(properties);
	maxTxnTimeout = wrappedProps.getLongProperty(
	    MAX_TXN_TIMEOUT_PROPERTY, DEFAULT_MAX_TXN_TIMEOUT,
	    (long) 1, Long.MAX_VALUE);
	int requestedPort = wrappedProps.getIntProperty(
	    PORT_PROPERTY, DEFAULT_PORT, 0, 65535);
	exporter = noRmi ? new SocketExporter() : new Exporter();
	port = exporter.export(this, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	long reapDelay = wrappedProps.getLongProperty(
	    REAP_DELAY_PROPERTY, DEFAULT_REAP_DELAY);
	executor = Executors.newSingleThreadScheduledExecutor();
	executor.scheduleAtFixedRate(
	    new Runnable() {
		public void run() {
		    try {
			reapExpiredTransactions();
		    } catch (Throwable t) {
			logger.logThrow(
			    Level.WARNING, t,
			    "Problem reaping expired transactions");
		    }
		}
	    },
	    reapDelay, reapDelay, TimeUnit.MILLISECONDS);
    }

    /* -- Implement DataStoreServer -- */

    /** {@inheritDoc} */
    public long allocateObjects(long tid, int count) {
	Txn txn = getTxn(tid);
	try {
	    return store.allocateObjects(txn, count);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void markForUpdate(long tid, long oid) {
	Txn txn = getTxn(tid);
	try {
	    store.markForUpdate(txn, oid);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public byte[] getObject(long tid, long oid, boolean forUpdate) {
	Txn txn = getTxn(tid);
	try {
	    return store.getObject(txn, oid, forUpdate);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void setObject(long tid, long oid, byte[] data) {
	Txn txn = getTxn(tid);
	try {
	    store.setObject(txn, oid, data);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void setObjects(long tid, long[] oids, byte[][] dataArray) {
	Txn txn = getTxn(tid);
	try {
	    store.setObjects(txn, oids, dataArray);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void removeObject(long tid, long oid) {
	Txn txn = getTxn(tid);
	try {
	    store.removeObject(txn, oid);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public long getBinding(long tid, String name) {
	Txn txn = getTxn(tid);
	try {
	    return store.getBinding(txn, name);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void setBinding(long tid, String name, long oid) {
	Txn txn = getTxn(tid);
	try {
	    store.setBinding(txn, name, oid);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void removeBinding(long tid, String name) {
	Txn txn = getTxn(tid);
	try {
	    store.removeBinding(txn, name);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public String nextBoundName(long tid, String name) {
	Txn txn = getTxn(tid);
	try {
	    return store.nextBoundName(txn, name);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public int getClassId(long tid, byte[] classInfo) {
	Txn txn = getTxn(tid);
	try {
	    return store.getClassId(txn, classInfo);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public byte[] getClassInfo(long tid, int classId)
	throws ClassInfoNotFoundException
    {
	Txn txn = getTxn(tid);
	try {
	    return store.getClassInfo(txn, classId);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public long createTransaction(long timeout) {
	return store.createTransaction(Math.min(timeout, maxTxnTimeout));
    }

    /** {@inheritDoc} */
    public boolean prepare(long tid) {
	Txn txn = getTxn(tid);
	try {
	    return store.prepare(txn);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void commit(long tid) {
	Txn txn = getTxn(tid);
	try {
	    store.commit(txn);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(long tid) {
	Txn txn = getTxn(tid);
	try {
	    store.prepareAndCommit(txn);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void abort(long tid) {
	Txn txn = getTxn(tid);
	try {
	    store.abort(txn);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /* -- Other public methods -- */

    /**
     * Attempts to shut down this server, returning a value that specifies
     * whether the attempt was successful.
     *
     * @return	{@code true} if the shut down was successful, else
     *		{@code false}
     * @throws	IllegalStateException if the {@code shutdown} method has
     *		already been called and returned {@code true}
     */
    public synchronized boolean shutdown() {
	if (!store.shutdown()) {
	    return false;
	}
	executor.shutdownNow();
	return exporter.unexport();
    }

    /**
     * Returns the port being used for the server.
     *
     * @return	the port
     */
    public int getPort() {
	return port;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "DataStoreServerImpl[store:" + store + ", port:" + port + "]";
    }

    /* -- Package access and private methods -- */

    /**
     * Find transactions that are expired and not in use, and tell the data
     * store to abort them.  The data store needs this nudging to notice that
     * it can perform the abort.
     */
    void reapExpiredTransactions() {
	/*
	 * Note that a transaction is only marked REAPING if it has expired and
	 * is not currently in use.  All subsequent operations will notice that
	 * it is expired, either because the transaction has been aborted, or
	 * because they will perform their own expiration check.  As a result,
	 * the only database access for that transaction will be to abort.
	 * Whether the reaper will be the one to perform the abort or another
	 * request to the server will do it doesn't matter as far as avoiding
	 * concurrent access to the transaction because the abort operation
	 * atomically removes the transaction from the transaction table.
	 * -tjb@sun.com (02/14/2007)
	 */
	Collection<Transaction> expired = txnTable.getExpired();
	for (Transaction txn : expired) {
	    try {
		store.abort(txn);
	    } catch (TransactionNotActiveException e) {
		/*
		 * The abort call should fail this way because this is an
		 * already expired exception.
		 */
	    } catch (TransactionTimeoutException e) {
		/* The transaction already timed out */
	    }
	}
	int numExpired = expired.size();
	if (numExpired > 0) {
	    logger.log(
		Level.FINE, "Reaped {0} expired transactions", numExpired);
	}
    }

    /**
     * Returns the transaction for the specified ID, throwing
     * TransactionNotActiveException if the transaction is not active.
     */
    private Txn getTxn(long tid) {
	try {
	    return txnTable.get(tid);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e,
			    "Getting transaction stid:{0,number,#} failed",
			    tid);
	    throw e;
	}
    }
}
