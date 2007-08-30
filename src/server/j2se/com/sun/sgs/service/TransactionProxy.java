/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.service;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;

import com.sun.sgs.kernel.TaskOwner;


/**
 * This is a proxy that provides access to the current transaction and
 * its owner. Note that there is only ever one instance of
 * <code>TransactionProxy</code>.
 */
public interface TransactionProxy {

    /**
     * Returns the current transaction state.
     *
     * @return the current <code>Transaction</code>
     *
     * @throws TransactionNotActiveException if there is no current, active
     *                                       transaction, or if the current
     *                                       transaction has already started
     *                                       preparing or aborting
     * @throws TransactionTimeoutException if the current transaction has
     *                                     timed out
     */
    public Transaction getCurrentTransaction();

    /**
     * Returns the owner of the task that is executing the current
     * transaction.
     *
     * @return the current transaction's <code>TaskOwner</code>
     */
    public TaskOwner getCurrentOwner();

    /**
     * Returns a <code>Service</code>, based on the given type, that is
     * available in the context of the current <code>Transaction</code>. If
     * the type is unknown, or if there is more than one <code>Service</code>
     * of the given type, <code>MissingResourceException</code> is thrown.
     *
     * @param <T> the type of the <code>Service</code>
     * @param type the <code>Class</code> of the requested <code>Service</code>
     *
     * @return the requested <code>Service</code>
     *
     * @throws MissingResourceException if there wasn't exactly one match to
     *                                  the requested type
     */
    <T extends Service> T getService(Class<T> type);

}
