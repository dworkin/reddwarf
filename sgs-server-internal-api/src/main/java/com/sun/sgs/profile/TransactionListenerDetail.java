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

package com.sun.sgs.profile;

/**
 * Provides profiling detail about a {@code TransactionListener} that
 * listened to the progress of a transaction.
 */
public interface TransactionListenerDetail {

    /**
     * Returns the name of the listener.
     *
     * @return the listener's name
     */
    String getListenerName();

    /**
     * Returns whether the listener's {@code beforeCompletion} method
     * was called.
     *
     * @return {@code true} if the listener's {@code beforeCompletion} method
     *         was called, {@code false} otherwise
     */
    boolean calledBeforeCompletion();

    /**
     * Returns whether the listener's {@code beforeCompletion} method
     * threw an exception thus aborting the transaction.
     *
     * @return {@code true} if the listener's {@code beforeCompletion} method
     *         aborted the transaction, {@code false} otherwise
     */
    boolean abortedBeforeCompletion();

    /**
     * Returns the length of time the listener spent in its
     * {@code beforeCompletion} call.
     *
     * @return the time in milliseconds spent before completing
     */
    long getBeforeCompletionTime();

    /**
     * Returns the length of time the listener spent in its
     * {@code afterCompletion} call.
     *
     * @return the time in milliseconds spent after completing
     */
    long getAfterCompletionTime();

}
