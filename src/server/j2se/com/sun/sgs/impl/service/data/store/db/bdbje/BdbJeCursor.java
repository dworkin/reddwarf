/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.OperationStatus;
import static com.sleepycat.je.OperationStatus.KEYEXIST;
import static com.sleepycat.je.OperationStatus.NOTFOUND;
import static com.sleepycat.je.OperationStatus.SUCCESS;
import com.sleepycat.je.Transaction;
import com.sun.sgs.impl.service.data.store.db.DbCursor;
import com.sun.sgs.impl.service.data.store.db.DbDatabaseException;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;

/** Provides a cursor implementation using Berkeley DB. */
public class BdbJeCursor implements DbCursor {

    /** The Berkeley DB cursor. */
    private final Cursor cursor;

    /** The entry for the key if isCurrent is true. */
    private DatabaseEntry keyEntry = new DatabaseEntry();

    /** The entry for the value if isCurrent is true. */
    private final DatabaseEntry valueEntry = new DatabaseEntry();

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
    BdbJeCursor(Database db, Transaction txn) {
	try {
	    cursor = db.openCursor(txn, null);
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public byte[] getKey() {
	return isCurrent
	    ? BdbJeDatabase.convertData(keyEntry.getData()) : null;
    }

    /** {@inheritDoc} */
    public byte[] getValue() {
	return isCurrent
	    ? BdbJeDatabase.convertData(valueEntry.getData()) : null;
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
	    throw BdbJeEnvironment.convertException(e, true);
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
	    throw BdbJeEnvironment.convertException(e, true);
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
	    throw BdbJeEnvironment.convertException(e, true);
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
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }

    /** {@inheritDoc} */
    public boolean putNoOverwrite(byte[] key, byte[] value) {
	try {
	    OperationStatus status = cursor.putNoOverwrite(
		new DatabaseEntry(key), new DatabaseEntry(value));
	    if (status == SUCCESS) {
		isCurrent = true;
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
    public void close() {
	try {
	    cursor.close();
	} catch (DatabaseException e) {
	    throw BdbJeEnvironment.convertException(e, true);
	}
    }
}
