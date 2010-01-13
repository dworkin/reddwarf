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

package com.sun.sgs.impl.service.data.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;

/** Provides methods for encoding and decoding data stored in the database. */
public final class DataEncoding {

    /** This class should not be instantiated. */
    private DataEncoding() {
	throw new AssertionError();
    }

    /**
     * Encodes a {@code short} into a two byte array.  The higher order bits of
     * the value are stored in the first byte.  The high order bit of the first
     * byte is inverted to insure that negative values sort first.
     *
     * @param	n the number to encode
     * @return	the encoded byte array
     */
    public static byte[] encodeShort(short n) {
	byte[] bytes = new byte[2];
	bytes[0] = (byte) ((n >>> 8) ^ 0x80);
	bytes[1] = (byte) n;
	return bytes;
    }

    /**
     * Decodes a two byte array into a {@code short}.  The higher order bits of
     * the value are read from the first byte.  The high order bit of the first
     * byte is inverted.
     *
     * @param	bytes the byte array to decode
     * @return	the decoded number
     * @throws	IndexOutOfBoundsException if {@code bytes} is less than two
     *		bytes in length
     * @throws	IllegalArgumentException if {@code bytes} is more than two
     *		bytes in length
     */
    public static short decodeShort(byte[] bytes) {
	if (bytes.length > 2) {
	    throw new IllegalArgumentException(
		"The argument must not have a length longer than 2");
	}
	int n = (bytes[0] & 0xff) ^ 0x80;
	n <<= 8;
	n += (bytes[1] & 0xff);
	return (short) n;
    }

    /**
     * Encodes an {@code int} into a four byte array.  The higher order bits of
     * the value are stored in the lower numbered bytes.  The high order bit of
     * the first byte is inverted to insure that negative values sort first.
     *
     * @param	n the number to encode
     * @return	the encoded byte array
     */
    public static byte[] encodeInt(int n) {
	byte[] bytes = new byte[4];
	encodeInt(n, bytes, 0);
	return bytes;
    }

    /**
     * Encodes an {@code int} into a four byte subsequence of an array.  The
     * higher order bits of the value are stored in the lower numbered bytes.
     * The high order bit of the first byte is inverted to insure that negative
     * values sort first.
     *
     * @param	n the number to encode
     * @param	bytes the array in which to store the encoded bytes
     * @param	offset the array index at which to store the first byte
     * @throws	IndexOutOfBoundsException if {@code bytes} is not long enough
     *		to contain the encoded bytes
     */
    public static void encodeInt(int n, byte[] bytes, int offset) {
	bytes[offset++] = (byte) ((n >>> 24) ^ 0x80);
	bytes[offset++] = (byte) (n >>> 16);
	bytes[offset++] = (byte) (n >>> 8);
	bytes[offset] = (byte) n;
    }

    /**
     * Decodes a four byte array into an {@code int}.  The higher order bits of
     * the value are read from the lower numbered bytes.  The high order bit of
     * the first byte is inverted.
     *
     * @param	bytes the byte array to decode
     * @return	the decoded number
     * @throws	IndexOutOfBoundsException if {@code bytes} is less than four
     *		bytes in length
     * @throws	IllegalArgumentException if {@code bytes} is more than four
     *		bytes in length
     */
    public static int decodeInt(byte[] bytes) {
	if (bytes.length > 4) {
	    throw new IllegalArgumentException(
		"The argument must not have a length longer than 4");
	}
	return decodeInt(bytes, 0);
    }

    /**
     * Decodes a four byte subsequence of an array into an {@code int}.  The
     * higher order bits of the value are read from the lower numbered bytes.
     * The high order bit of the first byte is inverted.
     *
     * @param	bytes the byte array to decode
     * @param	offset the array index of the first byte to decode
     * @return	the decoded number
     * @throws	IndexOutOfBoundsException if {@code bytes} is not long enough
     *		to contain the encoded bytes
     */
    public static int decodeInt(byte[] bytes, int offset) {
	int n = (bytes[offset++] & 0xff) ^ 0x80;
	n <<= 8;
	n += (bytes[offset++] & 0xff);
	n <<= 8;
	n += (bytes[offset++] & 0xff);
	n <<= 8;
	n += (bytes[offset] & 0xff);
	return n;
    }

