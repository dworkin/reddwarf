/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db;

/**
 * The interface to a database.  Databases are associated with a single
 * database environment, and must be closed before the environment is closed.
 * Databases must not be used after the {@link #close close} method is called.
 */
public interface DbDatabase {

    /**
     * Gets the value associated with a key in this database.  Returns {@code
     * null} if the key is not found.
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @param	forUpdate whether the object should be locked for update
     * @return	the associated value, or {@code null} if the key was not found
     * @throws	IllegalArgumentException if {@code txn} was not created by the
     *		associated environment
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    byte[] get(DbTransaction txn, byte[] key, boolean forUpdate);

    /**
     * Sets the value associated with a key in this database, regardless of
     * whether the key already has an associated value.
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @param	value the value
     * @throws	IllegalArgumentException if {@code txn} was not created by the
     *		associated environment
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void put(DbTransaction txn, byte[] key, byte[] value);

    /**
     * Sets the value associated with a key in this database, but only if the
     * key does not already have an associated value.
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @param	value the value
     * @return	{@code true} if a value was stored for the key, and {@code
     *		false} if the key already had a value
     * @throws	IllegalArgumentException if {@code txn} was not created by the
     *		associated environment
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean putNoOverwrite(DbTransaction txn, byte[] key, byte[] value);

    /**
     * Removes the value associated with a key in this database
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @return	{@code true} if the value was removed, and {@code false} if the
     *		key had no associated value
     * @throws	IllegalArgumentException if {@code txn} was not created by the
     *		associated environment
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean delete(DbTransaction txn, byte[] key);

    /**
     * Returns a cursor for iterating over the contents of this database.  The
     * cursor needs to be closed before the associated transaction ends.
     *
     * @return	the cursor
     * @throws	IllegalArgumentException if {@code txn} was not created by the
     *		associated environment
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    DbCursor openCursor(DbTransaction txn);

    /**
     * Closes this database, releasing any associated resources.  This database
     * should not be used after this method is called.  This method should not
     * be called if the transactions associated with any operations performed
     * on this database are still open.  This method should be called before
     * closing the associated environment.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void close();
}

