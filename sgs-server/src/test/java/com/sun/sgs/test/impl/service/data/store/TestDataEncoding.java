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

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.impl.service.data.store.DataEncoding;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.util.Arrays;
import junit.framework.TestCase;
import org.junit.runner.RunWith;

/** Test the DataEncoding class. */
@RunWith(FilteredJUnit3TestRunner.class)
public class TestDataEncoding extends TestCase {

    /** The byte value 0 */
    private static final byte zero = 0;

    /** The byte value 0xff */
    private static final byte ff = (byte) 0xff;

    /** Creates the test. */
    public TestDataEncoding(String name) {
	super(name);
    }

    /** Prints the test case */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
    }

    /* -- Tests -- */

    /* -- Test encodeShort -- */

    public void testEncodeShort() {
	verifyEncodeDecodeShort((short) 0, b(0x80), zero);
	verifyEncodeDecodeShort((short) -1, b(0x7f), ff);
	verifyEncodeDecodeShort(Short.MIN_VALUE, zero, zero);
	verifyEncodeDecodeShort(Short.MAX_VALUE, ff, ff);
	verifyEncodeDecodeShort((short) 0x1234, b(0x92), b(0x34));
    }

    private static void verifyEncodeDecodeShort(short n, byte... b) {
	assertEquals(2, b.length);
	byte[] bytes = DataEncoding.encodeShort(n);
	assertEquals(2, bytes.length);
	assertEquals(Arrays.toString(b), Arrays.toString(bytes));
	short decoded = DataEncoding.decodeShort(bytes);
	assertEquals(n, decoded);
    }

    /* -- Test decodeShort -- */

    public void testDecodeShort() {
	try {
	    DataEncoding.decodeShort(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	byte[] bytes = new byte[0];
	try {
	    DataEncoding.decodeShort(bytes);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[1];
	try {
	    DataEncoding.decodeShort(bytes);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[3];
	try {
	    DataEncoding.decodeShort(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test encodeInt -- */

    public void testEncodeInt() {
	verifyEncodeDecodeInt(0, b(0x80), zero, zero, zero);
	verifyEncodeDecodeInt(-1, b(0x7f), ff, ff, ff);
	verifyEncodeDecodeInt(Integer.MIN_VALUE, zero, zero, zero, zero);
	verifyEncodeDecodeInt(Integer.MAX_VALUE, ff, ff, ff, ff);
	verifyEncodeDecodeInt(0x12345678, b(0x92), b(0x34), b(0x56), b(0x78));
    }

    private static void verifyEncodeDecodeInt(int n, byte... b) {
	assertEquals(4, b.length);
	byte[] bytes = DataEncoding.encodeInt(n);
	assertEquals(4, bytes.length);
	assertEquals(Arrays.toString(b), Arrays.toString(bytes));
	int decoded = DataEncoding.decodeInt(bytes);
	assertEquals(n, decoded);
    }

    public void testEncodeIntSubseq() {
	try {
	    DataEncoding.encodeInt(0, null, 0);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	byte[] bytes = new byte[6];
	try {
	    DataEncoding.encodeInt(0, bytes, -1);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	try {
	    DataEncoding.encodeInt(0, bytes, 6);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[3];
	try {
	    DataEncoding.encodeInt(0, bytes, 0);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };
	DataEncoding.encodeInt(0x89abcdef, bytes, 2);
	byte[] shouldBe = { 0, 1, 0x09, b(0xab), b(0xcd), b(0xef), 6, 7 };
	assertEquals(Arrays.toString(shouldBe), Arrays.toString(bytes));
	assertEquals(0x89abcdef, DataEncoding.decodeInt(bytes, 2));
    }

    /* -- Test decodeInt -- */

    public void testDecodeInt() {
	try {
	    DataEncoding.decodeInt(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	byte[] bytes = new byte[0];
	try {
	    DataEncoding.decodeInt(bytes);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[3];
	try {
	    DataEncoding.decodeInt(bytes);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[5];
	try {
	    DataEncoding.decodeInt(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testDecodeIntSubseq() {
	try {
	    DataEncoding.decodeInt(null, 0);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	byte[] bytes = new byte[10];
	try {
	    DataEncoding.decodeInt(bytes, -1);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	try {
	    DataEncoding.decodeInt(bytes, 10);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[3];
	try {
	    DataEncoding.decodeInt(bytes, 0);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
    }

    /* -- Test encodeLong -- */

    public void testEncodeLong() {
	verifyEncodeDecodeLong(
	    0, b(0x80), zero, zero, zero, zero, zero, zero, zero);
	verifyEncodeDecodeLong(-1, b(0x7f), ff, ff, ff, ff, ff, ff, ff);
	verifyEncodeDecodeLong(
	    Long.MIN_VALUE, zero, zero, zero, zero, zero, zero, zero, zero);
	verifyEncodeDecodeLong(Long.MAX_VALUE, ff, ff, ff, ff, ff, ff, ff, ff);
	verifyEncodeDecodeLong(0x123456789abcdef0l, b(0x92), b(0x34), b(0x56),
			 b(0x78), b(0x9a), b(0xbc), b(0xde), b(0xf0));
    }

    private static void verifyEncodeDecodeLong(long n, byte... b) {
	assertEquals(8, b.length);
	byte[] bytes = DataEncoding.encodeLong(n);
	assertEquals(8, bytes.length);
	assertEquals(Arrays.toString(b), Arrays.toString(bytes));
	long decoded = DataEncoding.decodeLong(bytes);
	assertEquals(n, decoded);
    }

    /* -- Test decodeLong -- */

    public void testDecodeLong() {
	try {
	    DataEncoding.decodeLong(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	byte[] bytes = new byte[0];
	try {
	    DataEncoding.decodeLong(bytes);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[7];
	try {
	    DataEncoding.decodeLong(bytes);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	}
	bytes = new byte[9];
	try {
	    DataEncoding.decodeLong(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Test encodeString -- */

    public void testEncodeString() {
	try {
	    DataEncoding.encodeString(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	char[] chars = new char[(1<<16) - 1];
	Arrays.fill(chars, 'a');
	byte[] bytes = new byte[1<<16];
	Arrays.fill(bytes, (byte) 'a');
	bytes[bytes.length - 1] = 0;
	verifyEncodeDecodeString(new String(chars), bytes);
	chars[0] = '\u1234';
	String longString = new String(chars);
	try {
	    DataEncoding.encodeString(longString);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	chars = new char[1<<16];
	Arrays.fill(chars, 'a');
	longString = new String(chars);
	try {
	    DataEncoding.encodeString(longString);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	verifyEncodeDecodeString("", zero);
	verifyEncodeDecodeString("hi", b('h'), b('i'), zero);
	verifyEncodeDecodeString(
	    "Some weird stuff: \u0123\u1234\u3456",
	    b('S'), b('o'), b('m'), b('e'), b(' '), b('w'), b('e'), b('i'),
	    b('r'), b('d'), b(' '), b('s'), b('t'), b('u'), b('f'), b('f'),
	    b(':'), b(' '), 
	    b(0xc4), b(0xa3),		/* \u0123 */
	    b(0xe1), b(0x88), b(0xb4),	/* \u1234 */
	    b(0xe3), b(0x91), b(0x96),  /* \u3456 */
	    zero);
	verifyEncodeDecodeString("\u0000", b(0xc0), b(0x80), zero);
    }

    private static void verifyEncodeDecodeString(String string, byte... b) {
	byte[] bytes = DataEncoding.encodeString(string);
	assertEquals(b.length, bytes.length);
	assertEquals(Arrays.toString(b), Arrays.toString(bytes));
	String decoded = DataEncoding.decodeString(bytes);
	assertEquals(string, decoded);
    }

    /* -- Test decodeString -- */

    public void testDecodeString() {
	try {
	    DataEncoding.decodeString(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	byte[] bytes = new byte[0];
	try {
	    DataEncoding.decodeString(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	bytes = new byte[] { ff, ff, zero };
	try {
	    DataEncoding.decodeString(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	bytes = new byte[] { b(0xef), ff, zero };
	try {
	    DataEncoding.decodeString(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	bytes = new byte[] { (byte) 'a', (byte) 'b' };
	try {
	    DataEncoding.decodeString(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	bytes = new byte[(1<<16) + 1];
	Arrays.fill(bytes, (byte) 'a');
	bytes[bytes.length - 1] = 0;
	try {
	    DataEncoding.decodeString(bytes);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Other methods -- */

    /** Coerces an integer to a byte, for convenience */
    private static final byte b(int n) {
	return (byte) n;
    }
}
