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

import com.sun.sgs.impl.util.ShouldRetryIo;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the {@link ShouldRetryIo} class. */
@RunWith(FilteredNameRunner.class)
public class TestShouldRetryIo extends Assert {

    /* -- Tests -- */

    /* -- Test constructor -- */

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNegativeMaxRetry() {
	new ShouldRetryIo(-1, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorNegativeRetryWait() {
	new ShouldRetryIo(1, -1);
    }

    /* -- Test shouldRetry -- */

    @Test
    public void testShouldRetryMaxRetry() throws InterruptedException {
	ShouldRetryIo should = new ShouldRetryIo(300, 110);
	long start = System.currentTimeMillis();
	long stop = start + 300;
	while (stop > System.currentTimeMillis() + 110) {
	    assertTrue(should.shouldRetry());
	}
	assertFalse(should.shouldRetry());
    }

    @Test
    public void testShouldRetryRetryWait() throws InterruptedException {
	ShouldRetryIo should = new ShouldRetryIo(300, 100);
	long stop = System.currentTimeMillis() + 300;
	while (stop < System.currentTimeMillis()) {
	    long now = System.currentTimeMillis();
	    should.shouldRetry();
	    long delay = System.currentTimeMillis() - now;
	    assertTrue("Delay should be greater than 90 and less than 110: " +
		       delay,
		       delay > 90 && delay < 110);
	}
    }

    /* -- Test ioSucceeded -- */

    @Test
    public void testIoSucceeded() throws InterruptedException {
	ShouldRetryIo should = new ShouldRetryIo(100, 0);
	assertTrue(should.shouldRetry());
	Thread.sleep(110);
	should.ioSucceeded();
	assertTrue(should.shouldRetry());
    }
}
