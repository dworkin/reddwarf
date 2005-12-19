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
 * ChangeTRAMLoggingOption.java
 * 
 * Module Description:  This is an utility module that can be used to turn 
 *                      on logging dynamically by sending a control message
 *                      to the receivers. The description of the design and
 *                      the packet format is described below.
 *
 *                      The control message will be multicast message sent to
 *                      the same multicast address and the port on which a 
 *                      session is proceeding or is expected to take place.  
 *                      The minimal criteria that the message needs to satisfy
 *                      are-
 *                         1. Should contain the correct data source address 
 *                            of the expected sender of the multicast session.
 *                         2. Should contain the session Id of the session.
 *    
 *                      The actual source of this multicast souce can be 
 *                      anything. Though the above two requirements offer 
 *                      'some' sort of DoS attack prevention but is not a big 
 *                      deal at all!. Any node can obtain the info once the 
 *                      session starts. So we can discuss whether we need to 
 *                      have the above two checks or not. The only shield from
 *                      DoS is that the command and the format is not 
 *                      documented. Even if somebody found out about it, the 
 *                      only hinderance they can cause is to slow down and 
 *                      cannot make the protocol malfunction.
 *  
 *                      Advantages of making this command to be a multicast 
 *                      message:
 *                       1. Ability to turn on logging on multiple nodes in 
 *                          one message.
 *                       2. Doing via unicast will require the knowledge of 
 *                          the unicast port # on which the node is listening 
 *                          for TRAM control message. Even if have access to 
 *                          such info, enabling the logging on multiple 
 *                          machines is a pain.
 *                       3. Ability to turn on logging on few machines can be 
 *                          easily accommodated by listing the addresses of 
 *                          the nodes in the message. Nodes not listed will 
 *                          ignore this message.
 *                       4. We could use the TTL to our advantage to localize 
 *                          the logging operation to a region or to a 
 *                          particular LAN. Using TTL of 1 will only enable
 *                          logging on a particular LAN assuming that the 
 *                          message is being sent by a node on that LAN.
 *                       5. Easy and flexible. We can support an application 
 *                          that will send such a command based on the options 
 *                          specified by the user.
 *       
 *                   Disadvantages:
 *                       1. Security.
 *                       2. Need to determine the SessionId and the Data 
 *                          source address for sending command ( if we decide 
 *                          to have the checks).
 *                       3. Some of the sessions that we create today do not 
 *                          list a sessionId and the Data Source address in 
 *                          the transport Profile. The nodes learn about them 
 *                          from a beacon or the first datapacket they receive.
 *                          In such cases we will not be able to activate the 
 *                          logging before the session starts.
 *       
 *   The packet format for the control message:
 *   
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  | Version Number| Message Type  |   Sub Type    |A|I|     0   |V|
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |			     Session Id				   |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |           Length              |      Address count            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+   
 *  |                      Logging Level/Options                    |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        Source IP Address (Variable)           |
 *  ~                                                               ~
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        Host1 IP Address (Variable)            |
 *  ~                                                               ~
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        Host2 IP Address (Variable)            |
 *  ~                                                               ~
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  ~                             :                                  ~
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        Hostn IP Address (Variable)            |
 *  ~                                                               ~
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+      
 *
 *  Message Type: 1
 *
 *  Message SubType: 5
 *
 *  Flags:
 *
 *	A: Set when all the nodes have to assume the logging level specified.
 *	I: Set when the SessionId and Data source address can be ignored.
 *	V: Set when the Source IP address is a IPV6 address.
 *
 * Session Id:	    The identifier for the current session.
 *
 * Length:	    The packet's length.
 *
 * Address Count: Number of Host addresses listed in the message. Should
 *                be 0 if the flag 'A' bit is set.
 *
 * Logging Level: BitMask of the logging level.. details to be decided.
 *
 * Source IP Address: IP address of the multicast source (4 bytes for
 *		      IPV4 and 16 bytes for IPV6).
 * 
 * Host1 IP Address: IP address of the host 1 that needs to turn on the 
 *                   Logging to the appropriate level. 
 *
 * Hostn IP Address: IP address of the host n that needs to turn on the 
 *                   Logging to the appropriate level. 
 */
package com.sun.multicast.reliable.transport.tram;

import java.io.*;
import java.net.*;
import java.util.*;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.util.Util;

/**
 * ChangeTRAMLoggingOption is a basic TRAM configuration file parser cum loader.
 * The ChangeTRAMLoggingOption will have multiple LoadConfig methods and each
 * of the method just initializes the parameters that are relavent to the
 * class that is passed in as formal argument.
 */
final class ChangeTRAMLoggingOption {

    // TRAMTransportProfile related string tokens.

