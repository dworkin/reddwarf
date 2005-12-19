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
 * TMODE.java
 */
package com.sun.multicast.reliable.transport.tram;

/**
 * Constants to define the various modes of TRAM Transport.
 */
public class TMODE {

    /**
     * The constant SEND_ONLY is used to specify the transport to perform the
     * role of a sender only. Such a transport will discard all multicast data
     * packets received from the network.
     */
    public static final byte SEND_ONLY = 1;

    /**
     * The constant RECEIVE_ONLY is used to specify the transport to perform
     * the role of a receiver only. Such a transport will discard all
     * multicast data transmission requests by the application.
     */
    public static final byte RECEIVE_ONLY = 2;

    /**
     * The constant SEND_RECEIVE is used to specify the transport to perform
     * the role of a sender as well as a receiver. This option is typically
     * used in a multisender session. This option is currently not supported
     * by TRAM.
     */
    public static final byte SEND_RECEIVE = 3;

    /**
     * The constant REPAIR_NODE is used to specify the transport to perform
     * the role of a designated repair node. Such a transport will support
     * no local application. It neither accepts multicast data for transmission
     * nor forwards received multicast data towards the application. Such
     * node will just receive multicast data from the network and perform
     * retranmissions of reported lost multicast packets.
     */
    public static final byte REPAIR_NODE = 4;
}

