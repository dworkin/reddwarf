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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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
