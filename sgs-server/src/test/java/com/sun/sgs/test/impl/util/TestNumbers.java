/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
