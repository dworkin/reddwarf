package com.sun.sgs.impl.util;

/**
 * Utility class for converting a byte array to a hex-formatted string.
 */
public final class HexDumper {
    /**
     * Returns a string constructed with the contents of the byte
     * array converted to hex format.
     *
     * @param bytes a byte array to convert
     * @return the converted byte array as a hex-formatted string
     */
    public static String format(byte[] bytes) {
        if (bytes.length == 0) {
            return "[]";
        }
        int i = 0;
        StringBuilder buf = new StringBuilder("[");
        // First element
        buf.append(String.format("%02X", bytes[i++]));
        // Remaining elements
        while (i < bytes.length) {
            buf.append(String.format(" %02X", bytes[i++]));
        }
        buf.append(']');
        return buf.toString();
    }
}
