/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides utilities for reading and writing 30-bit integers in a compressed
 * format that is smaller for smaller integers.  Does not support negative
 * integers or values above 2^30 - 1 or {@value #MAX_VALUE}. <p>
 *
 * The high order two bits of the first byte specify the number of additional
 * bytes beyond the first byte that will be needed to store the entire integer.
 * The bottom 6 bits of the first byte hold the highest order bits of the
 * integer, with the following bytes holding the remaining bytes. <p>
 *
 * Values less than 2^6 are represented by just storing the value in a single
 * byte, since the top two marking bits are zero: <p>
 *
 * <ul>
 * <li> 53 (0x35) => 0x35
 * </ul> <p>
 *
 * Values less than 2^14 are represented in two bytes, with the top two bits of
 * the first byte set to 01 binary: <p>
 *
 * <ul>
 * <li> 4000 (0xfa0) => 0x4f 0xa0
 * </ul> <p>
 *
 * Values less than 2^22 take three bytes, with 10 binary in the top bits, and
 * less than 2^30 take four, with 11 binary in the top bits: <p>
 *
 * <ul>
 * <li> 123456 (0x1e240) => 0x81 0xe2 0x40
 * <li> 123456789 (0x75bcd15) => 0xc7 0x5b 0xcd 0x15
 * <li> 456789012 (0x1b3a0c14) => 0xdb 0x3a 0x0c 0x14
 * </ul> <p>
 */
public final class Int30 {

    /** The maximum integer that can be encoded. */
    public static final int MAX_VALUE = (1 << 30) - 1;

    /*
     * These fields represent the integer bits that are not allowed to be set
     * in order to represent the integer in the specified number of bytes.
     */
    private static final int FORBIDDEN4 = 0xc0000000;
    private static final int FORBIDDEN3 = 0xffc00000;
    private static final int FORBIDDEN2 = 0xffffc000;
    private static final int FORBIDDEN1 = 0xffffffc0;

    /*
     * These fields represent the value stored in the upper two bits of the
     * first byte that says how many bytes follow.
     */
    private static final byte MARKER3 = (byte) 0xc0;
    private static final byte MARKER2 = (byte) 0x80;
    private static final byte MARKER1 = (byte) 0x40;

    /** This class should not be instantiated. */
    private Int30() { }

    /**
     * Writes a 30-bit integer to an output stream.
     *
     * @param	n the integer
     * @param	out the output stream
     * @throws	IllegalArgumentException if {@code n} is less than {@code 0} or
     *		greater than {@value #MAX_VALUE}
     * @throws	IOException if an I/O error occurs
     */
    public static void write(int n, OutputStream out) throws IOException {
	if ((n & FORBIDDEN4) != 0) {
	    throw new IllegalArgumentException("Bad argument: " + n);
	} else if ((n & FORBIDDEN3) != 0) {
	    out.write(MARKER3 | (n >>> 24));
	    out.write((n >>> 16) & 0xff);
	    out.write((n >>> 8) & 0xff);
	    out.write(n & 0xff);
	} else if ((n & FORBIDDEN2) != 0) {
	    out.write(MARKER2 | (n >>> 16));
	    out.write((n >>> 8) & 0xff);
	    out.write(n & 0xff);
	} else if ((n & FORBIDDEN1) != 0) {
	    out.write(MARKER1 | (n >>> 8));
	    out.write(n & 0xff);
	} else {
	    out.write(n & 0xff);
	}
    }	
	
    /**
     * Reads a 30-bit integer from an input stream.
     *
     * @param	in the input stream
     * @return	the integer
     * @throws	IOException if an I/O error occurs
     */
    public static int read(InputStream in) throws IOException {
	int b = in.read();
	if (b == -1) {
	    throw new EOFException("Encountered end of file");
	}
	int count = b >>> 6;
	int n = b & 0x3f;
	for (int i = 0; i < count; i++) {
	    n <<= 8;
	    b = in.read();
	    if (b == -1) {
		throw new EOFException("Encountered end of file");
	    }
	    n += (b & 0xff);
	}
	return n;
    }
}
