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
 * TRAMInputStream
 */
package com.sun.multicast.reliable.transport.tram;

import java.io.InputStream;
import java.io.IOException;
import com.sun.multicast.reliable.transport.SessionDownException;

class TRAMInputStream extends InputStream {
    private TRAMControlBlock tramblk = null;
    private TRAMInputOutput pktio = null;
    private byte[] readBuffer = null;
    private int index = 0;
    private int bytesToRead = 0;
    private boolean dataEnd = false;
    private TRAMLogger logger = null;

    /**
     * TRAMInputStream
     */
    public TRAMInputStream(TRAMControlBlock tramblk, TRAMInputOutput pktio) {
        this.tramblk = tramblk;
        this.pktio = pktio;
        logger = tramblk.getLogger();
        dataEnd = false;
    }

    /**
     * An InputStream object has to implement the read method. This
     * method takes the next byte out of the input buffer and returns
     * it to the caller. In this implementation, the incoming packets are
     * queued in the data_inputq. The packets in the queue have the TRAM header
     * which needs to be stripped off before passing the data.
     * 
     * @exception IOException if an I/O error occurs
     */
    public int read() throws IOException {
        if (bytesToRead <= 0) {
            if (loadUnreadData()) {
                return -1;
            }
        }

        int b = readBuffer[index] & 0xff;

        index++;
        bytesToRead--;

        return b;
    }

    /**
     * This method is an extension of read() and returns an array of bytes.
     * The number of bytes returned is dependent on th esize of the array
     * passed and the number of bytes of data available to be read. If the
     * available data is more than the byte array passed, the number of
     * bytes returned is the size of the array passed. If the byte array
     * passed is larger than the available bytes, the available bytes to
     * read is copied and returned.
     * 
     * @param byte[] - place holder to copy the unread bytes of incoming data.
     * @return int - number of bytes read. -1 if end of the data/no more
     * data is to be read.
     * @exception IOException if an I/O error occurs
     */
    public int read(byte[] b) throws IOException {
        if (bytesToRead <= 0) {
            if (loadUnreadData() == true) {
                return -1;
            }
        }

        int nbytes = 0;

        if (bytesToRead < b.length) {
            nbytes = bytesToRead;
        } else {
            nbytes = b.length;
        }

        for (int i = 0; i < nbytes; i++) {
            b[i] = readBuffer[index++];
            bytesToRead--;
        }

        return nbytes;
    }

    /**
     * Method to read the next incoming packet from the packet database.
     * 
     * @return boolean - true if end of data.
     * - false if otherwise.
     * 
     */
    private boolean loadUnreadData() throws IOException {

        /*
         * If we've already read the data end, let the caller know.
         */
        if (dataEnd) {
            return true;
        } 

        /*
         * Perform the following in a while loop just in case the
         * packet read contains no data.
         */
        while (bytesToRead <= 0) {
            TRAMDataPacket sdp = null;

            sdp = (TRAMDataPacket) pktio.getPacket();

            /*
             * Check if it's the end packet. If so return true..
             * 
             */
            if ((sdp.getFlags() & TRAMDataPacket.FLAGBIT_TXDONE) != 0) {
		if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE |
		    TRAMLogger.LOG_SESSION | TRAMLogger.LOG_DATAMESG)) {

                    logger.putPacketln(this, "GOT END OF THE MESSAGE");
		}

                dataEnd = true;
                return true;
            }
            if ((sdp.getFlags() & TRAMDataPacket.FLAGBIT_SESSION_DOWN) != 0) {
                throw new IOException("Session is down");
            } 
            if ((sdp.getFlags() & TRAMDataPacket.FLAGBIT_UNRECOVERABLE) != 0) {
                throw new IOException("Unrecoverable data");
            } 
	    if ((sdp.getFlags() & TRAMDataPacket.FLAGBIT_MEMBER_PRUNED) != 0) {
                throw new IOException("Member Pruned");
            }

            readBuffer = sdp.getData();
            bytesToRead = sdp.getDataLength();
            index = 0;
        }

        return false;
    }

}

