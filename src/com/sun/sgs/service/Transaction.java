
package com.sun.sgs.service;

import com.sun.sgs.ManagedObject;


/**
 * This interface represents the meta-data associated with any transaction
 * in the system. Typically, this is used by <code>Service</code>s to
 * maintain transaction state.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface Transaction extends ManagedObject
{

    /**
     * Returns a unique identifier for this <code>Transaction</code>. This
     * may be used by <code>Service</code>s or other parties to maintain
     * state associated with this transaction.
     * <p>
     * FIXME: we should decide if we actually want to expose this, or
     * whether transactions are simply hashable
     *
     * @return the transaction's identifier
     */
    public long getId();

    /**
     * Returns the time at which this <code>Transaction</code> was created.
     *
     * @return the creation time-stamp
     */
    public long getTimeStamp();

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
