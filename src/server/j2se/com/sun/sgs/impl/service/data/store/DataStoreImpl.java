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

package com.sun.sgs.impl.service.data.store;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.db.CheckpointConfig;
import com.sleepycat.db.Cursor;
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
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileCounter;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.io.File;
import java.io.FileNotFoundException;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * XXX: Implement recovery for prepared transactions after a crash.
 * -tjb@sun.com (11/07/2006)
 */

/**
 * Provides an implementation of <code>DataStore</code> based on <a href=
 * "http://www.oracle.com/database/berkeley-db.html">Berkeley DB</a>. <p>
 *
 * Operations on this class will throw an {@link Error} if the underlying
 * Berkeley DB database requires recovery.  In that case, callers need to
 * restart the server or create a new instance of this class. <p>
 *
 * Note that, although this class provides support for the {@link
 * TransactionParticipant#prepare TransactionParticipant.prepare} method, it
 * does not provide facilities for resolving prepared transactions after a
 * crash.  Callers can work around this limitation by insuring that the
 * transaction implementation calls {@link
 * TransactionParticipant#prepareAndCommit
 * TransactionParticipant.prepareAndCommit} to commit transactions on this
 * class.  The current transaction implementation calls
 * <code>prepareAndCommit</code> on durable participants, such as this class,
 * so the inability to resolve prepared transactions should have no effect at
 * present. <p>
 *
 * The {@link #DataStoreImpl constructor} supports these public <a
 * href="../../../../app/doc-files/config-properties.html#DataStore">
 * properties</a>, and the following additional properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.data.store.DataStoreImpl.allocation.block.size
 *	</b></code> <br>
 *	<i>Default:</i> <code>100</code>
 *
 * <dd style="padding-top: .5em">The number of object IDs to allocate at a
 *	time.  This value must be greater than <code>0</code>.  Object IDs are
 *	allocated in an independent transaction, and are discarded if a
 *	transaction aborts, if a managed object is made reachable within the
 *	data store but is removed from the store before the transaction
 *	commits, or if the program exits before it uses the object IDs it has
 *	allocated.  This number limits the maximum number of object IDs that
 *	would be discarded when the program exits. <p>
 *
 * </dl> <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.DataStoreImpl</code> to log information
 * at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Initialization failures
 * <li> {@link Level#WARNING WARNING} - Berkeley DB errors
 * <li> {@link Level#INFO INFO} - Berkeley DB statistics
 * <li> {@link Level#CONFIG CONFIG} - Constructor properties, data store
 *	headers
 * <li> {@link Level#FINE FINE} - Berkeley DB messages, allocating blocks of
 *	object IDs
 * <li> {@link Level#FINER FINER} - Transaction operations
 * <li> {@link Level#FINEST FINEST} - Name and object operations
 * </ul> <p>
 *
 */
