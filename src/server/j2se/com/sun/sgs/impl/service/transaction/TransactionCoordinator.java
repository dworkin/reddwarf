/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.transaction;

/** Defines an interface for managing transactions. */
public interface TransactionCoordinator {

    /** The property used to specify the timeout value for transactions. */
    public static final String TXN_TIMEOUT_PROPERTY =
	"com.sun.sgs.txn.timeout";

    /** The property used to specify the value for unbounded timeout. */
    public static final String TXN_UNBOUNDED_TIMEOUT_PROPERTY =
	"com.sun.sgs.txn.timeout.unbounded";

    /**
     * Creates a new transaction, and returns a handle for managing it.
     * 
     * @param unbounded <code>true</code> if this transaction's timeout is
     *                  unbounded, <code>false</code> otherwise
     *
     * @return	a handle for managing the newly created transaction.
     */
    TransactionHandle createTransaction(boolean unbounded);
}
