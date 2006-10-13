package com.sun.sgs.impl.service.data.store;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseConfig;
import com.sleepycat.db.DatabaseEntry;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.DeadlockException;
import com.sleepycat.db.Environment;
import com.sleepycat.db.EnvironmentConfig;
import com.sleepycat.db.LockDetectMode;
import com.sleepycat.db.LockMode;
import com.sleepycat.db.OperationStatus;
import com.sleepycat.db.TransactionConfig;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.impl.service.data.Util;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/*
 * XXX: Implement recovery
 * XXX: Join
 * XXX: Close
 */

public class DataStoreImpl implements DataStore, TransactionParticipant {

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

    /** The directory in which to store database files. */
    private final String directory;

    /** The number of object IDs to allocate at one time. */
    private final int allocationBlockSize;

    /** The Berkeley DB environment. */
    private final Environment env;

    /** The Berkeley DB database that maps object IDs to object bytes. */
    private final Database ids;

    /** The Berkeley DB database that maps name bindings to object IDs. */
    private final Database names;

    /**
     * Maps a transaction to information about the transaction.  Callers should
     * synchronize on the map when accessing it.
     */
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

	TxnInfo(Transaction txn, Environment env) throws DatabaseException {
	    this.txn = txn;
	    bdbTxn = env.beginTransaction(null, null);
	}
    }

    public DataStoreImpl(Properties properties) {
	directory = properties.getProperty(DIRECTORY_PROPERTY);
	if (directory == null) {
	    throw new IllegalArgumentException("Directory must be specified");
	}
	allocationBlockSize = Util.getIntProperty(
	    properties, ALLOCATION_BLOCK_SIZE_PROPERTY,
	    DEFAULT_ALLOCATION_BLOCK_SIZE);
	com.sleepycat.db.Transaction bdbTxn = null;
	boolean done = false;
	try {
	    env = getEnvironment(properties);
	    bdbTxn = env.beginTransaction(null, null);
	    DatabaseConfig createConfig = new DatabaseConfig();
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
		    throw new AssertionError();
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
	    done = true;
	    bdbTxn.commit();
	} catch (DatabaseException e) {
	    throw new DataStoreException(
		"Problem initializing DataStore: " + e.getMessage(), e);
	} finally {
	    if (bdbTxn != null && !done) {
		try {
		    bdbTxn.abort();
		} catch (DatabaseException e) {
		    // XXX: Log
		}
	    }
	}
    }

    private Environment getEnvironment(Properties properties)
	throws DatabaseException
    {
        EnvironmentConfig config = new EnvironmentConfig();
	config.setLockTimeout(
	    Util.getLongProperty(
		properties, TXN_TIMEOUT_PROPERTY, DEFAULT_TXN_TIMEOUT));
        config.setAllowCreate(true);
        config.setInitializeCache(true);
        config.setInitializeLocking(true);
        config.setInitializeLogging(true);
        config.setLockDetectMode(LockDetectMode.MINWRITE);
        config.setRunRecovery(true);
        config.setTransactional(true);
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
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	}
	throw new AssertionError();
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
	    return value.getData();
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	}
	throw new AssertionError();
    }

    public void setObject(Transaction txn, long id, byte[] data) {
	checkId(id);
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
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
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
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	}
    }

    public long getBinding(Transaction txn, String name) {
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
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	}
	throw new AssertionError();
    }

    public void setBinding(Transaction txn, String name, long id) {
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
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	}
    }

    public void removeBinding(Transaction txn, String name) {
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
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	}
    }

    /* -- Implement TransactionParticipant -- */

    public String getIdentifier() {
	return toString();
    }

    public boolean prepare(Transaction txn) {
	TxnInfo txnInfo;
	synchronized (txnInfoMap) {
	    txnInfo = txnInfoMap.get(txn);
	}
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has already been prepared");
	} else {
	    txnInfo.prepared = true;
	}
	boolean done = false;
	try {
	    txnInfo.bdbTxn.prepare(txn.getId());
	    done = true;
	} catch (DeadlockException e) {
	    handleDeadlockException(e, txn);
	} catch (DatabaseException e) {
	    handleUnexpectedException(e, txn);
	} finally {
	    if (!done) {
		txnInfo.prepared = false;
	    }
	}
	return false;
    }

    public void commit(Transaction txn) {
	TxnInfo txnInfo;
	synchronized (txnInfoMap) {
	    txnInfo = txnInfoMap.get(txn);
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    } else if (!txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has not been prepared");
	    } else {
		txnInfoMap.remove(txn);
	    }
	}
	try {
	    txnInfo.bdbTxn.commit();
	} catch (DeadlockException e) {
	    throw new TransactionConflictException(e.getMessage(), e);
	} catch (DatabaseException e) {
	    throw new DataStoreException(e.getMessage(), e);
	} catch (RuntimeException e) {
	    throw new DataStoreException(e.getMessage(), e);
	}
    }

    public void prepareAndCommit(Transaction txn) {
	TxnInfo txnInfo;
	synchronized (txnInfoMap) {
	    txnInfo = txnInfoMap.get(txn);
	    if (txnInfo == null) {
		throw new IllegalStateException("Transaction is not active");
	    } else if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    } else {
		txnInfoMap.remove(txn);
	    }
	}
	try {
	    txnInfo.bdbTxn.commit();
	} catch (DeadlockException e) {
	    throw new TransactionConflictException(e.getMessage(), e);
	} catch (DatabaseException e) {
	    throw new DataStoreException(e.getMessage(), e);
	} catch (RuntimeException e) {
	    throw new DataStoreException(e.getMessage(), e);
	}
    }

    public void abort(Transaction txn) {
	TxnInfo txnInfo;
	synchronized (txnInfoMap) {
	    txnInfo = txnInfoMap.remove(txn);
	}
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	}
	try {
	    txnInfo.bdbTxn.abort();
	} catch (DeadlockException e) {
	    throw new TransactionConflictException(e.getMessage(), e);
	} catch (DatabaseException e) {
	    throw new DataStoreException(e.getMessage(), e);
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

    /**
     * Checks that the transaction is in progress for an operation other than
     * prepare or commit.
     */
    private TxnInfo checkTxn(Transaction txn) throws DatabaseException {
	synchronized (txnInfoMap) {
	    TxnInfo txnInfo = txnInfoMap.get(txn);
	    if (txnInfo == null) {
		txnInfo = new TxnInfo(txn, env);
		txnInfoMap.put(txn, txnInfo);
	    } else if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has been prepared");
	    }
	    return txnInfo;
	}
    }

    private long createObjectInternal() throws DatabaseException {
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

    /** Throws a TransactionConflictException. */
    private void handleDeadlockException(
	DeadlockException e, Transaction txn)
    {
	throw new TransactionConflictException(e.getMessage(), e);
    }

    /** Throws a DataStoreException. */
    private void handleUnexpectedException(
	DatabaseException e, Transaction txn)
    {
	throw new DataStoreException(e.getMessage(), e);
    }
}
