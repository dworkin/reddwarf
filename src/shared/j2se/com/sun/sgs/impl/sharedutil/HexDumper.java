/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.impl.sharedutil;

import java.nio.ByteBuffer;

/**
 * Utility class for converting a byte array to a hex-formatted string.
 */
public final class HexDumper {

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
        if (! buf.hasRemaining())
            return "[]";

        ByteBuffer readBuf = buf.slice();
        if (limit > 0)
            readBuf.limit(limit);

        StringBuilder s = new StringBuilder((3 * readBuf.remaining()) + 1);
        s.append('[');
        // First element
        s.append(String.format("%02x", readBuf.get()));
        // Remaining elements
        while (readBuf.hasRemaining()) {
            s.append(String.format(" %02x", readBuf.get()));
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
            String hexByte = hexString.substring(2*i, 2*i+2);
            bytes[i] = Integer.valueOf(hexByte, 16).byteValue();
        }
        return bytes;
    }
}
