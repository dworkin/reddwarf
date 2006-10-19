package com.sun.sgs.impl.service.data.store;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseConfig;
import com.sleepycat.db.DatabaseEntry;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.DatabaseType;
import com.sleepycat.db.DeadlockException;
import com.sleepycat.db.Environment;
import com.sleepycat.db.EnvironmentConfig;
import com.sleepycat.db.LockDetectMode;
import com.sleepycat.db.LockMode;
import com.sleepycat.db.LockNotGrantedException;
import com.sleepycat.db.MessageHandler;
import com.sleepycat.db.OperationStatus;
import com.sleepycat.db.RunRecoveryException;
import com.sleepycat.db.StatsConfig;
import com.sleepycat.db.TransactionConfig;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.Util;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * XXX: Implement recovery, shutdown
 */
public final class DataStoreImpl implements DataStore, TransactionParticipant {

    /** The property that specifies the transaction timeout in milliseconds. */
    private static final String TXN_TIMEOUT_PROPERTY =
	"com.sun.sgs.txnTimeout";

    /** The default transaction timeout in milliseconds. */
    private static final long DEFAULT_TXN_TIMEOUT = 1000;

    /** This class name. */
    private static final String CLASSNAME = DataStoreImpl.class.getName();

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    private static final String DIRECTORY_PROPERTY =
	CLASSNAME + ".directory";

    /**
     * The property that specifies the number of object IDs to allocate at one
     * time.
     */
    private static final String ALLOCATION_BLOCK_SIZE_PROPERTY =
	CLASSNAME + ".allocationBlockSize";

    /** The default for the number of object IDs to allocate at one time. */
    private static final int DEFAULT_ALLOCATION_BLOCK_SIZE = 100;

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** An empty array returned when BDB returns null for a value. */
    private static final byte[] NO_BYTES = { };

    /** The configuration properties. */
    private final Properties properties;

    /** The directory in which to store database files. */
    private final String directory;

    /** The number of object IDs to allocate at one time. */
    private final int allocationBlockSize;

    private final Object dbLock = new Object();

    /** The Berkeley DB environment. */
    private Environment env;

    /** The Berkeley DB database that maps object IDs to object bytes. */
    private Database ids;

    /** The Berkeley DB database that maps name bindings to object IDs. */
    private Database names;

    /**
     * Maps a transaction to information about the transaction.  Callers should
     * synchronize on the map when accessing it.
     */
    private final ThreadLocal<TxnInfo> threadTxnInfo =
	new ThreadLocal<TxnInfo>();

    private final Map<Transaction, TxnInfo> txnInfoMap =
	new HashMap<Transaction, TxnInfo>();

    /**
     * Object to synchronize on when accessing nextObjectId and
     * lastObjectId.
     */
    private final Object objectIdLock = new Object();

    /**
     * The next object ID to use for allocating an object.  Valid if not
     * greater than lastObjectId.
     */
    private long nextObjectId;

    /**
     * The last object ID that is free for allocating an object before needing
     * to obtain more numbers from the database.
     */
    private long lastObjectId;

    /** Stores transaction information. */
    private static class TxnInfo {

	/** The SGS transaction. */
	final Transaction txn;

	/** The associated Berkeley DB transaction. */
	final com.sleepycat.db.Transaction bdbTxn;

	/** Whether preparation of the transaction has started. */
	boolean prepared;

	/** Whether any changes have been made in this transaction. */
	boolean modified;

	TxnInfo(Transaction txn, Environment env) throws DatabaseException {
	    this.txn = txn;
	    bdbTxn = env.beginTransaction(null, null);
	}
    }

