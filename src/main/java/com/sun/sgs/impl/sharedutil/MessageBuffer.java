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

package com.sun.sgs.impl.sharedutil;

import java.io.UTFDataFormatException;

/**
 * A buffer for composing/decomposing messages.
 *
 * <p>Strings are encoded in modified UTF-8 format as described in
 * {@link java.io.DataInput}.
 */
public class MessageBuffer {

    private final byte[] buf;

    private final int capacity;

    private int pos = 0;

    private int limit;

    /**
     * Returns the size of the specified string, encoded in modified
     * UTF-8 format.
     *
     * @param str a string
     * @return the size of the specified string, encoded in modified
     * UTF-8 format
     */
    public static int getSize(String str) {
	
	// Note: code adapted from java.io.DataOutputStream.writeUTF
	
	int utfLen = 0;
	
	for (int i = 0; i < str.length(); i++) {
            int c = str.charAt(i);
	    if ((c >= 0x0001) && (c <= 0x007F)) {
		utfLen++;
	    } else if (c > 0x07FF) {
		utfLen += 3;
	    } else {
		utfLen += 2;
	    }
	}

	return utfLen + 2;
    }

    /**
     * Constructs an empty message buffer with the specified capacity.
     *
     * @param capacity the buffer's capacity
     */
    public MessageBuffer(int capacity) {
	this(new byte[capacity]);
	if (capacity == 0) {
	    throw new IllegalArgumentException(
		"capacity must be greater than 0");
	}
	this.limit = 1;
    }

    /**
     * Constructs a message buffer using the specified byte array as
     * the byte array that backs this buffer.  Intializes this
     * buffer's capacity and limit to the length of the specified byte
     * array.
     *
     * @param buf the byte array to back this buffer
     */
    public MessageBuffer(byte[] buf) {
	this.buf = buf;
	this.capacity = buf.length;
	this.limit = buf.length;
    }

    /**
     * Returns the capacity of this buffer.  The capacity is the
     * number of elements this buffer contains.
     *
     * @return this buffer's capacity
     */
    public int capacity() {
	return capacity;
    }

    /**
     * Returns the limit of this buffer.  The limit is the index of
     * the first element that should not be written or read.  The limit
     * is never negative and is never greater than the buffer's
     * capacity.
     *
     * @return this buffer's limit
     */
    public int limit() {
	return limit;
    }

    /**
     * Returns the position of this buffer.  The position is the index
     * of the next element to be written or read.
     *
     * @return this buffer's position
     */
    public int position() {
	return pos;
    }

    /**
     * Sets the position of this buffer to zero, making this buffer
     * ready for re-reading of its elements.
     */
    public void rewind() {
	pos = 0;
    }

    /**
     * Puts the specified byte in this buffer's current position,
     * and advances the buffer's position and limit by one.
     *
     * @param b a byte
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the byte to the
     * buffer would overflow the buffer
     */
    public MessageBuffer putByte(int b) {
	if (pos == capacity) {
	    throw new IndexOutOfBoundsException();
	}
	buf[pos++] = (byte) b;
	limit =  (pos == capacity ? pos : pos + 1);
	return this;
    }

    /**
     * Puts into this buffer a short representing the length of the specified
     * byte array followed by the bytes from the specified byte array,
     * starting at the buffer's current position.  The buffer's
     * position and limit are advanced by the length of the specified
     * byte array plus two.
     *
     * @param bytes a byte array
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the bytes to this
     * buffer would overflow the buffer
     */
    public MessageBuffer putByteArray(byte[] bytes) {
        if (pos + 2 + bytes.length > capacity) {
            throw new IndexOutOfBoundsException();
        }
        putShort(bytes.length);
        putBytes(bytes);
        return this;
    }

