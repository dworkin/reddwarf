/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Utility methods for reading from data input streams and writing to data
 * output streams.
 */
public final class DataStreamUtil {

    /** This class should not be instantiated. */
    private DataStreamUtil() { }

    /**
     * Reads the number of bytes and the byte data from a data input stream and
     * returns the resulting array.  Returns {@code null} if the length is
     * {@code -1}.
     *
     * @param	in the data input stream
     * @return	the byte array or {@code null}
     * @throws	IOException if an I/O error occurs
     */
    public static byte[] readBytes(DataInput in) throws IOException {
	int numBytes = in.readInt();
	if (numBytes == -1) {
	    return null;
	}
	byte[] result = new byte[numBytes];
	in.readFully(result);
	return result;
    }

    /**
     * Writes the number of bytes and the byte data to a data output stream.
     * Writes a length of {@code -1} if the bytes are {@code null}
     *
     * @param	bytes the bytes or {@code null}
     * @param	out the data output stream
     * @throws	IOException if an I/O error occurs
     */
    public static void writeBytes(byte[] bytes, DataOutput out)
	throws IOException
    {
	if (bytes == null) {
	    out.writeInt(-1);
	} else {
	    out.writeInt(bytes.length);
	    out.write(bytes);
	}
    }

    /**
     * Reads a string or {@code null} from a data input stream.  If the initial
     * {@code boolean} value is {@code true}, the next item is the UTF for the
     * string; otherwise, the string was {@code null}.
     *
     * @param	in the data input stream
     * @return	the string or {@code null}
     * @throws	IOException if an I/O error occurs
     */
    public static String readString(DataInput in) throws IOException {
	return in.readBoolean() ? in.readUTF() : null;
    }

    /**
     * Writes a string or {@code null} to a data output stream.  Writes the
     * {@code boolean} value {@code true} followed by the UTF for the string
     * if it is not {@code null}, otherwise writes {@code false}.
     *
     * @param	string the string or {@code null}
     * @param	out the data output stream
     * @throws	IOException if an I/O error occurs
     */
    public static void writeString(String string, DataOutput out)
	throws IOException
    {
	if (string == null) {
	    out.writeBoolean(false);
	} else {
	    out.writeBoolean(true);
	    out.writeUTF(string);
	}
    }

    /**
     * Reads an array of strings from a data input stream.  The array elements
     * can be {@code null}.
     *
     * @param	in the data input stream
     * @return	the array
     * @throws	IOException if an I/O error occurs
     */
    public static String[] readStrings(DataInput in) throws IOException {
	int len = in.readInt();
	String[] result = new String[len];
	for (int i = 0; i < len; i++) {
	    result[i] = readString(in);
	}
	return result;
    }

    /**
     * Writes an array of strings to a data output stream.  The array elements
     * can be {@code null}. 
     *
     * @param	array the array
     * @param	out the data output stream
     * @throws	IOException if an I/O error occurs
     */
    public static void writeStrings(String[] array, DataOutput out)
	throws IOException
    {
	out.writeInt(array.length);
	for (String s : array) {
	    writeString(s, out);
	}
    }

    /**
     * Reads an array of {@code long}s from a data input stream.
     *
     * @param	in the data input stream
     * @return	the array
     * @throws	IOException if an I/O error occurs
     */
    public static long[] readLongs(DataInput in) throws IOException {
	int len = in.readInt();
	long[] result = new long[len];
	for (int i = 0; i < len; i++) {
	    result[i] = in.readLong();
	}
	return result;
    }

    /**
     * Writes an array of {@code long}s to a data output stream.
     *
     * @param	array the array
     * @param	out the data output stream
     * @throws	IOException if an I/O error occurs
     */
    public static void writeLongs(long[] array, DataOutput out)
	throws IOException
    {
	int len = array.length;
	out.writeInt(len);
	for (int i = 0; i < len; i++) {
	    out.writeLong(array[i]);
	}
    }

    /**
     * Reads an array of byte arrays from a data input stream.
     *
     * @param	in the data input stream
     * @return	the array of {@code byte} arrays
     * @throws	IOException if an I/O error occurs
     */
    public static byte[][] readByteArrays(DataInput in) throws IOException {
	int numElements = in.readInt();
	byte[][] result = new byte[numElements][];
	for (int i = 0; i < numElements; i++) {
	    result[i] = readBytes(in);
	}
	return result;
    }

    /**
     * Writes an array of byte arrays to a data output stream.
     *
     * @param	array the array of {@code byte} arrays
     * @param	out the data output stream
     * @throws	IOException if an I/O error occurs
     */
    public static void writeByteArrays(byte[][] array, DataOutput out)
	throws IOException
    {
	out.writeInt(array.length);
	for (byte[] bytes : array) {
	    writeBytes(bytes, out);
	}
    }
}
