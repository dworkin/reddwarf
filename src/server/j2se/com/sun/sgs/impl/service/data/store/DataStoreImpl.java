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

import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.db.DataEncoding;
import com.sun.sgs.impl.service.data.store.db.DbCursor;
import com.sun.sgs.impl.service.data.store.db.DbDatabase;
import com.sun.sgs.impl.service.data.store.db.DbDatabaseException;
import com.sun.sgs.impl.service.data.store.db.DbEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbEnvironmentFactory;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileCounter;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.profile.ProfileProducer;
import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.profile.ProfileSample;
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
 * Provides an implementation of <code>DataStore</code> based on the database
 * interface layer defined in the {@link
 * com.sun.sgs.impl.service.data.store.db} package. <p>
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
 * <dt> <i>Property:</i> <b>{@value #ALLOCATION_BLOCK_SIZE_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_ALLOCATION_BLOCK_SIZE}
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
 * The constructor also passes the properties to the {@link BdbEnvironment}
 * constructor, which supports additional properties. <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.DataStoreImpl</code> to log information
 * at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Initialization failures
 * <li> {@link Level#CONFIG CONFIG} - Constructor properties, data store
 *	headers
 * <li> {@link Level#FINE FINE} - Allocating blocks of object IDs
 * <li> {@link Level#FINER FINER} - Transaction operations
 * <li> {@link Level#FINEST FINEST} - Name and object operations
 * </ul> <p>
 *
 */
public class DataStoreImpl
    implements DataStore, TransactionParticipant, ProfileProducer
{
    /** The name of this class. */
    private static final String CLASSNAME =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl";

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    public static final String DIRECTORY_PROPERTY = CLASSNAME + ".directory";

    /** The default directory for database files from the app root. */
    private static final String DEFAULT_DIRECTORY = "dsdb";

    /**
     * The property that specifies the number of object IDs to allocate at one
     * time.
     */
    public static final String ALLOCATION_BLOCK_SIZE_PROPERTY =
	CLASSNAME + ".allocation.block.size";

    /** The default for the number of object IDs to allocate at one time. */
    public static final int DEFAULT_ALLOCATION_BLOCK_SIZE = 100;

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** The number of bytes in a SHA-1 message digest. */
    private static final int SHA1_SIZE = 20;

    /** The directory in which to store database files. */
    private final String directory;

    /** The number of object IDs to allocate at one time. */
    private final int allocationBlockSize;

    /** Stores information about transactions. */
    private final TxnInfoTable<TxnInfo> txnInfoTable;

    /** The database environment. */
    private final DbEnvironment env;

    /**
     * The database that holds version and next object ID information.  This
     * information is stored in a separate database to avoid concurrency
     * conflicts between the object ID and other data.
     */
    private final DbDatabase infoDb;

    /** The database that stores class information. */
    private final DbDatabase classesDb;

    /** The database that maps object IDs to object bytes. */
    private final DbDatabase oidsDb;

    /** The database that maps name bindings to object IDs. */
    private final DbDatabase namesDb;

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
    private ProfileOperation nextObjectIdOp = null;

    /**
     * The counters and samples used for profile reporting, which track the
     * bytes read and written within a task, and how many objects were read
     * and written
     */
    private ProfileCounter readBytesCounter = null;
    private ProfileCounter readObjectsCounter = null;
    private ProfileCounter writtenBytesCounter = null;
    private ProfileCounter writtenObjectsCounter = null;
    private ProfileSample readBytesSample = null;
    private ProfileSample writtenBytesSample = null;
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

	/** The associated database transaction. */
	final DbTransaction dbTxn;

	/** Whether preparation of the transaction has started. */
	boolean prepared;

	/** Whether any changes have been made in this transaction. */
	boolean modified;

	/** The currently open cursor over the names database, or null. */
	private DbCursor namesCursor;

	/** The last key returned by the namesCursor or null. */
	private String lastNamesCursorKey;

	/** The currently open cursor over the oids database, or null. */
	private DbCursor oidsCursor;

	/** The last key returned by the oidsCursor or -1. */
	private long lastOidsCursorKey = -1;

	TxnInfo(Transaction txn, DbEnvironment env) {
	    dbTxn = env.beginTransaction(txn.getTimeout());
	}

	/** Prepares the transaction, first closing the cursors, if present. */
	void prepare(byte[] gid) {
	    maybeCloseCursors();
	    dbTxn.prepare(gid);
	}

	/**
	 * Commits the transaction, first closing the cursors, if present, and
	 * returning the operations count for this transaction.
	 */
	void commit() {
	    maybeCloseCursors();
	    dbTxn.commit();
	}

	/**
	 * Aborts the transaction, first closing the cursors, if present, and
	 * returning the operations count for this transaction.
	 */
	void abort() {
	    maybeCloseCursors();
	    dbTxn.abort();
	}

	/** Returns the next name in the names database. */
	String nextName(String name, DbDatabase names) {
	    if (namesCursor == null) {
		namesCursor = names.openCursor(dbTxn);
	    }
	    if (name == null) {
		lastNamesCursorKey = namesCursor.findFirst()
		    ? DataEncoding.decodeString(namesCursor.getKey()) : null;
	    } else {
		boolean matchesLast = name.equals(lastNamesCursorKey);
		if (!matchesLast) {
		    /*
		     * The name specified was not the last key returned, so
		     * search for the specified name
		     */
		    lastNamesCursorKey =
			namesCursor.findNext(DataEncoding.encodeString(name))
			? DataEncoding.decodeString(namesCursor.getKey())
			: null;
		    /* Record if we found an exact match */
		    matchesLast = name.equals(lastNamesCursorKey);
		}
		if (matchesLast) {
		    /* The last key was an exact match, so find the next one */
		    lastNamesCursorKey = namesCursor.findNext()
			? DataEncoding.decodeString(namesCursor.getKey())
			: null;
		}
	    }
	    return lastNamesCursorKey;
	}

	/** Returns the next object ID in the oids database. */
	long nextObjectId(long oid, DbDatabase oids) {
	    if (oidsCursor == null) {
		oidsCursor = oids.openCursor(dbTxn);
	    }
	    if (oid == -1) {
		lastOidsCursorKey = oidsCursor.findFirst()
		    ? DataEncoding.decodeLong(oidsCursor.getKey()) : -1;
	    } else {
		boolean matchesLast = (oid == lastOidsCursorKey);
		if (!matchesLast) {
		    /*
		     * The OID specified was not the last key returned, so
		     * search for the specified OID
		     */
		    lastOidsCursorKey =
			oidsCursor.findNext(DataEncoding.encodeLong(oid))
			? DataEncoding.decodeLong(oidsCursor.getKey()) : -1;
		    /* Record if we found an exact match */
		    matchesLast = (oid == lastOidsCursorKey);
		}
		if (matchesLast) {
		    /* The last key was an exact match, so find the next one */
		    lastOidsCursorKey = oidsCursor.findNext()
			? DataEncoding.decodeLong(oidsCursor.getKey()) : -1;
		}
	    }
	    return lastOidsCursorKey;
	}

	/**
	 * Close the cursors if they are open.  Always null the cursor fields,
	 * since the Berkeley DB API doesn't permit closing a cursor after an
	 * attempt to close it.
	 */
	private void maybeCloseCursors() {
	    if (namesCursor != null) {
		DbCursor c = namesCursor;
		namesCursor = null;
		c.close();
	    }
	    if (oidsCursor != null) {
		DbCursor c = oidsCursor;
		oidsCursor = null;
		c.close();
	    }
	}
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

    /**
     * Stores information about the databases that constitute the data
     * store.
     */
    private static class Databases {
	private DbDatabase info, classes, oids, names;
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
	txnInfoTable = getTxnInfoTable(TxnInfo.class);
	DbTransaction dbTxn = null;
	boolean done = false;
	try {
	    env = DbEnvironmentFactory.getEnvironment(
		directory, properties, scheduler);
	    dbTxn = env.beginTransaction(Long.MAX_VALUE);
	    Databases dbs = getDatabases(dbTxn);
	    infoDb = dbs.info;
	    classesDb = dbs.classes;
	    oidsDb = dbs.oids;
	    namesDb = dbs.names;
	    done = true;
	    dbTxn.commit();
	} catch (RuntimeException e) { 
	    throw convertException(
		null, Level.SEVERE, e, "DataStore initialization");
	} catch (Error e) {
	    logger.logThrow(
		Level.SEVERE, e, "DataStore initialization failed");
	    throw e;
	} finally {
	    if (dbTxn != null && !done) {
		try {
		    dbTxn.abort();
		} catch (RuntimeException e) {
		    logger.logThrow(Level.FINE, e, "Exception during abort");
		}
	    }
	}
    }

    /**
     * Opens or creates the Berkeley DB databases associated with this data
     * store.
     */
    private Databases getDatabases(DbTransaction dbTxn) {
	Databases dbs = new Databases();
	boolean create = false;
	try {
	    dbs.info = env.openDatabase(dbTxn, "info", false);
	    int minorVersion = DataStoreHeader.verify(dbs.info, dbTxn);
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(Level.CONFIG, "Found existing header {0}",
			   DataStoreHeader.headerString(minorVersion));
	    }
	} catch (FileNotFoundException e) {
	    try {
		dbs.info = env.openDatabase(dbTxn, "info", true);
	    } catch (FileNotFoundException e2) {
		throw new DataStoreException(
		    "Problem creating database: " + e2.getMessage(), e2);
	    }
	    DataStoreHeader.create(dbs.info, dbTxn);
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(Level.CONFIG, "Created new header {0}",
			   DataStoreHeader.headerString());
	    }
	    create = true;
	}
	try {
	    dbs.classes = env.openDatabase(dbTxn, "classes", create);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"Classes database not found: " + e.getMessage(), e);
	}
	try {
	    dbs.oids = env.openDatabase(dbTxn, "oids", create);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"Oids database not found: " + e.getMessage(), e);
	}
	try {
	    dbs.names = env.openDatabase(dbTxn, "names", create);
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
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINEST, e, "createObject txn:" + txn);
	}
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
	try {
	    getObjectInternal(txn, oid, true, markForUpdateOp);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "markForUpdate txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINEST, e,
		"markForUpdate txn:" + txn + ", oid:" + oid);
	}
    }

    /** {@inheritDoc} */
    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2}",
		       txn, oid, forUpdate);
	}
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
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINEST, e,
				   "getObject txn:" + txn + ", oid:" + oid +
				   ", forUpdate:" + forUpdate);
	}
    }

    /** Implement getObject, without logging. */
    private byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate, ProfileOperation op)
    {
	checkId(oid);
	TxnInfo txnInfo = checkTxn(txn, op);
	byte[] result = oidsDb.get(
	    txnInfo.dbTxn, DataEncoding.encodeLong(oid), forUpdate);
	if (result == null) {
	    throw new ObjectNotFoundException("Object not found: " + oid);
	}
	if (readBytesCounter != null) {
	    readBytesCounter.incrementCount(result.length);
	    readBytesSample.addSample(result.length);
	    readObjectsCounter.incrementCount();
	}
	return result;
    }

    /** {@inheritDoc} */
    public void setObject(Transaction txn, long oid, byte[] data) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "setObject txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	try {
	    checkId(oid);
	    if (data == null) {
		throw new NullPointerException("The data must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, setObjectOp);
	    oidsDb.put(txnInfo.dbTxn, DataEncoding.encodeLong(oid), data);
	    if (writtenBytesCounter != null) {
		writtenBytesCounter.incrementCount(data.length);
		writtenObjectsCounter.incrementCount();
		writtenBytesSample.addSample(data.length);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "setObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINEST, e,
				   "setObject txn:" + txn + ", oid:" + oid);
	}
    }

    /** {@inheritDoc} */
    public void setObjects(Transaction txn, long[] oids, byte[][] dataArray) {
	logger.log(Level.FINEST, "setObjects txn:{0}", txn);
	long oid = -1;
	boolean oidSet = false;
	try {
	    TxnInfo txnInfo = checkTxn(txn, setObjectsOp);
	    int len = oids.length;
	    if (len != dataArray.length) {
		throw new IllegalArgumentException(
		    "The oids and dataArray must be the same length");
	    }
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
		oidsDb.put(txnInfo.dbTxn, DataEncoding.encodeLong(oid), data);
		if (writtenBytesCounter != null) {
		    writtenBytesCounter.incrementCount(data.length);
		    writtenObjectsCounter.incrementCount();
		    writtenBytesSample.addSample(data.length);
		}
	    }
	    txnInfo.modified = true;
	    logger.log(Level.FINEST, "setObjects txn:{0} returns", txn);
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINEST, e,
		"setObject txn:" + txn + (oidSet ? ", oid:" + oid : ""));
	}
    }

    /** {@inheritDoc} */
    public void removeObject(Transaction txn, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "removeObject txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	try {
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn, removeObjectOp);
	    boolean found = oidsDb.delete(
		txnInfo.dbTxn, DataEncoding.encodeLong(oid));
	    if (!found) {
		throw new ObjectNotFoundException("Object not found: " + oid);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "removeObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINEST, e,
				   "removeObject txn:" + txn + ", oid:" + oid);
	}
    }

    /** {@inheritDoc} */
    public long getBinding(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "getBinding txn:{0}, name:{1}", txn, name);
	}
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, getBindingOp);
	    byte[] value = namesDb.get(
		txnInfo.dbTxn, DataEncoding.encodeString(name), false);
	    if (value == null) {
		throw new NameNotBoundException("Name not bound: " + name);
	    }
	    long result = DataEncoding.decodeLong(value);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "getBinding txn:{0}, name:{1} returns oid:{2,number,#}",
		    txn, name, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINEST, e,
				   "getBinding txn:" + txn + ", name:" + name);
	}
    }

    /** {@inheritDoc} */
    public void setBinding(Transaction txn, String name, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "setBinding txn:{0}, name:{1}, oid:{2,number,#}",
		txn, name, oid);
	}
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn, setBindingOp);
	    namesDb.put(txnInfo.dbTxn, DataEncoding.encodeString(name),
			DataEncoding.encodeLong(oid));
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "setBinding txn:{0}, name:{1}, oid:{2,number,#} returns",
		    txn, name, oid);
	    }
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINEST, e,
		"setBinding txn:" + txn + ", name:" + name + ", oid:" + oid);
	}
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
	    boolean found = namesDb.delete(
		txnInfo.dbTxn, DataEncoding.encodeString(name));
	    if (!found) {
		throw new NameNotBoundException("Name not bound: " + name);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "removeBinding txn:{0}, name:{1} returns",
		    txn, name);
	    }
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINEST, e,
		"removeBinding txn:" + txn + ", name:" + name);
	}
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
	try {
	    TxnInfo txnInfo = checkTxn(txn, nextBoundNameOp);
	    String result = txnInfo.nextName(name, namesDb);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "nextBoundName txn:{0}, name:{1} returns {2}",
			   txn, name, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINEST, e,
		"nextBoundName txn:" + txn + ", name:" + name);
	}
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	logger.log(Level.FINER, "shutdown");
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
	} catch (RuntimeException e) {
	    throw convertException(null, Level.FINER, e, "shutdown");
	}
    }

    /** {@inheritDoc} */
    public int getClassId(Transaction txn, byte[] classInfo) {
	logger.log(Level.FINER, "getClassId txn:{0}", txn);
	String operation = "getClassId txn:" + txn;
	try {
	    checkTxn(txn, getClassIdOp);
	    if (classInfo == null) {
		throw new NullPointerException(
		    "The classInfo argument must not be null");
	    }
	    byte[] hashKey = getKeyFromClassInfo(classInfo);
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
	    DbTransaction dbTxn = env.beginTransaction(txn.getTimeout());
	    try {
		byte[] hashValue = classesDb.get(dbTxn, hashKey, false);
		if (hashValue != null) {
		    result = DataEncoding.decodeInt(hashValue);
		} else {
		    DbCursor cursor = classesDb.openCursor(dbTxn);
		    try {
			result = cursor.findLast(true)
			    ? getClassIdFromKey(cursor.getKey()) + 1 : 1; 
			byte[] idKey = getKeyFromClassId(result);
			boolean success =
			    cursor.putNoOverwrite(idKey, classInfo);
			if (!success) {
			    throw new DataStoreException(
				"Class ID key already present");
			}
		    } finally {
			cursor.close();
		    }
		    boolean success = classesDb.putNoOverwrite(
			dbTxn, hashKey, DataEncoding.encodeInt(result));
		    if (!success) {
			throw new DataStoreException(
			    "Class hash already present");
		    }
		}
		done = true;
		dbTxn.commit();
	    } finally {
		if (!done) {
		    dbTxn.abort();
		}
	    }
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "getClassId txn:{0} returns {1}",
			   txn, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINER, e, operation);
	}
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
	    byte[] key = getKeyFromClassId(classId);
	    byte[] result;
	    boolean done = false;
	    DbTransaction dbTxn = env.beginTransaction(txn.getTimeout());
	    try {
		result = classesDb.get(dbTxn, key, false);
		done = true;
		dbTxn.commit();
	    } finally {
		if (!done) {
		    dbTxn.abort();
		}
	    }
	    if (result != null) {
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
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINER, e, operation);
	}
    }

    /** {@inheritDoc} */
    public long nextObjectId(Transaction txn, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "nextObjectId txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	try {
	    if (oid < -1) {
		throw new IllegalArgumentException(
		    "Invalid object ID: " + oid);
	    }
	    TxnInfo txnInfo = checkTxn(txn, nextObjectIdOp);
	    long result = txnInfo.nextObjectId(oid, oidsDb);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "nextObjectId txn:{0}, oid:{1,number,#} " +
			   "returns oid:{2,number,#}",
			   txn, oid, result);
	    }
	    return result;
	} catch (RuntimeException e) {
	    throw convertException(txn, Level.FINEST, e,
				   "nextObjectId txn:" + txn + ", oid:" + oid);
	}
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
	logger.log(Level.FINER, "prepare txn:{0}", txn);
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
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINER, e, "prepare txn:" + txn);
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	logger.log(Level.FINER, "commit txn:{0}", txn);
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
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINER, e, "commit txn:" + txn);
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
	logger.log(Level.FINER, "prepareAndCommit txn:{0}", txn);
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
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINER, e, "prepareAndCommit txn:" + txn);
	}
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	logger.log(Level.FINER, "abort txn:{0}", txn);
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
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINER, e, "abort txn:" + txn);
	}
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
	nextObjectIdOp = consumer.registerOperation("nextObjectIdOp");

	readBytesCounter = consumer.registerCounter("readBytes", true);
	readObjectsCounter = consumer.registerCounter("readObjects", true);
	writtenBytesCounter = consumer.registerCounter("writtenBytes", true);
	writtenObjectsCounter =
	    consumer.registerCounter("writtenObjects", true);
	readBytesSample = consumer.registerSampleSource("readBytes", true,
							Integer.MAX_VALUE);
	writtenBytesSample = consumer.registerSampleSource("writtenBytes", true,
							   Integer.MAX_VALUE);

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
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINE, e,
		"allocateObjects txn:" + txn + ", count:" + count);
	}
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
	try {
	    return getNextId(DataStoreHeader.NEXT_TXN_ID_KEY, count, timeout);
	} catch (RuntimeException e) {
	    throw convertException(
		null, Level.FINE, e, "getNextTxnId count:" + count);
	}
    }

    /**
     * Explicitly joins a new transaction.
     *
     * @param	txn the transaction to join
     */
    protected void joinNewTransaction(Transaction txn) {
	try {
	    joinTransaction(txn);
	} catch (RuntimeException e) {
	    throw convertException(
		txn, Level.FINER, e, "joinNewTransaction txn:" + txn);
	}
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
    private TxnInfo checkTxn(Transaction txn, ProfileOperation op) {
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
    private TxnInfo joinTransaction(Transaction txn) {
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
     * Returns the correct exception for an exception thrown during an
     * operation.  The txn argument, if non-null, is used to abort the
     * transaction if a TransactionAbortedException is going to be thrown.  The
     * level argument is used to log the exception.  The operation argument
     * will be included in newly created exceptions and the log, and should
     * describe the operation that was underway when the exception was thrown.
     */
    private RuntimeException convertException(
	Transaction txn, Level level, RuntimeException e, String operation)
    {
	if (e instanceof TransactionTimeoutException) {
	    /* Include the operation in the message */
	    e = new TransactionTimeoutException(
		operation + " failed: " + e.getMessage(), e);
	} else if (e instanceof TransactionConflictException) {
	    e = new TransactionConflictException(
		operation + " failed: " + e.getMessage(), e);
	} else if (e instanceof DbDatabaseException) {
	    e = new DataStoreException(
		operation + " failed: " + e.getMessage(), e);
	}
	/*
	 * If we're throwing an exception saying that the transaction was
	 * aborted, then make sure to abort the transaction now.
	 */
	if (e instanceof TransactionAbortedException &&
	    txn != null &&
	    !txn.isAborted())
	{
	    txn.abort(e);
	}
	logger.logThrow(Level.FINEST, e, "{0} throws", operation);
	return e;
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
     * timeout when creating a DB transaction.
     */
    private long getNextId(long key, int blockSize, long timeout) {
	assert blockSize > 0;
	DbTransaction dbTxn = env.beginTransaction(timeout);
	boolean done = false;
	try {
	    long id = DataStoreHeader.getNextId(
		key, infoDb, dbTxn, blockSize);
	    done = true;
	    dbTxn.commit();
	    return id;
	} finally {
	    if (!done) {
		dbTxn.abort();
	    }
	}
    }

    /** Converts a database key to a class ID. */
    private static int getClassIdFromKey(byte[] key) {
	assert key[0] == DataStoreHeader.CLASS_ID_PREFIX;
	return DataEncoding.decodeInt(key, 1);
    }

    /** Converts a class ID to a database key. */
    private static byte[] getKeyFromClassId(int classId) {
	byte[] key = new byte[5];
	key[0] = DataStoreHeader.CLASS_ID_PREFIX;
	DataEncoding.encodeInt(classId, key, 1);
	return key;
    }

    /** Converts class information to a database key. */
    private byte[] getKeyFromClassInfo(byte[] classInfo) {
	byte[] keyBytes = new byte[1 + SHA1_SIZE];
	keyBytes[0] = DataStoreHeader.CLASS_HASH_PREFIX;
	MessageDigest md = messageDigest.get();
	try {
	    md.update(classInfo);
	    int numBytes = md.digest(keyBytes, 1, SHA1_SIZE);
	    assert numBytes == SHA1_SIZE;
	    return keyBytes;
	} catch (DigestException e) {
	    throw new AssertionError(e);
	}
    }
}
