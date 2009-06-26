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

package com.sun.sgs.test.impl.util;

import com.sun.sgs.impl.util.TransactionContext;
import com.sun.sgs.impl.util.TransactionContextFactory;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import junit.framework.TestCase;
import org.junit.runner.RunWith;

/** Test the TransactionContextFactory class. */
@RunWith(FilteredJUnit3TestRunner.class)
public class TestTransactionContextFactory extends TestCase {

    /** Creates an instance of this class. */
    public TestTransactionContextFactory(String name) {
	super(name);
    }

    /* -- Tests -- */

    /**
     * Test that a failure in isPrepared during commit doesn't cause the commit
     * to fail to clear the thread context.
     */
    public void testIsPreparedFailsDuringCommit() throws Exception {
	DummyTransactionProxy txnProxy = new DummyTransactionProxy();
	TransactionContextFactory<TransactionContext> contextFactory =
	    new TransactionContextFactory<TransactionContext>(txnProxy, 
                                          "TestTransactionContextFactory") 
            {
	        protected TransactionContext createContext(Transaction txn) {
		    return new TransactionContext(txn) {
			public boolean isPrepared() {
			    if (isPrepared) {
				throw new RuntimeException(
				    "isPrepared fails during commit");
			    } else {
				return false;
			    }
			}
			public void abort(boolean retryable) { }
			public void commit() { }
		    };
		}
	    };
	DummyTransaction txn = new DummyTransaction(
	    DummyTransaction.UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	contextFactory.joinTransaction();
	txn.commit();
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	contextFactory.joinTransaction();
	txn.commit();
    }
}