public class DataStoreImpl
    implements DataStore, TransactionParticipant, ProfileProducer
{
    /** The name of this class. */
    private static final String CLASSNAME = DataStoreImpl.class.getName();

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    private static final String DIRECTORY_PROPERTY = CLASSNAME + ".directory";

    /** The default directory for database files from the app root. */
    private static final String DEFAULT_DIRECTORY = "dsdb";

    /**
     * The property that specifies the number of object IDs to allocate at one
     * time.
     */
    private static final String ALLOCATION_BLOCK_SIZE_PROPERTY =
	CLASSNAME + ".allocation.block.size";

    /** The default for the number of object IDs to allocate at one time. */
    private static final int DEFAULT_ALLOCATION_BLOCK_SIZE = 100;

    /**
     * The property that specifies the size in bytes of the Berkeley DB cache.
     */
    private static final String CACHE_SIZE_PROPERTY =
	CLASSNAME + ".cache.size";

    /** The minimum cache size, as specified by Berkeley DB */
    private static final long MIN_CACHE_SIZE = 20000;

    /** The default cache size. */
    private static final long DEFAULT_CACHE_SIZE = 1000000L;

    /**
     * The property that specifies the time in milliseconds between
     * checkpoints.
     */
    private static final String CHECKPOINT_INTERVAL_PROPERTY =
	CLASSNAME + ".checkpoint.interval";

    /** The default checkpoint interval. */
    private static final long DEFAULT_CHECKPOINT_INTERVAL = 60000;

    /**
     * The property that specifies how many bytes need to be modified before
     * performing a checkpoint.
     */
    private static final String CHECKPOINT_SIZE_PROPERTY =
	CLASSNAME + ".checkpoint.size";

    /** The default checkpoint size. */
    private static final long DEFAULT_CHECKPOINT_SIZE = 100000;

    /**
     * The property that specifies whether to automatically remove log files.
     */
    private static final String REMOVE_LOGS_PROPERTY =
	CLASSNAME + ".remove.logs";

    /**
     * The property that specifies whether to flush changes to disk on
     * transaction boundaries.  The property is set to false by default.  If
     * false, some recent transactions may be lost in the event of a crash,
     * although integrity will be maintained.
     */
    private static final String FLUSH_TO_DISK_PROPERTY =
	CLASSNAME + ".flush.to.disk";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** An empty array returned when Berkeley DB returns null for a value. */
    private static final byte[] NO_BYTES = { };

    /** The number of bytes in a SHA-1 message digest. */
    private static final int SHA1_SIZE = 20;

    /** The directory in which to store database files. */
    private final String directory;

    /** The number of object IDs to allocate at one time. */
    private final int allocationBlockSize;

    /** The interval between checkpoints in milliseconds. */
    private final long checkpointInterval;

    /**
     * The number of bytes that need to be written in order to perform a
     * checkpoint.
     */
    private final long checkpointSize;

    /** Stores information about transactions. */
    private final TxnInfoTable<TxnInfo> txnInfoTable;

    /** The Berkeley DB environment. */
    private final Environment env;

    /**
     * The Berkeley DB database that holds version and next object ID
     * information.  This information is stored in a separate database to avoid
     * concurrency conflicts between the object ID and other data.
     */
    private final Database infoDb;

    /** The Berkeley DB database that stores class information. */
    private final Database classesDb;

    /** The Berkeley DB database that maps object IDs to object bytes. */
    private final Database oidsDb;

    /** The Berkeley DB database that maps name bindings to object IDs. */
    private final Database namesDb;

    /** Used to cancel the checkpoint task. */
    private TaskHandle checkpointTaskHandle = null;

    /**
     * Object to synchronize on when accessing nextObjectId and
     * lastObjectId.
     */
    private final Object objectIdLock = new Object();

    /**
     * The next object ID to use for creating an object.  Valid if not greater
     * than lastObjectId.
     */
    private long nextObjectId = 0;

    /**
     * The last object ID that is free for allocating an object before needing
     * to obtain more IDs from the database.
     */
    private long lastObjectId = -1;

    /** Object to synchronize on when accessing txnCount and allOps. */
    private final Object txnCountLock = new Object();

    /** The number of currently active transactions. */
    private int txnCount = 0;

    /** A message digest for use by the current thread. */
    private ThreadLocal<MessageDigest> messageDigest =
	new ThreadLocal<MessageDigest>() {
	    protected MessageDigest initialValue() {
		try {
		    return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
		    throw new AssertionError(e);
		}
	    }
        };

    /* -- The operations -- DataStore API -- */
    private ProfileOperation createObjectOp = null;
    private ProfileOperation markForUpdateOp = null;
    private ProfileOperation getObjectOp = null;
    private ProfileOperation getObjectForUpdateOp = null;
    private ProfileOperation setObjectOp = null;
    private ProfileOperation setObjectsOp = null;
    private ProfileOperation removeObjectOp = null;
    private ProfileOperation getBindingOp = null;
    private ProfileOperation setBindingOp = null;
    private ProfileOperation removeBindingOp = null;
    private ProfileOperation nextBoundNameOp = null;
    private ProfileOperation getClassIdOp = null;
    private ProfileOperation getClassInfoOp = null;

    /**
     * The counters used for profile reporting, which track the bytes read
     * and written within a task, and how many objects were read and written
     */
    private ProfileCounter readBytesCounter = null;
    private ProfileCounter readObjectsCounter = null;
    private ProfileCounter writtenBytesCounter = null;
    private ProfileCounter writtenObjectsCounter = null;

    /**
     * Records information about all active transactions.
     *
     * @param	<T> the type of information stored for each transaction
     */
    protected interface TxnInfoTable<T> {

	/**
	 * Returns the information associated with the transaction, or null if
	 * none is found.
	 *
	 * @param	txn the transaction
	 * @return	the associated information, or null if none is found
	 * @throws	TransactionNotActive if the implementation determines
	 *		that the transaction is no longer active
	 * @throws	IllegalStateException if the implementation determines
	 *		that the specified transaction does not match the
	 *		current context
	 */
	T get(Transaction txn);

	/**
	 * Removes the information associated with the transaction.
	 *
	 * @param	txn the transaction
	 * @return	the previously associated information
	 * @throws	IllegalStateException if the transaction is not active,
	 *		or if the implementation determines that the specified
	 *		transaction does not match the current context
	 */
	T remove(Transaction txn);

	/**
	 * Sets the information associated with the transaction, which should
	 * not currently have associated information.
	 *
	 * @param	txn the transaction
	 * @param	info the associated information
	 */
	void set(Transaction txn, T info);
    }

    /**
     * An implementation of TxnInfoTable that uses a thread local to record
     * information about transactions, and requires that the same thread always
     * be used with a given transaction.
     */
    private static class ThreadTxnInfoTable<T> implements TxnInfoTable<T> {

	/**
	 * Provides information about the transaction for the current thread.
	 */
	private final ThreadLocal<Entry<T>> threadInfo =
	    new ThreadLocal<Entry<T>>();

	/** Stores a transaction and the associated information. */
	private static class Entry<T> {
	    final Transaction txn;
	    final T info;
	    Entry(Transaction txn, T info) {
		this.txn = txn;
		this.info = info;
	    }
	}

	/** Creates an instance. */
	ThreadTxnInfoTable() { }

	/* -- Implement TxnInfoTable -- */

	public T get(Transaction txn) {
	    Entry<T> entry = threadInfo.get();
	    if (entry == null) {
		return null;
	    } else if (!entry.txn.equals(txn)) {
		throw new IllegalStateException(
		    "Wrong transaction: Got " + txn + ", expected " +
		    entry.txn);
	    } else {
		return entry.info;
	    }
	}

	public T remove(Transaction txn) {
	    Entry<T> entry = threadInfo.get();
	    if (entry == null) {
		throw new IllegalStateException("Transaction not active");
	    } else if (!entry.txn.equals(txn)) {
		throw new IllegalStateException("Wrong transaction");
	    }
	    threadInfo.set(null);
	    return entry.info;
	}

	public void set(Transaction txn, T info) {
	    threadInfo.set(new Entry<T>(txn, info));
	}
    }

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

	/**
	 * The currently open Berkeley DB cursor or null.  The cursor must be
	 * closed before the transaction is prepared, committed, or aborted.
	 * Note that the Berkeley DB documentation for prepare doesn't say you
	 * need to close cursors, but my testing shows that you do.
	 * -tjb@sun.com (12/14/2006)
	 */
	private Cursor cursor;

	/** The last key returned by the cursor or null. */
	private String lastCursorKey;

	TxnInfo(Transaction txn, Environment env) throws DatabaseException {
	    this.txn = txn;
	    bdbTxn = createBdbTxn(env, txn.getTimeout());
	}

	/** Prepares the transaction, first closing the cursor, if present. */
	void prepare(byte[] gid) throws DatabaseException {
	    maybeCloseCursor();
	    bdbTxn.prepare(gid);
	}

	/**
	 * Commits the transaction, first closing the cursor, if present, and
	 * returning the operations count for this transaction.
	 */
	void commit() throws DatabaseException {
	    maybeCloseCursor();
	    bdbTxn.commit();
	}

	/**
	 * Aborts the transaction, first closing the cursor, if present, and
	 * returning the operations count for this transaction.
	 */
	void abort() throws DatabaseException {
	    maybeCloseCursor();
	    bdbTxn.abort();
	}

	/** Returns the next name in the names database. */
	String nextName(String name, Database names) throws DatabaseException {
	    if (cursor == null) {
		cursor = names.openCursor(bdbTxn, null);
	    }
	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry value = new DatabaseEntry();
	    if (name == null) {
		OperationStatus status = cursor.getFirst(key, value, null);
		lastCursorKey = getNextBoundNameResult(null, status, key);
	    } else {
		boolean matchesLast = name.equals(lastCursorKey);
		if (!matchesLast) {
		    /*
		     * The name specified was not the last key returned, so
		     * search for the specified name
		     */
		    StringBinding.stringToEntry(name, key);
		    OperationStatus status =
			cursor.getSearchKeyRange(key, value, null);
		    lastCursorKey = getNextBoundNameResult(name, status, key);
		    /* Record if we found an exact match */
		    matchesLast = name.equals(lastCursorKey);
		}
		if (matchesLast) {
		    /* The last key was an exact match, so find the next one */
		    OperationStatus status = cursor.getNext(key, value, null);
		    lastCursorKey = getNextBoundNameResult(name, status, key);
		}
	    }
	    return lastCursorKey;
	}

	/**
	 * Close the cursor if it is open.  Always null the cursor field, since
	 * the Berkeley DB API doesn't permit closing a cursor after an attempt
	 * to close it.
	 */
	private void maybeCloseCursor() throws DatabaseException {
	    if (cursor != null) {
		Cursor c = cursor;
		cursor = null;
		c.close();
	    }
	}

	/**
	 * Returns the name of the next binding given the results of a cursor
	 * operation and the associated key.
	 */
	private String getNextBoundNameResult(
	    String name, OperationStatus status, DatabaseEntry key)
	{
	    if (status == OperationStatus.NOTFOUND) {
		return null;
	    } else if (status == OperationStatus.SUCCESS) {
		return StringBinding.entryToString(key);
	    } else {
		throw new DataStoreException(
		    "nextBoundName txn:" + txn + ", name:" + name +
		    " failed: " + status);
	    }
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
		logger.logThrow(Level.WARNING, new Exception("Stacktrace"),
				"Database error message: {0}{1}",
				prefix != null ? prefix : "", message);
	    }
	}
    }

    /** An interface for running periodic tasks. */
    public interface Scheduler {
    
	/**
	 * Runs the task every period milliseconds, and returns a handle to use
	 * to cancel the task.
	 *
	 * @param	task the task
	 * @param	period the period in milliseconds
	 * @return	a handle for cancelling future runs
	 */
	TaskHandle scheduleRecurringTask(Runnable task, long period);
    }

    /** An interface for cancelling a periodic task. */
    public interface TaskHandle {

	/**
	 * Cancels future runs of the task.
	 *
	 * @throws	IllegalStateException if the task has already been
	 *		cancelled
	 */
	void cancel();
    }

    /** The default implementation of Scheduler. */
    private static class BasicScheduler implements Scheduler {
	public TaskHandle scheduleRecurringTask(Runnable task, long period) {
	    final ScheduledExecutorService executor =
		Executors.newSingleThreadScheduledExecutor();
	    executor.scheduleAtFixedRate(
		task, period, period, TimeUnit.MILLISECONDS);
	    return new TaskHandle() {
		public synchronized void cancel() {
		    if (executor.isShutdown()) {
			throw new IllegalStateException(
			    "Task is already cancelled");
		    }
		    executor.shutdownNow();
		}
	    };
	}
    }

    /** A runnable that performs a periodic database checkpoint. */
    private static class CheckpointRunnable implements Runnable {
	private final Environment env;
	private final CheckpointConfig config = new CheckpointConfig();
	CheckpointRunnable(Environment env, long size) {
	    this.env = env;
	    config.setKBytes((int) (size / 1000));
	}
	public void run() {
	    try {
		env.checkpoint(config);
	    } catch (Throwable e) {
		logger.logThrow(Level.WARNING, e, "Checkpoint failed");
	    }
	}
    }

    /**
     * Stores information about the databases that constitute the data
     * store.
     */
    private static class Databases {
	private Database info, classes, oids, names;
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.  See the {@linkplain DataStoreImpl class documentation} for
     * a list of supported properties.
     *
     * @param	properties the properties for configuring this instance
     * @throws	DataStoreException if there is a problem with the database
     * @throws	IllegalArgumentException if any of the properties are invalid,
     *		as specified in the class documentation
     */
    public DataStoreImpl(Properties properties) {
	this(properties, new BasicScheduler());
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties and using the specified scheduler.  See the {@linkplain
     * DataStoreImpl class documentation} for a list of supported properties.
     *
     * @param	properties the properties for configuring this instance
     * @param	scheduler the scheduler used to schedule periodic tasks
     * @throws	DataStoreException if there is a problem with the database
     * @throws	IllegalArgumentException if any of the properties are invalid,
     *		as specified in the class documentation}
     */
    public DataStoreImpl(Properties properties, Scheduler scheduler) {
	logger.log(
	    Level.CONFIG, "Creating DataStoreImpl properties:{0}", properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	String specifiedDirectory =
	    wrappedProps.getProperty(DIRECTORY_PROPERTY);
	if (specifiedDirectory == null) {
	    String rootDir =
		properties.getProperty(StandardProperties.APP_ROOT);
	    if (rootDir == null) {
		throw new IllegalArgumentException(
		    "A value for the property " + StandardProperties.APP_ROOT +
		    " must be specified");
	    }
	    specifiedDirectory = rootDir + File.separator + DEFAULT_DIRECTORY;
	}
	/*
	 * Use an absolute path to avoid problems on Windows.
	 * -tjb@sun.com (02/16/2007)
	 */
	directory = new File(specifiedDirectory).getAbsolutePath();
	allocationBlockSize = wrappedProps.getIntProperty(
	    ALLOCATION_BLOCK_SIZE_PROPERTY, DEFAULT_ALLOCATION_BLOCK_SIZE,
	    1, Integer.MAX_VALUE);
	checkpointInterval = wrappedProps.getLongProperty(
	    CHECKPOINT_INTERVAL_PROPERTY, DEFAULT_CHECKPOINT_INTERVAL);
	checkpointSize = wrappedProps.getLongProperty(
	    CHECKPOINT_SIZE_PROPERTY, DEFAULT_CHECKPOINT_SIZE);
	txnInfoTable = getTxnInfoTable(TxnInfo.class);
	com.sleepycat.db.Transaction bdbTxn = null;
	boolean done = false;
	try {
	    env = getEnvironment(properties);
	    bdbTxn = createBdbTxn(env, Long.MAX_VALUE);
	    Databases dbs = getDatabases(bdbTxn);
	    infoDb = dbs.info;
	    classesDb = dbs.classes;
	    oidsDb = dbs.oids;
	    namesDb = dbs.names;
	    checkpointTaskHandle = scheduler.scheduleRecurringTask(
		new CheckpointRunnable(env, checkpointSize),
		checkpointInterval);
	    done = true;
	    bdbTxn.commit();
	} catch (DatabaseException e) {
	    throw convertException(
		null, Level.SEVERE, e, "DataStore initialization");
	} finally {
	    if (bdbTxn != null && !done) {
		try {
		    bdbTxn.abort();
		} catch (DatabaseException e) {
		    logger.logThrow(Level.FINE, e, "Exception during abort");
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
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	boolean flushToDisk = wrappedProps.getBooleanProperty(
	    FLUSH_TO_DISK_PROPERTY, false);
	long cacheSize = wrappedProps.getLongProperty(
	    CACHE_SIZE_PROPERTY, DEFAULT_CACHE_SIZE, MIN_CACHE_SIZE,
	    Long.MAX_VALUE);
	boolean removeLogs = wrappedProps.getBooleanProperty(
	    REMOVE_LOGS_PROPERTY, false);
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
	config.setCacheSize(cacheSize);
	config.setErrorHandler(new LoggingErrorHandler());
        config.setInitializeCache(true);
        config.setInitializeLocking(true);
        config.setInitializeLogging(true);
        config.setLockDetectMode(LockDetectMode.YOUNGEST);
	config.setLogAutoRemove(removeLogs);
	config.setMessageHandler(new LoggingMessageHandler());
        config.setRunRecovery(true);
        config.setTransactional(true);
	config.setTxnWriteNoSync(!flushToDisk);
	try {
	    return new Environment(new File(directory), config);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"DataStore directory does not exist: " + directory);
	}
    }

    /**
     * Opens or creates the Berkeley DB databases associated with this data
     * store.
     */
    private Databases getDatabases(com.sleepycat.db.Transaction bdbTxn)
	throws DatabaseException
    {
	Databases dbs = new Databases();
	DatabaseConfig createConfig = new DatabaseConfig();
	createConfig.setType(DatabaseType.BTREE);
	createConfig.setAllowCreate(true);
	boolean create = false;
	String infoFileName = directory + File.separator + "info";
	try {
	    dbs.info = env.openDatabase(bdbTxn, infoFileName, null, null);
	    int minorVersion = DataStoreHeader.verify(dbs.info, bdbTxn);
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(Level.CONFIG, "Found existing header {0}",
			   DataStoreHeader.headerString(minorVersion));
	    }
	} catch (FileNotFoundException e) {
	    try {
		dbs.info = env.openDatabase(
		    bdbTxn, infoFileName, null, createConfig);
	    } catch (FileNotFoundException e2) {
		throw new DataStoreException(
		    "Problem creating database: " + e2.getMessage(), e2);
	    }
	    DataStoreHeader.create(dbs.info, bdbTxn);
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(Level.CONFIG, "Created new header {0}",
			   DataStoreHeader.headerString());
	    }
	    create = true;
	}
	try {
	    dbs.classes = env.openDatabase(
		bdbTxn, directory + File.separator + "classes", null,
		create ? createConfig : null);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"Classes database not found: " + e.getMessage(), e);
	}
	try {
	    dbs.oids = env.openDatabase(
		bdbTxn, directory + File.separator + "oids", null,
		create ? createConfig : null);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"Oids database not found: " + e.getMessage(), e);
	}
	try {
	    dbs.names = env.openDatabase(
		bdbTxn, directory + File.separator + "names", null,
		create ? createConfig : null);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"Names database not found: " + e.getMessage(), e);
	}
	return dbs;
    }

    /* -- Implement DataStore -- */

    /** {@inheritDoc} */
    public long createObject(Transaction txn) {
	logger.log(Level.FINEST, "createObject txn:{0}", txn);
	Exception exception;
	try {
	    checkTxn(txn, createObjectOp);
	    long result;
	    synchronized (objectIdLock) {
		if (nextObjectId > lastObjectId) {
		    logger.log(Level.FINE, "Allocate more object IDs");
		    long newNextObjectId = getNextId(
			DataStoreHeader.NEXT_OBJ_ID_KEY, allocationBlockSize,
			txn.getTimeout());
		    nextObjectId = newNextObjectId;
		    lastObjectId = newNextObjectId + allocationBlockSize - 1;
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
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINEST, exception, "createObject txn:" + txn);
    }

    /** {@inheritDoc} */
    public void markForUpdate(Transaction txn, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "markForUpdate txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	/*
	 * Berkeley DB doesn't seem to provide a way to obtain a write lock
	 * without reading or writing, so get the object and ask for a write
	 * lock.  -tjb@sun.com (10/06/2006)
	 */
	Exception exception;
	try {
	    getObjectInternal(txn, oid, true, markForUpdateOp);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "markForUpdate txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "markForUpdate txn:" + txn + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2}",
		       txn, oid, forUpdate);
	}
	Exception exception;
	try {
	    byte[] result = getObjectInternal(
		txn, oid, forUpdate,
		forUpdate ? getObjectForUpdateOp : getObjectOp);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2} " +
		    "returns",
		    txn, oid, forUpdate);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "getObject txn:" + txn + ", oid:" + oid +
			       ", forUpdate:" + forUpdate);
    }

    /** Implement getObject, without logging. */
    private byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate, ProfileOperation op)
	throws DatabaseException
    {
	checkId(oid);
	TxnInfo txnInfo = checkTxn(txn, op);
	DatabaseEntry key = new DatabaseEntry();
	LongBinding.longToEntry(oid, key);
	DatabaseEntry value = new DatabaseEntry();
	OperationStatus status = oidsDb.get(
	    txnInfo.bdbTxn, key, value, forUpdate ? LockMode.RMW : null);
	if (status == OperationStatus.NOTFOUND) {
	    throw new ObjectNotFoundException("Object not found: " + oid);
	} else if (status != OperationStatus.SUCCESS) {
	    throw new DataStoreException("getObject txn:" + txn + ", oid:" +
					 oid + ", forUpdate:" + forUpdate +
					 " failed: " + status);
	}
	byte[] result = value.getData();
	if (readBytesCounter != null) {
	    if (result != null)
		readBytesCounter.incrementCount(result.length);
	    readObjectsCounter.incrementCount();
	}
	/* Berkeley DB returns null if the data is empty. */
	return result != null ? result : NO_BYTES;
    }

    /** {@inheritDoc} */
    public void setObject(Transaction txn, long oid, byte[] data) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "setObject txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	Exception exception;
	try {
	    checkId(oid);
	    if (data == null) {
		throw new NullPointerException("The data must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, setObjectOp);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    DatabaseEntry value = new DatabaseEntry(data);
	    OperationStatus status = oidsDb.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "setObject txn: " + txn + ", oid:" + oid + " failed: " +
		    status);
	    }
	    if (writtenBytesCounter != null) {
		writtenBytesCounter.incrementCount(data.length);
		writtenObjectsCounter.incrementCount();
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "setObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "setObject txn:" + txn + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public void setObjects(Transaction txn, long[] oids, byte[][] dataArray) {
	logger.log(Level.FINEST, "setObjects txn:{0}", txn);
	Exception exception;
	long oid = -1;
	boolean oidSet = false;
	try {
	    TxnInfo txnInfo = checkTxn(txn, setObjectsOp);
	    int len = oids.length;
	    if (len != dataArray.length) {
		throw new IllegalArgumentException(
		    "The oids and dataArray must be the same length");
	    }
	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry value = new DatabaseEntry();
	    for (int i = 0; i < len; i++) {
		oid = oids[i];
		oidSet = true;
		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(Level.FINEST,
			       "setObjects txn:{0}, oid:{1,number,#}",
			       txn, oid);
		}
		checkId(oid);
		byte[] data = dataArray[i];
		if (data == null) {
		    throw new NullPointerException(
			"The data must not be null");
		}
		LongBinding.longToEntry(oid, key);
		value.setData(data);
		OperationStatus status =
		    oidsDb.put(txnInfo.bdbTxn, key, value);
		if (status != OperationStatus.SUCCESS) {
		    throw new DataStoreException(
			"setObjects txn: " + txn + ", oid:" + oid +
			" failed: " + status);
		}
		if (writtenBytesCounter != null) {
		    writtenBytesCounter.incrementCount(data.length);
		    writtenObjectsCounter.incrementCount();
		}
	    }
	    txnInfo.modified = true;
	    logger.log(Level.FINEST, "setObjects txn:{0} returns", txn);
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINEST, exception,
	    "setObject txn:" + txn + (oidSet ? ", oid:" + oid : ""));
    }

    /** {@inheritDoc} */
    public void removeObject(Transaction txn, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "removeObject txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	Exception exception;
	try {
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn, removeObjectOp);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    OperationStatus status = oidsDb.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new ObjectNotFoundException("Object not found: " + oid);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "removeObject txn:" + txn + ", oid:" + oid + " failed: " +
		    status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "removeObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "removeObject txn:" + txn + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public long getBinding(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "getBinding txn:{0}, name:{1}", txn, name);
	}
	Exception exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, getBindingOp);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    OperationStatus status =
		namesDb.get(txnInfo.bdbTxn, key, value, null);
	    if (status == OperationStatus.NOTFOUND) {
		throw new NameNotBoundException("Name not bound: " + name);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "getBinding txn:" + txn + ", name:" + name + " failed: " +
		    status);
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
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "getBinding txn:" + txn + ", name:" + name);
    }

    /** {@inheritDoc} */
    public void setBinding(Transaction txn, String name, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "setBinding txn:{0}, name:{1}, oid:{2,number,#}",
		txn, name, oid);
	}
	Exception exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn, setBindingOp);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    LongBinding.longToEntry(oid, value);
	    OperationStatus status = namesDb.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "setBinding txn:" + txn + ", name:" + name + " failed: " +
		    status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "setBinding txn:{0}, name:{1}, oid:{2,number,#} returns",
		    txn, name, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINEST, exception,
	    "setBinding txn:" + txn + ", name:" + name + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public void removeBinding(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "removeBinding txn:{0}, name:{1}", txn, name);
	}
	Exception exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, removeBindingOp);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    OperationStatus status = namesDb.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new NameNotBoundException("Name not bound: " + name);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "removeBinding txn:" + txn + ", name:" + name +
		    " failed: " + status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "removeBinding txn:{0}, name:{1} returns",
		    txn, name);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "removeBinding txn:" + txn + ", name:" + name);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation uses a single cursor, so it provides better
     * performance when used to iterate over names in order.
     */
    public String nextBoundName(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "nextBoundName txn:{0}, name:{1}", txn, name);
	}
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxn(txn, nextBoundNameOp);
	    String result = txnInfo.nextName(name, namesDb);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "nextBoundName txn:{0}, name:{1} returns {2}",
			   txn, name, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "nextBoundName txn:" + txn + ", name:" + name);
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	logger.log(Level.FINER, "shutdown");
	Exception exception;
	try {
	    synchronized (txnCountLock) {
		while (txnCount > 0) {
		    try {
			logger.log(Level.FINEST,
				   "shutdown waiting for {0} transactions",
				   txnCount);
			txnCountLock.wait();
		    } catch (InterruptedException e) {
			logger.log(Level.FINEST, "shutdown interrupted");
			break;
		    }
		}
		if (txnCount < 0) {
		    throw new IllegalStateException("DataStore is shut down");
		}
		boolean ok = (txnCount == 0);
		if (ok) {
		    checkpointTaskHandle.cancel();
		    infoDb.close();
		    classesDb.close();
		    oidsDb.close();
		    namesDb.close();
		    env.close();
		    txnCount = -1;
		}
		logger.log(Level.FINER, "shutdown returns {0}", ok);
		return ok;
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(null, Level.FINER, exception, "shutdown");
    }

    /** {@inheritDoc} */
    public int getClassId(Transaction txn, byte[] classInfo) {
	logger.log(Level.FINER, "getClassId txn:{0}", txn);
	String operation = "getClassId txn:" + txn;
	Exception exception;
	try {
	    checkTxn(txn, getClassIdOp);
	    if (classInfo == null) {
		throw new NullPointerException(
		    "The classInfo argument must not be null");
	    }
	    DatabaseEntry hashKey = getKeyFromClassInfo(classInfo);
	    DatabaseEntry hashValue = new DatabaseEntry();
	    int result;
	    boolean done = false;
	    /*
	     * Use a separate transaction when obtaining the class ID so that
	     * the ID will be available for other transactions to use right
	     * away.  This approach means that the class info will be
	     * registered even if the main transaction fails.  If any
	     * transaction wants to register a new class, though, it's very
	     * likely that the class will be needed, even if that transaction
	     * aborts, so it makes sense to commit this operation separately to
	     * improve concurrency.  -tjb@sun.com (05/23/2007)
	     */
	    com.sleepycat.db.Transaction bdbTxn =
		createBdbTxn(env, txn.getTimeout());
	    try {
		if (get(classesDb, bdbTxn, hashKey, hashValue, operation)) {
		    result = IntegerBinding.entryToInt(hashValue);
		} else {
		    Cursor cursor = classesDb.openCursor(bdbTxn, null);
		    try {
			DatabaseEntry idKey = new DatabaseEntry();
			DatabaseEntry idValue = new DatabaseEntry();
			OperationStatus status =
			    cursor.getLast(idKey, idValue, null);
			result = checkStatusFound(status, operation)
			    ? getClassIdFromKey(idKey) + 1 : 1;
			getKeyFromClassId(result, idKey);
			idValue.setData(classInfo);
			checkStatus(
			    cursor.putNoOverwrite(idKey, idValue), operation);
		    } finally {
			cursor.close();
		    }
		    IntegerBinding.intToEntry(result, hashValue);
		    putNoOverwrite(
			classesDb, bdbTxn, hashKey, hashValue, operation);
		}
		done = true;
		bdbTxn.commit();
	    } finally {
		if (!done) {
		    bdbTxn.abort();
		}
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "getClassId txn:{0} returns {1}",
			   txn, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINER, exception, operation);
    }

    /** {@inheritDoc} */
    public byte[] getClassInfo(Transaction txn, int classId)
	throws ClassInfoNotFoundException
    {
	if (logger.isLoggable(Level.FINER)) {
	    logger.log(Level.FINER,
		       "getClassInfo txn:{0}, classId:{1,number,#}",
		       txn, classId);
	}
	String operation = "getClassInfo txn:" + txn + ", classId:" + classId;
	Exception exception;
	try {
	    checkTxn(txn, getClassInfoOp);
	    if (classId < 1) {
		throw new IllegalArgumentException(
		    "The classId argument must greater than 0");
	    }
	    DatabaseEntry key = new DatabaseEntry();
	    getKeyFromClassId(classId, key);
	    DatabaseEntry value = new DatabaseEntry();
	    boolean found;
	    boolean done = false;
	    com.sleepycat.db.Transaction bdbTxn =
		createBdbTxn(env, txn.getTimeout());
	    try {
		found = get(classesDb, bdbTxn, key, value, operation);
		done = true;
		bdbTxn.commit();
	    } finally {
		if (!done) {
		    bdbTxn.abort();
		}
	    }
	    if (found) {
		byte[] result = value.getData();
		if (result == null) {
		    result = NO_BYTES;
		}
		logger.log(Level.FINER,
			   "getClassInfo txn:{0} classId:{1,number,#} returns",
			   txn, classId);
		return result;
	    } else {
		ClassInfoNotFoundException e =
		    new ClassInfoNotFoundException(
			"No information found for class ID " + classId);
		if (logger.isLoggable(Level.FINER)) {
		    logger.logThrow(Level.FINER, e, operation + " throws");
		}
		throw e;
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINER, exception, operation);
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
	logger.log(Level.FINER, "prepare txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn);
	    txn.checkTimeout();
	    if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    if (txnInfo.modified) {
		byte[] tid = txn.getId();
		/*
		 * Berkeley DB requires transaction IDs to be at least 128
		 * bytes long.  -tjb@sun.com (11/07/2006)
		 */
		byte[] gid = new byte[128];
		/*
		 * The current transaction implementation uses 8 byte
		 * transaction IDs.  -tjb@sun.com (03/22/2007)
		 */
		assert tid.length < 128 : "Transaction ID is too long";
		System.arraycopy(tid, 0, gid, 128 - tid.length, tid.length);
		txnInfo.prepare(gid);
		txnInfo.prepared = true;
	    } else {
		/*
		 * Make sure to clear the transaction information, regardless
		 * of whether the Berkeley DB commit operation succeeds, since
		 * Berkeley DB doesn't permit operating on its transaction
		 * object after commit is called.
		 */
		try {
		    txnInfoTable.remove(txn);
		    txnInfo.commit();
		} finally {
		    decrementTxnCount();
		} 
	    }
	    boolean result = !txnInfo.modified;
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "prepare txn:{0} returns {1}", txn, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "prepare txn:" + txn);
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	logger.log(Level.FINER, "commit txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn);
	    if (!txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has not been prepared");
	    }
	    /*
	     * Make sure to clear the transaction information, regardless of
	     * whether the Berkeley DB commit operation succeeds, since
	     * Berkeley DB doesn't permit operating on its transaction object
	     * after commit is called.
	     */
	    txnInfoTable.remove(txn);
	    try {
		txnInfo.commit();
		logger.log(Level.FINER, "commit txn:{0} returns", txn);
		return;
	    } finally {
		decrementTxnCount();
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "commit txn:" + txn);
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
	logger.log(Level.FINER, "prepareAndCommit txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn);
	    txn.checkTimeout();
	    if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    /*
	     * Make sure to clear the transaction information, regardless of
	     * whether the Berkeley DB commit operation succeeds, since
	     * Berkeley DB doesn't permit operating on its transaction object
	     * after commit is called.
	     */
	    txnInfoTable.remove(txn);
	    try {
		txnInfo.commit();
		logger.log(
		    Level.FINER, "prepareAndCommit txn:{0} returns", txn);
		return;
	    } finally {
		decrementTxnCount();
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "prepareAndCommit txn:" + txn);
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	logger.log(Level.FINER, "abort txn:{0}", txn);
	Exception exception;
	try {
	    if (txn == null) {
		throw new NullPointerException("Transaction must not be null");
	    }
	    TxnInfo txnInfo = txnInfoTable.remove(txn);
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    }
	    try {
		txnInfo.abort();
		logger.log(Level.FINER, "abort txn:{0} returns", txn);
		return;
	    } finally {
		decrementTxnCount();
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "abort txn:" + txn);
    }

    /* -- Implements ProfileProducer -- */

    /** {@inheritDoc} */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        ProfileConsumer consumer =
            profileRegistrar.registerProfileProducer(this);

	createObjectOp = consumer.registerOperation("createObject");
	markForUpdateOp = consumer.registerOperation("markForUpdate");
	getObjectOp = consumer.registerOperation("getObject");
	getObjectForUpdateOp =
	    consumer.registerOperation("getObjectForUpdate");
	setObjectOp = consumer.registerOperation("setObject");
	setObjectsOp = consumer.registerOperation("setObjects");
	removeObjectOp = consumer.registerOperation("removeObject");
	getBindingOp = consumer.registerOperation("getBinding");
	setBindingOp = consumer.registerOperation("setBinding");
	removeBindingOp = consumer.registerOperation("removeBinding");
	nextBoundNameOp = consumer.registerOperation("nextBoundName");
	getClassIdOp = consumer.registerOperation("getClassId");
	getClassInfoOp = consumer.registerOperation("getClassInfo");

	readBytesCounter = consumer.registerCounter("readBytes", true);
	readObjectsCounter = consumer.registerCounter("readObjects", true);
	writtenBytesCounter = consumer.registerCounter("writtenBytes", true);
	writtenObjectsCounter =
	    consumer.registerCounter("writtenObjects", true);
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

    /**
     * Returns the next available object ID, and reserves the specified number
     * of IDs.
     *
     * @param	txn the transaction
     * @param	count the number of IDs to reserve
     * @return	the next available object ID
     */
    public long allocateObjects(Transaction txn, int count) {
	if (logger.isLoggable(Level.FINE)) {
	    logger.log(Level.FINE,
		       "allocateObjects txn:{0}, count:{1,number,#}",
		       txn, count);
	}
	Exception exception;
	try {
	    long result = getNextId(
		DataStoreHeader.NEXT_OBJ_ID_KEY, count, txn.getTimeout());
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINE,
			   "allocateObjects txn:{0}, count:{1,number,#} " +
			   "returns oid:{2,number,#}",
			   txn, count, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINE, exception,
	    "allocateObjects txn:" + txn + ", count:" + count);
    }

    /* -- Protected methods -- */

    /**
     * Returns the table that will be used to store transaction information.
     * Note that this method will be called during instance construction.
     *
     * @param	<T> the type of the information to be stored
     * @param	txnInfoType a class representing the type of the information to
     *		be stored
     * @return	the table
     */
    protected <T> TxnInfoTable<T> getTxnInfoTable(Class<T> txnInfoType) {
	return new ThreadTxnInfoTable<T>();
    }

    /**
     * Returns the next available transaction ID, and reserves the specified
     * number of IDs.
     *
     * @param	count the number of IDs to reserve
     * @param	timeout the transaction timeout in milliseconds
     * @return	the next available transaction ID
     */
    protected long getNextTxnId(int count, long timeout) {
	Exception exception;
	try {
	    return getNextId(DataStoreHeader.NEXT_TXN_ID_KEY, count, timeout);
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    null, Level.FINE, exception, "getNextTxnId count:" + count);
    }

    /**
     * Explicitly joins a new transaction.
     *
     * @param	txn the transaction to join
     */
    protected void joinNewTransaction(Transaction txn) {
	Exception exception;
	try {
	    joinTransaction(txn);
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "joinNewTransaction txn:" + txn);
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
     * Checks that the correct transaction is in progress, and join if none is
     * in progress.  The op argument, if non-null, specifies the operation
     * being performed under the specified transaction.
     */
    private TxnInfo checkTxn(Transaction txn, ProfileOperation op)
	throws DatabaseException
    {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = txnInfoTable.get(txn);
	if (txnInfo == null) {
	    txnInfo = joinTransaction(txn);
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has been prepared");
	}
        if (op != null) {
            op.report();
	}
	return txnInfo;
    }

    /**
     * Joins the specified transaction, checking first to see if the data store
     * is currently shutting down, and returning the new TxnInfo.
     */
    private TxnInfo joinTransaction(Transaction txn) throws DatabaseException {
	synchronized (txnCountLock) {
	    if (txnCount < 0) {
		throw new IllegalStateException("Service is shut down");
	    }
	    txnCount++;
	}
	boolean joined = false;
	try {
	    txn.join(this);
	    joined = true;
	} finally {
	    if (!joined) {
		decrementTxnCount();
	    }
	}
	TxnInfo txnInfo = new TxnInfo(txn, env);
	txnInfoTable.set(txn, txnInfo);
	return txnInfo;
    }

    /**
     * Checks that the correct transaction is in progress, throwing an
     * exception if the transaction has not been joined.  Checks if the store
     * is shutting down, but does not check the prepared state of the
     * transaction.
     */
    private TxnInfo checkTxnNoJoin(Transaction txn) {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = txnInfoTable.get(txn);
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (getTxnCount() < 0) {
	    throw new IllegalStateException("DataStore is shutting down");
	}
	return txnInfo;
    }

    /**
     * Returns the correct SGS exception for a Berkeley DB DatabaseException
     * thrown during an operation.  Throws an Error if recovery is needed.  The
     * txn argument, if non-null, is used to abort the transaction if a
     * TransactionAbortedException is going to be thrown.  The level argument
     * is used to log the exception.  The operation argument will be included
     * in newly created exceptions and the log, and should describe the
     * operation that was underway when the exception was thrown.  The supplied
     * exception may also be a RuntimeException, which will be logged and
     * returned.
     */
    private RuntimeException convertException(
	Transaction txn, Level level, Exception e, String operation)
    {
	RuntimeException re;
	/*
	 * Don't include DatabaseExceptions as the cause because, even though
	 * that class implements Serializable, the Environment object
	 * optionally contained within them is not.  -tjb@sun.com (01/19/2007)
	 */
	if (e instanceof LockNotGrantedException) {
	    re = new TransactionTimeoutException(
		operation + " failed due to timeout: " + e);
	} else if (e instanceof DeadlockException) {
	    re = new TransactionConflictException(
		operation + " failed due to deadlock: " + e);
	} else if (e instanceof RunRecoveryException) {
	    /*
	     * It is tricky to clean up the data structures in this instance in
	     * order to reopen the Berkeley DB databases, because it's hard to
	     * know when they are no longer in use.  It's OK to catch this
	     * Error and create a new DataStoreImpl instance, but this instance
	     * is dead.  -tjb@sun.com (10/19/2006)
	     */
	    Error error = new Error(
		operation + " failed: " +
		"Database requires recovery -- need to restart the server " +
		"or create a new instance of DataStoreImpl: " + e.getMessage(),
		e);
	    logger.logThrow(Level.SEVERE, error, "{0} throws", operation);
	    throw error;
	} else if (e instanceof TransactionTimeoutException) {
	    /* Include the operation in the message */
	    re = new TransactionTimeoutException(
		operation + " failed: " + e.getMessage(), e);
	} else if (e instanceof DatabaseException) {
	    re = new DataStoreException(
		operation + " failed: " + e.getMessage(), e);
	} else if (e instanceof RuntimeException) {
	    re = (RuntimeException) e;
	} else {
	    throw new DataStoreException("Unexpected exception: " + e, e);
	}
	/*
	 * If we're throwing an exception saying that the transaction was
	 * aborted, then make sure to abort the transaction now.
	 */
	if (re instanceof TransactionAbortedException && txn != null) {
	    txn.abort(re);
	}
	logger.logThrow(Level.FINEST, re, "{0} throws", operation);
	return re;
    }

    /** Returns the current transaction count. */
    private int getTxnCount() {
	synchronized (txnCountLock) {
	    return txnCount;
	}
    }

    /**
     * Decrements the current transaction count.  If the argument is not null,
     * tallies the operations that were recorded for the transaction.
     */
    private void decrementTxnCount() {
	synchronized (txnCountLock) {
	    txnCount--;
	    if (txnCount <= 0) {
		txnCountLock.notifyAll();
	    }
	}
    }

    /**
     * Returns the next available ID stored under the specified key, and
     * increments the stored value by the specified amount.  Uses the specified
     * timeout when creating a BDB transaction.
     */
    private long getNextId(long key, int blockSize, long timeout)
	throws DatabaseException
    {
	assert blockSize > 0;
	com.sleepycat.db.Transaction bdbTxn = createBdbTxn(env, timeout);
	boolean done = false;
	try {
	    long id = DataStoreHeader.getNextId(
		key, infoDb, bdbTxn, blockSize);
	    done = true;
	    bdbTxn.commit();
	    return id;
	} finally {
	    if (!done) {
		bdbTxn.abort();
	    }
	}
    }

    /** Converts a database entry key to a class ID. */
    private static int getClassIdFromKey(DatabaseEntry key) {
	TupleInput in = new TupleInput(key.getData());
	byte first = in.readByte();
	assert first == DataStoreHeader.CLASS_ID_PREFIX;
	return in.readInt();
    }

    /** Converts a class ID to a database entry key. */
    private static void getKeyFromClassId(int classId, DatabaseEntry key) {
	TupleOutput out = new TupleOutput(new byte[5]);
	out.writeByte(DataStoreHeader.CLASS_ID_PREFIX);
	out.writeInt(classId);
	key.setData(out.getBufferBytes());
    }

    /** Converts class information to a database entry key. */
    private DatabaseEntry getKeyFromClassInfo(byte[] classInfo) {
	byte[] keyBytes = new byte[1 + SHA1_SIZE];
	keyBytes[0] = DataStoreHeader.CLASS_HASH_PREFIX;
	MessageDigest md = messageDigest.get();
	try {
	    md.update(classInfo);
	    int numBytes = md.digest(keyBytes, 1, SHA1_SIZE);
	    assert numBytes == SHA1_SIZE;
	    return new DatabaseEntry(keyBytes);
	} catch (DigestException e) {
	    throw new AssertionError(e);
	}
    }

    /** Gets a value from the database, returning whether it was found. */
    private static boolean get(Database db,
			       com.sleepycat.db.Transaction bdbTxn,
			       DatabaseEntry key,
			       DatabaseEntry value,
			       String operation)
	throws DatabaseException
    {
	return checkStatusFound(
	    db.get(bdbTxn, key, value, null), operation);
    }

    /**
     * Puts a value into the database, throwing an exception if the key was
     * already present.
     */
    private static void putNoOverwrite(Database db,
				       com.sleepycat.db.Transaction bdbTxn,
				       DatabaseEntry key,
				       DatabaseEntry value,
				       String operation)
	throws DatabaseException
    {
	checkStatus(db.putNoOverwrite(bdbTxn, key, value), operation);
    }

    /**
     * Checks that the status was SUCCESS or NOTFOUND, returning true for
     * SUCCESS.
     */
    private static boolean checkStatusFound(OperationStatus status,
					    String operation)
    {
	if (status == OperationStatus.NOTFOUND) {
	    return false;
	} else {
	    checkStatus(status, operation);
	    return true;
	}
    }

    /** Checks that the status was SUCCESS. */
    private static void checkStatus(OperationStatus status,
				    String operation)
    {
	if (status != OperationStatus.SUCCESS) {
	    throw new DataStoreException(operation + " failed: " + status);
	}
    }	

    /**
     * Creates a Berkeley DB transaction with the specified timeout, measured
     * in milliseconds.
     */
    private static com.sleepycat.db.Transaction createBdbTxn(
	Environment env, long timeout)
	throws DatabaseException
    {
	assert timeout > 0;
	com.sleepycat.db.Transaction bdbTxn = env.beginTransaction(null, null);
	long timeoutMicros = 1000 * timeout;
	if (timeoutMicros < 0) {
	    /* Berkeley DB treats a zero timeout as unlimited */
	    timeoutMicros = 0;
	}
	bdbTxn.setLockTimeout(timeoutMicros);
	bdbTxn.setTxnTimeout(timeoutMicros);
	return bdbTxn;
    }
}
