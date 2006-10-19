
package com.sun.sgs.service;


/**
 * This is the base interface used for all services. Services support
 * specific funcationality and work in a transactional context. Each
 * service instance works on behalf of a single application, and can only
 * interact with other services working in the same application context.
 * See <code>TransactionParticipant</code> for details on when interaction
 * between <code>Service</code>s is allowed.
 * <p>
 * Note that this is where startup configuration methods will be added
 * once we understand how this process works. Included in this is access
 * to implementation-specific properties, a <code>TransactionProxy</code>
 * instance, and the other <code>Service</code>s available to this
 * <code>Service</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Service {

    /**
     * Returns the name used to identify this service.
     *
     * @return the service's name
     */
    public String getName();

}
