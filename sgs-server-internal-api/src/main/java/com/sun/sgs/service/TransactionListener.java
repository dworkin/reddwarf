/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
