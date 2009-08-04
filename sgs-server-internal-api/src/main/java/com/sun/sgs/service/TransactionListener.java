/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.service;

/**
 * A listener to be notified before and after the completion of a
 * transaction. <p>
 *
 * Callers can register {@code TransactionListener}s to learn about the
 * completion of a transaction without joining the transaction as a {@link
 * TransactionParticipant}.  Because {@link Service}s may depend on operations
 * performed by the {@link #beforeCompletion
 * TransactionListener.beforeCompletion} methods of listeners that they
 * register, {@code beforeCompletion} methods should not make calls to other
 * services, even though the transaction is still considered active when {@code
 * beforeCompletion} is called.  The results of such calls are unspecified.
 *
 * @see	Transaction#registerListener Transaction.registerListener
 */
public interface TransactionListener {

    /**
     * Called after the main work of the transaction is complete, just before
     * the transaction is prepared.  The transaction is still considered active
     * when this method is called, although calls should not be made to
     * independent {@link Service}s.  If the transaction has multiple
     * transaction listeners registered, the order in which the listeners are
     * called is unspecified.<p>
     *
     * If this method throws an exception, then the transaction will be
     * aborted, and the exception will be treated as if it were thrown by the
     * main body of the transaction. <p>
     *
     * This method will not be called if the transaction is aborted before it
     * reaches the preparation stage, including if an earlier call to this
     * method on another listener throws an exception or aborts the
     * transaction.
     *
     * @throws	RuntimeException if the transaction should be aborted
     */
    void beforeCompletion();

    /**
     * Called after a transaction is committed or aborted.  The parameter will
     * be set to {@code true} if the transaction committed, else {@code false}
     * if it aborted.  If the transaction has multiple transaction listeners
     * registered, the order in which the listeners are called is unspecified.
     *
     * @param	committed {@code true} if the transaction committed, else
     *		{@code false} if it aborted
     */
    void afterCompletion(boolean committed);

    /**
     * Returns the fully qualified type name of the listener. Typically this
     * is the implementing class or some other distinguishing name that will
     * remain constant between transactions.
     * 
     * @return the name of the listener
     */
    String getTypeName();

}
