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
import com.sleepycat.db.ErrorHandler;
import com.sleepycat.db.LockDetectMode;
import com.sleepycat.db.LockMode;
import com.sleepycat.db.LockNotGrantedException;
import com.sleepycat.db.MessageHandler;
import com.sleepycat.db.OperationStatus;
import com.sleepycat.db.RunRecoveryException;
import com.sleepycat.db.TransactionConfig;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.PropertiesUtil;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * XXX: Implement recovery for prepared transactions
 */
/** Provides an implementation of <code>DataStore</code>. */
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

    /**
     * The property that specifies the number of transactions between logging
     * database statistics.
     */
    private static final String LOG_STATS_PROPERTY =
	CLASSNAME + ".logStats";

    /**
     * The property that specifies whether to flush changes to disk on
     * transaction boundaries.  The property is set to false by default.  If
     * false, some recent transactions may be lost in the event of a crash,
     * although integrity will be maintained.
     */
    private static final String FLUSH_TO_DISK_PROPERTY =
	CLASSNAME + ".flushToDisk";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** An empty array returned when Berkeley DB returns null for a value. */
    private static final byte[] NO_BYTES = { };

    /** A transaction configuration that supports uncommitted reads. */
    private static final TransactionConfig uncommittedReadTxnConfig =
	new TransactionConfig();
    static {
	uncommittedReadTxnConfig.setReadUncommitted(true);
    }

    /** The directory in which to store database files. */
    private final String directory;

    /** The number of object IDs to allocate at one time. */
    private final int allocationBlockSize;

    private final int logStats;
    private int logStatsCount;

    /** The Berkeley DB environment. */
    private final Environment env;

    /** The Berkeley DB database that maps object IDs to object bytes. */
    private final Database oids;

    /** The Berkeley DB database that maps name bindings to object IDs. */
    private final Database names;

    /** Provides information about the transaction for the current thread. */
    private final ThreadLocal<TxnInfo> threadTxnInfo =
	new ThreadLocal<TxnInfo>();

    /**
     * Object to synchronize on when accessing nextObjectId and
     * lastObjectId.
     */
    private final Object objectIdLock = new Object();

    /**
     * The next object ID to use for allocating an object.  Valid if not
     * greater than lastObjectId.
     */
    private long nextObjectId = 0;

    /**
     * The last object ID that is free for allocating an object before needing
     * to obtain more numbers from the database.
     */
    private long lastObjectId = 0;

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

    /** A Berkeley DB message handler that uses logging. */
    private static class LoggingMessageHandler implements MessageHandler {
	public void message(Environment env, String message) {
	    logger.log(Level.FINE, "Database message: {0}", message);
	}
    }

    /** A Berkeley DB error handler that uses logging. */
    private static class LoggingErrorHandler implements ErrorHandler {
	public void error(Environment env, String prefix, String message) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.log(Level.WARNING, "Database error message: {0}{1}",
			   prefix != null ? prefix : "", message);
	    }
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
	directory = properties.getProperty(DIRECTORY_PROPERTY);
	if (directory == null) {
	    throw new IllegalArgumentException("Directory must be specified");
	}
	allocationBlockSize = PropertiesUtil.getIntProperty(
	    properties, ALLOCATION_BLOCK_SIZE_PROPERTY,
	    DEFAULT_ALLOCATION_BLOCK_SIZE);
	if (allocationBlockSize < 1) {
	    throw new IllegalArgumentException(
		"The allocation block size must be greater than zero");
	}
	logStats = PropertiesUtil.getIntProperty(
	    properties, LOG_STATS_PROPERTY, Integer.MAX_VALUE);
	com.sleepycat.db.Transaction bdbTxn = null;
	boolean done = false;
	try {
	    env = getEnvironment(properties);
	    bdbTxn = env.beginTransaction(null, null);
	    DatabaseConfig createConfig = new DatabaseConfig();
	    createConfig.setType(DatabaseType.BTREE);
	    createConfig.setAllowCreate(true);
	    boolean create = false;
	    String oidsFileName = directory + File.separator + "oids";
	    Database oids;
	    try {
		oids = env.openDatabase(bdbTxn, oidsFileName, null, null);
		DataStoreHeader.verify(oids, bdbTxn);
	    } catch (FileNotFoundException e) {
		try {
		    oids = env.openDatabase(
			bdbTxn, oidsFileName, null, createConfig);
		} catch (FileNotFoundException e2) {
		    throw new DataStoreException(
			"Problem creating database: " + e2.getMessage(),
			e2);
		}
		DataStoreHeader.create(oids, bdbTxn);
		create = true;
	    }
	    this.oids = oids;
	    try {
		names = env.openDatabase(
		    bdbTxn, directory + File.separator + "names", null,
		    create ? createConfig : null);
	    } catch (FileNotFoundException e) {
		throw new DataStoreException("Names database not found");
	    }
	    done = true;
	    bdbTxn.commit();
	} catch (DatabaseException e) {
	    logger.logThrow(
		Level.SEVERE, "DataStore initialization failed", e);
	    throw new DataStoreException(
		"Problem initializing DataStore: " + e.getMessage(), e);
	} finally {
	    if (bdbTxn != null && !done) {
		try {
		    bdbTxn.abort();
		} catch (DatabaseException e) {
		    logger.logThrow(Level.FINE, "Exception during abort", e);
		}
	    }
	}
    }

    /**
     * Obtains a Berkeley DB environment suitable for the specified
     * properties.
     */
    private Environment getEnvironment(Properties properties)
	throws DatabaseException
    {
	long timeout = 1000L * PropertiesUtil.getLongProperty(
	    properties, TXN_TIMEOUT_PROPERTY, DEFAULT_TXN_TIMEOUT);
	boolean flushToDisk = PropertiesUtil.getBooleanProperty(
	    properties, FLUSH_TO_DISK_PROPERTY, false);
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
	config.setErrorHandler(new LoggingErrorHandler());
        config.setInitializeCache(true);
        config.setInitializeLocking(true);
        config.setInitializeLogging(true);
        config.setLockDetectMode(LockDetectMode.MINWRITE);
	config.setLockTimeout(timeout);
	config.setMessageHandler(new LoggingMessageHandler());
        config.setRunRecovery(true);
        config.setTransactional(true);
	config.setTxnTimeout(timeout);
	config.setTxnWriteNoSync(!flushToDisk);
	try {
	    return new Environment(new File(directory), config);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"DataStore directory does not exist: " + directory);
	}
    }

    /* -- Implement DataStore -- */

    /** {@inheritDoc} */
    public long createObject(Transaction txn) {
	RuntimeException exception;
	try {
	    checkTxn(txn);
	    long result;
	    synchronized (objectIdLock) {
		if (nextObjectId >= lastObjectId) {
		    logger.log(Level.FINE, "Obtaining more object IDs");
		    long newNextObjectId;
		    com.sleepycat.db.Transaction bdbTxn =
			env.beginTransaction(
			    null, uncommittedReadTxnConfig);
		    boolean done = false;
		    try {
			newNextObjectId = DataStoreHeader.getNextId(
			    oids, bdbTxn, allocationBlockSize);
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
		result = nextObjectId++;
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "createObject txn:{0} returns oid:{1,number,#}",
			   txn, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	logger.logThrow(
	    Level.FINEST, "createObject txn:{0} fails", exception, txn);
	throw exception;
    }

    /** {@inheritDoc} */
    public void markForUpdate(Transaction txn, long oid) {
	/*
	 * Berkeley DB doesn't seem to provide a way to obtain a write lock
	 * without reading or writing, so get the object and ask for a write
	 * lock.  -tjb@sun.com (10/06/2006)
	 */
	try {
	    getObjectInternal(txn, oid, true);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "markForUpdate txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST,
		    "markForUpdate txn:{0}, oid:{1,number,#} fails",
		    e, txn, oid);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	try {
	    byte[] result = getObjectInternal(txn, oid, forUpdate);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2} " +
		    "returns",
		    txn, oid, forUpdate);
	    }
	    return result;
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST,
		    "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2} fails",
		    e, txn, oid, forUpdate);
	    }
	    throw e;
	}
    }

    /** Implement getObject, without logging. */
    private byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate)
    {
	checkId(oid);
	try {
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    DatabaseEntry value = new DatabaseEntry();
	    OperationStatus status = oids.get(
		txnInfo.bdbTxn, key, value, forUpdate ? LockMode.RMW : null);
	    if (status == OperationStatus.NOTFOUND) {
		throw new ObjectNotFoundException("Object not found");
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Getting object failed: " + status);
	    }
	    byte[] result = value.getData();
	    /* Berkeley DB returns null if the data is empty. */
	    return result != null ? result : NO_BYTES;
	} catch (DatabaseException e) {
	    throw convertDatabaseException(e);
	}
    }

    /** {@inheritDoc} */
    public void setObject(Transaction txn, long oid, byte[] data) {
	RuntimeException exception;
	try {
	    checkId(oid);
	    if (data == null) {
		throw new NullPointerException("The data must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    DatabaseEntry value = new DatabaseEntry(data);
	    OperationStatus status = oids.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Setting object failed: " + status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "setObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, "setObject txn:{0}, oid:{1,number,#} fails",
		    exception, txn, oid);
	}
	throw exception;
    }

    /** {@inheritDoc} */
    public void removeObject(Transaction txn, long oid) {
	RuntimeException exception;
	try {
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    OperationStatus status = oids.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new ObjectNotFoundException("Object not found: " + oid);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Removing object failed: " + status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "removeObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.logThrow(Level.FINEST,
			    "removeObject txn:{0}, oid:{1,number,#} fails",
			    exception, txn, oid);
	}
	throw exception;
    }

    /** {@inheritDoc} */
    public long getBinding(Transaction txn, String name) {
	RuntimeException exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
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
	    long result = LongBinding.entryToLong(value);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "getBinding txn:{0}, name:{1} returns oid:{2,number,#}",
		    txn, name, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "getBinding txn:{0}, name:{1} fails",
		       exception, txn, name);
	}
	throw exception;
    }

    /** {@inheritDoc} */
    public void setBinding(Transaction txn, String name, long oid) {
	RuntimeException exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    LongBinding.longToEntry(oid, value);
	    OperationStatus status = names.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "Setting binding failed: " + status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "setBinding txn:{0}, name:{1} returns",
		    txn, name);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.logThrow(
		Level.FINEST, "setBinding txn:{0}, name:{1} fails",
		exception, txn, name);
	}
	throw exception;
    }

    /** {@inheritDoc} */
    public void removeBinding(Transaction txn, String name) {
	RuntimeException exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
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
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "removeBinding txn:{0}, name:{1} returns",
		    txn, name);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	if (logger.isLoggable(Level.FINEST)) {
	    logger.logThrow(
		Level.FINEST, "removeBinding txn:{0}, name:{1} fails",
		exception, txn, name);
	}
	throw exception;
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
	RuntimeException exception;
	try {
	    if (txn == null) {
		throw new NullPointerException("Transaction must not be null");
	    }
	    TxnInfo txnInfo = threadTxnInfo.get();
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    } else if (!txnInfo.txn.equals(txn)) {
		throw new IllegalStateException("Wrong transaction");
	    } else if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    txnInfo.prepared = true;
	    boolean done = false;
	    try {
		if (txnInfo.modified) {
		    byte[] oid = txn.getId();
		    byte[] gid = new byte[128];
		    System.arraycopy(
			oid, 0, gid, 128 - oid.length, oid.length);
		    txnInfo.bdbTxn.prepare(gid);
		} else {
		    txnInfo.bdbTxn.commit();
		    threadTxnInfo.set(null);
		}
		done = true;
	    } finally {
		if (!done) {
		    txnInfo.prepared = false;
		}
	    }
	    boolean result = !txnInfo.modified;
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "prepare txn:{0} returns {1}", txn, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	logger.logThrow(Level.FINER, "prepare txn:{0} fails", exception, txn);
	throw exception;
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	RuntimeException exception;
	try {
	    if (txn == null) {
		throw new NullPointerException("Transaction must not be null");
	    }
	    TxnInfo txnInfo = threadTxnInfo.get();
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    } else if (!txnInfo.txn.equals(txn)) {
		throw new IllegalStateException("Wrong transaction");
	    } else if (!txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has not been prepared");
	    }
	    threadTxnInfo.set(null);
	    txnInfo.bdbTxn.commit();
	    logger.log(Level.FINER, "commit txn:{0} returns", txn);
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	logger.log(Level.FINER, "commit txn:{0} fails", exception, txn);
	throw exception;
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
	RuntimeException exception;
	try {
	    if (txn == null) {
		throw new NullPointerException("Transaction must not be null");
	    }
	    TxnInfo txnInfo = threadTxnInfo.get();
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    } else if (!txnInfo.txn.equals(txn)) {
		throw new IllegalStateException("Wrong transaction");
	    } else if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    threadTxnInfo.set(null);
	    txnInfo.bdbTxn.commit();
	    logger.log(Level.FINER, "prepareAndCommit txn:{0} returns", txn);
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	logger.logThrow(
	    Level.FINER, "prepareAndCommit txn:{0} fails", exception, txn);
	throw exception;
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	RuntimeException exception;
	try {
	    if (txn == null) {
		throw new NullPointerException("Transaction must not be null");
	    }
	    TxnInfo txnInfo = threadTxnInfo.get();
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    } else if (!txnInfo.txn.equals(txn)) {
		throw new IllegalStateException("Wrong transaction");
	    }
	    threadTxnInfo.set(null);
	    txnInfo.bdbTxn.abort();
	    logger.log(Level.FINER, "abort txn:{0} returns", txn);
	    return;
	} catch (DatabaseException e) {
	    exception = convertDatabaseException(e);
	} catch (RuntimeException e) {
	    exception = e;
	}
	logger.logThrow(Level.FINER, "abort txn:{0} fails", exception, txn);
	throw exception;
    }

    /* -- Other public methods -- */

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "DataStoreImpl[directory=\"" + directory + "\"]";
    }

    /* -- Private methods -- */

    /** Checks that the object ID argument is not negative. */
    private void checkId(long oid) {
	if (oid < 0) {
	    throw new IllegalArgumentException(
		"Object ID must not be negative");
	}
    }

    /**
     * Checks that the transaction is in progress for an operation other than
     * prepare or commit.
     */
    private TxnInfo checkTxn(Transaction txn) throws DatabaseException {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = threadTxnInfo.get();
	if (txnInfo == null) {
	    txn.join(this);
	    txnInfo = new TxnInfo(txn, env);
	    threadTxnInfo.set(txnInfo);
	    if (++logStatsCount >= logStats) {
		logStatsCount = 0;
		logStats(txnInfo);
	    }
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has been prepared");
	}
	return txnInfo;
    }

    /**
     * Returns the correct SGS exception for a Berkeley DB DatabaseException
     * thrown during an operation.  Throws an Error if recovery is needed.
     */
    private RuntimeException convertDatabaseException(DatabaseException e) {
	if (e instanceof LockNotGrantedException) {
	    return new TransactionTimeoutException(e.getMessage(), e);
	} else if (e instanceof DeadlockException) {
	    return new TransactionConflictException(e.getMessage(), e);
	} else if (e instanceof RunRecoveryException) {
	    /*
	     * It is tricky to clean up the data structures in this instance in
	     * order to reopen the Berkeley DB databases, because it's hard to
	     * know when they are no longer in use.  It's OK to catch this
	     * Error and create a new DataStoreImpl instance, but this instance
	     * is dead.  -tjb@sun.com (10/19/2006)
	     */
	    throw new Error(
		"Database requires recovery -- need to restart the server " +
		"or create a new instance of DataStoreImpl",
		e);
	} else {
	    return new DataStoreException(e.getMessage(), e);
	}
    }

    /** Log statistics using the specified transaction. */
    private void logStats(TxnInfo txnInfo) throws DatabaseException {
	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO,
		       "Berkeley DB statistics:\n" +
		       "Oids database: {0}\n" +
		       "Names database: {1}\n" +
		       "{2}\n" +
		       "{3}\n" +
		       "{4}\n" +
		       "{5}\n" +
		       "{6}\n" +
		       "{7}",
		       oids.getStats(txnInfo.bdbTxn, null),
		       names.getStats(txnInfo.bdbTxn, null),
		       Arrays.asList(env.getCacheFileStats(null)),
		       env.getCacheStats(null),
		       env.getLockStats(null),
		       env.getLogStats(null),
		       env.getMutexStats(null),
		       env.getTransactionStats(null));
	}
    }
}