    /** A BDB message handler that uses logging. */
    private static class LoggingMessageHandler implements MessageHandler {
	public void message(Environment env, String message) {
	    logger.log(Level.FINE, "Database message: {0}", message);
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.
     *
     * @param	properties the properties for configuring this instance
     * @throws	DataStoreException if there is a problem with the database
     * @throws	IllegalArgumentException if the <code>DIRECTORY_PROPERTY</code>
     *		property is not specified, or if the value of the
     *		<code>ALLOCATION_BLOCK_SIZE_PROPERTY</code> is not a valid
     *		integer greater than zero
     */
    public DataStoreImpl(Properties properties) {
	this.properties = properties;
	directory = properties.getProperty(DIRECTORY_PROPERTY);
	if (directory == null) {
	    throw new IllegalArgumentException("Directory must be specified");
	}
	allocationBlockSize = Util.getIntProperty(
	    properties, ALLOCATION_BLOCK_SIZE_PROPERTY,
	    DEFAULT_ALLOCATION_BLOCK_SIZE);
	if (allocationBlockSize < 1) {
	    throw new IllegalArgumentException(
		"The allocation block size must be greater than zero");
	}
	initialize();
    }

    private void initialize() {
	synchronized (dbLock) {
	    if (env != null) {
		return;
	    }
	    logger.log(Level.FINE, "Initializing database");
	    com.sleepycat.db.Transaction bdbTxn = null;
	    boolean committing = false;
	    boolean done = false;
	    try {
		env = getEnvironment(properties);
		bdbTxn = env.beginTransaction(null, null);
		DatabaseConfig createConfig = new DatabaseConfig();
		createConfig.setType(DatabaseType.BTREE);
		createConfig.setAllowCreate(true);
		boolean create = false;
		String idsFileName = directory + File.separator + "ids";
		Database ids;
		try {
		    ids = env.openDatabase(bdbTxn, idsFileName, null, null);
		    DataStoreHeader.verify(ids, bdbTxn);
		} catch (FileNotFoundException e) {
		    try {
			ids = env.openDatabase(
			    bdbTxn, idsFileName, null, createConfig);
		    } catch (FileNotFoundException e2) {
			throw new DataStoreException(
			    "Problem creating database: " + e2.getMessage(),
			    e2);
		    }
		    DataStoreHeader.create(ids, bdbTxn);
		    create = true;
		}
		this.ids = ids;
		try {
		    names = env.openDatabase(
			bdbTxn, directory + File.separator + "names", null,
			create ? createConfig : null);
		} catch (FileNotFoundException e) {
		    throw new DataStoreException("Names database not found");
		}
		committing = true;
		bdbTxn.commit();
		done = true;
	    } catch (DatabaseException e) {
		throw new DataStoreException(
		    "Problem initializing DataStore: " + e.getMessage(), e);
	    } finally {
		if (!done) {
		    env = null;
		    ids = null;
		    names = null;
		    if (bdbTxn != null) {
			try {
			    bdbTxn.abort();
			} catch (DatabaseException e) {
			    logger.logThrow(
				Level.FINE, "Exception during abort", e);
			}
		    }
		}
	    }
	}
    }

    private Environment getEnvironment(Properties properties)
	throws DatabaseException
    {
        EnvironmentConfig config = new EnvironmentConfig();
	long timeout = 1000L * Util.getLongProperty(
	    properties, TXN_TIMEOUT_PROPERTY, DEFAULT_TXN_TIMEOUT);
        config.setAllowCreate(true);
        config.setInitializeCache(true);
        config.setInitializeLocking(true);
        config.setInitializeLogging(true);
        config.setLockDetectMode(LockDetectMode.MINWRITE);
	config.setLockTimeout(timeout);
	config.setMessageHandler(new LoggingMessageHandler());
        config.setRunRecovery(true);
        config.setTransactional(true);
	config.setTxnTimeout(timeout);
	try {
	    return new Environment(new File(directory), config);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"DataStore directory does not exist: " + directory);
	}
    }

    /* -- Implement DataStore -- */

    public long createObject(Transaction txn) {
	try {
	    checkTxn(txn);
	    return createObjectInternal();
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	    throw new AssertionError();
	}
    }

    public void markForUpdate(Transaction txn, long id) {
	/*
	 * Berkeley DB doesn't seem to provide a way to obtain a write lock
	 * without reading or writing, so get the object and ask for a write
	 * lock.  -tjb@sun.com (10/06/2006)
	 */
	getObject(txn, id, true);
    }

