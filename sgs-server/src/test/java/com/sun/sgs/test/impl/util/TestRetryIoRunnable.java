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

package com.sun.sgs.test.impl.util;

import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.impl.util.RetryIoRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the {@link RetryIoRunnable} class. */
@RunWith(FilteredNameRunner.class)
public class TestRetryIoRunnable extends Assert {

    /* -- Tests -- */

    /* -- Test constructor -- */

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNegativeMaxRetry() {
	new SimpleRetryIoRunnable(-1, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNegativeRetryWait() {
	new SimpleRetryIoRunnable(1, -1);
    }

    /* -- Test run -- */

    @Test
    public void testRunShutdownRequestedBefore() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(100, 0);
	retry.shutdownRequested = true;
	retry.run();
	assertEquals(0, retry.calls);
    }

    @Test
    public void testRunShutdownRequestedDuring() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(100, 0) {
	    protected String callOnce() throws IOException {
		calls++;
		shutdownRequested = true;
		throw new IOException();
	    }
	};
	retry.run();
	assertEquals(1, retry.calls);
    }

    @Test
    public void testRunMaxRetry() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(300, 110) {
	    protected String callOnce() throws IOException {
		calls++;
		throw new IOException();
	    }
	};
	long start = System.currentTimeMillis();
	retry.run();
	assertEquals(3, retry.calls);
	assertTrue("Should have reported IOException: " + retry.exception,
		   retry.exception instanceof IOException);
	long time = System.currentTimeMillis() - start;
	assertTrue("Time should be greater than 210 and less than 300: " + time,
		   time > 210 && time < 300);
    }

    @Test
    public void testRunRetryWait() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(300, 110) {
	    private long lastCall = -1;
	    protected String callOnce() throws IOException {
		calls++;
		long now = System.currentTimeMillis();
		if (lastCall != -1) {
		    long delay = now - lastCall;
		    assertTrue("Delay should be no less than 110 and less" +
			       " than 130: " + delay,
			       delay >= 110 && delay < 130);
		}
		lastCall = now;
		throw new IOException();
	    }
	};
	retry.run();
	assertEquals(3, retry.calls);
	assertTrue("Should have reported IOException: " + retry.exception,
		   retry.exception instanceof IOException);
    }

    @Test
    public void testRunRetryable() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(100, 0) {
	    protected String callOnce() throws IOException {
		calls++;
		if (calls == 1) {
		    throw new TransactionAbortedException("");
		}
		return "ok";
	    }
	};
	retry.run();
	assertEquals(2, retry.calls);
	assertEquals("ok", retry.result);
    }

    @Test
    public void testRunNotRetryable() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(100, 0) {
	    protected String callOnce() throws IOException {
		calls++;
		throw new IllegalStateException();
	    }
	};
	retry.run();
	assertEquals(1, retry.calls);
	assertTrue("Should have reported IllegalStateException: " +
		   retry.exception,
		   retry.exception instanceof IllegalStateException);
    }

    @Test
    public void testRunRetry() {
	SimpleRetryIoRunnable retry = new SimpleRetryIoRunnable(100, 0) {
	    protected String callOnce() throws IOException {
		calls++;
		if (calls == 1) {
		    throw new IOException();
		}
		return "ok";
	    }
	};
	retry.run();
	assertEquals(2, retry.calls);
	assertEquals("ok", retry.result);
    }

    /* -- Other classes and methods -- */

    private static class SimpleRetryIoRunnable extends RetryIoRunnable<String> {
	int calls = 0;
	boolean shutdownRequested;
	String result;
	Throwable exception;
	SimpleRetryIoRunnable(long maxRetry, long retryWait) {
	    super(1, maxRetry, retryWait);
	}
	protected String callOnce() throws IOException {
	    calls++;
	    return "ok";
	}
	protected boolean shutdownRequested() {
	    return shutdownRequested;
	}
	protected void runWithResult(String result) {
	    this.result = result;
	}
	protected void reportFailure(Throwable exception) {
	    this.exception = exception;
	}
    }
}
