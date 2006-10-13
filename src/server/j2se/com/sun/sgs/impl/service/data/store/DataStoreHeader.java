package com.sun.sgs.impl.service.data.store;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.ShortBinding;
import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseEntry;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.LockMode;
import com.sleepycat.db.OperationStatus;
import com.sun.sgs.impl.service.data.Util;

/**
 * Encapsulates the layout of meta data stored at the start of the IDs
 * database.  This class cannot be instantiated. <p>
 *
 * The IDs database stores a mapping from object IDs to object values.  The
 * first block of IDs is reserved for storing data used by {@link
 * DataStoreImpl}.  The first ID allocated after this block is specified by
 * {@link NEXT_ID_ID}. <p>
 *
 * ID <code>0</code> contains a magic number common to all
 * <code>DataStoreImpl</code> databases. <p>
 *
 * ID <code>1</code> contains the major version number, which match the value
 * in the current version of the implementation. <p>
 *
 * ID <code>2</code> contains the minor version number, which can vary between
 * the database and the implementation. <p>
 *
 * ID <code>3</code> contains the ID of the next free ID number to use for
 * allocating new objects.
 */
final class DataStoreHeader {

    /** The ID for the magic number. */
    static final long MAGIC_ID = 0;

    /** The ID for the major version number. */
    static final long MAJOR_ID = 1;

    /** The ID for the minor version number. */
    static final long MINOR_ID = 2;

    /** The ID for the value of the next free ID. */
    static final long NEXT_ID_ID = 3;

    /** The magic number. */
    static final long MAGIC = 0xb2bfd03aafd9acc5l;

    /** The major version number. */
    static final short MAJOR_VERSION = 1;

    /** The minor version number. */
    static final short MINOR_VERSION = 0;

    /** The first free ID. */
    static final long INITIAL_NEXT_ID = 1024;

    /** This class cannot be instantiated. */
    private DataStoreHeader() {
	throw new AssertionError();
    }

    /**
     * Verifies the header information in the database, and returns its minor
     * version number.
     *
     * @param	db the database
     * @param	bdbTxn the Berkeley DB transaction
     * @return	the minor version number
     * @throws	DatabaseException if a problem occurs accessing the database
     * @throws	DataStoreException if the format of the header information is
     *		incorrect
     */
    static int verify(Database db, com.sleepycat.db.Transaction bdbTxn)
	throws DatabaseException
    {
	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry value = new DatabaseEntry();

	LongBinding.longToEntry(MAGIC_ID, key);
	get(db, bdbTxn, key, value, null);
	long magic = LongBinding.entryToLong(value);
	if (magic != MAGIC) {
	    throw new DataStoreException(
		"Bad magic number in header: expected " +
		Util.toHexString(MAGIC) + ", found " +
		Util.toHexString(magic));
	}

	LongBinding.longToEntry(MAJOR_ID, key);
	get(db, bdbTxn, key, value, null);
	long majorVersion = ShortBinding.entryToShort(value);
	if (majorVersion != MAJOR_VERSION) {
	    throw new DataStoreException(
		"Wrong major version number: expected " + MAJOR_VERSION +
		", found " + majorVersion);
	}

	LongBinding.longToEntry(MINOR_ID, key);
	get(db, bdbTxn, key, value, null);
	return ShortBinding.entryToShort(value);
    }

    /**
     * Stores header information in the database.
     *
     * @param	db the database
     * @param	bdbTxn the Berkeley DB transaction
     * @throws	DatabaseException if a problem occurs accessing the database
     */
    static void create(Database db, com.sleepycat.db.Transaction bdbTxn)
	throws DatabaseException
    {
	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry value = new DatabaseEntry();

	LongBinding.longToEntry(MAGIC_ID, key);
	LongBinding.longToEntry(MAGIC, value);
	putNoOverwrite(db, bdbTxn, key, value);

	LongBinding.longToEntry(MAJOR_ID, key);
	ShortBinding.shortToEntry(MAJOR_VERSION, value);
	putNoOverwrite(db, bdbTxn, key, value);

	LongBinding.longToEntry(MINOR_ID, key);
	ShortBinding.shortToEntry(MINOR_VERSION, value);
	putNoOverwrite(db, bdbTxn, key, value);

	LongBinding.longToEntry(NEXT_ID_ID, key);
	LongBinding.longToEntry(INITIAL_NEXT_ID, value);
	putNoOverwrite(db, bdbTxn, key, value);
    }

    /**
     * Returns the next available ID for storing a newly allocated object, and
     * increments the stored value by the specified amount.  The return value
     * will be a positive number.
     *
     * @param	db the database
     * @param	bdbTxn the Berkeley DB transaction
     * @param	increment the amount to increment the stored amount
     * @return	the next available ID
     * @throws	DatabaseException if a problem occurs accessing the database
     */
    static long getNextId(
	Database db, com.sleepycat.db.Transaction bdbTxn, long increment)
	throws DatabaseException
    {
	DatabaseEntry key = new DatabaseEntry();
	LongBinding.longToEntry(NEXT_ID_ID, key);
	DatabaseEntry value = new DatabaseEntry();
	get(db, bdbTxn, key, value, LockMode.RMW);
	long result = LongBinding.entryToLong(value);
	LongBinding.longToEntry(result + increment, value);
	put(db, bdbTxn, key, value);
	return result;
    }

    /**
     * Reads a value from the database, throwing an exception if the key is not
     * present.
     */
    private static void get(Database db,
			    com.sleepycat.db.Transaction bdbTxn,
			    DatabaseEntry key,
			    DatabaseEntry value,
			    LockMode lockMode)
	throws DatabaseException
    {
	OperationStatus status = db.get(bdbTxn, key, value, lockMode);
	if (status == OperationStatus.NOTFOUND) {
	    throw new DataStoreException("Item not found");
	} else if (status != OperationStatus.SUCCESS) {
	    throw new DataStoreException(
		"Problem reading item: " + status);
	}
    }

    /**
     * Writes a value to the database, throwing an exception if the key is
     * already present.
     */
    private static void putNoOverwrite(Database db,
				       com.sleepycat.db.Transaction bdbTxn,
				       DatabaseEntry key,
				       DatabaseEntry value)
	throws DatabaseException
    {
	OperationStatus status = db.putNoOverwrite(bdbTxn, key, value);
	if (status == OperationStatus.KEYEXIST) {
	    throw new DataStoreException("Item already present");
	} else if (status != OperationStatus.SUCCESS) {
	    throw new DataStoreException("Problem writing item: " + status);
	}
    }

    /** Writes a value to the database. */
    private static void put(Database db,
			    com.sleepycat.db.Transaction bdbTxn,
			    DatabaseEntry key,
			    DatabaseEntry value)
	throws DatabaseException
    {
	OperationStatus status = db.put(bdbTxn, key, value);
	if (status != OperationStatus.SUCCESS) {
	    throw new DataStoreException("Problem writing item: " + status);
	}
    }
}
