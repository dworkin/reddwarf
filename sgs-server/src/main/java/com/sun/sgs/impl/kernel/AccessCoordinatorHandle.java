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
 */
package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.AccessCoordinator;
import com.sun.sgs.service.Transaction;

/**
 * A subinterface of {@code AccessCoordinator} that the kernel uses to notify
 * the access coordinator of new transactions.
 */
public interface AccessCoordinatorHandle extends AccessCoordinator {
    
    /** 
     * Notifies the coordinator that a new transaction is starting. 
     *
     * @param	txn the transaction
     * @param	requestedStartTime the time in milliseconds that the task
     *		associated with the transaction was originally requested to
     *		start
     * @param	tryCount the number of times that transactions have been
     *		attempted for the task associated with {@code txn}
     * @throws	IllegalArgumentException if {@code requestedStartTime} is less
     *		than {@code 0} or {@code tryCount} is less than {@code 1}
     * @throws	IllegalStateException if this transaction has already been
     *		started
     */
    void notifyNewTransaction(
	Transaction txn, long requestedStartTime, int tryCount);
}
