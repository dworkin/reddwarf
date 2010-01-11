/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
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
