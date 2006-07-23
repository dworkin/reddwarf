
package com.sun.sgs.kernel.impl;

import com.sun.sgs.Quality;

import com.sun.sgs.kernel.TransactionCoordinator;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
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

    // TEST: an implementation of DataService
    private DataService dataService;

    // TEST: an implementation of TaskService
    private TaskService taskService;

    /**
     * Creates a new instance of <code>SimpleTransactionCoordinator</code>.
     * <p>
     * The parameters here is just for testing purposes...obviously this
     * would actually come from another facility, or would be provided
     * to this class, through a factory, with all the other available
     * services (more likely the former).
     */
    public SimpleTransactionCoordinator(DataService dataService,
                                        TaskService taskService) {
        serialGenerator = new AtomicLong(0);

        this.dataService = dataService;
        this.taskService = taskService;
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
                                     null, dataService, taskService, null);
    }

}