    public byte[] getObject(Transaction txn, long id, boolean forUpdate) {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER,
		       "getObject txn:{0}, id:{1,number,#}, forUpdate:{2}",
		       txn, id, forUpdate);
	}
	checkId(id);
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(id, key);
	    DatabaseEntry value = new DatabaseEntry();
	    OperationStatus status = ids.get(
		txnInfo.bdbTxn, key, value, forUpdate ? LockMode.RMW : null);
	    if (status == OperationStatus.NOTFOUND) {
		throw new ObjectNotFoundException("Object not found");
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Getting object failed: " + status);
	    }
	    byte[] result = value.getData();
	    /* BDB returns null if the data is empty. */
	    return result != null ? result : NO_BYTES;
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	    throw new AssertionError();
	}
    }

    public void setObject(Transaction txn, long id, byte[] data) {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER, "setObject txn:{0}, id:{1,number,#}",
		       txn, id);
	}
	checkId(id);
	if (data == null) {
	    throw new NullPointerException("The data must not be null");
	}
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(id, key);
	    DatabaseEntry value = new DatabaseEntry(data);
	    OperationStatus status = ids.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Setting object failed: " + status);
	    }
	    txnInfo.modified = true;
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	}
    }

    public void removeObject(Transaction txn, long id) {
	checkId(id);
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(id, key);
	    OperationStatus status = ids.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new ObjectNotFoundException("Object not found: " + id);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Removing object failed: " + status);
	    }
	    txnInfo.modified = true;
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	}
    }

    public long getBinding(Transaction txn, String name) {
	if (name == null) {
	    throw new NullPointerException("Name must not be null");
	}
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    OperationStatus status =
		names.get(txnInfo.bdbTxn, key, value, null);
	    if (status == OperationStatus.NOTFOUND) {
		throw new NameNotBoundException("Name not bound: " + name);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Getting binding failed: " + status);
	    }
	    return LongBinding.entryToLong(value);
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	    throw new AssertionError();
	}
    }

    public void setBinding(Transaction txn, String name, long id) {
	if (name == null) {
	    throw new NullPointerException("Name must not be null");
	}
	checkId(id);
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    LongBinding.longToEntry(id, value);
	    OperationStatus status = names.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Setting binding failed: " + status);
	    }
	    txnInfo.modified = true;
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	}
    }

    public void removeBinding(Transaction txn, String name) {
	if (name == null) {
	    throw new NullPointerException("Name must not be null");
	}
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    OperationStatus status = names.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new NameNotBoundException("Name not bound: " + name);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Removing binding failed: " + status);
	    }
	    txnInfo.modified = true;
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	}
    }

    /* -- Implement TransactionParticipant -- */

    public String getIdentifier() {
	return toString();
    }

    public boolean prepare(Transaction txn) {
	logger.log(Level.FINE, "prepare txn:{0}", txn);
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = getTxnInfo();
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has already been prepared");
	} else {
	    txnInfo.prepared = true;
	}
	boolean done = false;
	try {
	    if (txnInfo.modified) {
		byte[] id = txn.getId();
		byte[] gid = new byte[128];
		System.arraycopy(id, 0, gid, 128 - id.length, id.length);
		txnInfo.bdbTxn.prepare(gid);
	    }
	    done = true;
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	    throw new AssertionError();
	} finally {
	    if (!done) {
		txnInfo.prepared = false;
	    }
	}
	boolean result = !txnInfo.modified;
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE, "prepare txn:{0} returns {1}", txn, result);
	}
	return result;
    }

    public void commit(Transaction txn) {
	logger.log(Level.FINE, "commit txn:{0}", txn);
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = getTxnInfo();
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (!txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has not been prepared");
	} else {
	    removeTxnInfo(txnInfo);
	}
	try {
	    txnInfo.bdbTxn.commit();
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	} catch (RuntimeException e) {
	    throw new DataStoreException(e.getMessage(), e);
	}
    }

    public void prepareAndCommit(Transaction txn) {
	logger.log(Level.FINE, "prepareAndCommit txn:{0}", txn);
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = getTxnInfo();
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has already been prepared");
	} else {
	    removeTxnInfo(txnInfo);
	}
	try {
	    txnInfo.bdbTxn.commit();
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	} catch (RuntimeException e) {
	    throw new DataStoreException(e.getMessage(), e);
	}
    }

    public void abort(Transaction txn) {
	logger.log(Level.FINE, "abort txn:{0}", txn);
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = getTxnInfo();
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	}
	removeTxnInfo(txnInfo);
	if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	}
	try {
	    txnInfo.bdbTxn.abort();
	} catch (DatabaseException e) {
	    handleDatabaseException(e);
	} catch (RuntimeException e) {
	    throw new DataStoreException(e.getMessage(), e);
	}
    }

    /* -- Other public methods -- */

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "DataStoreImpl[directory=" + directory + "]";
    }

    /* -- Private methods -- */

    /** Checks that the object ID argument is not negative. */
    private void checkId(long id) {
	if (id < 0) {
	    throw new IllegalArgumentException(
		"Object ID must not be negative");
	}
    }

    private void printStats() throws DatabaseException {
	StatsConfig config = new StatsConfig();
	config.setClear(true);
	System.err.println(env.getTransactionStats(config));
	System.err.println(env.getLogStats(config));
	System.err.println(env.getMutexStats(config));
    }

    /**
     * Checks that the transaction is in progress for an operation other than
     * prepare or commit.
     */
    private TxnInfo checkTxn(Transaction txn) throws DatabaseException {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = getTxnInfo();
	if (txnInfo == null) {
	    txn.join(this);
	    txnInfo = new TxnInfo(txn, env);
	    setTxnInfo(txnInfo);
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has been prepared");
	}
	return txnInfo;
    }

    private long createObjectInternal() throws DatabaseException {
	// XXX: Need interlock for shutdown here!
	synchronized (objectIdLock) {
	    if (nextObjectId >= lastObjectId) {
		long newNextObjectId;
		TransactionConfig txnConfig = new TransactionConfig();
		txnConfig.setReadUncommitted(true);
		com.sleepycat.db.Transaction bdbTxn =
		    env.beginTransaction(null, txnConfig);
		boolean done = false;
		try {
		    newNextObjectId = DataStoreHeader.getNextId(
			ids, bdbTxn, allocationBlockSize);
		    done = true;
		    bdbTxn.commit();
		} finally {
		    if (!done) {
			bdbTxn.abort();
		    }
		}
		nextObjectId = newNextObjectId;
		lastObjectId = newNextObjectId + allocationBlockSize;
	    }
	    return nextObjectId++;
	}
    }   

    private TxnInfo getTxnInfo() {
	initialize();
	return threadTxnInfo.get();
    }

    private void setTxnInfo(TxnInfo txnInfo) {
	threadTxnInfo.set(txnInfo);
	synchronized (txnInfoMap) {
	    txnInfoMap.put(txnInfo.txn, txnInfo);
	}
    }

    private void removeTxnInfo(TxnInfo txnInfo) {
	threadTxnInfo.set(null);
	TxnInfo result;
	synchronized (txnInfoMap) {
	    result = txnInfoMap.remove(txnInfo.txn);
	}
	assert txnInfo.equals(result);
    }

    public void shutdown() {
	synchronized (dbLock) {
	    synchronized (txnInfoMap) {
		for (TxnInfo txnInfo : txnInfoMap.values()) {
		    try {
			txnInfo.bdbTxn.abort();
		    } catch (DatabaseException e) {
			logger.logThrow(
			    Level.FINE, "Aborting transaction failed", e);
		    }
		}
		txnInfoMap.clear();
	    }
	    env = null;
	    try {
		ids.close();
	    } catch (DatabaseException e) {
		logger.logThrow(
		    Level.FINE, "Closing ids database failed", e);
	    }
	    ids = null;
	    try {
		names.close();
	    } catch (DatabaseException e) {
		logger.logThrow(
		    Level.FINE, "Closing names database failed", e);
	    }
	    names = null;
	}
    }

    private void handleDatabaseException(DatabaseException e) {
	if (e instanceof LockNotGrantedException) {
	    throw new TransactionTimeoutException(e.getMessage(), e);
	} else if (e instanceof DeadlockException) {
	    throw new TransactionConflictException(e.getMessage(), e);
	} else if (e instanceof RunRecoveryException) {
	    synchronized (dbLock) {
		env = null;
		ids = null;
		names = null;
	    }
	    throw new DataStoreException(e.getMessage(), e);
	} else {
	    throw new DataStoreException(e.getMessage(), e);
	}
    }

    public void panic() {
	synchronized (dbLock) {
	    if (env != null) {
		try {
		    env.panic(true);
		} catch (DatabaseException e) {
		    throw new DataStoreException(e.getMessage(), e);
		}
	    }
	}
    }
}
