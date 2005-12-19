/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * Util.java
 * 
 * Module Description:
 * 
 * Util class contains methods that can be used to read and write short,
 * int and long values to / from byte arrays at specified indices.
 * Similar to the corresponding methods provided in DataInputStream and
 * DataOutputStream classes, but saves the effort of having two layers
 * of streams. Further, values can be randomly (as in random access)
 * written and read from the byte array.
 */
package com.sun.multicast.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.sun.multicast.util.ImpossibleException;

/**
 * Utility class for reading and writing bigendian integers
 * to and from a byte array. This class is used for encoding
 * and decoding network packets.
 */
public class Util {

    /**
     * Private constructor so the class can't be instantiated.
     */
    private Util() {}

    /**
     * Reads an unsigned short (2 byte bigendian integer) from a
     * byte array starting at index pos.
     * @param arr the byte array
     * @param pos the index into the byte array
     * @return the unsigned short (expanded to an int to avoid sign problems)
     */
    public static int readUnsignedShort(byte[] arr, int pos) {
        return ((arr[pos] & 0xff) << 8) + (arr[pos + 1] & 0xff);
    }

    /**
     * Writes a signed short (2 byte bigendian integer) to a
     * byte array starting at index pos.
     * @param s the signed short
     * @param arr the byte array
     * @param pos the index into the byte array
     */
    public static void writeShort(short s, byte[] arr, int pos) {
        arr[pos] = (byte) (s >>> 8);
        arr[pos + 1] = (byte) (s & 0xff);
    }

    /**
     * Reads an unsigned int (4 byte bigendian integer) from a
     * byte array starting at index pos.
     * @param arr the byte array
     * @param pos the index into the byte array
     * @return the unsigned int (expanded to a long to avoid sign problems)
     */
    public static long readUnsignedInt(byte[] arr, int pos) {
        return ((arr[pos] & 0xffL) << 24) + ((arr[pos + 1] & 0xff) << 16) 
               + ((arr[pos + 2] & 0xff) << 8) + (arr[pos + 3] & 0xff);
    }

    /**
     * Reads a signed int (4 byte bigendian integer) from a
     * byte array starting at index pos.
     * @param arr the byte array
     * @param pos the index into the byte array
     * @return the signed int
     */
    public static int readInt(byte[] arr, int pos) {
        return ((arr[pos] & 0xff) << 24) + ((arr[pos + 1] & 0xff) << 16) 
               + ((arr[pos + 2] & 0xff) << 8) + (arr[pos + 3] & 0xff);
    }

    /**
     * Writes a signed int (4 byte bigendian integer) to a
     * byte array starting at index pos.
     * @param i the signed int
     * @param arr the byte array
     * @param pos the index into the byte array
     */
    public static void writeInt(int i, byte[] arr, int pos) {
        arr[pos] = (byte) (i >>> 24);
        arr[pos + 1] = (byte) ((i >>> 16) & 0xff);
        arr[pos + 2] = (byte) ((i >>> 8) & 0xff);
        arr[pos + 3] = (byte) (i & 0xff);
    }

    /**
     * Reads a signed long (8 byte bigendian integer) from a
     * byte array starting at index pos.
     * @param arr the byte array
     * @param pos the index into the byte array
     * @return the signed long
     */
    public static long readLong(byte[] arr, int pos) {
        return ((arr[pos] & 0xffL) << 56) + ((arr[pos + 1] & 0xff) << 48) 
               + ((arr[pos + 2] & 0xff) << 40) 
               + ((arr[pos + 3] & 0xff) << 32) 
               + ((arr[pos + 4] & 0xff) << 24) 
               + ((arr[pos + 5] & 0xff) << 16) + ((arr[pos + 6] & 0xff) << 8) 
               + (arr[pos + 7] & 0xff);
    }

    /**
     * Writes a signed long (8 byte bigendian integer) to a
     * byte array starting at index pos.
     * @param l the signed long
     * @param arr the byte array
     * @param pos the index into the byte array
     */
    public static void writeLong(long l, byte[] arr, int pos) {
        arr[pos] = (byte) (l >>> 56);
        arr[pos + 1] = (byte) ((l >>> 48) & 0xff);
        arr[pos + 2] = (byte) ((l >>> 40) & 0xff);
        arr[pos + 3] = (byte) ((l >>> 32) & 0xff);
        arr[pos + 4] = (byte) ((l >>> 24) & 0xff);
        arr[pos + 5] = (byte) ((l >>> 16) & 0xff);
        arr[pos + 6] = (byte) ((l >>> 8) & 0xff);
        arr[pos + 7] = (byte) (l & 0xff);
    }

    /**
     * Converts an int into an InetAddress.
     * @param address the int
     * @return a new InetAddress for the int
     */
    public static InetAddress intToInetAddress(int address) {
        StringBuffer sb = new StringBuffer();

        sb.append((int) (address >>> 24) & 0xff);
        sb.append(".");
        sb.append((int) (address >>> 16) & 0xff);
        sb.append(".");
        sb.append((int) (address >>> 8) & 0xff);
        sb.append(".");
        sb.append((int) (address & 0xff));

        InetAddress iAddr = null;

        try {
            iAddr = InetAddress.getByName(sb.toString());
        } catch (UnknownHostException e) {
            throw new ImpossibleException(e);       // Should never happen
        }

        return (iAddr);
    }

    /**
     * Converts an InetAddress into an int.
     * @param ia the InetAddress
     * @return the int
     */
    public static int InetAddressToInt(InetAddress ia) {
        byte b[] = ia.getAddress();

        return ((b[0] & 0xff) << 24) + ((b[1] & 0xff) << 16) 
               + ((b[2] & 0xff) << 8) + (b[3] & 0xff);
    }

    /**
     * Read an object from a byte array using serialization.
     * @param object the byte stream to read
     * @return the object read
     */
    public static Object readObject(byte[] bytes) {
        Object o = null;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream p = new ObjectInputStream(bais);

            o = p.readObject();

            p.close();
            bais.close();
        } catch (IOException e) {

            // @@@ Not really impossible. Just internal.

            throw new ImpossibleException(e);
        } catch (ClassNotFoundException e) {

            // @@@ Not really impossible. Just internal.

            throw new ImpossibleException(e);
        }

        return (o);
    }

    /**
     * Write an object to a byte array using serialization.
     * @param object the object to be written
     * @return the byte stream
     */
    public static byte[] writeObject(Object object) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            ObjectOutputStream p = new ObjectOutputStream(baos);

            p.writeObject(object);
            p.close();
        } catch (IOException e) {

            // @@@ Not really impossible. Just internal.

            throw new ImpossibleException(e);
        }

        return (baos.toByteArray());
    }

    /**
     * Perform a deep clone of an object. This ensures that the
     * new object shares no state with the old one. This is
     * achieved by serializing and deserializing the object.
     * @param object the object to be cloned
     * @return the new object
     */
    public static Object deepClone(Object object) {
        return (readObject(writeObject(object)));
    }

    /**
     * Method to write a byte array to a file.
     */
    public static void writeByteArrayToFile(byte[] buf, String fileName, 
                                            int len) throws IOException {
        FileOutputStream ostream = new FileOutputStream(fileName);
        ObjectOutputStream p = new ObjectOutputStream(ostream);

        p.write(buf, 0, len);
        p.flush();
        ostream.close();
    }
}
