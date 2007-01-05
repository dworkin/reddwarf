package com.sun.sgs.impl.util;

public class HexDumper {
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