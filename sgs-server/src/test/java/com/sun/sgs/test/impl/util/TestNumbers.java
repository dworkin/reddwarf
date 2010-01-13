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

import com.sun.sgs.impl.util.Numbers;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link Numbers} class. */
@RunWith(FilteredNameRunner.class)
public class TestNumbers extends Assert {

    /* -- Tests -- */

    @Test
    public void testAddCheckOverflowIllegalArgs() {
	try {
	    Numbers.addCheckOverflow(-1, 0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    Numbers.addCheckOverflow(Long.MIN_VALUE, 0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    Numbers.addCheckOverflow(0, -1);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    Numbers.addCheckOverflow(0, Long.MIN_VALUE);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddCheckOverflowMisc() {
	assertEquals(0, Numbers.addCheckOverflow(0, 0));
	assertEquals(5, Numbers.addCheckOverflow(2, 3));
	assertEquals(Long.MAX_VALUE,
		     Numbers.addCheckOverflow(Long.MAX_VALUE, 1));
	assertEquals(Long.MAX_VALUE,
		     Numbers.addCheckOverflow(Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
