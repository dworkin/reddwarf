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
 * TRAM_STATE.java
 */
package com.sun.multicast.reliable.transport.tram;

/*
 * TRAM Operational states.
 */

class TRAM_STATE {
    public static final byte INIT = 1;
    public static final byte PRE_DATA_BEACON = 2;
    public static final byte DATA_TXM = 3;
    public static final byte CONGESTION_IN_EFFECT = 4;
    public static final byte AWAITING_BEACON = 5;
    public static final byte SEEKING_HA_MEMBERSHIP = 6;
    public static final byte SEEKING_MTHA_MEMBERSHIP = 7;
    public static final byte HEAD_BINDING = 8;
    public static final byte ATTAINED_MEMBERSHIP = 9;
    public static final byte POST_DATA_BEACON = 10;
    public static final byte SEEKING_REAFFIL_HEAD = 11;
    public static final byte REAFFIL_HEAD_BINDING = 12;
    public static final byte REAFFILIATED = 13;
    public static final String TRAMStateNames[] = {
        "", "INIT", "PRE_DATA_BEACON", "DATA_TXM", "CONGESTION_IN_EFFECT", 
        "AWAITING_BEACON", "SEEKING_HA_MEMBERSHIP", 
        "SEEKING_MTHA_MEMBERSHIP", "HEAD_BINDING", "ATTAINED_MEMBERSHIP", 
        "POST_DATA_BEACON ", "SEEKING_REAFFIL_HEAD", "REAFFIL_HEAD_BINDING", 
    };
}

