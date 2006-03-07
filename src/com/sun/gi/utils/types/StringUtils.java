package com.sun.gi.utils.types;

import java.util.ArrayList;

public abstract class StringUtils {

    public static String[] explode(String str, String delim) {
        return explode(str, delim, true);
    }

    public static String[] explode(String str, String delim, boolean trim) {
        ArrayList<String> plist = new ArrayList<String>();
        while (str.length() > 0) {
            int delimpos = str.indexOf(delim);
            String leftString = str.substring(0, delimpos);
            if (leftString.length() > 0) {
                if (trim) {
                    leftString = leftString.trim();
                }
                plist.add(leftString);
            }
            str = str.substring(delimpos + delim.length());
        }
        String[] retarray = new String[plist.size()];
        return plist.toArray(retarray);
    }

    public static String bytesToHex(byte[] bytes, int start, int length) {
        StringBuffer buff = new StringBuffer();
        for (int i = 0; i < length; i++) {
            buff.append(byteToHex(bytes[i + start]));
        }
        return buff.toString();
    }

    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, 0, bytes.length);
    }

    private static String[] hexDigits = { "A", "B", "C", "D", "E", "F" };

    public static String byteToHex(byte b) {
        return halfByteToHex((byte) (b >> 4)) + halfByteToHex((byte) (b & 0xF));
    }

    private static String halfByteToHex(byte b) {
        b &= (byte) 0xF;
        if (b < 10)
            return String.valueOf((int) b);
        return hexDigits[b - 10];
    }

    public static String bytesToQuads(byte[] bytes) {
        StringBuffer buff = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            buff.append(String.valueOf((int) (bytes[i])));
            if (i + 1 < bytes.length) {
                buff.append(":");
            }
        }
        return buff.toString();
    }

    public static String bytesToHex(byte[] from, int i) {
        return bytesToHex(from, i, from.length - i);
    }
}
