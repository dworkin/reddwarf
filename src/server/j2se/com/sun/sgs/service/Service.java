
package com.sun.sgs.service;

import com.sun.sgs.kernel.ComponentRegistry;

import java.util.Properties;


/**
 * This is the base interface used for all services. Services support
 * specific funcationality and work in a transactional context. Each
 * service instance works on behalf of a single application, and can only
 * interact with other services working in the same application context.
 * See <code>TransactionParticipant</code> for details on when interaction
 * between <code>Service</code>s is allowed.
 * <p>
 * On startup of an application, services are created and configured. This
 * is done in two stages. First, all <code>Service</code>s are constructed
 * (see details below). This provides access to the non-transactional
 * aspects of the system. Second, all <code>Service</code>'s
 * <code>configure</code> methods are called in a transactional context.
 * The order in which the <code>Service</code>'s <code>configure</code>
 * methods are called is always to start with the <code>DataService</code>,
 * then <code>TaskService</code>, next the <code>ChannelManager</code>,
 * and finish with any custom <code>Service</code>s ordered based on the
 * application's configuration.
 * <p>
 * All implementations of <code>Service</code> must have a constructor of
 * the form (<code>Properties</code>, <code>ComponentRegistry</code>). This
 * is how the <code>Service</code> is created on startup. The
 * <code>Properties</code> parameter provides service-specific properties.
 * The <code>ComponentRegistry</code> provides access to non-transactional
 * kernel and system components like the <code>TaskScheduler</code>. If
 * there is an error, the constructor may throw any <code>Exception</code>,
 * and this will halt startup of the application.
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

    /**
     * This method should be called only once in the lifetime of any given
     * instance of <code>Service</code>. It is called by the kernel when
     * an application is starting up. The <code>ComponentRegistry</code>
     * provides access to any <code>Service</code>s that have already
     * finished their configuration, and are therefore available for use.
     * <p>
     * Note that this method is called in a transactional context, and
     * specifically in the same context in which all other
     * <code>Service</code>'s <code>configure</code> methods are called. If
     * this <code>Service</code> does anything during configuration that
     * requires it to vote or get notified if the transaction is aborted, it
     * should <code>join</code> the transaction as it would during normal
     * operation. If there is any error during configuration, and an
     * <code>Exception</code> is thrown that implements
     * <code>ExceptionRetryStatus</code>, then the system will consult
     * the <code>shouldRetry</code> method to see if configuration should
     * be attempted again. In most if not all cases, configuration should
     * be retried, since the alternative is to halt startup of the
     * application.
     *
     * @param registry the <code>ComponentRegistry</code> of already
     *                 configured <code>Service</code>s
     * @param proxy the proxy used to resolve details of the current
     *              transaction
     *
     * @throws IllegalStateException if this method has already been called
     */
    public void configure(ComponentRegistry registry, TransactionProxy proxy);

}
