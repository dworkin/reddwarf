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

