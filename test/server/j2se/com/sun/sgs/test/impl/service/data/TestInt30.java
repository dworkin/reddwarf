/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.impl.service.data.Int30;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import junit.framework.TestCase;

/** Test the Int30 class. */
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

