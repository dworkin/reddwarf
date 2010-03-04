/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.service.store.db;

import java.io.FileNotFoundException;

/**
 * The interface for interacting with the database implementation.
 * Environments must not be used after the {@link #close close} method is
 * called.
 */
public interface DbEnvironment {

    /**
     * Begins a new transaction with the specified timeout and the
     * implementation-specific default isolation level.
     *
     * @param	timeout the number of milliseconds the transaction should be
     *		allowed to run
     * @return	the transaction
     * @throws	IllegalArgumentException if timeout is less than {@code 1}
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    DbTransaction beginTransaction(long timeout);

    /**
     * Begins a transaction with the specified timeout and isolation level.
     *
     * @param	timeout the number of milliseconds the transaction should be
     *		allowed to run
     * @param	fullIsolation if {@code true}, requires the transaction to
     *		support full serializable isolation, otherwise uses the default
     *		transaction isolation level
     * @return	the transaction
     * @throws	IllegalArgumentException if timeout is less than {@code 1}
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    DbTransaction beginTransaction(long timeout, boolean fullIsolation);

    /**
     * Opens a database.  Relative database filenames will be interpreted
     * relative to whatever root directory was specified when this environment
     * was created, typically the {@code directory} argument passed to {@code
     * DbEnvironmentFactory.getEnvironment}.
     *
     * @param	txn the transaction under which the database should be opened
     * @param	fileName the name of the file containing the database
     * @param	create whether to create the database if it does not exist
     * @return	the database
     * @throws	IllegalArgumentException if {@code txn} was not created by this
     *		environment
     * @throws	FileNotFoundException if {@code create} is {@code false} and
     *		the database is not found
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    DbDatabase openDatabase(DbTransaction txn, String fileName, boolean create)
        throws FileNotFoundException;

    /**
     * Closes the environment, releasing any associated resources.  This
     * environment should not be used after this method is called.  This method
     * should not be called if any of the  transactions or databases associated
     * with this environment are still open.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void close();

    /**
     * Specifies whether object allocations in this environment should use a
     * placeholder at the end of each allocation block to avoid allocation
     * concurrency conflicts.
     *
     * @return	whether object allocations should use an end-of-block
     *		placeholder
     */
    boolean useAllocationBlockPlaceholders();
}
