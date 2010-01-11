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
 * --
 */

package com.sun.sgs.impl.service.transaction;

import com.sun.sgs.profile.TransactionListenerDetail;


/**
 * Simple implementation of {@code TransactionListenerDetail} that
 * is package-private and used by {@code TransactionImpl} to report
 * detail associated with each listener.
 */
class TransactionListenerDetailImpl implements TransactionListenerDetail {

    // the name of the listener
    private final String name;

    // whether the beforeCompletion call was made, and if threw an exception
    private boolean beforeCompletionCalled = false;
    private boolean beforeCompletionFailed = false;

    // the length of time spent before and after completion
    private long beforeCompletionTime = 0L;
    private long afterCompletionTime = 0L;

    /**
     * Creates an instance of {@code TransactionListenerDetailImpl} for the
     * given named listener.
     *
     * @param listenerName the name of the listener associated with this detail
     */
    TransactionListenerDetailImpl(String listenerName) {
        if (listenerName == null) {
            throw new NullPointerException("name must not be null");
        }
        this.name = listenerName;
    }

    /* Implement TransactionListenerDetail. */

    /** {@inheritDoc} */
    public String getListenerName() {
        return name;
    }

    /** {@inheritDoc} */
    public boolean calledBeforeCompletion() {
        return beforeCompletionCalled;
    }

    /** {@inheritDoc} */
    public boolean abortedBeforeCompletion() {
        return beforeCompletionFailed;
    }

    /** {@inheritDoc} */
    public long getBeforeCompletionTime() {
        return beforeCompletionTime;
    }

    /** {@inheritDoc} */
    public long getAfterCompletionTime() {
        return afterCompletionTime;
    }

    /* Package-private modifier methods. */

    /**
     * Sets that the listener's {@code beforeCompletion} method was called,
     * whether it failed, and how long the call took
     */
    void setCalledBeforeCompletion(boolean failed, long time) {
        beforeCompletionCalled = true;
        beforeCompletionFailed = failed;
        beforeCompletionTime = time;
    }

    /**
     * Sets that the listener's {@code afterCompletion} method was called
     * and how long the call took.
     */
    void setCalledAfterCompletion(long time) {
        afterCompletionTime = time;
    }

}
