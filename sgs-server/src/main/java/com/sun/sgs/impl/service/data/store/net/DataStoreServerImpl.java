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

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.NullAccessCoordinator;
import com.sun.sgs.impl.service.data.store.BindingValue;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import static com.sun.sgs.impl.sharedutil.Objects.checkNull;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.impl.util.NamedThreadFactory;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionListener;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.ClassInfoNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import static java.util.logging.Level.FINEST;
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
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.net.max.txn.timeout
 *	</b></code><br>
 *      <i>Default:</i> {@code 600000}
 *
 * <dd style="padding-top: .5em">The maximum amount of time in milliseconds
 *	that a transaction will be permitted to run before it is a candidate
 *	for being aborted. <p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.net.server.reap.delay
 *	</b></code><br>
 *      <i>Default:</i> {@code 500}
 *
 * <dd style="padding-top: .5em">The delay in milliseconds between attempts to
 *	reap timed out transactions. <p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.net.server.port
 *	</b></code><br>
 *      <i>Default:</i> {@code 44530}
 *
 * <dd style="padding-top: .5em">The network port for running the server. This
 *	value must be greater than or equal to {@code 0} and no greater than
 *	{@code 65535}.  If the value specified is {@code 0}, then an anonymous
 *	port will be chosen.  The value chosen will be logged, and can also be
 *	accessed with the {@link #getPort getPort} method. <p>
 *
 * </dl> <p>
 *
 * In addition to any logging performed by the {@code DataStoreImpl} class,
 * this class uses the {@link Logger} named {@code
 * com.sun.sgs.impl.service.data.store.net.server} to log
 * information at the following levels: <p>
 *
 * <ul>
 * <li> {@link Level#INFO INFO} - actual port if anonymous port was requested
 * <li> {@link Level#CONFIG CONFIG} - server properties
 * <li> {@link Level#FINE FINE} - allocation transaction IDs, problems
 *	unexporting the server, reaping expired transactions, problems
 *	the specified transaction ID
 * <li> {@link Level#FINER FINER} - create transactions
 * </ul> <p>
 */
public class DataStoreServerImpl implements DataStoreServer {

    /** The package for this class. */
    private static final String PACKAGE =
	"com.sun.sgs.impl.service.data.store.net";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PACKAGE + ".server"));

    /** The property that specifies the maximum transaction timeout. */
    private static final String MAX_TXN_TIMEOUT_PROPERTY =
	PACKAGE + ".max.txn.timeout";

    /** The default maximum transaction timeout in milliseconds. */
    private static final long DEFAULT_MAX_TXN_TIMEOUT = 600000;

    /**
     * The property that specifies the delay in milliseconds between attempts
     * to reap timed out transactions.
     */
    private static final String REAP_DELAY_PROPERTY = PACKAGE +
	".server.reap.delay";

    /** The default reap delay. */
    private static final long DEFAULT_REAP_DELAY = 500;

    /**
     * The name of the property for specifying the port for running the server.
     */
    private static final String PORT_PROPERTY = PACKAGE + ".server.port";

    /** The default value of the port for running the server. */
    private static final int DEFAULT_PORT = 44530;

    /** The number of transactions to allocate at a time. */
    private static final int TXN_ALLOCATION_BLOCK_SIZE = 100;

    /**
     * Whether to replace Java(TM) RMI with an experimental, socket-based
     * facility.
     */
    private static final boolean noRmi = Boolean.getBoolean(
	PACKAGE + ".no.rmi");

    /** The underlying data store. */
    private final CustomDataStoreImpl store;

    /** The maximum transaction timeout in milliseconds. */
    private final long maxTxnTimeout;

    /** The object used to export the server. */
    private final Exporter<DataStoreServer> exporter;

    /** The port for running the server. */
    private final int port;

    /** Used to execute the expired transaction reaper. */
    private final ScheduledExecutorService executor;

    /** Stores information about transactions. */
    TxnTable<?> txnTable;

    /** Implement Transactions using a long for the transaction ID. */
    private static class Txn implements Transaction {

	/**
	 * The state value for when the transaction is not in use, prepared, or
	 * being reaped.
	 */
	private static final int IDLE = 0;

	/**
	 * The state value for when the transaction is currently in use, and is
	 * not prepared or being reaped.
	 */
	private static final int IN_USE = 1;

	/**
	 * The state value for when the transaction is not in use, has been
	 * prepared, and is not being reaped.
	 */
	private static final int PREPARED = 2;

	/**
	 * The state value for when the transaction is currently in use and
	 * prepared, and is not being reaped.
	 */
	private static final int IN_USE_PREPARED = IN_USE | PREPARED;

	/**
	 * The state value for when the transaction is being reaped because it
	 * is expired.  Once this state is reached, it never changes.
	 * Transactions that are in use or prepared are not reaped.
	 */
	private static final int REAPING = 4;

	/** The transaction ID. */
	private final long tid;

	/** The creation time. */
	private final long creationTime;

	/** The timeout value. */
	private final long timeout;

	/** The information associated with this transaction, or null. */
	private Object txnInfo;

	/**
	 * The current state, one of IDLE, IN_USE, PREPARED, IN_USE_PREPARED,
	 * or REAPING.
	 */
	private final AtomicInteger state = new AtomicInteger(IDLE);

	/** The transaction participant or null. */
	private TransactionParticipant participant;

	/** Whether the transaction has already started aborting. */
	private boolean aborting;

	/** Whether the transaction has been committed or aborted. */
	private boolean inactive;

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
	 * successful.  The attempt succeeds if the state is REAPING or if the
	 * IN_USE bit is the opposite of the requested state, independent of
	 * the PREPARED bit.
	 */
	boolean setInUse(boolean inUse) {
	    int prepared = state.get() & PREPARED;
	    int expect = (inUse ? IDLE : IN_USE) | prepared;
	    int update = (inUse ? IN_USE : IDLE) | prepared;
	    return state.compareAndSet(expect, update) ||
		state.get() == REAPING;
	}

	/**
	 * Sets this transaction as being reaped.  Returns whether the attempt
	 * to set the state was successful.  The attempt fails if the
	 * transaction is in use or if it has been prepared.
	 */
	boolean setReaping() {
	    boolean success = state.compareAndSet(IDLE, REAPING);
	    return success || state.get() == REAPING;
	}

	/** Returns true if this transaction is being reaped. */
	boolean getReaping() {
	    return state.get() == REAPING;
	}

	/**
	 * Marks the transaction as prepared.  This method should only be
	 * called when the transaction is in use and has not already been
	 * prepared.
	 */
	void setPrepared() {
	    boolean success = state.compareAndSet(IN_USE, IN_USE_PREPARED);
	    assert success;
	}

	/**
	 * Marks the transaction as inactive.  This method should only be
	 * called when the transaction is in use.
	 */
	void setInactive() {
	    assert (state.get() & IN_USE) != 0;
	    inactive = true;
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
	    if (inactive) {
		throw new TransactionNotActiveException(
		    "The transaction is not active");
	    } else if ((state.get() & PREPARED) != 0) {
		return;
	    }
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
	    if (cause == null) {
	        throw new NullPointerException("Cause cannot be null");
	    }
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

	/**
	 * Don't bother to support transaction listeners for just the data
	 * store, which doesn't use them.
	 */
	public void registerListener(TransactionListener listener) {
	    throw new UnsupportedOperationException(
		"DataStoreServerImpl doesn't support transaction listeners");
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
	final NavigableMap<Long, Txn> table =
	    new ConcurrentSkipListMap<Long, Txn>();

	/** Creates an instance. */
	TxnTable() { }

	/**
	 * Gets the transaction associated with the specified ID, and marks it
	 * in use.  Checks if the transaction has timed out if checkTimeout is
	 * true, and considers transactions being reaped as not active.
	 */
	Txn get(long tid, boolean checkTimeout) {
	    Txn txn = table.get(tid);
	    if (txn != null) {
		if (checkTimeout) {
		    txn.checkTimeout();
		}
		if (!txn.setInUse(true)) {
		    throw new IllegalStateException(
			"Multiple simultaneous accesses to transaction: " +
			txn);
		}
		if (!txn.getReaping()) {
		    return txn;
		}
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
		if (txn != null &&
		    txn.getCreationTime() + txn.getTimeout() < now &&
		    txn.setReaping())
		{
		    result.add(txn);
		}
		/* Search for the next entry */
		nextId = table.higherKey(nextId);
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
	CustomDataStoreImpl(Properties properties,
			    ComponentRegistry systemRegistry,
			    TransactionProxy txnProxy)
	{
	    super(properties,
		  /* Use a NullAccessCoordinator */
		  new ComponentRegistryWithOverride(
		      new NullAccessCoordinator(), systemRegistry),
		  txnProxy);
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

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation does logging and checks that {@code name} is not
	 * {@code null}, otherwise delegating to the superclass.
	 */
	protected BindingValue getBindingInternal(
	    Transaction txn, String name)
	{
	    if (logger.isLoggable(FINEST)) {
		logger.log(FINEST, "getBinding txn:{0}, name:{1}", txn, name);
	    }
	    try {
		checkNull("name", name);
		BindingValue result = super.getBindingInternal(txn, name);
		if (logger.isLoggable(FINEST)) {
		    logger.log(FINEST,
			       "getBinding txn:{0}, name:{1} returns " +
			       "oid:{2,number,#}",
			       txn, name, result.getObjectId());
		}
		return result;
	    } catch (RuntimeException e) {
		throw handleException(
		    txn, FINEST, e,
		    "getBinding txn:" + txn + ", name:" + name);
	    }
	}

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation does logging and checks that {@code name} is not
	 * {@code null}, otherwise delegating to the superclass.
	 */
	protected BindingValue setBindingInternal(
	    Transaction txn, String name, long oid)
	{
	    if (logger.isLoggable(FINEST)) {
		logger.log(
		    FINEST, "setBinding txn:{0}, name:{1}, oid:{2,number,#}",
		    txn, name, oid);
	    }
	    try {
		checkNull("name", name);
		BindingValue result = super.setBindingInternal(txn, name, oid);
		if (logger.isLoggable(FINEST)) {
		    logger.log(FINEST,
			       "setBinding txn:{0}, name:{1}," +
			       " oid:{2,number,#} returns",
			       txn, name, oid);
		}
		return result;
	    } catch (RuntimeException e) {
		throw handleException(txn, FINEST, e,
				      "setBinding txn:" + txn + ", name:" +
				      name + ", oid:" + oid);
	    }
	}

	/**
	 * {@inheritDoc} <p>
	 *
	 * This implementation does logging and checks that {@code name} is not
	 * {@code null}, otherwise delegating to the superclass.
	 */
	protected BindingValue removeBindingInternal(
	    Transaction txn, String name)
	{
	    if (logger.isLoggable(FINEST)) {
		logger.log(
		    FINEST, "removeBinding txn:{0}, name:{1}", txn, name);
	    }
	    try {
		checkNull("name", name);
		BindingValue result = super.removeBindingInternal(txn, name);
		if (logger.isLoggable(FINEST)) {
		    logger.log(FINEST,
			       "removeBinding txn:{0}, name:{1} returns {2}",
			       txn, name, result);
		}
		return result;
	    } catch (RuntimeException e) {
		throw handleException(txn, FINEST, e,
				      "removeBinding txn:" + txn +
				      ", name:" + name);
	    }
	}

	/** Provide access to newNodeId. */
	long localNewNodeId() {
	    return super.newNodeId();
	}
    }

    /**
     * Defines a {@code ComponentRegistry} with a component that overrides
     * matches from a supplied registry.
     */
    private static class ComponentRegistryWithOverride
	implements ComponentRegistry
    {
	/** The overriding component. */
	private final Object overrideComponent;

	/** The base registry. */
	private final ComponentRegistry registry;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	overrideComponent the overriding component
	 * @param	registry the base registry
	 */
	ComponentRegistryWithOverride(Object overrideComponent,
				      ComponentRegistry registry)
	{
	    this.overrideComponent = overrideComponent;
	    this.registry = registry;
	}

	/** {@inheritDoc} */
	public <T> T getComponent(Class<T> type) {
	    if (type.isAssignableFrom(overrideComponent.getClass())) {
		return type.cast(overrideComponent);
	    } else {
		return registry.getComponent(type);
	    }
	}

	/** {@inheritDoc} */
	public Iterator<Object> iterator() {
	    return new Iterator<Object>() {
		private boolean first = true;
		private final Iterator iterator = registry.iterator();
		public boolean hasNext() {
		    return first || iterator.hasNext();
		}
		public Object next() {
		    if (!first) {
			return iterator.next();
		    }
		    first = false;
		    return overrideComponent;
		}
		public void remove() {
		    throw new UnsupportedOperationException();
		}
	    };
	}
    }

    /**
     * An alternative exporter that uses an experimental socket-based facility
     * instead of Java RMI.
     */
    private static class SocketExporter extends Exporter<DataStoreServer> {
	private DataStoreServerRemote remote;
	SocketExporter(Class<DataStoreServer> type) {
	    super(type);
	}
	public int export(DataStoreServer server, String name, int port)
	    throws IOException
	{
	    remote = new DataStoreServerRemote(server, port);
	    return remote.getLocalPort();
	}
	public void unexport() {
	    if (remote == null) {
		return;
	    }
	    try {
		remote.shutdown();
		remote = null;
	    } catch (IOException e) {
		logger.logThrow(
		    Level.FINE, e, "Problem shutting down server");
		return;
	    }
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.  See the {@link DataStoreServerImpl class documentation} for
     * a list of supported properties.
     *
     * @param	properties the properties for configuring this instance
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     * @throws	DataStoreException if there is a problem with the database
     * @throws	IllegalArgumentException if the value of the {@code
     *	      com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl.port}
     *	      property is less than {@code 0} or greater than {@code 65535},
     *	      or if thrown by the {@link
     *	      DataStoreImpl#DataStoreImpl DataStoreImpl constructor}
     * @throws	IOException if a network problem occurs
     */
    public DataStoreServerImpl(Properties properties,
			       ComponentRegistry systemRegistry,
			       TransactionProxy txnProxy)
	throws IOException
    {
        logger.log(Level.CONFIG, "Creating DataStoreServerImpl");
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	store = new CustomDataStoreImpl(properties, systemRegistry, txnProxy);
	maxTxnTimeout = wrappedProps.getLongProperty(
	    MAX_TXN_TIMEOUT_PROPERTY, DEFAULT_MAX_TXN_TIMEOUT,
	    1, Long.MAX_VALUE);
	int requestedPort = wrappedProps.getIntProperty(
	    PORT_PROPERTY, DEFAULT_PORT, 0, 65535);
	exporter = noRmi ?
	    new SocketExporter(DataStoreServer.class) :
	    new Exporter<DataStoreServer>(DataStoreServer.class);
	port = exporter.export(this, "DataStoreServer", requestedPort);
	if (requestedPort == 0) {
	    logger.log(Level.INFO, "Server is using port {0,number,#}", port);
	}
	long reapDelay = wrappedProps.getLongProperty(
	    REAP_DELAY_PROPERTY, DEFAULT_REAP_DELAY);
	executor = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("DataStoreServer-TransactionReaper"));
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

        logger.log(Level.CONFIG,
                   "Created DataStoreServerImpl with properties:" +
                   "\n  " + MAX_TXN_TIMEOUT_PROPERTY + "=" +
                   maxTxnTimeout +
                   "\n  " + PORT_PROPERTY + "=" + requestedPort +
                   "\n  " + REAP_DELAY_PROPERTY + "=" + reapDelay);
        
    }

    /* -- Implement DataStoreServer -- */

    /** {@inheritDoc} */
    public long newNodeId() {
	return store.localNewNodeId();
    }

    /** {@inheritDoc} */
    public long createObject(long tid) {
	Txn txn = getTxn(tid);
	try {
	    return store.createObject(txn);
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
    public BindingValue getBinding(long tid, String name) {
	Txn txn = getTxn(tid);
	try {
	    return store.getBindingInternal(txn, name);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public BindingValue setBinding(long tid, String name, long oid) {
	Txn txn = getTxn(tid);
	try {
	    return store.setBindingInternal(txn, name, oid);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public BindingValue removeBinding(long tid, String name) {
	Txn txn = getTxn(tid);
	try {
	    return store.removeBindingInternal(txn, name);
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
    public long nextObjectId(long tid, long oid) {
	Txn txn = getTxn(tid);
	try {
	    return store.nextObjectId(txn, oid);
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public long createTransaction(long timeout) {
	if (timeout <= 0) {
	    throw new IllegalArgumentException(
		"Timeout must be greater than zero: " + timeout);
	}
	return store.createTransaction(Math.min(timeout, maxTxnTimeout));
    }

    /** {@inheritDoc} */
    public boolean prepare(long tid) {
	Txn txn = getTxn(tid);
	try {
	    boolean result = store.prepare(txn);
	    txn.setPrepared();
	    return result;
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void commit(long tid) {
	Txn txn = getTxn(tid, false);
	try {
	    store.commit(txn);
	    txn.setInactive();
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(long tid) {
	Txn txn = getTxn(tid);
	try {
	    store.prepareAndCommit(txn);
	    txn.setInactive();
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /** {@inheritDoc} */
    public void abort(long tid) {
	Txn txn = getTxn(tid, false);
	try {
	    store.abort(txn);
	    txn.setInactive();
	} finally {
	    txnTable.notInUse(txn);
	}
    }

    /* -- Other public methods -- */

    /**
     * Shuts down this server. Calls to this method will block until the
     * shutdown is complete.
     *
     */
    public synchronized void shutdown() {
        store.shutdown();
	executor.shutdownNow();
	exporter.unexport();
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
     * TransactionNotActiveException if the transaction is not active, and
     * checking whether the transaction has timed out.
     */
    private Txn getTxn(long tid) {
	return getTxn(tid, true);
    }

    /**
     * Returns the transaction for the specified ID, throwing
     * IllegalArgumentException if the ID is negative, throwing
     * TransactionNotActiveException if the transaction is not active, and
     * checking, if requested, whether the transaction has timed out.  Treats
     * transactions that are being reaped as being not active.
     */
    private Txn getTxn(long tid, boolean checkTimeout) {
	if (tid < 0) {
	    throw new IllegalArgumentException(
		"The transaction ID must not be negative: " + tid);
	}
	try {
	    return txnTable.get(tid, checkTimeout);
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINE, e,
			    "Getting transaction stid:{0,number,#} failed",
			    tid);
	    throw e;
	}
    }
}
