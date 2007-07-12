/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db;

import java.io.FileNotFoundException;

/**
 * The interface for interacting with the database implementation.
 * Environments must not be used after the {@link #close close} method is
 * called.
 */
public interface DbEnvironment {

    /**
     * Begins a new transaction with the specified timeout.
     *
     * @param	timeout the number of milliseconds the transaction should be
     *		allowed to run
     * @return	the transaction
     * @throws	IllegalArgumentException if timeout is less than {@code 1}
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    DbTransaction beginTransaction(long timeout);

    /**
     * Opens a database.  If the implementation can be configured with a
     * default directory, then relative database filenames will be interpreted
     * relative to that directory.
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
     * should not be called if there are any open transactions or databases.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void close();
}
