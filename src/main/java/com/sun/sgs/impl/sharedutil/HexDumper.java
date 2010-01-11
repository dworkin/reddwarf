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
