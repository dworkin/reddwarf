/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.Exceptions;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;

/** Tests the {@link Exceptions} class. */
@RunWith(FilteredNameRunner.class)
public class TestExceptions extends Assert {

    /* -- Tests -- */

    /* -- Test throwUnchecked -- */

    @Test(expected=NullPointerException.class)
    public void testThrowUncheckedNull() {
	Exceptions.throwUnchecked(null);
    }

    @Test
    public void testThrowUncheckedUncheckedException() {
	Exception exception = new IllegalArgumentException();
	try {
	    Exceptions.throwUnchecked(exception);
	} catch (IllegalArgumentException e) {
	    assertSame(exception, e);
	}
    }

    @Test
    public void testThrowError() {
	Error exception = new UnknownError();
	try {
	    Exceptions.throwUnchecked(exception);
	} catch (UnknownError e) {
	    assertSame(exception, e);
	}
    }

    @Test
    public void testThrowCheckedException() {
	Exception exception = new IOException();
	try {
	    Exceptions.throwUnchecked(exception);
	} catch (AssertionError e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testThrowCheckedThrowable() {
	Throwable exception = new MyThrowable();
	try {
	    Exceptions.throwUnchecked(exception);
	} catch (AssertionError e) {
	    System.err.println(e);
	}
    }

    private static class MyThrowable extends Throwable { }
}
