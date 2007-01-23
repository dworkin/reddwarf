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
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * FIXME: Timeout transactions
 */
/**
 * Provides an implementation of <code>DataStoreServer</code>, using the {@link
 * DataStoreImpl} class to support the same databases which that class
 * supports.
 *
 * FIXME: Document properties
 */
public class DataStoreServerImpl implements DataStoreServer {

    /** The name of this class. */
    private static final String CLASSNAME =
	DataStoreServerImpl.class.getName();

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /**
     * The name of the property for specifying the port for running the
     * RMI registry.
     */
    private static final String PORT_PROPERTY = CLASSNAME + ".port";

    /**
     * The default value of the port for running the RMI registry and the
     * server.
     */
    private static final int DEFAULT_PORT = 54321;

    /** The number of transactions to allocate at a time. */
    private static final int TXN_ALLOCATION_BLOCK_SIZE = 100;

    /** Set by main to make sure that the server is reachable. */
    private static DataStoreServerImpl server;

    /** The underlying data store. */
    private final CustomDataStoreImpl store;

    /** Stores information about transactions. */
    private TxnTable<?> txnTable;

    /** The port for running the RMI registry and the server. */
    private int port;

    /** The RMI registry. */
    private Registry registry;

    /** Object to synchronize on when accessing nextTxnId and lastTxnId. */
    private final Object tidLock = new Object();

    /**
     * The next transaction ID to use for allocation Transaction IDs.  Valid if
     * not greater than lastTxnId.
     */
    private long nextTxnId = 0;

    /**
     * The last transaction ID that is free for allocating an transaction ID
     * before needing to obtain more IDs from the database.
     */
    private long lastTxnId = -1;

    /** Implement Transactions using a long for the transaction ID. */
    private static class Txn implements Transaction {
	/** The transaction ID. */
	private final long tid;

	/** The creation time. */
	private final long creationTime;

	/** Synchronize on this object when accessing the inUse field. */
	private final Object inUseLock = new Object();

	/** Whether this transaction is in use. */
	private boolean inUse;

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

	/** Marks this transaction as in use. */
	void lock() {
	    synchronized (inUseLock) {
		if (inUse) {
		    throw new IllegalStateException();
		}
		inUse = true;
	    }
	}

	/** Marks this transaction as not in use. */
	void unlock() {
	    synchronized (inUseLock) {
		if (!inUse) {
		    throw new IllegalStateException();
		}
		inUse = false;
	    }
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
	    return "Txn[tid:" + tid + "]";
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
     * transaction IDs.
     */
    private static class TxnTable<T> implements TxnInfoTable<T> {

	/**
	 * Maps transaction IDs to transactions and their associated
	 * information.
	 */
	final SortedMap<Long, Entry<T>> table = new TreeMap<Long, Entry<T>>();

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

	/** Gets the transaction associated with the specified ID. */
	Txn get(long tid) {
	    synchronized (table) {
		Entry<T> entry = table.get(tid);
		if (entry != null) {
		    return entry.txn;
		}
	    }
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}

	/* -- Implement TxnInfoTable -- */

	public T get(Transaction txn) {
	    if (txn instanceof Txn) {
		long tid = ((Txn) txn).getTid();
		synchronized (table) {
		    Entry<T> entry = table.get(tid);
		    if (entry != null) {
			return entry.info;
		    }
		}
	    }
	    throw new TransactionNotActiveException(
		"Transaction is not active");
	}

	public void remove(Transaction txn) {
	    if (txn instanceof Txn) {
		long tid = ((Txn) txn).getTid();
		synchronized (table) {
		    table.remove(tid);
		}
	    }
	}

	public synchronized void set(
	    Transaction txn, T info, boolean explicit)
	{
	    if (!explicit) {
		throw new IllegalStateException("Implicit join not permitted");
	    }
	    if (txn instanceof Txn) {
		Txn t = (Txn) txn;
		long tid = t.getTid();
		Entry<T> newInfo = new Entry<T>(t, info);
		synchronized (table) {
		    table.put(tid, newInfo);
		}
	    }
	}
    }

    /**
     * Customize DataStoreImpl to use a different transaction table, to create
     * transactions explicitly, and to allocate blocks of objects.
     */
    private class CustomDataStoreImpl extends DataStoreImpl {
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
	    long tid;
	    synchronized (tidLock) {
		if (nextTxnId > lastTxnId) {
		    nextTxnId = getNextTxnId(TXN_ALLOCATION_BLOCK_SIZE);
		    lastTxnId = nextTxnId + TXN_ALLOCATION_BLOCK_SIZE - 1;
		}
		tid = nextTxnId++;
	    }
	    joinNewTransaction(new Txn(tid));
	    return tid;
	}
	/* Make this method accessible to the containing class. */
	protected long allocateObjects(int count) {
	    return super.allocateObjects(count);
	}
    }

