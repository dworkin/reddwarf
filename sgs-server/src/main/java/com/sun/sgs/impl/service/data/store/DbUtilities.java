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

import static com.sun.sgs.impl.service.data.store.
    DataStoreHeader.NEXT_NODE_ID_KEY;
import static com.sun.sgs.impl.service.data.store.
    DataStoreHeader.NEXT_OBJ_ID_KEY;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.store.db.DbCursor;
import com.sun.sgs.service.store.db.DbDatabase;
import com.sun.sgs.service.store.db.DbEnvironment;
import com.sun.sgs.service.store.db.DbTransaction;
import java.io.FileNotFoundException;
import java.io.ObjectStreamClass;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

/**
 * Utility methods for database operations needed to implement a {@code
 * DataStore} using the {@link com.sun.sgs.service.store.db} package.  This
 * class should not be instantiated.
 */
public final class DbUtilities {

    /** The number of bytes in a SHA-1 message digest. */
    private static final int SHA1_SIZE = 20;

    /** A message digest for use by the current thread. */
    private static final ThreadLocal<MessageDigest> messageDigest =
	new ThreadLocal<MessageDigest>() {
	    protected MessageDigest initialValue() {
		try {
		    return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
		    throw new AssertionError(e);
		}
	    }
        };

    /**
     * Stores information about the databases that constitute the data
     * store.
     */
    public static class Databases {
	
	/** The info database. */
	private DbDatabase info;

	/** The classes database. */
	private DbDatabase classes;

	/** The object IDs database. */
	private DbDatabase oids;

	/** The names database. */
	private DbDatabase names;

	/** Creates an instance of this class. */
	Databases() { }

	/**
	 * Returns the info database.
	 *
	 * @return the info database
	 */
	public DbDatabase info() {
	    return info;
	}

	/**
	 * Returns the classes database.
	 *
	 * @return the classes database
	 */
	public DbDatabase classes() {
	    return classes;
	}

	/**
	 * Returns the object IDs database.
	 *
	 * @return the object IDs database
	 */
	public DbDatabase oids() {
	    return oids;
	}

	/**
	 * Returns the names database.
	 *
	 * @return the names database
	 */
	public DbDatabase names() {
	    return names;
	}
    }

    /** This class should not be instantiated. */
    private DbUtilities() {
	throw new AssertionError();
    }

    /**
     * Opens or creates the Berkeley DB databases for a data store.
     *
     * @param	env the database environment
     * @param	dbTxn the database transaction
     * @param	logger the logger for logging messages
     * @return	the databases
     */
    public static Databases getDatabases(
	DbEnvironment env, DbTransaction dbTxn, LoggerWrapper logger)
    {
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

    /**
     * Returns the next available object ID and increments the stored value by
     * the specified amount.
     *
     * @param	infoDb the info database
     * @param	dbTxn the transaction
     * @param	blockSize the number of items to allocate
     * @return	the next object ID
     */
    public static long getNextObjectId(
	DbDatabase infoDb, DbTransaction dbTxn, int blockSize)
    {
	return DataStoreHeader.getNextId(
	    NEXT_OBJ_ID_KEY, infoDb, dbTxn, blockSize);
    }

    /**
     * Returns the next available node ID and increments the stored value by
     * the specified amount.
     *
     * @param	infoDb the info database
     * @param	dbTxn the transaction
     * @param	blockSize the number of node IDs to allocate
     * @return	the next node ID
     */
    public static long getNextNodeId(
	DbDatabase infoDb, DbTransaction dbTxn, int blockSize)
    {
	return DataStoreHeader.getNextId(
	    NEXT_NODE_ID_KEY, infoDb, dbTxn, blockSize);
    }

    /**
     * Returns the class ID to represent classes with the specified class
     * information.  Obtains an existing ID for the class information if
     * present; otherwise, stores the information and returns the new ID
     * associated with it.  Class IDs are always greater than {@code 0}.  The
     * class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	env the database environment
     * @param	classesDb the classes database
     * @param	classInfo the class information
     * @param	timeout the timeout to use when creating a transaction
     * @return	the associated class ID
     */
    public static int getClassId(DbEnvironment env,
				 DbDatabase classesDb,
				 byte[] classInfo,
				 long timeout)
    {
	byte[] hashKey = getKeyFromClassInfo(classInfo);
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
	 *
	 * Use full transaction isolation to insure consistency when
	 * concurrently allocating new class IDs.  -tjb@sun.com (03/17/2009)
	 */
	DbTransaction dbTxn = env.beginTransaction(timeout, true);
	try {
	    int result;
	    byte[] hashValue = classesDb.get(dbTxn, hashKey, false);
	    if (hashValue != null) {
		result = DataEncoding.decodeInt(hashValue);
	    } else {
		DbCursor cursor = classesDb.openCursor(dbTxn);
		try {
		    result = cursor.findLast()
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
	    return result;
	} finally {
	    if (!done) {
		dbTxn.abort();
	    }
	}
    }

    /**
     * Returns the class information associated with the specified class ID.
     * The class information is the serialized form of the {@link
     * ObjectStreamClass} instance that serialization uses to represent the
     * class.
     *
     * @param	env the database environment
     * @param	classesDb the classes database
     * @param	classId the class ID
     * @param	timeout the transaction timeout for creating a transaction
     * @return	the associated class information or {@code null} if the ID is
     *		not found
     * @throws	IllegalArgumentException if {@code classId} is not greater than
     *		{@code 0}
     */
    public static byte[] getClassInfo(
	DbEnvironment env, DbDatabase classesDb, int classId, long timeout)
    {
	if (classId <= 0) {
	    throw new IllegalArgumentException(
		"The class ID must be greater than 0");
	}
	byte[] key = getKeyFromClassId(classId);
	boolean done = false;
	DbTransaction dbTxn = env.beginTransaction(timeout);
	try {
	    byte[] result = classesDb.get(dbTxn, key, false);
	    done = true;
	    dbTxn.commit();
	    return result;
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
    private static byte[] getKeyFromClassInfo(byte[] classInfo) {
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
