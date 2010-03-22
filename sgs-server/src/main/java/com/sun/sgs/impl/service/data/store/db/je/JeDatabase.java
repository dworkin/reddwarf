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

package com.sun.sgs.impl.service.data.store.db.je;

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
import com.sun.sgs.service.store.db.DbCursor;
import com.sun.sgs.service.store.db.DbDatabase;
import com.sun.sgs.service.store.db.DbDatabaseException;
import com.sun.sgs.service.store.db.DbTransaction;
import java.io.FileNotFoundException;

/** Provides a database implementation using Berkeley DB Java Edition. */
public class JeDatabase implements DbDatabase {

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
     * @throws	FileNotFoundException if the database file is not found
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    JeDatabase(
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
	    throw JeEnvironment.convertException(e, false);
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
		JeTransaction.getJeTxn(txn), new DatabaseEntry(key),
		valueEntry, forUpdate ? LockMode.RMW : null);
	    if (status == SUCCESS) {
		return convertData(valueEntry.getData());
	    } else if (status == NOTFOUND) {
		return null;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public void markForUpdate(DbTransaction txn, byte[] key) {
	try {
	    DatabaseEntry valueEntry = new DatabaseEntry();
	    /* Ignore value by truncating to zero bytes */
	    valueEntry.setPartial(0, 0, true);
	    OperationStatus status = db.get(
		JeTransaction.getJeTxn(txn), new DatabaseEntry(key),
		valueEntry, LockMode.RMW);
	    if (status != SUCCESS && status != NOTFOUND) {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public void put(DbTransaction txn, byte[] key, byte[] value) {
	try {
	    OperationStatus status = db.put(
		JeTransaction.getJeTxn(txn), new DatabaseEntry(key),
		new DatabaseEntry(value));
	    if (status != SUCCESS) {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean putNoOverwrite(
	DbTransaction txn, byte[] key, byte[] value)
    {
	try {
	    OperationStatus status = db.putNoOverwrite(
		JeTransaction.getJeTxn(txn), new DatabaseEntry(key),
		new DatabaseEntry(value));
	    if (status == SUCCESS) {
		return true;
	    } else if (status == KEYEXIST) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean delete(DbTransaction txn, byte[] key) {
	try {
	    OperationStatus status = db.delete(
		JeTransaction.getJeTxn(txn), new DatabaseEntry(key));
	    if (status == SUCCESS) {
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public DbCursor openCursor(DbTransaction txn) {
	return new JeCursor(db, JeTransaction.getJeTxn(txn));
    }

    /** {@inheritDoc} */
    public void close() {
	try {
	    db.close();
	} catch (DatabaseException e) {
	    throw JeEnvironment.convertException(e, false);
	}
    }
}
