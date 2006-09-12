
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.Quality;

import com.sun.sgs.service.TransactionCoordinator;

import com.sun.sgs.service.Transaction;

import com.sun.sgs.impl.service.SimpleTransaction;

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
        return new SimpleTransaction(serialGenerator.getAndIncrement());
    }

}
