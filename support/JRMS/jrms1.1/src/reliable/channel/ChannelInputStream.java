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
 * ChannelInputStream
 */
package com.sun.multicast.reliable.channel;

import java.io.InputStream;
import java.io.IOException;

class ChannelInputStream extends InputStream {
    private InputStream is;

    /**
     * Create a ChannelInputStream.
     */
    public ChannelInputStream(InputStream is) {
        this.is = is;
    }

    /**
     * An InputStream object has to implement the read method. This
     * method takes the next byte out of the input buffer and returns
     * it to the caller.
     * 
     * @return the next byte of data or -1 if end of stream has been reached.
     * @Exception IOException
     */
    public int read() throws IOException {
        return is.read();
    }

    /**
     * This method is an extension of read() and returns an array of bytes.
     * The number of bytes returned is dependent on the size of the array
     * passed and the number of bytes of data available to be read. If the
     * available data is more than the byte array passed, the number of
     * bytes returned is the size of the array passed. If the byte array
     * passed is larger than the available bytes, the available bytes to
     * read is copied and returned.
     * 
     * @param b the buffer into which the data is read.
     * 
     * @return total number of bytes read into the buffer or -1 if end
     * of stream is reached.
     * 
     * @Exception IOException if an I/O error occurs.
     * 
     */
    public int read(byte[] b) throws IOException {
        return is.read(b);
    }

    /**
     * Reads up to len bytes of data from this input stream into an
     * array of bytes. This method blocks until some input is available.
     * If the first argument is null, up to len bytes are read and discarded.
     * The read method of InputStream reads a single byte at a time using
     * the read method of zero arguments to fill in the array. Subclasses
     * are encouraged to provide a more efficient implementation of this
     * method.
     * 
     * @param b the buffer into whioch data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * 
     * @return the number of bytes actually read
     * 
     * @exception IOException if an I/O error occurs.
     */
    public int read(byte b[], int off, int len) throws IOException {
        return is.read(b, off, len);
    }

    /**
     * Returns the number of bytes that can be read from this input stream
     * without blocking.
     * 
     * @return the number of bytes that can be read from this input stream
     * without blocking.
     */
    public int avaliable() throws IOException {
        return is.available();
    }

    /**
     * Closes this input stream and releases and system resources associated
     * with the stream.
     * 
     * @exception IOException if an I/O error occurs.
     */
    public void close() throws IOException {
        is.close();
    }

    /**
     * Marks the current position in this input stream. A subsequent call
     * to the reset method repositions this stream at the last marked position
     * so that subsequent reads re-read the same bytes.
     * 
     * The readlimit arguments tells this input stream to allow that many
     * bytes to be read before the mark position gets invalidated.
     * 
     * The mark method of InputStream does nothing.
     * 
     * @param readlimit the maximum limit of bytes that can be read before the
     * mark position becomes invalid.
     */
    public synchronized void mark(int readlimit) {
        is.mark(readlimit);
    }

    /**
     * Repositions this stream to the position at the time the mark
     * method was last called on this input stream.
     * 
     * The reset method of InputStream throws an IOException, because
     * input streams, by default, do not support mark and reset.
     * 
     * Stream marks are intended to be used in situations where you
     * need to read ahead a little to see what's in the stream. Often
     * this is most easily done by invoking some general parser. If the
     * stream is of the type handled by the parser, it just chugs along
     * happily. If the stream is not of that type, the parser should toss
     * an exception when it fails, which, if it happens within readlimit
     * bytes, allows the outer code to reset the stream and try another
     * parser.
     * 
     * @exception IOException if this stream has not been marked or the mark
     * has been invalidated.
     */
    public synchronized void reset() throws IOException {
        is.reset();
    }

    /**
     * Tests if this input stream supports the mark and reset methods.
     * 
     * @return true if input stream supports the mark and reset methods.
     */
    public boolean markSupported() {
        return is.markSupported();
    }

    /**
     * Skips over and discards n bytes of data from this input stream.
     * The skip method may, for a variety of reasons, end up skipping
     * over some smaller number of bytes, possibly 0. The actual number
     * of bytes skipped is returned.
     * 
     * The skip method of InputStream creates a byte array of length n
     * and then reads into it until n bytes have been read or the end of the
     * stream has been reached. Subclasses are encouraged to provide a more
     * efficient implementation of this method.
     * 
     * @param n the number of bytes to skip.
     * @return the actual number of bytes skipped.
     * @exception IOException if an I/O error occurs.
     */
    public long skip(long n) throws IOException {
        return is.skip(n);
    }

}

