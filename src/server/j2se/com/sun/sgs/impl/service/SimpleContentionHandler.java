
package com.sun.sgs.impl.service;

import com.sun.sgs.service.ContentionHandler;
import com.sun.sgs.service.Transaction;


/**
 * A simple implementation of <code>ContentionHandler</code>. Note that
 * at present it always approves calls and always sides with the
 * existing lock-holder.
 * <p>
 * NOTE: We had toyed with the idea of having a sleep-like call on here
 * that would either sleep or divvy up task time, but that seems pretty
 * complicated, and even more so given the mechanics required to provide
 * some waking call. Therefore, we decided this would be better placed
 * on the Resource Manager. This is still an open issue to investigate.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleContentionHandler implements ContentionHandler {

    // the managing service
    private SimpleContentionService contentionService;

    // the cooresponding transaction
    private Transaction transaction;

    /**
     * Creates an instance of <code>SimpleContentionService</code>.
     *
     * @param contentionService the manageing service for this handler
     * @param transaction the cooresponding transaction state
     */
    public SimpleContentionHandler(SimpleContentionService contentionService,
                                   Transaction transaction) {
        this.contentionService = contentionService;
        this.transaction = transaction;
    }

    /**
     * Returns the <code>Transaction</code> that is being represented
     * by this handler.
     *
     * @return the cooresponding <code>Transaction</code>
     */
    public Transaction getTransaction() {
        return transaction;
    }

    /**
     * Resolves a conflict between the calling transaction and the given
     * transaction. One of these 
     */
    public boolean resolveConflict(Transaction conflictingTxn) {
        return false;
    }

    /**
     * This must be called before a blocking call is made. If the operation
     * is not allowed then an exception is thrown that must not be
     * caught. If the method returns normally, then the caller is free
     * to execute the blocking call.
     */
    public void approveBlockingCall() {
        
    }

    /**
     * This may be called at any point to validate that continued execution
     * is allowed for the cooresponding transaction. If the operation
     * is not allowed then an exception is thrown that must not be
     * caught. If the method returns normally, then the caller is free
     * to execute the blocking call.
     */
    public void approveContinue() {
        
    }

}
