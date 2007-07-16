/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbdb;

import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseConfig;
import com.sleepycat.db.DatabaseEntry;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.DatabaseType;
import com.sleepycat.db.Environment;
import com.sleepycat.db.LockMode;
import com.sleepycat.db.OperationStatus;
import static com.sleepycat.db.OperationStatus.KEYEXIST;
import static com.sleepycat.db.OperationStatus.NOTFOUND;
import static com.sleepycat.db.OperationStatus.SUCCESS;
import com.sleepycat.db.Transaction;
import com.sun.sgs.impl.service.data.store.db.DbCursor;
import com.sun.sgs.impl.service.data.store.db.DbDatabase;
import com.sun.sgs.impl.service.data.store.db.DbDatabaseException;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import java.io.FileNotFoundException;

/** Provides a database implementation using Berkeley DB. */
public class BdbDbDatabase implements DbDatabase {

    /** An empty array returned when Berkeley DB returns null for a value. */
    private static final byte[] NO_BYTES = { };

    /** The database configuration when creating a new database. */
    private static final DatabaseConfig createConfig = new DatabaseConfig();
    static {
	createConfig.setType(DatabaseType.BTREE);
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
    BdbDbDatabase(
	Environment env, Transaction txn, String fileName, boolean create)
	throws FileNotFoundException
    {
	try {
	    db = env.openDatabase(
		txn, fileName, null, create ? createConfig : null);
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, false);
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
		BdbDbTransaction.getBdbTxn(txn), new DatabaseEntry(key),
		valueEntry, forUpdate ? LockMode.RMW : null);
	    if (status == SUCCESS) {
		return convertData(valueEntry.getData());
	    } else if (status == NOTFOUND) {
		return null;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public void put(DbTransaction txn, byte[] key, byte[] value) {
	try {
	    OperationStatus status = db.put(
		BdbDbTransaction.getBdbTxn(txn), new DatabaseEntry(key),
		new DatabaseEntry(value));
	    if (status != SUCCESS) {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean putNoOverwrite(
	DbTransaction txn, byte[] key, byte[] value)
    {
	try {
	    OperationStatus status = db.putNoOverwrite(
		BdbDbTransaction.getBdbTxn(txn), new DatabaseEntry(key),
		new DatabaseEntry(value));
	    if (status == SUCCESS) {
		return true;
	    } else if (status == KEYEXIST) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean delete(DbTransaction txn, byte[] key) {
	try {
	    OperationStatus status = db.delete(
		BdbDbTransaction.getBdbTxn(txn), new DatabaseEntry(key));
	    if (status == SUCCESS) {
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public DbCursor openCursor(DbTransaction txn) {
	return new BdbDbCursor(db, BdbDbTransaction.getBdbTxn(txn));
    }

    /** {@inheritDoc} */
    public void close() {
	try {
	    db.close();
	} catch (DatabaseException e) {
	    throw BdbDbEnvironment.convertException(e, false);
	}
    }
}
