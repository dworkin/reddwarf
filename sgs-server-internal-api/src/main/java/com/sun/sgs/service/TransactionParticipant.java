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
 * This interface is used by participants in transactions. Typically, each
 * implementation of <code>Service</code> will either implement
 * <code>TransactionParticipant</code> directly, or use some proxy as
 * their participant. Classes that implement
 * <code>TransactionParticipant</code> must also implement
 * <code>Serializable</code>.
 * <p>
 * Note that the general model assumes that <code>Service</code>s will use
 * each other during a transaction. For instance, most <code>Service</code>s
 * will use the <code>DataService</code> to persist data. However once the
 * transaction begins to prepare or is aborted (i.e., once any of the methods
 * defined here are called on a participant), a <code>Service</code> may
 * not interact with any other <code>Service</code> in the context of that
 * transaction. Doing so results in unspecified behavior.
 * <p>
 * This interface does not specify how transaction participants learn the
 * outcome of prepared transactions following a crash.  Doing so requires a
 * separate interaction between the participant and the transaction coordinator
 * that is not specified by this interface.  Without that additional
 * communication, this interface is sufficient to support transactions with at
 * most one durable transaction participant.
 *
 * @see NonDurableTransactionParticipant
 */
public interface TransactionParticipant {

    /**
     * Tells the participant to prepare for commiting its state associated
     * with the given transaction. This method returns a <code>boolean</code>
     * flag stating whether the prepared state is read-only, meaning that no
     * external state is modified by this participant. If this method
     * returns true, then neither <code>commit</code> nor <code>abort</code>
     * will be called.
     * <p>
     * If this method throws an exception, then the preparation failed, and
     * the transaction will be aborted. If this method completes successfully,
     * then the participant is required to be able to commit the transaction
     * without failure.
     *
     * @param txn the <code>Transaction</code> object
     *
     * @return true if this participant is read-only, false otherwise
     *
     * @throws Exception if there are any failures in preparing
     * @throws IllegalStateException if this participant has already been
     *                               prepared, committed, or aborted, or
     *                               if this participant is not participating
     *                               in the given transaction
     */
    boolean prepare(Transaction txn) throws Exception;

    /**
     * Tells the participant to commit its state associated with the given
     * transaction.
     *
     * @param txn the <code>Transaction</code> object
     *
     * @throws IllegalStateException if this participant was not previously
     *                               prepared, or if this participant has
     *                               already committed or aborted, or
     *                               if this participant is not participating
     *                               in the given transaction
     */
    void commit(Transaction txn);

    /**
     * Tells the participant to both prepare and commit its state associated
     * with the given transaction. 
     *
     * @param txn the <code>Transaction</code> object
     *
     * @throws Exception if there are any failures in preparing
     * @throws IllegalStateException if this participant has already been
     *                               prepared, committed, or aborted, or
     *                               if this participant is not participating
     *                               in the given transaction
     */
    void prepareAndCommit(Transaction txn) throws Exception;

    /**
     * Tells the participant to abort its involvement with the given
     * transaction.
     *
     * @param txn the <code>Transaction</code> object
     *
     * @throws IllegalStateException if this participant has already been
     *                               aborted or committed, or if this
     *                               participant is not participating in
     *                               the given transaction
     */
    void abort(Transaction txn);
    
    /**
     * Returns the fully qualified type name of the participant.
     * If this participant is acting as a proxy for a {@code Service}, this
     * will typically be the {@code Service}'s type name. 
     * 
     * @return the name of the participant
     */
    String getTypeName();
}