    static final String str_SESSION_ID = "SESSION_ID";
    static final String str_SOURCE_ADDRESS = "SOURCE_ADDRESS";
    static final String str_LOG_OPTION = "LOG_OPTION";
    static final String str_MCAST_ADDRESS = "MULTICAST_ADDRESS";
    static final String str_PORT = "PORT";
    static final String str_TTL = "TTL";
    static final String str_MEMBER_ADDRESS = "MEMBER_ADDRESS";
    static final String str_LOG_OPTION_PERFMON = "LOG_PERFORMANCE_MONITOR";
    static final String str_LOG_OPTION_CONG = "LOG_CONGESTION";
    static final String str_LOG_OPTION_CNTLMESG = "LOG_CONTROL_MESSAGES";
    static final String str_LOG_OPTION_DATAMESG = "LOG_DATA_MESSAGES";
    static final String str_LOG_OPTION_SESSION = "LOG_SESSION";
    static final String str_LOG_OPTION_SECURITY = "LOG_SECURITY";
    static final String str_LOG_OPTION_DATACACHE = "LOG_DATACACHE";
    static final String str_LOG_OPTION_DIAGNOSTICS = "LOG_DIAGNOSTICS";
    static final String str_LOG_OPTION_VERBOSE = "LOG_VERBOSE";
    static final String str_LOG_OPTION_INFO = "LOG_INFO";
    static final String str_LOG_OPTION_ALL = "LOG_ALL";
    static final String str_LOG_OPTION_NONE = "LOG_NONE";
    static final String str_LOG_OPTION_ABORT_TRAM = "LOG_ABORT_TRAM";

    private int sessionId = 0;
    private InetAddress srcAddress = null;
    private InetAddress mcastAddress = null;
    private Vector memberList = new Vector();
    private int port = 0;
    private int logOption = 0;
    private MulticastSocket ms = null;
    private int ttlVal = 1;

    /*
     * Main method to test or send the change logging option packet.
     */

    public static void main(String args[]) {

	if (args.length < 1) {
	    System.out.println("\n\n Usage: requires " +
			       "<logOptionConfig fileName> as input"  + 
			       "\n \n See logOptionConfig.txt for config " +
			       "file specification \n\n");
	    return;
	} 
	System.out.println("Got file logOptionConfig fileName as " + args[0]);
	ChangeTRAMLoggingOption logOpt = new ChangeTRAMLoggingOption();
	try {
	    logOpt.loadLogOptions(args[0]);
	}catch (IOException ie1) {
	    System.out.println("File Not Found");
	}
	try {
	    logOpt.send();
	}catch (IOException ie2) {
	    System.out.println("Options packet could not be sent");
	}

    }
    public ChangeTRAMLoggingOption() {
	try {
	    ms = new MulticastSocket();
	} catch (IOException e) {
	    System.out.println("Unable to create a multicast socket for " +
			       "sending");
	    return;
	}	
    }
    
