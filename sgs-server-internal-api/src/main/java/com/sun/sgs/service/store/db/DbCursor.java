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
 * The interface to a cursor for iterating over the contents of a database.  A
 * newly created cursor has no current key or value.  Cursors are associated
 * with a single transaction, and must be created and closed within that single
 * transaction.  Cursors must not be used after the {@link #close close} method
 * is called.  Cursor implementations are not required to be synchronized.
 */
public interface DbCursor {

    /**
     * Returns the current key, or {@code null} if the cursor has no current
     * key.
     *
     * @return	the current key or {@code null}
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    byte[] getKey();

    /**
     * Returns the current value, or {@code null} if the cursor has no current
     * value.
     *
     * @return	the current value or {@code null}
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
     * specified key.  If the result is {@code true}, then sets the current key
     * and value to the key found and its associated value.
     *
     * @param	key the key at which to start searching
     * @return	{@code true} if the next key was found, else {@code false}
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
     * transaction. <p>
     *
     * Note that the Berkeley DB documentation for prepare doesn't say you need
     * to close cursors, but my testing shows that you do.  -tjb@sun.com
     * (12/14/2006)
     *
     * @throws	TransactionAbortedException if the transaction should be
     *		aborted due to timeout or conflict
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void close();
}
