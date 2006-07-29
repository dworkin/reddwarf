
package com.sun.sgs.service;


/**
 * This type of <code>Service</code> handles contention for shared resources.
 * Note that there is no cooresponding manager because
 * <code>ContentionService</code> is an internal mechanism used only by
 * other <code>Service</code>s.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface ContentionService extends Service {

    /**
     * Returns the <code>ContentionHandler</code> used for the given
     * <code>Transaction</code>.
     * <p>
     * NOTE: Currently we haven't specified how communications works between
     * nodes, so we don't know how services share information or how
     * transactions on one node are identified on other nodes. This needs
     * to be defined to make contention work across nodes (obviously). This
     * said, the assumption is that we will use some abstractaction like
     * different implementations of <code>Transaction</code> to represent
     * local and remote parties, and therefore this method's signature
     * is probably correct.
     *
     * @param txn the transaction state
     *
     * @return the handler for this transaction
     */
    public ContentionHandler getContentionHandler(Transaction txn);

}
