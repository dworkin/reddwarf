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
 */

package com.sun.sgs.test.util;

import com.sun.sgs.service.TransactionListener;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Provides a simple implementation of TransactionListener, for testing. */
public class DummyTransactionListener implements TransactionListener {

    /** Whether afterCompletion was called and how. */
    public enum CalledAfter {
	/** Not called. */
	NO,
	/** Called with commit=true. */
	COMMIT,
	/** Called with commit=false. */
	ABORT;
    }

    /** Whether beforeCompletion was called. */
    private boolean beforeCalled;

    /** Whether afterCompletion was called and how. */
    private CalledAfter afterCalled = CalledAfter.NO;

    /** The exception to throw from beforeCompletion or null. */
    private final RuntimeException failBefore;

    /** The exception to throw from afterCompletion or null. */
    private final RuntimeException failAfter;

    /** Creates a listener that throws no exceptions. */
    public DummyTransactionListener() {
	this(null, null);
    }

    /**
     * Creates a listener whose beforeCompletion and afterCompletion methods
     * throws the specified exceptions if the are not null.
     */
    public DummyTransactionListener(
	RuntimeException failBefore, RuntimeException failAfter)
    {
	this.failBefore = failBefore;
	this.failAfter = failAfter;
    }

    /** {@inheritDoc} */
    public synchronized void beforeCompletion() {
	if (beforeCalled) {
	    fail("beforeCompletion called twice");
	}
	beforeCalled = true;
	if (failBefore != null) {
	    throw failBefore;
	}
    }

    /** {@inheritDoc} */
    public synchronized void afterCompletion(boolean commit) {
	if (afterCalled != CalledAfter.NO) {
	    fail("afterCompletion called twice");
	}
	afterCalled = commit ? CalledAfter.COMMIT : CalledAfter.ABORT;
	if (failAfter != null) {
	    throw failAfter;
	}
    }

    /** {@inheritDoc} */
    public String getTypeName() {
        return DummyTransactionListener.class.getName();
    }

    /** Checks that and afterCompletion was called as specified. */
    public synchronized void assertCalledAfter(
	CalledAfter assertCalledAfter)
    {
	assertSame(assertCalledAfter, afterCalled);
    }

    /**
     * Checks that beforeCompletion and afterCompletion were called as
     * specified.
     */
    public synchronized void assertCalled(
	boolean assertCalledBefore, CalledAfter assertCalledAfter)
    {
	if (assertCalledBefore) {
	    assertTrue("beforeCompletion was not called", beforeCalled);
	} else {
	    assertFalse("beforeCompletion was called", beforeCalled);
	}
	assertCalledAfter(assertCalledAfter);
    }
}
