
package com.sun.sgs.kernel.impl;

import com.sun.sgs.Quality;

import com.sun.sgs.kernel.TransactionCoordinator;

import com.sun.sgs.service.Transaction;

import com.sun.sgs.service.impl.SimpleTransaction;

import java.util.concurrent.atomic.AtomicLong;


/**
 * This is a simple implementation of <code>TransactionCoordinator</code>
 * that makes no attempt to optimize the type of <code>Transaction</code>
 * based on input parameters.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleTransactionCoordinator implements TransactionCoordinator {
    
    // generator for the transaction serial identifiers
    private AtomicLong serialGenerator;

    /**
     * Creates a new instance of <code>SimpleTransactionCoordinator</code>.
     */
    public SimpleTransactionCoordinator() {
        serialGenerator = new AtomicLong(0);
    }

    /**
     * Returns a new instance of <code>Transaction</code>.
     *
     * @param quality the Quality of Service parameters
     *
     * @return a new <code>Transaction</code>
     */
    public Transaction getTransaction(Quality quality) {
        // FIXME: we actually need some kind of factory mechanism here
        // so that the parameters can be provided in a general way,
        // or so that a reference to the coordinator is provided which
        // in turn provides these values
        return new SimpleTransaction(serialGenerator.getAndIncrement(),
                                     null, null, null, null);
    }

}
