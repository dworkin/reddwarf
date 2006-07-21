
package com.sun.sgs.service;

import com.sun.sgs.ManagedObject;


/**
 * This interface represents the meta-data associated with any transaction
 * in the system. Typically, this is used by <code>Service</code>s to
 * maintain transaction state and to resolve other participating services.
 * <p>
 * NOTE: Currently, this interface provides access to <code>Service</code>s
 * that have been made available for use in this transaction. The goal is
 * to provide different sets of services for each transaction (for instance,
 * if different applications have different services). We may scale back
 * this ability, in which case there would be a single global set of
 * services available, and then these accessor methods would be removed.
 * It's also possible that these might be further generalized to be
 * retrieved by some identifier.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface Transaction extends ManagedObject
{

    /**
     * Returns a unique identifier for this Transaction. This may be used
     * by <code>Service</code>s or other parties to maintain state
     * associated with this transaction.
     *
     * @return the transaction's identifier
     */
    public long getId();

    /**
     * Returns the <code>ChannelService</code> used by this Transaction.
     *
     * @return the available <code>ChannelService</code>
     */
    public ChannelService getChannelService();

    /**
     * Returns the <code>DataService</code> used by this Transaction.
     *
     * @return the available <code>DataService</code>.
     */
    public DataService getDataService();

    /**
     * Returns the <code>TaskService</code> used by this Transaction.
     *
     * @return the available <code>TaskService</code>.
     */
    public TaskService getTaskService();

    /**
     * Returns the <code>TimerService</code> used by this Transaction.
     *
     * @return the available <code>TimerService</code>.
     */
    public TimerService getTimerService();

    /**
     * Tells the <code>Transaction</code> that the given <code>Service</code>
     * is participating in the transaction. If a <code>Service</code> does
     * not join, then it's assumed that it did not take part in the
     * transaction.
     *
     * @param service the <code>Service</code> joining the transaction
     */
    public void join(Service service);

    /**
     * Commits the transaction. If this fails, an exception is thrown.
     * <p>
     * FIXME: what does this throw? There are probably 2 types of exceptions:
     * one where we can re-try, and one where we can't (although it's not
     * clear that the first case actually exists in this system).
     */
    public void commit() throws Exception;

    /**
     * Aborts the transaction.
     */
    public void abort();

}
