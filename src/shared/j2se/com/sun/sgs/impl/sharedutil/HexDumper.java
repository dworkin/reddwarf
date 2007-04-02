/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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
