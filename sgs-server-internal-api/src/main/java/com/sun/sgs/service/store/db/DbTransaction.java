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

/** The interface for a database transaction. */
public interface DbTransaction {

    /**
     * Initiates the first phase of a two-phase commit.  This method should not
     * be called if any cursors associated with this transaction are still
     * open.  No methods should be called on this transaction after this method
     * is called.
     *
     * @param	gid the global transaction ID, which must be at least 128 bytes
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void prepare(byte[] gid);

    /**
     * Commits the transaction.  This method should not be called if any
     * cursors associated with this transaction are still open.  No methods
     * should be called on this transaction after this method is called.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void commit();

    /**
     * Aborts the transaction.  This method should not be called if any cursors
     * associated with this transaction are still open.  No methods should be
     * called on this transaction after this method is called.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void abort();    
}
