
package com.sun.sgs.service.impl;

import com.sun.sgs.service.NotPreparedException;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;

import java.util.HashSet;


/**
 * This is a simple implementation of <code>Transaction</code>.
 * <p>
 * FIXME: This needs to keep state so we can only commit/abort once, but
 * that can't be added until we figure out what <code>commit</code> actually
 * returns, and how we're allowed to recover from that (e.g., an exception
 * that allows us to try committing again).
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleTransaction implements Transaction {

    // our unique identifier
    private long id;

    // the set of services that actually joined this transaction
    private HashSet<Service> joinedServices;

    /**
     * Returns a new instance of <code>SimpleTransaction</code>.
     *
     * @param id a unique identifier for this transaction
     */
    public SimpleTransaction(long id) {
        this.id = id;

        joinedServices = new HashSet<Service>();
    }

    /**
     * Returns a unique identifier for this Transaction. This may be used
     * by <code>Service</code>s or other parties to maintain state
     * associated with this transaction.
     *
     * @return the transaction's identifier
     */
    public long getId() {
        return id;
    }

    /**
     * Tells the <code>Transaction</code> that the given <code>Service</code>
     * is participating in the transaction. If a <code>Service</code> does
     * not join, then it's assumed that it did not take part in the
     * transaction.
     *
     * @param service the <code>Service</code> joining the transaction
     */
    public void join(Service service) {
        joinedServices.add(service);
    }

    /**
     * Commits the transaction. If this fails, an exception is thrown. This
     * simply iterates over the joined services calling prepare on each,
     * and if that succeeds, the services are iterated over again and commit
     * is called on each. If there is only one joined service, then
     * <code>prepareAndCommit</code> is called directly.
     * <p>
     * FIXME: what does this throw?
     */
    public void commit() throws Exception {
        // see if any services actually joined this transaction
        if (joinedServices.isEmpty())
            return;

        // see if there is only one service
        if (joinedServices.size() == 1) {
            joinedServices.iterator().next().prepareAndCommit(this);
            return;
        }

        try {
            // iterate through all the services and prepare them
            for (Service service : joinedServices)
                service.prepare(this);
        } catch (Exception e) {
            // abort all the services and re-throw the exeption
            abort();
            throw e;
        }

        try {
            // iterate through all the services and commit them
            for (Service service : joinedServices)
                service.commit(this);
        } catch (NotPreparedException npe) {
            // NOTE: this shouldn't actually happen, since we prepare all
            // the joined services before this step...if it does happen,
            // then a service has been incorrectly implemented, and there
            // is no way to recover anyway
            // FIXME: This needs to be severely logged
            throw npe;
        }
    }

    /**
     * Aborts the transaction by aborting all joined services.
     */
    public void abort() {
        for (Service service : joinedServices)
            service.abort(this);
    }

}
