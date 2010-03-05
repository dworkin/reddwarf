/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.service.store.db;

import com.sun.sgs.app.TransactionAbortedException;

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
     * Locks the key and associated value for update.
     *
     * @param	txn the transaction for this operation
     * @param	key the key
     * @throws	IllegalArgumentException if {@code txn} was not created by the
     *		associated environment
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void markForUpdate(DbTransaction txn, byte[] key);

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
     * Removes the value associated with a key in this database.
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
     * @param	txn the transaction for this operation
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

