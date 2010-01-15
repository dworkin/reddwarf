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

import com.sun.sgs.impl.util.Int30;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import junit.framework.TestCase;
import org.junit.runner.RunWith;

/** Test the Int30 class. */
@RunWith(FilteredJUnit3TestRunner.class)
public class TestInt30 extends TestCase {
    ByteArrayOutputStream out;

    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
    }

    /* -- Tests -- */

    public void testWriteBadArgs() throws Exception {
	OutputStream out = new ByteArrayOutputStream();
	try {
	    Int30.write(-1, out);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    Int30.write(1<<30, out);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    Int30.write(1, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testReadBadArgs() throws Exception {
	try {
	    Int30.read(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testReadEOF() throws Exception {
	byte[][] tests = {
	    { },
	    { 0x44 },
	    { (byte) 0x81, (byte) 0xff },
	    { (byte) 0xcf, 0x12, 0x34 }
	};
	for (byte[] bytes : tests) {
	    InputStream in = new ByteArrayInputStream(bytes);
	    try {
		Int30.read(in);
		fail("Expected IOException");
	    } catch (IOException e) {
		System.err.println(e);
	    }
	}
    }

    public void testValues() throws Exception {
	testValue(0, (byte) 0);
	testValue(1, (byte) 1);
	testValue(0x3e, (byte) 0x3e);
	testValue(0x3f, (byte) 0x3f);
	testValue(0x40, (byte) 0x40, (byte) 0x40);
	testValue(0xef, (byte) 0x40, (byte) 0xef);
	testValue(0xfed, (byte) 0x4f, (byte) 0xed);
	testValue(0x3f3a, (byte) 0x7f, (byte) 0x3a);
	testValue(0x4000, (byte) 0x80, (byte) 0x40, (byte) 0);
	testValue(0xfedc, (byte) 0x80, (byte) 0xfe, (byte) 0xdc);
	testValue(0xfedcb, (byte) 0x8f, (byte) 0xed, (byte) 0xcb);
	testValue(0x3edcba, (byte) 0xbe, (byte) 0xdc, (byte) 0xba);
	testValue(0x400000, (byte) 0xc0, (byte) 0x40, (byte) 0, (byte) 0);
	testValue(0xfedcba,
		  (byte) 0xc0, (byte) 0xfe, (byte) 0xdc, (byte) 0xba);
	testValue(0x1234567,
		  (byte) 0xc1, (byte) 0x23, (byte) 0x45, (byte) 0x67);
	testValue(0x3456789a,
		  (byte) 0xf4, (byte) 0x56, (byte) 0x78, (byte) 0x9a);
	testValue(0x3fffffff,
		  (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff);
    }

    /* -- Other methods -- */

    private static void testValue(int n, byte... bytes) throws IOException {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	Int30.write(n, out);
	byte[] encoding = out.toByteArray();
	assertEquals("Wrong encoding:",
		     Arrays.toString(bytes), Arrays.toString(encoding));
	ByteArrayInputStream in = new ByteArrayInputStream(encoding);
	int result = Int30.read(in);
	assertEquals(n, result);
    }
}

