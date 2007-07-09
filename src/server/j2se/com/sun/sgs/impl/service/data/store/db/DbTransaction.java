/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db;

/** The interface for a database transaction. */
public interface DbTransaction {

    /**
     * Initiates the first phase of a two-phase commit.  This method should not
     * be called if any cursors associated with this transaction are still
     * open.  This method should not be called after this method, or the {@link
     * #commit commit} or {@link #abort abort} methods, has been called.
     *
     * @param	gid the global transaction ID, which must be at least 128 bytes
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void prepare(byte[] gid);

    /**
     * Commits the transaction.  All cursors should be closed before calling
     * this method.  This method should not be called if any cursors associated
     * with this transaction are still open.  No methods should be called on
     * this transaction after this method is called.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void commit();

    /**
     * Aborts the transaction.  All cursors should be closed before calling
     * this method.  No methods should be called on this transaction after this
     * method is called.
     *
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    void abort();    
}