    /**
     * Defines a server socket factory that provides access to the server
     * socket's local port.
     */
    private class ServerSocketFactory implements RMIServerSocketFactory {
	private ServerSocket serverSocket;
	ServerSocketFactory() { }
	public ServerSocket createServerSocket(int port) throws IOException {
	    serverSocket = new ServerSocket(port);
	    return serverSocket;
	}
	int getLocalPort() {
	    return (serverSocket == null) ? -1 : serverSocket.getLocalPort();
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
     * @throws	RemoteException if a network problem occurs
     */
    public DataStoreServerImpl(Properties properties) throws RemoteException {
	logger.log(Level.CONFIG, "Creating DataStoreServerImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	store = new CustomDataStoreImpl(properties);
	int requestedPort = wrappedProps.getIntProperty(
	    PORT_PROPERTY, DEFAULT_PORT);
	ServerSocketFactory ssf = new ServerSocketFactory();
	registry = LocateRegistry.createRegistry(requestedPort, null, ssf);
	port = ssf.getLocalPort();
	registry.rebind(
	    "DataStoreServer",
	    UnicastRemoteObject.exportObject(this, requestedPort, null, ssf));
    }

    /* -- Implement DataStoreServer -- */

    /** {@inheritDoc} */
    public long allocateObjects(int count) {
	return store.allocateObjects(count);
    }

    /** {@inheritDoc} */
    public void markForUpdate(long tid, long oid) {
	store.markForUpdate(getTxn(tid), oid);
    }

    /** {@inheritDoc} */
    public byte[] getObject(long tid, long oid, boolean forUpdate) {
	return store.getObject(getTxn(tid), oid, forUpdate);
    }

    /** {@inheritDoc} */
    public void setObject(long tid, long oid, byte[] data) {
	store.setObject(getTxn(tid), oid, data);
    }

    /** {@inheritDoc} */
    public void removeObject(long tid, long oid) {
	store.removeObject(getTxn(tid), oid);
    }

    /** {@inheritDoc} */
    public long getBinding(long tid, String name) {
	return store.getBinding(getTxn(tid), name);
    }

    /** {@inheritDoc} */
    public void setBinding(long tid, String name, long oid) {
	store.setBinding(getTxn(tid), name, oid);
    }

    /** {@inheritDoc} */
    public void removeBinding(long tid, String name) {
	store.removeBinding(getTxn(tid), name);
    }

    /** {@inheritDoc} */
    public String nextBoundName(long tid, String name) {
	return store.nextBoundName(getTxn(tid), name);
    }

    /** {@inheritDoc} */
    public long createTransaction() {
	return store.createTransaction();
    }

    /** {@inheritDoc} */
    public boolean prepare(long tid) {
	return store.prepare(getTxn(tid));
    }

    /** {@inheritDoc} */
    public void commit(long tid) {
	store.commit(getTxn(tid));
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(long tid) {
	store.prepareAndCommit(getTxn(tid));
    }

    /** {@inheritDoc} */
    public void abort(long tid) {
	store.abort(getTxn(tid));
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
	if (registry == null) {
	    throw new IllegalStateException(
		"The server is already shut down");
	}
	if (!store.shutdown()) {
	    return false;
	}
	try {
	    UnicastRemoteObject.unexportObject(this, true);
	} catch (NoSuchObjectException e) {
	    logger.logThrow(Level.FINE, e, "Problem unexporting server");
	    return false;
	}
	try {
	    UnicastRemoteObject.unexportObject(registry, true);
	} catch (NoSuchObjectException e) {
	    logger.logThrow(Level.FINE, e, "Problem unexporting registry");
	    return false;
	}
	registry = null;
	return true;
    }

    /**
     * Returns the port being used for the RMI registry and the server.
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

    /* -- Private methods -- */

    /**
     * Returns the transaction for the specified ID, or null if not found, and
     * throwing TransactionNotActiveException if the transaction is not active.
     */
    private Transaction getTxn(long tid) {
	try {
	    return txnTable.get(tid);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e,
			    "Getting transaction tid:{0,number,#} failed",
			    tid);
	    throw e;
	}
    }
}
