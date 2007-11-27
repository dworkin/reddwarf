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

package com.sun.sgs.test.util;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

/** Provides a simple implementation of TransactionProxy, for testing. */
public class DummyTransactionProxy implements TransactionProxy {

    /** Stores information about the transaction for the current thread. */
    private final ThreadLocal<DummyTransaction> threadTxn =
	new ThreadLocal<DummyTransaction>();

    /** The task owner. */
    private final DummyTaskOwner taskOwner = new DummyTaskOwner();

    /** Mapping from type to service. */
    private final Map<Class<? extends Service>, Service> services =
	new HashMap<Class<? extends Service>, Service>();

    /** Creates an instance of this class. */
    public DummyTransactionProxy() { }

    /* -- Implement TransactionProxy -- */

    public Transaction getCurrentTransaction() {
	Transaction txn = threadTxn.get();
	if (txn == null) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	txn.checkTimeout();
	return txn;
    }

    public TaskOwner getCurrentOwner() {
	return taskOwner;
    }

    public <T extends Service> T getService(Class<T> type) {
	Object service = services.get(type);
	if (service == null) {
	    throw new MissingResourceException(
		"Service of type " + type + " was not found",
		type.getName(), "Service");
	}
	return type.cast(service);
    }

    /* -- Other public methods -- */

    /**
     * Specifies the transaction object that will be returned for the current
     * transaction, or null to specify that no transaction should be
     * associated.  Also stores itself in the transaction instance, so that the
     * transaction can clear the current transaction on prepare, commit, or
     * abort.
     */
    public void setCurrentTransaction(DummyTransaction txn) {
	threadTxn.set(txn);
	if (txn != null) {
	    txn.proxy = this;
	}
    }

    /**
     * Specifies the service that should be returned for an exact match for
     * the specified type.
     */
    public <T extends Service> void setComponent(Class<T> type, T service) {
	if (type == null || service == null) {
	    throw new NullPointerException("Arguments must not be null");
	}
	services.put(type, service);
    }

}
