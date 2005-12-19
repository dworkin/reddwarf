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
 * IrrecoverableDataException.java
 */
package com.sun.multicast.reliable.transport;

import com.sun.multicast.reliable.RMException;

/**
 * The IrrecoverableDataException is thrown when the transport layer
 * cannot recover a lost packet. This exception may be thrown by the receive
 * method of RMPacketSocket or read method of InputStream of the
 * RMStreamSocket.
 * When the transport finds that a data block cannot be recovered,
 * this exception will be thrown.
 * Once this exception is thrown, the application can continue receiving
 * data(typically when unordered delivery is opted) or abort reception
 * by closing the socket.
 * 
 */
public class IrrecoverableDataException extends RMException {

    /**
     * Constructs a IrrecoverableDataException message with no detail.
     */
    public IrrecoverableDataException() {
        super();
    }

    /**
     * Constructs a IrrecoverableDataException message with
     * a detail string.
     * 
     * @param s the detail message
     */
    public IrrecoverableDataException(String s) {
        super(s);
    }

}