    /**
     * LoadLogOptions method to read the configuration information. 
     * 
     * @param	Configuration file name.
     * @param 	reference to TRAMTransportProfile which is to be initialized
     * with configuration information.
     * 
     * @Exception Throws IOEception if the config file cannot be opened.
     */
    public final void loadLogOptions(String filename) throws 
    IOException {

        /*
         * ------------------------------------------------------------------
         * TRAMTransportProfile Configuration file format specification
         * <Param Token> = <Param value>
         * Examples
         * SESSION_ID = 1234567
         * SOURCE_ADDRESS = 129.148.75.95
	 * MULTICAST_ADDRESS = 224.10.10.20
	 * PORT = 4321
	 * TTL = 10
         * LOG_OPTION = LOG_CONGESTION | LOG_CONTROL_MESSAGES | LOG_INFO
         * ------------------------------------------------------------------
         */
        FileInputStream inputfile = new FileInputStream(filename);
        Properties prop = new Properties();
	byte flags = 0;
        InetAddress memberAddress = null;
        prop.load(inputfile);

        /*
         * Basically from this point on just check is a config item is
         * listed in the config file. If so load the specified value and go
         * to the next config item.
         */
        String val = null;

        /*
	 * Look for SESSION_ID entry. If present load the specified config 
	 * value.
	 */

        if ((val = prop.getProperty(str_SESSION_ID)) != null) {
	    Integer I = new Integer(val);
	    sessionId = I.intValue();
            if (sessionId < 0) {
                sessionId = 0;
            } 
        }

        // Look for Multicast address address.

        if ((val = prop.getProperty(str_MCAST_ADDRESS)) != null) {
            InetAddress ia = null;

            try {
                ia = InetAddress.getByName(val);
		mcastAddress = ia;
            } catch (UnknownHostException e) {
                System.out.println("Invalid Multicast Address");
            }
        }
        if ((val = prop.getProperty(str_SOURCE_ADDRESS)) != null) {
            try {
		srcAddress = InetAddress.getByName(val);
            } catch (UnknownHostException e) {
                System.out.println("Invalid Data SourceAddress");
            }
        }
        if ((val = prop.getProperty(str_PORT)) != null) {
            Integer I = new Integer(val);
            port = I.intValue();
        }

        if ((val = prop.getProperty(str_TTL)) != null) {
            Integer I = new Integer(val);
            ttlVal = I.intValue();
	    if (ttlVal > 255)
		ttlVal = 255;
        }

        if ((val = prop.getProperty(str_MEMBER_ADDRESS)) != null) {
	    System.out.println(val);
	    StringTokenizer stk = new StringTokenizer(val, "\t\n\r\f, ");
	    while (stk.hasMoreTokens()) {
		String tok = stk.nextToken();
		try {
		    memberAddress = InetAddress.getByName(tok);
		    memberList.addElement((Object)memberAddress);
		} catch (UnknownHostException e) {
		    System.out.println("Invalid Data SourceAddress " + tok);
		}
	    }
        }

        if ((val = prop.getProperty(str_LOG_OPTION)) != null) {
	    System.out.println(val);
	    StringTokenizer stk = new StringTokenizer(val, "\t\n\r\f| ");
	    logOption = 0;
	    while (stk.hasMoreTokens()) {
		String tok = stk.nextToken();
		System.out.println(tok);
		if (str_LOG_OPTION_PERFMON.equalsIgnoreCase(tok)) {
		    logOption |= TRAMLogger.LOG_PERFMON;
		    System.out.println("enabled Performance Monitoring");
		    continue;
		}
		if (str_LOG_OPTION_CONG.equalsIgnoreCase(tok)) {
		    logOption |= TRAMLogger.LOG_CONG;
		    System.out.println("enabled Congestion");
		    continue;
		}
		if (str_LOG_OPTION_CNTLMESG.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_CNTLMESG;
		    System.out.println("enabled Control Message");
		    continue;
		}
		if (str_LOG_OPTION_DATAMESG.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_DATAMESG;
		    System.out.println("enabled Data Message");
		    continue;
		}
		if (str_LOG_OPTION_SESSION.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_SESSION;
		    System.out.println("enabled Session level");
		    continue;
		}
		if (str_LOG_OPTION_SECURITY.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_SECURITY;
		    System.out.println("enabled Security");
		    continue;
		}
		if (str_LOG_OPTION_DATACACHE.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_DATACACHE;
		    System.out.println("enabled Data Cache");
		    continue;
		}
		if (str_LOG_OPTION_DIAGNOSTICS.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_DIAGNOSTICS;
		    System.out.println("enabled DIAGNOSTICS");
		    continue;
		}
		if (str_LOG_OPTION_VERBOSE.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_VERBOSE;
		    System.out.println("enabled VERBOSE");
		    continue;
		}
		if (str_LOG_OPTION_INFO.equalsIgnoreCase(tok)) {
		    logOption |=  TRAMLogger.LOG_INFO;
		    System.out.println("enabled INFO");
		    continue;
		}
		if (str_LOG_OPTION_ALL.equalsIgnoreCase(tok)) {
		    logOption =  TRAMLogger.LOG_ANY;
		    System.out.println("enabled ALL LEVELS");
		    continue;
		}
		if (str_LOG_OPTION_NONE.equalsIgnoreCase(tok)) {
		    logOption =  TRAMLogger.LOG_NONE;
		    System.out.println("DISABLED ALL LEVELS");
		}
		if (str_LOG_OPTION_ABORT_TRAM.equalsIgnoreCase(tok)) {
		    logOption =  TRAMLogger.LOG_ABORT_TRAM;
		    System.out.println("Abort TRAM!");
		}
	    }
        }
    }
    /*
     *
     */
    public void send() throws IOException {
    
	TRAMLoggingOptionPacket	opkt = null;
	/*
	 * First checkout if any address list to be listed and proceed 
	 * accordingly.
	 */
	if (memberList.size() == 0) {
	    opkt = new TRAMLoggingOptionPacket(mcastAddress, port,
					       sessionId, srcAddress,
					       logOption, null, 0);
	} else {

	    /*
	     * first make the address liste into an array of InetAddresses
	     */
	    InetAddress[] ad = new InetAddress[memberList.size()];
	    for (int j = 0; j < memberList.size(); j++) {
		try {
		    ad[j] = (InetAddress) memberList.elementAt(j);
		    System.out.println("Including Member Address " + 
				       ad[j]);
		} catch (IndexOutOfBoundsException ie) {
		    break;
		}
	    }
	    opkt = new TRAMLoggingOptionPacket(mcastAddress, port,
					       sessionId, srcAddress,
					       logOption, ad, ad.length);
	}
	DatagramPacket dp = opkt.createDatagramPacket();
	ms.send(dp, (byte)ttlVal);
	System.out.println("Sent Change Logging option packet to " + 
			   mcastAddress + " on port " + port +
			   " with a TTL of " + ttlVal +
			   " with a logging option value of " + logOption);
    }
}

