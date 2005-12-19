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
 * SUBMESGTYPE.java
 */
package com.sun.multicast.reliable.transport.tram;

/*
 * STP Sub Message types
 */

class SUBMESGTYPE {

    // Sub message types for multicast control messages

    public static final byte BEACON = 1;
    public static final byte HELLO = 2;
    public static final byte HA = 3;
    public static final byte MS = 4;
    public static final byte CHANGE_LOGGING = (byte)0x5a;
    public static final String mcastControl[] = {
        "", "Beacon", "Hello", "Head Advertisement", "Member Solicit",
	"Change Logging"
    };

    // Sub message types for Unicast control messages.

    public static final byte AM = 1;
    public static final byte RM = 2;
    public static final byte HELLO_Uni = 3;
    public static final byte ACK = 4;
    public static final byte HB = 5;
    public static final String ucastControl[] = {
        "", "Accept Member", "Reject Member", "Hello", "Ack", 
        "Head Bind"
    };

    // Sub message types multicast Data messages.

    public static final byte DATA = 1;
    public static final byte DATA_RETXM = 2;
    public static final String mcastData[] = {
        "", "Data", "Retransmitted Data", 
    };

    public static String toString(int m, int sm) {
	String messageType = MESGTYPE.toString(m);
	String submessageType = null;

	switch (m) {
	case MESGTYPE.MCAST_CNTL:
	    if (sm == SUBMESGTYPE.CHANGE_LOGGING) {
		submessageType = "Change Logging";
		break;
	    }

	    if (sm >= 1 && sm <= 4)
	        submessageType = mcastControl[sm];

	    break;

	case MESGTYPE.MCAST_DATA:
	    if (sm == 1 || sm == 2)
	        submessageType = mcastData[sm];

	    break;

	case MESGTYPE.UCAST_CNTL:
	    if (sm >= 1 && sm <= 6)
	        submessageType = ucastControl[sm];

	    break;
        }

	if (submessageType == null)
	    submessageType = "Invalid submessage type " + sm;

	return messageType + " " + submessageType;
    }

}