    /**
     * Puts the bytes from the specified byte array in this buffer,
     * starting at the buffer's current position.  The buffer's
     * position and limit are advanced by the length of the specified
     * byte array.
     *
     * @param bytes a byte array
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the bytes to this
     * buffer would overflow the buffer
     */
    public MessageBuffer putBytes(byte[] bytes) {
	if (pos + bytes.length > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	for (byte b : bytes) {
	    putByte(b);
	}
	return this;
    }
    
    /**
     * Puts the specified char as a two-byte value (high byte first)
     * starting in the buffer's current position, and advances the
     * buffer's position and limit by two.
     *
     * @param v a char value
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the char to this
     * buffer would overflow the buffer
     */
    public MessageBuffer putChar(int v) {
        if (pos + 2 > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putByte((v >>> 8) & 0xFF);	
	putByte((v >>> 0) & 0xFF);
	return this;
    }

    /**
     * Puts the specified short as a two-byte value (high byte first)
     * starting in the buffer's current position, and advances the
     * buffer's position and limit by two.
     *
     * @param v a short value
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the short to this
     * buffer would overflow the buffer
     */
    public MessageBuffer putShort(int v) {
        if (pos + 2 > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putByte((v >>> 8) & 0xFF);	
	putByte((v >>> 0) & 0xFF);
	return this;
    }

    /**
     * Puts the specified int as four bytes (high byte first)
     * starting in the buffer's current position, and advances the
     * buffer's position and limit by 4.
     *
     * @param v an int value
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the int to this
     * buffer would overflow the buffer
     */
    public MessageBuffer putInt(int v) {
        if (pos + 4 > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putByte((v >>> 24) & 0xff);
	putByte((v >>> 16) & 0xff);
	putByte((v >>> 8) & 0xff);
	putByte((v >>> 0) & 0xff);
	return this;
    }
    
    /**
     * Puts the specified long as eight bytes (high byte first)
     * starting in the buffer's current position, and advances the
     * buffer's position and limit by 8.
     *
     * @param v a long value
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the long to this
     * buffer would overflow the buffer
     */
    public MessageBuffer putLong(long v) {
        if (pos + 8 > capacity) {
	    throw new IndexOutOfBoundsException();
	}
	putByte((byte) (v >>> 56));
	putByte((byte) (v >>> 48));
	putByte((byte) (v >>> 40));
	putByte((byte) (v >>> 32));
	putByte((byte) (v >>> 24));
	putByte((byte) (v >>> 16));
	putByte((byte) (v >>> 8));
	putByte((byte) (v >>> 0));
	return this;
    }
    
    /**
     * Puts the specified string, encoded in modified UTF-8 format,
     * in the buffer starting in the buffer's the current position, and
     * advances the buffer's position and limit by the size of the
     * encoded string.
     *
     * @param str a string
     * @return this buffer
     * @throws IndexOutOfBoundsException if adding the encoded string
     * to this buffer would overflow the buffer
     */
    public MessageBuffer putString(String str) {
	
	// Note: code adapted from java.io.DataOutputStream.writeUTF
	
	int size = getSize(str);
        if (pos + size > capacity) {
	    throw new IndexOutOfBoundsException();
	}

	/*
	 * Put length of modified UTF-8 encoded string, as two bytes.
	 */
	putShort(size - 2);

	/*
	 * Now, encode string, and put in buffer.
	 */
	int strlen = str.length();
        int i = 0;

	
        for (i = 0; i < strlen; i++) {
           char c = str.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F))) {
                break;
            }
           buf[pos++] = (byte) c;
        }
	
        for (; i < strlen; i++) {
            char c = str.charAt(i);
	    if ((c >= 0x0001) && (c <= 0x007F)) {
		buf[pos++] = (byte) c;
               
	    } else if (c > 0x07FF) {
		buf[pos++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
		buf[pos++] = (byte) (0x80 | ((c >>  6) & 0x3F));
		buf[pos++] = (byte) (0x80 | ((c >>  0) & 0x3F));
	    } else {
		buf[pos++] = (byte) (0xC0 | ((c >>  6) & 0x1F));
		buf[pos++] = (byte) (0x80 | ((c >>  0) & 0x3F));
	    }
	}

	/*
	 * Adjust limit, because we didn't use putByte.
	 */
	limit =  (pos == capacity ? pos : pos + 1);
	
	return this;
    }

    /**
     * Returns the byte in this buffer's current position, and
     * advances the buffer's position by one.
     *
     * @return the byte at the buffer's current position
     * @throws IndexOutOfBoundsException if this buffer's limit has
     * been reached
     */
    public byte getByte() {
	if (pos == limit) {
	    throw new IndexOutOfBoundsException();
	}
	byte b = buf[pos++];
	return b;
    }

    /**
     * Returns a byte array encoded as a 2-byte length followed by the
     * bytes, starting at this buffer's current position, and advances
     * the buffer's position by the number of bytes obtained.
     *
     * @return a byte array with the bytes from this buffer
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the encoded bytes
     */
    public byte[] getByteArray() {
        int savePos = pos;
        try {
            return getBytes(getUnsignedShort());
        } catch (IndexOutOfBoundsException e) {
            pos = savePos;
            throw e;
        }
    }

    /**
     * Returns a byte array containing the specified number of bytes,
     * starting at this buffer's current position, and advances the
     * buffer's position by the number of bytes obtained.
     *
     * @param size the number of bytes to get from this buffer
     * @return a byte array with the bytes from this buffer
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the specified number of bytes
     */
    public byte[] getBytes(int size) {
        if (pos + size > limit) {
	    throw new IndexOutOfBoundsException();
	}

	byte[] bytes = new byte[size];
	for (int i = 0; i < size; i++) {
	    bytes[i] = getByte();
	}
	return bytes;
    }

    /**
     * Returns a short value, composed of the next two bytes (high
     * byte first) in this buffer, and advances the buffer's position
     * by two.
     *
     * @return the short value
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the next two bytes
     */
    public short getShort() {
        if (pos + 2 > limit) {
	    throw new IndexOutOfBoundsException();
	}
	
	return (short) ((getByte() << 8) + (getByte() & 255));
    }

    /**
     * Returns an unsigned short value (as an int), composed of the
     * next two bytes (high byte first) in this buffer, and advances
     * the buffer's position by two.  The value returned is between
     * 0 and 65535, inclusive.
     *
     * @return the unsigned short value as an int between 0 and 65535
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the next two bytes
     */
    public int getUnsignedShort() {
        if (pos + 2 > limit) {
            throw new IndexOutOfBoundsException();
        }
        
        return ((getByte() & 255) << 8) + ((getByte() & 255) << 0);
    }

    /**
     * Returns an int value, composed of the next four bytes (high
     * byte first) in this buffer, and advances the buffer's position
     * by 4.
     *
     * @return the int value
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the next four bytes
     */
    public int getInt() {
        if (pos + 4 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	return
	    ((getByte() & 255) << 24) +
	    ((getByte() & 255) << 16) +
	    ((getByte() & 255) << 8) +
	    ((getByte() & 255) << 0);
    }
    
    /**
     * Returns a long value, composed of the next eight bytes (high
     * byte first) in this buffer, and advances the buffer's position
     * by 8.
     *
     * @return the long value
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the next eight bytes
     */
    public long getLong() {
        if (pos + 8 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	return
	    ((long) (getByte() & 255) << 56) +
	    ((long) (getByte() & 255) << 48) +
	    ((long) (getByte() & 255) << 40) +
	    ((long) (getByte() & 255) << 32) +
	    ((long) (getByte() & 255) << 24) +
	    ((long) (getByte() & 255) << 16) +
	    ((long) (getByte() & 255) << 8) +
	    ((long) (getByte() & 255) << 0);
    }
    
    /**
     * Returns a char, composed of the next two bytes (high
     * byte first) in this buffer, and advances the buffer's position
     * by two.
     *
     * @return the char
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the next two bytes
     */
    public char getChar() {
        if (pos + 2 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	return (char) ((getByte() << 8) + (getByte() & 255));
    }

    /**
     * Returns a string that has been encoded in modified UTF-8
     * format, starting at the buffer's current position, and advances
     * the buffer's position by the length of the encoded string.
     *
     * @return the string
     * @throws IndexOutOfBoundsException if this buffer's limit would
     * be reached as a result of getting the encoded string
     */
    public String getString() {

	// Note: code adapted from java.io.DataInputStream.readUTF
	
        if (pos + 2 > limit) {
	    throw new IndexOutOfBoundsException();
	}

	int savePos = pos;

	/*
	 * Get length of UTF encoded string.
	 */
	int utfLen = getUnsignedShort();
	int utfEnd = utfLen + pos;
	if (utfEnd > limit) {
	    pos = savePos;
	    throw new IndexOutOfBoundsException();
	}

	/*
	 * Decode string.
	 */
	char[] chars = new char[utfLen * 2];
        int c, char2, char3;
        int index = 0;

        while (pos < utfEnd) {
            c = buf[pos] & 0xff;      
            if (c > 127) {
                break;
            }
            pos++;
            chars[index++] = (char) c;
        }

	try {
	    while (pos < utfEnd) {
		c = buf[pos] & 0xff;
		
		switch (c >> 4) {
		    
                case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                    /* 0xxxxxxx*/
                    pos++;
                    chars[index++] = (char) c;
                    break;
		    
                case 12: case 13:
                    /* 110x xxxx   10xx xxxx*/
                    pos += 2;
                    if (pos > utfEnd) {
                        throw new UTFDataFormatException(
			    "malformed input: partial character at end");
		    }
                    char2 = buf[pos - 1];
                    if ((char2 & 0xC0) != 0x80) {
                        throw new UTFDataFormatException(
			    "malformed input around byte " + pos);
		    }
                    chars[index++] =
			(char) (((c & 0x1F) << 6) | (char2 & 0x3F));  
                    break;
		    
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    pos += 3;
                    if (pos > utfEnd) {
                        throw new UTFDataFormatException(
			    "malformed input: partial character at end");
		    }
                    char2 = buf[pos - 2];
                    char3 = buf[pos - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
                        throw new UTFDataFormatException(
                                "malformed input around byte " + (pos - 1));
		    }
                    chars[index++] =
			(char) (((c & 0x0F) << 12) |
				((char2 & 0x3F) << 6) |
				((char3 & 0x3F) << 0));
                    break;
		    
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new UTFDataFormatException(
			"malformed input around byte " + pos);
		}
	    }
		
	} catch (UTFDataFormatException e) {
	    // restore position
	    pos = savePos;
	    throw (RuntimeException) (new RuntimeException()).initCause(e);
	}
        // The number of chars produced may be less than utfLen
        return new String(chars, 0, index);
    }

    /**
     * Returns the byte array that backs this buffer.
     *
     * @return the byte array that backs this buffer
     */
    public byte[] getBuffer() {
	return buf;
    }
	
}
