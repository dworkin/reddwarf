/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
