/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

import com.sun.sgs.service.store.db.DbDatabase;
import com.sun.sgs.service.store.db.DbTransaction;
import java.math.BigInteger;

/**
 * Encapsulates the layout of meta data stored at the start of the info
 * database, and in the classes database.  This class cannot be instantiated.
 *
 * In the info database, the value for key 0 stores a magic number common to
 * all DataStoreImpl databases.
 *
 * Key 1 stores the major version number, which must match the value in the
 * current version of the implementation.
 *
 * Key 2 stores the minor version number, which can vary between the database
 * and the implementation.
 *
 * Key 3 stores the ID of the next free ID number to use for allocating new
 * objects.
 *
 * Key 4 stores the ID of the next free transaction ID number for the network
 * version to use in allocating transactions.
 *
 * Key 5 stores the ID of the lowest allocation block placeholder, or -1 if
 * there are no placeholders.  This field is used during initialization to
 * remove any existing placeholders which were created for allocation blocks
 * that are no longer in use.  Placeholders always appear at the end of each
 * allocation block, whose size is fixed (as of version 4.0) at 1024 bytes.
 *
 * Key 6 store the ID of the next free node ID to use for giving unique
 * identifiers to nodes.
 *
 * In the classes database, keys whose initial byte is 1 map the SHA-1 hash of
 * the serialized form of a class descriptor (a ObjectStreamClass) to the class
 * ID, which is 4 byte integer.
 *
 * Keys whose initial byte is 2 map a class ID to the bytes making up the
 * serialized form of the associated class descriptor.  Since these entries
 * come at the end, we can find the next class ID by using a cursor to find the
 * last entry.
 *
 * In the names database, keys are the UTF8 encoding of binding names, and
 * values are the object IDs of the associated objects.
 *
 * In the oids database, keys are object IDs, and values are the bytes
 * representing the associated objects.
 *
 * The serialized forms used for the object values are compressed as follows:
 *
 * - If the first byte is 1, then the value was created by serialization
 *   protocol version 2, and the 4 bytes at the start of that format have been
 *   elided.
 *
 * - If the first byte is 2, then the subsequent bytes represent the
 *   uncompressed object data.
 *
 * - Class descriptors in the serialized form have been replaced by an integer
 *   which refers to a class ID stored in the classes database.  The class IDs
 *   themselves have been compressed using the Int30 class
 *
 * Object values also have two additional, distinguished initial byte values
 * to support placeholders:
 *
 * - If the first byte is 3, then the entry represents a placeholder, which
 *   means that the entry appears within the database but should not be
 *   considered to represent an object.  Placeholders are used to create a
 *   barrier object in the database to improve concurrency for new object
 *   allocations when using BDB Java Edition.
 *
 * - If the first byte is 4, then the actual data value is represented by the
 *   remaining bytes.  This "quoting" value can be used to represent data that
 *   starts with the 3 that marks placeholders, or the 4 used for quoting.
 *   Since serialized data will always start with 1 or 2, though, this value
 *   should not be used in practice.
 *
 * Version history:
 *
 * Version 1.0: Initial version, 11/3/2006
 * Version 2.0: Add NEXT_TXN_ID, 2/15/2007
 * Version 3.0: Add classes DB, compress object values, 5/18/2007
 * Version 4.0: Add placeholders, 7/8/2008
 * Version 5.0: Add node IDs, 7/21/2009
 */
final class DataStoreHeader {

    /** The key for the magic number. */
    static final long MAGIC_KEY = 0;

    /** The key for the major version number. */
    static final long MAJOR_KEY = 1;

    /** The key for the minor version number. */
    static final long MINOR_KEY = 2;

    /** The key for the value of the next free object ID. */
    static final long NEXT_OBJ_ID_KEY = 3;

    /**
     * The key for the value of the next free transaction ID, used in the
     * network version.
     */
    static final long NEXT_TXN_ID_KEY = 4;

    /** The key for the value of the first allocation block placeholder ID. */
    static final long FIRST_PLACEHOLDER_ID_KEY = 5;

    /** The key for the value of the next free node ID. */
    static final long NEXT_NODE_ID_KEY = 6;

    /** The magic number: DaRkStAr. */
    static final long MAGIC = 0x4461526b53744172L;

    /** The major version number. */
    static final short MAJOR_VERSION = 5;

    /** The minor version number. */
    static final short MINOR_VERSION = 0;

    /** The first free object ID. */
    static final long INITIAL_NEXT_OBJ_ID = 1;

    /** The first free transaction ID. */
    static final long INITIAL_NEXT_TXN_ID = 1;

    /** The first free node ID. */
    static final long INITIAL_NEXT_NODE_ID = 1;

    /** The first byte stored in keys for the classes database hash keys. */
    static final byte CLASS_HASH_PREFIX = 1;

    /**
     * The first byte value stored in class ID keys.  This value should be
     * greater than CLASS_HASH, to insure that class ID keys come after class
     * hash ones.
     */
    static final byte CLASS_ID_PREFIX = 2;

    /** The first byte stored in the object value for a placeholder */
    static final byte PLACEHOLDER_OBJ_VALUE = 3;

    /**
     * The first byte stored in an object value in order to ignore the meaning
     * of the first byte, in particular if it is PLACEHOLDER_OBJ_VALUE or this
     * value.  When this is the first byte of object data, the actual data
     * consists of the second and following bytes.  Because object data is
     * always serialized data, which uses a specialized encoding that starts
     * with either 1 or 2, this value should not be used in practice.
     */
    static final byte QUOTE_OBJ_VALUE = 4;

