/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db;

/** The interface to a database. */
public interface DbDatabase {

    /**
     * Gets the value associated with a key in this database.  Returns {@code
     * null} if the key is not found.
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @param	forUpdate whether the object should be locked for update
     * @return	the associated value, or {@code null} if the key was not found
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
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void put(DbTransaction txn, byte[] key, byte[] value);

    /**
     * Sets the value associated with a key in this database, if the key does
     * not already have an associated value.
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @param	value the value
     * @return	{@code true} if a value was stored for the key, and {@code
     *		false} if the key already had a value
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
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean delete(DbTransaction txn, byte[] key);

    /**
     * Returns a cursor for iterating over the contents of this database.  Note
     * that cursors need to be closed before the associated transaction ends.
     *
     * @return	the cursor
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

