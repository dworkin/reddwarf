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
 * TRAMLoggingOptionPacket.java
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

class TRAMLoggingOptionPacket extends TRAMPacket {


    /*
     * Flag definitions.
     */
    public static final byte FLAGBIT_ALLNODES = (byte) (1 << 0);
    public static final byte FLAGBIT_IGNORE_ID_ADDRESS = (byte) (1 << 1);
    public static final byte FLAGBIT_V6ADDRESS = (byte) (1 << 7);

    private static final int IPV4_ADDRESS_SIZE = 4;

    private static final int ADDRESS_COUNT = 0;
    private static final int LOG_OPTION = ADDRESS_COUNT + 2;
    private static final int SOURCE_ADDR = LOG_OPTION + 4;
    private static final int HOST1_ADDRESS = SOURCE_ADDR  + IPV4_ADDRESS_SIZE;
                                        // 4  is a valid V4 address
					// length. Needs to be changed
					// when Java supports V6.
					//
    private int logOption = TRAMTransportProfile.LOG_NONE;
    private InetAddress srcAddress = null;
    private int  addressCount = 0;
    private InetAddress[] addressList = null;
    /**
     */
    public TRAMLoggingOptionPacket(DatagramPacket dp) {

        /*
         * The parent constructor is called to retrieve the data buffer
         * and load the TRAM header fields.
         */
        super(dp);
	int i = super.readInt(SOURCE_ADDR);
	srcAddress = Util.intToInetAddress(i);
        logOption = (super.readInt(LOG_OPTION));
	addressCount = ((int)super.readShort(ADDRESS_COUNT)) & 0x0000ffff;
	if (addressCount == 0)
	    return;

	/*
	 * Read in the included addresses into the addressList.
	 */
	addressList = new InetAddress[addressCount];
	int offset = HOST1_ADDRESS;
	for (i = 0; i < addressCount; i++) {
	    addressList[i] = Util.intToInetAddress(super.readInt(offset));
	    offset = offset + IPV4_ADDRESS_SIZE;
	}
    }

    /**
     * Create a congestion packet for transmission. Set the target address
     * and port.
     *
     * @param ia the IP address of the head.
     * @param port the IP port number of the head.
     */
    public TRAMLoggingOptionPacket(InetAddress ia, int port, int sessionId, 
				   InetAddress srcAddress, int logOption,
				   InetAddress[] addressList, int addrCount) {
	super((HOST1_ADDRESS + (addrCount * IPV4_ADDRESS_SIZE)), 
	      sessionId);
        setAddress(ia);
        setPort(port);
        setLogOption(logOption);
	this.srcAddress = srcAddress;
        setMessageType(MESGTYPE.MCAST_CNTL);
        setSubType(SUBMESGTYPE.CHANGE_LOGGING);
	addressCount = addrCount;
	this.addressList = addressList;
	byte flagValue = 0;
	if ((srcAddress == null) || (sessionId == 0))
	    flagValue |= FLAGBIT_IGNORE_ID_ADDRESS;
	if ((addressList == null) || (addressList.length == 0))
	    flagValue |= FLAGBIT_ALLNODES;
	setFlags(flagValue);
    }

    /**
     * Create a DatagramPacket from the existing data in this class.
     *
     * @return a DatagramPacket with the current TRAMPacket contents.
     */
    public DatagramPacket createDatagramPacket() {
	if (srcAddress == null)
	    super.writeInt(0, SOURCE_ADDR);
	else
	    super.writeInt(Util.InetAddressToInt(srcAddress), SOURCE_ADDR);

        super.writeInt(logOption, LOG_OPTION);
	
        if ((addressList == null) || (addressList.length == 0)) {
	    addressCount = 0;
	    
	} else {
	    int offset = HOST1_ADDRESS;
	    addressCount = addressList.length;
	    for (int i = 0; i < addressCount; i++) {
		super.writeInt(Util.InetAddressToInt(addressList[i]), offset);
		offset = offset + IPV4_ADDRESS_SIZE; 
	    }
	}
	super.writeShort((short)addressCount, ADDRESS_COUNT);	   
        return super.createDatagramPacket();
    }

    /**
     * Get the Count of addresses listed in the packet.
     *
     * @return the number of member addresses listed in the packet.
     */
    public int getAddressCount() {
        return addressCount;
    }

    /**
     * Set the count of addresses that is listed in the packet.
     *
     * @param the count of addresses that is listed in the packet.
     */
    public void setAddressCount(int value) {
        addressCount = value;
        super.writeShort((short)value, ADDRESS_COUNT);
    }

    /**
     * Get the logging option specified in the packet.
     *
     * @return the logging option specified in the packet.
     */
    public int getLogOption() {
        return logOption;
    }

    /**
     * Set the logging option specified in the packet.
     *
     * @param the logging option specified in the packet.
     */
    public void setLogOption(int value) {
        logOption = value;
        super.writeInt(value, LOG_OPTION);
    }


    /**
     * gets the Inet address stored in the Source address field.
     *
     * @return InetAddress - source address of the Hello packet.
     */
    public InetAddress getSrcAddress() {
        return srcAddress;
    }

    /**
     * set the specified address to be the source address of the
     * hello packet.
     *
     * @param InetAddress - source address to be set.
     */
    public void setSrcAddress(InetAddress address) {
        srcAddress = address;
    }

    /**
     * gets the addresses of the members listed in the packet.
     *
     * @return InetAddress - Address list of the nodes that need to adopt the
     *                       logging option.
     */
    public InetAddress[] getAddressList() {
        return addressList;
    }

    /**
     * set the specified addresses to be list of the members that need to adopt
     * logging option being specified.
     *
     * @param InetAddress[] - Members' address list to be set.
     */
    public void setAddressList(InetAddress[] addresses) {
        addressList = addresses;
    }

}




