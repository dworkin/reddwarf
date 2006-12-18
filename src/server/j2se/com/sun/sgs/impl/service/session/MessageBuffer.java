package com.sun.sgs.impl.service.session;

public class MessageBuffer {

    private final byte[] buf;

    private final int capacity;

    private int pos = 0;

    private int limit;

    public static int getSize(String s) {
	return 2 + s.length();
    }

    public MessageBuffer(int capacity) {
	this(new byte[capacity]);
	if (capacity == 0) {
	    throw new IllegalArgumentException(
		"capacity must be greater than 0");
	}
	this.limit = 1;
    }

    public MessageBuffer(byte[] buf) {
	this.buf = buf;
	this.capacity = buf.length;
	this.limit = buf.length;
    }

    public int capacity() {
	return capacity;
    }

    public int limit() {
	return limit;
    }

    public int position() {
	return pos;
    }

    public void rewind() {
	pos = 0;
    }

    public MessageBuffer putByte(int b) {
	if (pos == capacity) {
	    throw new IndexOutOfBoundsException();
	}
	buf[pos++] = (byte) b;
	limit =  (pos == capacity ? pos : pos + 1);
	return this;
    }

    public MessageBuffer putBytes(byte[] bytes) {
	if (pos + bytes.length > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	for (byte b : bytes) {
	    putByte(b);
	}
	return this;
    }

    public MessageBuffer putChar(int v) {
	if (pos+2 > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putByte((v >>> 8) & 0xFF);	
	putByte((v >>> 0) & 0xFF);
	return this;
    }

    public MessageBuffer putShort(int v) {
	if (pos+2 > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putByte((v >>> 8) & 0xFF);	
	putByte((v >>> 0) & 0xFF);
	return this;
    }
    
    public MessageBuffer putString(String str) {
	int size = (str.length() * 2) + 2;
	if (pos+size > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putShort(str.length());
	for (int i = 0; i < str.length(); i++) {
	    putChar(str.charAt(i));
	}
	return this;
    }

    public byte getByte() {
	if (pos == limit) {
	    throw new IndexOutOfBoundsException();
	}
	byte b = buf[pos++];
	return b;
    }

    public byte[] getBytes(int size) {
	if (pos+size > limit) {
	    throw new IndexOutOfBoundsException();
	}

	byte[] bytes = new byte[size];
	for (int i = 0; i < size; i++) {
	    bytes[i] = getByte();
	}
	return bytes;
    }

    public short getShort() {
	if (pos+2 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	int b1 = getByte();
	int b2 = getByte();
	return (short) ((b1 << 8) + (b2 << 0));
    }

    public char getChar() {
	if (pos+2 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	int b1 = getByte();
	int b2 = getByte();
	return (char) ((b1 << 8) + (b2 << 0));
    }
    
    public String getString() {
	if (pos+2 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	int size = getShort();

	if ((size * 2) + pos > limit) {
	    pos--;
	    throw new IndexOutOfBoundsException();
	}

	char[] chars = new char[size];
	for (int i = 0; i < size; i++) {
	    chars[i] = getChar();
	}
	return new String(chars);
    }

    public byte[] getBuffer() {
	return buf;
    }
	
}
