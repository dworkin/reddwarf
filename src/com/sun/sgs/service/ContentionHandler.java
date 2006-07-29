
package com.sun.sgs.service;


/**
 * This class handles contention and associated state with a single
 * <code>Transaction</code>. It is provided to <code>Service</code>s
 * to manage possible or definite contention cases.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ContentionHandler {

    /**
     * Returns the <code>Transaction</code> that is being represented
     * by this handler.
     *
     * @return the cooresponding <code>Transaction</code>
     */
    public Transaction getTransaction();

    /**
     * Resolves a conflict between the calling transaction and the given
     * transaction. One of these 
     * <p>
     * FIXME: should this also take some key that represents the object
     * being contended, so that multiple parties contending for the
     * same thing or rings of contention can be hendled? (this would
     * probably just be an Object that you can call <code>equals</code>
     * on).
     * FIXME: throws some kind of runtime exception
     */
    public boolean resolveConflict(Transaction conflictingTxn);

    /**
     * This must be called before a blocking call is made. If the operation
     * is not allowed then an exception is thrown that must not be
     * caught. If the method returns normally, then the caller is free
     * to execute the blocking call.
     * <p>
     * FIXME: this name needs to be improved
     * FIXME: this throws some kind of runtime exception
     */
    public void approveBlockingCall();

    /**
     * This may be called at any point to validate that continued execution
     * is allowed for the cooresponding transaction. If the operation
     * is not allowed then an exception is thrown that must not be
     * caught. If the method returns normally, then the caller is free
     * to execute the blocking call.
     * <p>
     * FIXME: this name needs to be improved
     * FIXME: throws some kind of runtime exception
     */
    public void approveContinue();

}
