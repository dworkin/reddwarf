/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import static com.sleepycat.je.OperationStatus.KEYEXIST;
import static com.sleepycat.je.OperationStatus.NOTFOUND;
import static com.sleepycat.je.OperationStatus.SUCCESS;
import com.sleepycat.je.Transaction;
import com.sun.sgs.impl.service.data.store.db.DbCursor;
import com.sun.sgs.impl.service.data.store.db.DbDatabase;
import com.sun.sgs.impl.service.data.store.db.DbDatabaseException;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import java.io.FileNotFoundException;

/** Provides a database implementation using Berkeley DB, Java Edition. */
public class BdbJeDatabase implements DbDatabase {

    /** An empty array returned when Berkeley DB returns null for a value. */
    private static final byte[] NO_BYTES = { };

    /** The database configuration when opening an existing database. */
    private static final DatabaseConfig openConfig = new DatabaseConfig();
    static {
	openConfig.setTransactional(true);
    }

    /** The database configuration when creating a new database. */
    private static final DatabaseConfig createConfig = new DatabaseConfig();
    static {
	createConfig.setTransactional(true);
	createConfig.setAllowCreate(true);
    }

    /** The Berkeley DB database. */
    private final Database db;

    /**
     * Creates an instance of this class.
     *
     * @param	env the Berkeley DB environment
     * @param	txn the Berkeley DB transaction
     * @param	fileName the name of the file containing the database
     * @param	create whether to create the database if it does not exist
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    BdbJeDatabase(
	Environment env, Transaction txn, String fileName, boolean create)
	throws FileNotFoundException
    {
	try {
	    db = env.openDatabase(
		txn, fileName, create ? createConfig : openConfig);
	} catch (DatabaseNotFoundException e) {
	    throw (FileNotFoundException)
		new FileNotFoundException(e.getMessage()).initCause(e);
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }

    /**
     * Converts a Berkeley DB DatabaseEntry data value, replacing the null
     * that BDB uses if the data is empty with an empty array.
     */
    static byte[] convertData(byte[] bytes) {
	return bytes != null ? bytes : NO_BYTES;
    }

    /* -- Implement DbDatabase -- */

    /** {@inheritDoc} */
    public byte[] get(DbTransaction txn, byte[] key, boolean forUpdate) {
	try {
	    DatabaseEntry valueEntry = new DatabaseEntry();
	    OperationStatus status = db.get(
		BdbJeTransaction.getBdbTxn(txn), new DatabaseEntry(key),
		valueEntry, forUpdate ? LockMode.RMW : null);
	    if (status == SUCCESS) {
		return convertData(valueEntry.getData());
	    } else if (status == NOTFOUND) {
		return null;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public void put(DbTransaction txn, byte[] key, byte[] value) {
	try {
	    OperationStatus status = db.put(
		BdbJeTransaction.getBdbTxn(txn), new DatabaseEntry(key),
		new DatabaseEntry(value));
	    if (status != SUCCESS) {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean putNoOverwrite(
	DbTransaction txn, byte[] key, byte[] value)
    {
	try {
	    OperationStatus status = db.putNoOverwrite(
		BdbJeTransaction.getBdbTxn(txn), new DatabaseEntry(key),
		new DatabaseEntry(value));
	    if (status == SUCCESS) {
		return true;
	    } else if (status == KEYEXIST) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean delete(DbTransaction txn, byte[] key) {
	try {
	    OperationStatus status = db.delete(
		BdbJeTransaction.getBdbTxn(txn), new DatabaseEntry(key));
	    if (status == SUCCESS) {
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public DbCursor openCursor(DbTransaction txn) {
	return new BdbJeCursor(db, BdbJeTransaction.getBdbTxn(txn));
    }

    /** {@inheritDoc} */
    public void close() {
	try {
	    db.close();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, false);
	}
    }
}
