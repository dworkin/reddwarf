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
 * Byte0Filter.java
 */
package com.sun.multicast.reliable.channel;

import java.net.DatagramPacket;
import java.io.IOException;
import java.io.Serializable;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.reliable.transport.SessionDoneException;

/**
 * A dynamic filter that removes all packets whose data begins with the
 * byte 0.
 * 
 * This is not part of the public interface of the channel code. 
 * It's a package-local class.
 */
class Byte0Filter extends BasicDynamicFilter implements Serializable {

    /**
     * Create a Byte0Filter.
     */
    Byte0Filter() {}

    /**
     * Read a packet of data. This method will block until a packet is
     * available or an exception is thrown.
     * 
     * @return the next packet of data
     * @exception IOException if an I/O error occurs
     * @exception SessionDoneException if the session is done
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public DatagramPacket receive() 
            throws IOException, SessionDoneException, RMException {
        DatagramPacket dp = null;

        while (dp == null) {
            dp = lowerSocket.receive();

            if (dp.getData()[0] == 0) {
                dp = null;
            } 
        }

        return (dp);
    }

}

