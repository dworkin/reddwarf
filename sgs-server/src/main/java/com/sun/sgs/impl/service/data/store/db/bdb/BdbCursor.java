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

package com.sun.sgs.impl.service.data.store.db.bdb;

import com.sleepycat.db.Cursor;
import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseEntry;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.OperationStatus;
import static com.sleepycat.db.OperationStatus.KEYEXIST;
import static com.sleepycat.db.OperationStatus.NOTFOUND;
import static com.sleepycat.db.OperationStatus.SUCCESS;
import com.sleepycat.db.Transaction;
import com.sun.sgs.service.store.db.DbCursor;
import com.sun.sgs.service.store.db.DbDatabaseException;

/** Provides a cursor implementation using Berkeley DB. */
public class BdbCursor implements DbCursor {

    /** The Berkeley DB cursor. */
    private final Cursor cursor;

    /** An entry containing the current key if isCurrent is true. */
    private DatabaseEntry keyEntry = new DatabaseEntry();

    /** An entry containing the current value if isCurrent is true. */
    private DatabaseEntry valueEntry = new DatabaseEntry();

    /** Whether the data in keyEntry and valueEntry is valid. */
    private boolean isCurrent = false;

    /**
     * Creates an instance of this class.
     *
     * @param	db the Berkeley DB database
     * @param	txn the Berkeley DB transaction
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    BdbCursor(Database db, Transaction txn) {
	try {
	    cursor = db.openCursor(txn, null);
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public byte[] getKey() {
	return isCurrent
	    ? BdbDatabase.convertData(keyEntry.getData()) : null;
    }

    /** {@inheritDoc} */
    public byte[] getValue() {
	return isCurrent
	    ? BdbDatabase.convertData(valueEntry.getData()) : null;
    }

    /** {@inheritDoc} */
    public boolean findFirst() {
	try {
	    OperationStatus status =
		cursor.getFirst(keyEntry, valueEntry, null);
	    if (status == SUCCESS) {
		isCurrent = true;
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean findNext() {
	try {
	    OperationStatus status =
		cursor.getNext(keyEntry, valueEntry, null);
	    if (status == SUCCESS) {
		isCurrent = true;
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean findNext(byte[] key) {
	DatabaseEntry searchEntry = new DatabaseEntry(key);
	try {
	    OperationStatus status =
		cursor.getSearchKeyRange(searchEntry, valueEntry, null);
	    if (status == SUCCESS) {
		keyEntry = searchEntry;
		isCurrent = true;
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean findLast() {
	try {
	    OperationStatus status =
		cursor.getLast(keyEntry, valueEntry, null);
	    if (status == SUCCESS) {
		isCurrent = true;
		return true;
	    } else if (status == NOTFOUND) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean putNoOverwrite(byte[] key, byte[] value) {
	try {
	    DatabaseEntry putKeyEntry = new DatabaseEntry(key);
	    DatabaseEntry putValueEntry = new DatabaseEntry(value);
	    OperationStatus status = cursor.putNoOverwrite(
		putKeyEntry, putValueEntry);
	    if (status == SUCCESS) {
		isCurrent = true;
		keyEntry = putKeyEntry;
		valueEntry = putValueEntry;
		return true;
	    } else if (status == KEYEXIST) {
		return false;
	    } else {
		throw new DbDatabaseException("Operation failed: " + status);
	    }
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }	    

    /** {@inheritDoc} */
    public void close() {
	try {
	    cursor.close();
	} catch (DatabaseException e) {
	    throw BdbEnvironment.convertException(e, true);
	}
    }
}
