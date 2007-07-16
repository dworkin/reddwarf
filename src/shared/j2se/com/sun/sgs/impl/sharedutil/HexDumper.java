/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
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
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    public static String format(byte[] bytes) {
        if (bytes.length == 0) {
            return "[]";
        }
        int i = 0;
        StringBuilder buf = new StringBuilder((3 * bytes.length) + 1);
        buf.append('[');
        // First element
        buf.append(String.format("%02x", bytes[i++]));
        // Remaining elements
        while (i < bytes.length) {
            buf.append(String.format(" %02x", bytes[i++]));
        }
        buf.append(']');
        return buf.toString();
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
