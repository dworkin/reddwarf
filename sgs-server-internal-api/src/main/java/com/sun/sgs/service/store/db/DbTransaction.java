/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
