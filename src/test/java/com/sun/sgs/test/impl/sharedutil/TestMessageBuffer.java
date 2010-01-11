/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.MessageBuffer;

import junit.framework.TestCase;

public class TestMessageBuffer extends TestCase {


    public void testConstructorNullArg() {
	try {
	    new MessageBuffer(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testPutByte() {
	int capacity = 10;
	MessageBuffer buf1 = new MessageBuffer(capacity);
	for (byte b = 0; b < capacity; b++) {
	    buf1.putByte(b);
	}
	MessageBuffer buf2 = new MessageBuffer(buf1.getBuffer());
	for (byte b = 0; b < capacity; b++) {
	    byte b2 = buf2.getByte();
	    if (b != b2) {
		fail("Mismatched bytes; b:"+ b + ", b2:" + b2);
	    }
	}
	System.err.println("bytes match");
    }

    public void testPutByteRewind() {
	int capacity = 10;
	MessageBuffer buf1 = new MessageBuffer(capacity);
	for (byte b = 0; b < capacity; b++) {
	    buf1.putByte(b);
	}
	buf1.rewind();
	for (byte b = 0; b < capacity; b++) {
	    byte b2 = buf1.getByte();
	    if (b != b2) {
		fail("Mismatched bytes; b:"+ b + ", b2:" + b2);
	    }
	}
	System.err.println("bytes match");
    }

    public void testPutByteOverflow() {
	MessageBuffer buf = new MessageBuffer(1);
	buf.putByte(0x01);
	try {
	    buf.putByte(0x02);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	    System.err.println(e);
	}
    }

    public void testPutChar() {
	MessageBuffer buf = new MessageBuffer(2);
	buf.putChar('x');
	buf.rewind();
	char c = buf.getChar();
	if (c != 'x') {
	    fail("Expected char 'x', got " + c);
	}
    }

    public void testPutChars() {
	String s = "The quick brown fox jumps over the lazy dog.";
	MessageBuffer buf = new MessageBuffer(s.length() * 2);
	for (char c : s.toCharArray()) {
	    buf.putChar(c);
	    System.err.print(c);
	}
	System.err.println("\nlimit: " + buf.limit());
	buf.rewind();
	char[] charArray = new char[s.length()];
	for (int i = 0; i < s.length(); i++) {
	    charArray[i] = buf.getChar();
	    System.err.print(charArray[i]);
	}
	System.err.println();
	if (!(s.equals(new String(charArray)))) {
	    fail("strings don't match");
	}
    }

    public void testPutShort() {
	MessageBuffer buf = new MessageBuffer(2);
	short value1 = 53;
	buf.putShort(value1);
	buf.rewind();
	short value2 = buf.getShort();
	if (value1 != value2) {
	    fail("Expected short " + value1 + ", got " + value2);
	}
    }

    public void testPutShortSignedBytes() {
        MessageBuffer buf = new MessageBuffer(2);
        short value1 = 0x10ff;
        buf.putShort(value1);
        buf.rewind();
        short value2 = buf.getShort();
        if (value1 != value2) {
            fail("Expected short " + value1 + ", got " + value2);
        }
    }

    public void testGetUnsignedShort() {
        MessageBuffer buf = new MessageBuffer(2);
        int value1 = 64000;
        buf.putShort(value1);
        buf.rewind();
        int value2 = buf.getUnsignedShort();
        if (value1 != value2) {
            fail("Expected unsigned short " + value1 + ", got " + value2);
        }
        
        // test that signed getShort is different
        buf.rewind();
        value2 = buf.getShort();
        if (value1 == value2) {
            fail("Expected unequal, but got " + value2);
        }
        System.err.println("ushort " + value1 + " != " + value2);
    }

    public void testPutInt() {
        MessageBuffer buf = new MessageBuffer(4);
        int value1 = 0x01020304;
        buf.putInt(value1);
        buf.rewind();
        int value2 = buf.getInt();
        if (value1 != value2) {
            fail("Expected int " + value1 + ", got " + value2);
        }
    }

    public void testPutIntSignedBytes() {
        MessageBuffer buf = new MessageBuffer(4);
        int value1 = 0x01ff02fe;
        buf.putInt(value1);
        buf.rewind();
        int value2 = buf.getInt();
        if (value1 != value2) {
            fail("Expected int " + value1 + ", got " + value2);
        }
    }

    public void testPutLong() {
        MessageBuffer buf = new MessageBuffer(8);
        long value1 = 0x0102030405060708L;
        buf.putLong(value1);
        buf.rewind();
        long value2 = buf.getLong();
        if (value1 != value2) {
            fail("Expected long " + value1 + ", got " + value2);
        }
    }

    public void testPutLongSignedBytes() {
        MessageBuffer buf = new MessageBuffer(8);
        long value1 = 0x01f203f4f506f708L;
        buf.putLong(value1);
        buf.rewind();
        long value2 = buf.getLong();
        if (value1 != value2) {
            fail("Expected long " + value1 + ", got " + value2);
        }
    }
    
    public void testPutBytes() {
	int size = 100;
	byte[] bytes = new byte[size];
	for (int i = 0; i < bytes.length; i++) {
	    bytes[i] = (byte) i;
	}
	MessageBuffer buf = new MessageBuffer(size);
	buf.putBytes(bytes);
	buf.rewind();
	for (int i = 0; i < bytes.length; i++) {
	    if (buf.getByte() != bytes[i]) {
		fail("Expected byte " + bytes[i]);
	    }
	}
	buf.rewind();
	byte[] moreBytes = buf.getBytes(bytes.length);
	if (moreBytes.length != bytes.length) {
	    fail("Mismatched size; expected " + bytes.length +
		 ", got " + moreBytes.length);
	}
	for (int i = 0; i < bytes.length; i++) {
	    if (bytes[i] != moreBytes[i]) {
		fail("Expected byte " + bytes[i] + ", got " + moreBytes[i]);
	    }
	}
    }

    public void testPutString() {
	String s = "Supercalafragilisticexpalidocious";
	MessageBuffer buf = new MessageBuffer(MessageBuffer.getSize(s));
	buf.putString(s);
	buf.rewind();
	String newString = buf.getString();
	System.err.println("newString: " + newString);
	if (!s.equals(newString)) {
	    fail("Expected: " + s + ", got: " + newString);
	}
    }

    public void testPutStringAndInt() {
	String s = "zowie!";
	int x = 1024;
	MessageBuffer buf = new MessageBuffer(MessageBuffer.getSize(s) + 4);
	buf.putString(s);
	buf.putInt(x);
	buf.rewind();
	String newString = buf.getString();
	System.err.println("newString: " + newString);
	int newX = buf.getInt();
	System.err.println("newX: " + newX);
	if (!s.equals(newString)) {
	    fail("Expected string: " + s + ", got: " + newString);
	}
	if (x != newX) {
	    fail("Expected int: " + x + ", got: " + newX);
	}
	if (buf.position() != buf.limit()) {
	    fail("limit not equal to position; limit: " + buf.limit() +
		 ", position: " + buf.position());
	}
    }

    public void testPutStringGetUTF8() {
	String s = "The quick brown fox jumps over the lazy dog.";
	MessageBuffer buf = new MessageBuffer(MessageBuffer.getSize(s));
	buf.putString(s);
	buf.rewind();
	short utfLen = buf.getShort();
	byte[] utfBytes = buf.getBytes(utfLen);
	String newString;
	try {
	    newString = new String(utfBytes, "UTF-8");
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	System.err.println(newString);
	if (!s.equals(newString)) {
	    fail("Expected: " + s + ", got: " + newString);
	}
    }

    public void testPutUTF8GetString() {
	String s = "The quick brown fox jumps over the lazy dog.";
	byte[] utfBytes;
	try {
	    utfBytes = s.getBytes("UTF-8");
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	int utfLen = utfBytes.length;
	MessageBuffer buf = new MessageBuffer(2 + utfLen);
	buf.putShort(utfLen).
	    putBytes(utfBytes);
	buf.rewind();
	String newString = buf.getString();
	System.err.println("newString: " + newString);
	if (!s.equals(newString)) {
	    fail("Expected: " + s + ", got: " + newString);
	}
    }
}
