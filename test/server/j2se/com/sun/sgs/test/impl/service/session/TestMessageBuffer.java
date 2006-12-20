package com.sun.sgs.test.impl.service.session;

import com.sun.sgs.impl.service.session.MessageBuffer;
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
}