    /**
     * The size of allocation blocks.  The size is fixed so that it can be used
     * at initialization time to find allocation block placeholders that appear
     * at multiples of this size after the offset is stored under the
     * FIRST_PLACEHOLDER_ID_KEY key.
     */
    static final int ALLOCATION_BLOCK_SIZE = 1024;

    /** This class cannot be instantiated. */
    private DataStoreHeader() {
	throw new AssertionError();
    }

    /**
     * Verifies the header information in the database, and returns its minor
     * version number.
     *
     * @param	db the database
     * @param	dbTxn the database transaction
     * @return	the minor version number
     * @throws	DbDatabaseException if a problem occurs accessing the database
     * @throws	DataStoreException if the format of the header information is
     *		incorrect
     */
    static int verify(DbDatabase db, DbTransaction dbTxn) {
	byte[] value =
	    db.get(dbTxn, DataEncoding.encodeLong(MAGIC_KEY), false);
	if (value == null) {
	    throw new DataStoreException("Magic number not found");
	}
	long magic = DataEncoding.decodeLong(value);
	if (magic != MAGIC) {
	    throw new DataStoreException(
		"Bad magic number in header: expected " +
		toHexString(MAGIC) + ", found " + toHexString(magic));
	}
	value = db.get(dbTxn, DataEncoding.encodeLong(MAJOR_KEY), false);
	if (value == null) {
	    throw new DataStoreException("Major version number not found");
	}
	short majorVersion = DataEncoding.decodeShort(value);
	if (majorVersion < MAJOR_VERSION) {
	    upgrade(db, dbTxn, majorVersion);
	} else if (majorVersion > MAJOR_VERSION) {
	    throw new DataStoreException(
		"Wrong major version number: expected " + MAJOR_VERSION +
		", found " + majorVersion);
	}
	value = db.get(dbTxn, DataEncoding.encodeLong(MINOR_KEY), false);
	if (value == null) {
	    throw new DataStoreException("Minor version number not found");
	}
	return DataEncoding.decodeShort(value);
    }

    /**
     * Upgrades a database with a lower major version to the current version if
     * possible, and otherwise throws an exception.
     */
    private static void upgrade(
	DbDatabase db, DbTransaction dbTxn, int majorVersion)
    {
	switch (majorVersion) {
	case 1:
	case 2:
	case 3:
	case 4:
	    throw new DataStoreException(
		"Database version number " + majorVersion +
		" is not supported");
	default:
	    throw new AssertionError();
	}
    }

    /**
     * Stores header information in the database.
     *
     * @param	db the database
     * @param	dbTxn the database transaction
     * @throws	DbDatabaseException if a problem occurs accessing the database
     */
    static void create(DbDatabase db, DbTransaction dbTxn) {
	boolean success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(MAGIC_KEY),
	    DataEncoding.encodeLong(MAGIC));
	assert success;
	success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(MAJOR_KEY),
	    DataEncoding.encodeShort(MAJOR_VERSION));
	assert success;
	success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(MINOR_KEY),
	    DataEncoding.encodeShort(MINOR_VERSION));
	assert success;
	success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(NEXT_OBJ_ID_KEY),
	    DataEncoding.encodeLong(INITIAL_NEXT_OBJ_ID));
	assert success;
	success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(NEXT_TXN_ID_KEY),
	    DataEncoding.encodeLong(INITIAL_NEXT_TXN_ID));
	assert success;
	success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(FIRST_PLACEHOLDER_ID_KEY),
	    DataEncoding.encodeLong(-1));
	assert success;
	success = db.putNoOverwrite(
	    dbTxn, DataEncoding.encodeLong(NEXT_NODE_ID_KEY),
	    DataEncoding.encodeLong(INITIAL_NEXT_NODE_ID));
	assert success;
    }

    /**
     * Returns the next available ID stored under the specified key, and
     * increments the stored value by the specified amount, which must be
     * greater than zero.  The return value will be a positive number.
     *
     * @param	key the key under which the ID is stored
     * @param	db the database
     * @param	dbTxn the database transaction
     * @param	increment the amount to increment the stored amount
     * @return	the next available ID
     * @throws	DbDatabaseException if a problem occurs accessing the database
     * @throws	IllegalArgumentException if increment is not greater than zero
     */
    static long getNextId(long key,
			  DbDatabase db,
			  DbTransaction dbTxn,
			  long increment)
    {
	if (increment <= 0) {
	    throw new IllegalArgumentException(
		"The increment must be greater than zero");
	}
	byte[] keyBytes = DataEncoding.encodeLong(key);
	byte[] valueBytes = db.get(dbTxn, keyBytes, true);
	if (valueBytes == null) {
	    throw new DataStoreException("Key not found: " + key);
	}
	long result = DataEncoding.decodeLong(valueBytes);
	db.put(dbTxn, keyBytes, DataEncoding.encodeLong(result + increment));
	return result;
    }

    /** Returns a string that describes the standard header. */
    static String headerString() {
	return headerString(MINOR_VERSION);
    }

    /**
     * Returns a string that describes the header with the specified minor
     * version number.
     */
    static String headerString(int minorVersion) {
	return "DataStoreHeader[magic:" + toHexString(MAGIC) +
	    ", version:" + MAJOR_VERSION + "." + minorVersion + "]";
    }

    /** Converts a long to a string in hexadecimal. */
    private static String toHexString(long l) {
	/* Avoid sign extension if bit 63 is set */
	BigInteger bi = BigInteger.valueOf(l & (-1L >>> 1));
	if ((l & (1L << 63)) != 0) {
	    bi = bi.setBit(63);
	}
	return "0x" + bi.toString(16);
    }
}
