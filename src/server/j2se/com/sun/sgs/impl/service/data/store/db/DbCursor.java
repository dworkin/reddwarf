/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db;

/**
 * The interface to a cursor for iterating over the contents of a database.  A
 * newly created cursor has no current key or value.
 */
public interface DbCursor {

    /**
     * Returns the current key, or {@code null} if the cursor has no current
     * key.
     *
     * @return	the key or {@code null}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    byte[] getKey();

    /**
     * Returns the current value, or {@code null} if the cursor has no current
     * value.
     *
     * @return	the value or {@code null}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    byte[] getValue();

    /**
     * Searches for the first key in the database.  If the result is {@code
     * true}, then sets the current key and value to the first key and its
     * associated value.
     *
     * @return	{@code true} if the first key was found, else {@code false}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean findFirst();

    /**
     * Searches for the next key in the database.  If the cursor has no current
     * key, then searches for the first key.  If the result is {@code true},
     * then sets the current key and value to the key found and its associated
     * value.
     *
     * @return	{@code true} if the next key was found, else {@code false}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean findNext();

    /**
     * Searches for the first key that is greater than or equal to the
     * specified key.  If the result is {@code true}, then the {@link #getKey
     * getKey} and {@link #getValue getValue} methods will return the key found
     * and its associated value.
     *
     * @return	{@code true} if a key was found, else {@code false}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean findNext(byte[] key);

    /**
     * Searches for the last key in the database.  If the result is {@code
     * true}, then sets the current key and value to the last key and its
     * associated value.
     *
     * @return	{@code true} if the last key was found, else {@code false}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean findLast();

    /**
     * Uses the cursor to set the value associated with a key in the database,
     * if the key does not already have an associated value.  If the result is
     * {@code true}, then sets the current key and value to the newly inserted
     * key and its associated value.
     *
     * @param	key the key
     * @param	value the value
     * @return	{@code true} if a value was stored for the key, and {@code
     *		false} if the key already had a value
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    boolean putNoOverwrite(byte[] key, byte[] value);

    /**
     * Closes this cursor, releasing any associated resources.  This cursor
     * should not be used after this method is called.  This method should be
     * called before preparing, committing, or aborting the associated
     * transaction.
     *
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void close();
}