    /**
     * Encodes a {@code long} into an eight byte array.  The higher order bits
     * of the value are stored in the lower numbered bytes.  The high order bit
     * of the first byte is inverted to insure that negative values sort first.
     *
     * @param	n the number to encode
     * @return	the encoded byte array
     */
    public static byte[] encodeLong(long n) {
	byte[] result = new byte[8];
	result[0] = (byte) ((n >>> 56) ^ 0x80);
	result[1] = (byte) (n >>> 48);
	result[2] = (byte) (n >>> 40);
	result[3] = (byte) (n >>> 32);
	result[4] = (byte) (n >>> 24);
	result[5] = (byte) (n >>> 16);
	result[6] = (byte) (n >>> 8);
	result[7] = (byte) n;
	return result;
    }

    /**
     * Decodes an eight byte array into a {@code long}.  The higher order bits
     * of the value are read from the lower numbered bytes.  The high order bit
     * of the first byte is inverted.
     *
     * @param	bytes the byte array to decode
     * @return	the decoded number
     * @throws	IndexOutOfBoundsException if {@code bytes} is less than eight
     *		bytes in length
     * @throws	IllegalArgumentException if {@code bytes} is more than eight
     *		bytes in length
     */
    public static long decodeLong(byte[] bytes) {
	if (bytes.length > 8) {
	    throw new IllegalArgumentException(
		"The argument must not have a length longer than 8");
	}
	long n = (bytes[0] & 0xff) ^ 0x80;
	n <<= 8;
	n += (bytes[1] & 0xff);
	n <<= 8;
	n += (bytes[2] & 0xff);
	n <<= 8;
	n += (bytes[3] & 0xff);
	n <<= 8;
	n += (bytes[4] & 0xff);
	n <<= 8;
	n += (bytes[5] & 0xff);
	n <<= 8;
	n += (bytes[6] & 0xff);
	n <<= 8;
	n += (bytes[7] & 0xff);
	return n;
    }

    /**
     * Encodes a {@code String} into an array of bytes encoded in modified
     * UTF-8 format, as documented by {@link DataInput}, but with the two byte
     * size value omitted from the start of the encoding, and with a
     * terminating null byte.  Removing the size from the start of the encoding
     * means that the strings will sort properly when used as keys, and adding
     * a null termination removes the need to depend on the length of the array
     * to find the end of the data, which turns out to be useful for Berkeley
     * DB.
     *
     * @param	string the string to encode
     * @return	the encoded byte array
     * @throws	IllegalArgumentException if the UTF-8 encoding of the string
     *		contains more than {@code 65535} characters
     */
    public static byte[] encodeString(String string) {
	ByteArrayOutputStream baos =
	    new ByteArrayOutputStream(string.length() + 2) {
		/** Strip off the first two bytes and add a zero at the end */
		public byte[] toByteArray() {
		    byte[] newbuf = new byte[count - 1];
		    System.arraycopy(buf, 2, newbuf, 0, count - 2);
		    newbuf[count - 2] = 0;
		    return newbuf;
		}
	    };
	DataOutputStream out = new DataOutputStream(baos);
	try {
	    out.writeUTF(string);
	} catch (UTFDataFormatException e) {
	    throw new IllegalArgumentException(e.getMessage(), e);
	} catch (IOException e) {
	    /* Should be no I/O errors to an in-memory output stream. */
	    throw new AssertionError(e);
	}
	return baos.toByteArray();
    }

    /**
     * Decodes an array of bytes encoded as modified UTF-8, but with the
     * two-byte size value omitted from the start and with a null termination,
     * into a {@code String}.
     *
     * @param	bytes the byte array to decode
     * @return	the decoded string
     * @throws	IllegalArgumentException if the format of the bytes is not
     *		valid UTF-8 data, if it is not null terminated, or if it
     *		represents more than {@code 65535} bytes
     */
    public static String decodeString(byte[] bytes) {
	int length;
	for (length = 0; length < bytes.length; length++) {
	    if (bytes[length] == 0) {
		break;
	    }
	}
	if (length >= bytes.length) {
	    throw new IllegalArgumentException(
		"Problem decoding string: Null termination not found");
	} else if (length > 65535) {
	    throw new IllegalArgumentException(
		"Problem decoding string: Length is too large: " + length);
	}
	byte[] newBytes = new byte[length + 2];
	newBytes[0] = (byte) (length >>> 8);
	newBytes[1] = (byte) length;
	System.arraycopy(bytes, 0, newBytes, 2, length);
	DataInputStream in =
	    new DataInputStream(
		new ByteArrayInputStream(newBytes));
	try {
	    return in.readUTF();
	} catch (IOException e) {
	    throw new IllegalArgumentException(
		"Problem decoding string: " + e.getMessage(), e);
	}
    }
}
