/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides utilities for reading and writing 30-bit integers in a compressed
 * format that is smaller for smaller integers.  Does not support negative
 * integers or values above {@value #MAX_VALUE}. <p>
 *
 * The high order two bits of the first byte specify the number of following
 * bytes.  The bottom 6 bits of the first byte hold the highest order bits of
 * the integer, with the following bytes holding the remaining bytes. <p>
 */
public final class Int30 {

    /** The maximum integer that can be encoded. */
    public static final int MAX_VALUE = (1 << 30) - 1;

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
	if ((n & 0xc0000000) != 0) {
	    throw new IllegalArgumentException("Bad argument: " + n);
	} else if ((n & 0x3fc00000) != 0) {
	    out.write((0xc0 | (n >>> 24)) & 0xff);
	    out.write((n >>> 16) & 0xff);
	    out.write((n >>> 8) & 0xff);
	    out.write(n & 0xff);
	} else if ((n & 0x3fffc000) != 0) {
	    out.write((0x80 | (n >>> 16)) & 0xff);
	    out.write((n >>> 8) & 0xff);
	    out.write(n & 0xff);
	} else if ((n & 0x3fffffc0) != 0) {
	    out.write((0x40 | (n >>> 8)) & 0xff);
	    out.write(n & 0xff);
	} else {
	    out.write(n & 0x3f);
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
