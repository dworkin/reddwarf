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
 * ChannelOutputStream.java
 */
package com.sun.multicast.reliable.channel;

import java.io.OutputStream;
import java.io.IOException;

class ChannelOutputStream extends OutputStream {
    OutputStream os;

    /**
     */
    public ChannelOutputStream(OutputStream os) {
        this.os = os;
    }

    /**
     * Write the specified byte to this output stream.
     * @param b the byte to send.
     * @Exception IOException if an I/O error occurs.
     */
    public void write(int b) throws IOException {
        os.write(b);
    }

    /**
     * Writes b.length bytes from the specified byte array to this output
     * stream.
     * 
     * This write method calls the write method with the three arguments
     * (b, 0, b.length)
     * 
     * @param b a byte array containing the data to send.
     * @Exception  IOException if an I/O error occurs.
     */
    public void write(byte[] b) throws IOException {
        os.write(b);
    }

    /**
     * Writes len bytes from the specified byte array starting at offset
     * off to this output stream.
     * 
     * The write method of OutputStream calls the write method of one
     * argument on each of the bytes to be written out. Subclasses are
     * encouraged to override this method and provide a more efficient
     * implementation.
     * 
     * @param b the data to be written
     * @param offset the start offset into the data.
     * @param length the number of bytes to write.
     * 
     * @exception IOException if an I/O error occurs.
     */
    public void write(byte b[], int offset, int length) throws IOException {
        os.write(b, offset, length);
    }

    /**
     * Flushes the output stream and forces any buffered bytes to be written
     * out.
     * 
     * @exception IOException if an I/O error occurs.
     */
    public void flush() throws IOException {
        os.flush();
    }

    /**
     * Close this output stream and release any system resources associated
     * with the stream.
     * 
     * @exception IOException if an I/O error occurs.
     */
    public void close() throws IOException {
        os.close();
    }

}

