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
 * SessionDownException.java
 */
package com.sun.multicast.reliable.transport;

import com.sun.multicast.reliable.RMException;

/**
 * The SessionDownException is thrown when the transport detects that
 * all the senders of the multicast session are inactive. This exception may
 * be thrown by any method of RMPacketSocket or RMStreamSocket
 * when the transport session finds that sender(s) are inactive or
 * are found to be offline. Once this exception is thrown,
 * the application should close the socket.
 * 
 * <P>Some transports that support only one sender will throw this
 * exception upon detecting that sender is offline. The transports
 * that support multiple senders will throw this exception when all
 * senders are alleged to have gone offline - typically signifying
 * a network partition.
 */
public class SessionDownException extends RMException {

    /**
     * Constructs a SessionDownException message with no detail.
     */
    public SessionDownException() {
        super();
    }

    /**
     * Constructs a SessionDownException message with
     * a detail string.
     * 
     * @param s the detail message
     */
    public SessionDownException(String s) {
        super(s);
    }

}

