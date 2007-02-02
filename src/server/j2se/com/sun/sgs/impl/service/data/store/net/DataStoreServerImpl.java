package com.sun.sgs.impl.service.data.store.net;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl.TxnInfoTable;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.PropertiesWrapper;
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
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an implementation of <code>DataStoreServer</code>, using the {@link
 * DataStoreImpl} class to support the same database format which that class
 * supports. <p>
 *
 * In addition to those supported by the {@link DataStoreImpl} class, the
 * {@link #DataStoreServerImpl constructor} supports the following properties:
 * <p>
 *
 * <ul>
 *
 * <li> <i>Key:</i> <code>
 *	com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.reap.delay
 *	</code> <br>
 *      <i>Default:</i> <code>500</code> <br>
 *	The delay in milliseconds between attempts to reap timed out
 *	transactions. <p>
 *
 * <li> <i>Key:</i> <code>
 *	com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.port
 *	</code> <br>
 *      <i>Default:</i> <code>54321</code> <br>
 *	The network port for running the server. This value must
 *	be greater than or equal to <code>0</code> and no greater than
 *	<code>65535</code>.  If the value specified is <code>0</code>, then an
 *	anonymous port will be chosen.  The value chosen will be logged, and
 *	can also be accessed with the {@link #getPort getPort} method. <p>
 * </ul> <p>
 *
 * In addition to any logging performed by the <code>DataStoreImpl</code>
 * class, this class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl</code> to
 * log information at the following levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - problems starting the server from {@link
 *	#main main} 
 * <li> {@link Level#INFO INFO} - starting the server from <code>main</code>,
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

    /** The property that specifies the transaction timeout in milliseconds. */
    private static final String TXN_TIMEOUT_PROPERTY =
	"com.sun.sgs.txnTimeout";

    /** The default transaction timeout in milliseconds. */
    private static final long DEFAULT_TXN_TIMEOUT = 1000;

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
    private static final int DEFAULT_PORT = 54321;

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

    /** The object used to export the server. */
    private Exporter exporter;

    /** The underlying data store. */
    private final CustomDataStoreImpl store;

    /** Stores information about transactions. */
    TxnTable<?> txnTable;

    /** The transaction timeout in milliseconds. */
    private final long txnTimeout;

    /** The port for running the server. */
    private final int port;

    /** Used to execute the expired transaction reaper. */
    private final ScheduledExecutorService executor;

    /** Implement Transactions using a long for the transaction ID. */
    private static class Txn implements Transaction {

	/** The transaction ID. */
	private final long tid;

	/** The creation time. */
	private final long creationTime;

	/**
	 * Whether this transaction is in use.  Synchronize on the
	 * TxnTable.lock field when accessing this field.
	 */
	boolean inUse;

	/** The transaction participant or null. */
	private TransactionParticipant participant;

	/** Creates an instance with the specified ID. */
	Txn(long tid) {
	    this.tid = tid;
	    creationTime = System.currentTimeMillis();
	}

	/** Returns the associated ID as a long. */
	long getTid() {
	    return tid;
	}

	/* -- Implement Transaction -- */

	public byte[] getId() {
	    return longToBytes(tid);
	}

	public long getCreationTime() {
	    return creationTime;
	}

	public void join(TransactionParticipant participant) {
	    this.participant = participant;
	}

	public void abort() {
	    abort(null);
	}

	public void abort(Throwable cause) {
	    participant.abort(this);
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
     * An implementation of TxnInfoTable that uses a sorted map keyed on
     * transaction IDs, and that requires all transactions to be joined
     * explicitly.  Since transaction IDs are allocated in order, the
     * transaction IDs will order the transactions by creation time.
     */
    private static class TxnTable<T> implements TxnInfoTable<T> {

	/**
	 * Synchronize on this object when accessing the table or the inUse
	 * field of a TxnInfo object.
	 */
	private final Object lock = new Object();

	/**
	 * Maps transaction IDs to transactions and their associated
	 * information.
	 */
	private final SortedMap<Long, Entry<T>> table =
	    new TreeMap<Long, Entry<T>>();

	/** Stores a transaction and the associated information. */
	private static class Entry<T> {
	    final Txn txn;
	    final T info;
	    Entry(Txn txn, T info) {
		this.txn = txn;
		this.info = info;
	    }
	}

	/** Creates an instance. */
	TxnTable() { }

	/**
	 * Gets the transaction associated with the specified ID, and marks
	 * it in use.
	 */
	Txn get(long tid) {
	    synchronized (lock) {
		Entry<T> entry = table.get(tid);
		if (entry != null) {
		    entry.txn.inUse = true;
		    return entry.txn;
		}
	    }
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}

	/** Marks the transaction as not in use. */
	void notInUse(Txn txn) {
	    synchronized (lock) {
		txn.inUse = false;
	    }
	}

	/** Returns all expired transactions that are not in use. */
	Collection<Transaction> getExpired(long txnTimeout) {
	    Collection<Transaction> result = new ArrayList<Transaction>();
	    long last = System.currentTimeMillis() - txnTimeout;
	    synchronized (lock) {
		for (Entry<?> entry : table.values()) {
		    Txn txn = entry.txn;
		    if (txn.getCreationTime() > last) {
			break;
		    } else if (!txn.inUse) {
			result.add(txn);
		    }
		}
	    }
	    return result;
	}

	/* -- Implement TxnInfoTable -- */

	public T get(Transaction txn) {
	    if (txn instanceof Txn) {
		long tid = ((Txn) txn).getTid();
		Entry<T> entry;
		synchronized (lock) {
		    entry = table.get(tid);
		}
		if (entry != null) {
		    assert entry.txn.inUse;
		    return entry.info;
		}
	    }
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}

	public void remove(Transaction txn) {
	    if (txn instanceof Txn) {
		long tid = ((Txn) txn).getTid();
		synchronized (lock) {
		    table.remove(tid);
		}
	    }
	}

	public void set(
	    Transaction txn, T info, boolean explicit)
	{
	    if (!explicit) {
		throw new IllegalStateException("Implicit join not permitted");
	    }
	    if (txn instanceof Txn) {
		Txn t = (Txn) txn;
		long tid = t.getTid();
		Entry<T> newInfo = new Entry<T>(t, info);
		synchronized (lock) {
		    table.put(tid, newInfo);
		}
	    }
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

	/** Creates an instance. */
	CustomDataStoreImpl(Properties properties) {
	    super(properties);
	}

	protected <T> TxnInfoTable<T> getTxnInfoTable(Class<T> txnInfoType) {
	    TxnTable<T> table = new TxnTable<T>();
	    txnTable = table;
	    return table;
	}

	/** Creates a new transaction. */
	long createTransaction() {
	    logger.log(Level.FINER, "createTransaction");
	    long tid;
	    synchronized (tidLock) {
		if (nextTxnId > lastTxnId) {
		    logger.log(Level.FINE, "Allocate more transaction IDs");
		    nextTxnId = getNextTxnId(TXN_ALLOCATION_BLOCK_SIZE);
		    lastTxnId = nextTxnId + TXN_ALLOCATION_BLOCK_SIZE - 1;
		}
		tid = nextTxnId++;
	    }
	    joinNewTransaction(new Txn(tid));
	    logger.log(Level.FINER,
		       "createTransaction returns tid:{0,number,#}",
		       tid);
	    return tid;
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
     * @throws	IllegalArgumentException if the <code>
     *		com.sun.sgs.impl.service.data.store.directory</code> property
     *		is not specified, or if the value of the <code>
     *		com.sun.sgs.impl.service.data.store.allocationBlockSize</code>
     *		property is not a valid integer greater than zero
     * @throws	IOException if a network problem occurs
     */
    public DataStoreServerImpl(Properties properties) throws IOException {
	logger.log(Level.CONFIG, "Creating DataStoreServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	store = new CustomDataStoreImpl(properties);
	txnTimeout = wrappedProps.getLongProperty(
	    TXN_TIMEOUT_PROPERTY, DEFAULT_TXN_TIMEOUT);
	int requestedPort = wrappedProps.getIntProperty(
	    PORT_PROPERTY, DEFAULT_PORT);
	if (requestedPort < 0 || requestedPort > 65535) {
	    throw new IllegalArgumentException(
		"The " + PORT_PROPERTY + " property value must be " +
		"greater than or equal to 0 and less than 65535: " +
		requestedPort);
	}
	exporter = noRmi ? new SocketExporter() : new Exporter();
	port = exporter.export(this, requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0}", port);
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
			    Level.FINE, t,
			    "Problem reaping expired transactions");
		    }
		}
	    },
	    reapDelay, reapDelay, TimeUnit.MILLISECONDS);
    }

    /* -- Implement DataStoreServer -- */

    /** {@inheritDoc} */
    public long allocateObjects(int count) {
	return store.allocateObjects(count);
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
    public void setObjects(
	long tid, final long[] oids, final byte[][] dataArray)
    {
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
    public long createTransaction() {
	return store.createTransaction();
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
     * @return	<code>true</code> if the shut down was successful, else
     *		<code>false</code>
     * @throws	IllegalStateException if the <code>shutdown</code> method has
     *		already been called and returned <code>true</code>
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
	Collection<Transaction> expired = txnTable.getExpired(txnTimeout);
	for (Transaction txn : expired) {
	    try {
		store.abort(txn);
	    } catch (TransactionNotActiveException e) {
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
