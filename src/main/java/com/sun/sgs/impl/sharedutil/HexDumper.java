/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.sharedutil;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Utility class for converting a byte array to a hex-formatted string.
 */
public final class HexDumper {
    
    /**
     * This class should not be instantiated.
     */
    private HexDumper() {
        
    }

    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.  The entire string is enclosed
     * in square brackets, and the octets are separated by a single
     * space character.
     *
     * @param bytes a byte array to format
     * @return the contents of the byte array as a hex-formatted string
     */
    public static String format(byte[] bytes) {
        return format(ByteBuffer.wrap(bytes));
    }

    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.  The entire string is enclosed
     * in square brackets, and the octets are separated by a single
     * space character.
     *
     * @param bytes a byte array to format
     * @param limit the maximum number of bytes to format, or {@code 0}
     *              meaning unlimited
     * @return the contents of the byte array as a hex-formatted string
     */
    public static String format(byte[] bytes, int limit) {
        return format(ByteBuffer.wrap(bytes), limit);
    }

    /**
     * Returns a string constructed with the contents of the ByteBuffer
     * converted to hex format.  The entire string is enclosed
     * in square brackets, and the octets are separated by a single
     * space character.
     *
     * @param buf a buffer to format
     * @return the contents of the buffer as a hex-formatted string
     */
    public static String format(ByteBuffer buf) {
        return format(buf, 0);
    }

    /**
     * Returns a string constructed with the contents of the ByteBuffer
     * converted to hex format.  The entire string is enclosed
     * in square brackets, and the octets are separated by a single
     * space character.
     *
     * @param buf a buffer to format
     * @param limit the maximum number of bytes to format, or {@code 0}
     *              meaning unlimited
     * @return the contents of the buffer as a hex-formatted string
     */
    public static String format(ByteBuffer buf, int limit) {
        if (!buf.hasRemaining()) {
            return "[]";
        }

        boolean truncate = false;
        ByteBuffer readBuf = buf.slice().asReadOnlyBuffer();
        if ((limit > 0) && (limit < buf.remaining())) {
            truncate = true;
            readBuf.limit(limit);
        }

        StringBuilder s = new StringBuilder(
            (3 * readBuf.remaining()) + (truncate ? 3 : 0) + 1);
        s.append('[');
        // First element
        s.append(String.format("%02x", readBuf.get()));
        // Remaining elements
        while (readBuf.hasRemaining()) {
            s.append(String.format(" %02x", readBuf.get()));
        }

        if (truncate) {
            s.append("...");
        }

        s.append(']');
        return s.toString();
    }

    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.
     *
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder buf = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            buf.append(String.format("%02x", b));
        }
        return buf.toString();
    }

    /**
     * Returns the specified {@code bigInt} as a hex-formatted string.
     *
     * @param	bigInt a big integer to format
     * @return	the specified {@code bigInt} as a hex-formatted string
     */
    public static String toHexString(BigInteger bigInt) {
	return toHexString(bigInt.toByteArray());
    }

    /**
     * Returns a byte array constructed with the contents of the given
     * string, which contains a series of byte values in hex format.
     *
     * @param hexString a string to convert
     * @return the byte array corresponding to the hex-formatted string
     *
     * @throws NumberFormatException if the {@code String}
     *         does not contain a parsable series of hex-formatted
     *         values {@code int}
     */
    public static byte[] fromHexString(String hexString) {
        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < bytes.length; ++i) {
            String hexByte = hexString.substring(2 * i, 2 * i + 2);
            bytes[i] = Integer.valueOf(hexByte, 16).byteValue();
        }
        return bytes;
    }
}
