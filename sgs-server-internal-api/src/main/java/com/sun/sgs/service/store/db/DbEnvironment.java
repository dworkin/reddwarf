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
