/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.service;

import com.sun.sgs.kernel.ComponentRegistry;


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
     * @throws RuntimeException if there is any trouble configuring this
     *                          <code>Service</code>
     */
    public void configure(ComponentRegistry registry, TransactionProxy proxy);

    /** 
     * Attempts to shut down this service, returning a value indicating whether
     * the attempt was successful.  The call will throw {@link
     * IllegalStateException} if a call to this method has already completed
     * with a return value of <code>true</code>. <p>
     *
     * This method does not require a transaction, and should not be called
     * from one because this method will typically not succeed if there are
     * outstanding transactions. <p>
     *
     * Typical implementations will refuse to accept calls associated with
     * transactions that were not joined prior to the <code>shutdown</code>
     * call by throwing an <code>IllegalStateException</code>, and will wait
     * for already joined transactions to commit or abort before returning,
     * although the precise behavior is implementation specific.
     * Implementations are also permitted, but not required, to return
     * <code>false</code> if {@link Thread#interrupt Thread.interrupt} is
     * called on a thread that is currently blocked within a call to this
     * method. <p>
     *
     * Callers should assume that, in a worst case, this method may block
     * indefinitely, and so should arrange to take other action (for example,
     * calling {@link System#exit System.exit}) if the call fails to complete
     * successfully in a certain amount of time.
     *
     * @return	<code>true</code> if the shut down was successful, else
     *		<code>false</code>
     * @throws	IllegalStateException if the <code>shutdown</code> method has
     *		already been called and returned <code>true</code>
     */
    public boolean shutdown();

}
