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
 * TRAMTransportprofile.java
 */

/*
 * Module Name: TRAMTransportProfile
 * 
 * Module Description:  TRAMTransportProfile class definition.
 * TRAMTransportProfile implements the generic
 * Transport profile interface.
 */
package com.sun.multicast.reliable.transport.tram;

import java.awt.*;
import java.io.IOException;
import java.net.*;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.TransportProfile;

/**
 * A TransportProfile for TRAM (Tree-based Reliable Multicast Protocol).
 * 
 * <P>This class adds several transport-specific public methods
 * that control transport-specific parameters. Several of the
 * most interesting are getMinDataRate, setMinDataRate, getMaxDataRate,
 * and setMaxDataRate. These allow the application to specify
 * minimum and maximum data rates for the session. The minimum
 * rate is purely advisory for now. It may be used later to
 * prune receivers that can't keep up. The maximum data rate
 * is used to throttle outgoing data. This avoids overrunning
 * network elements.
 * 
 * @see com.sun.multicast.reliable.transport.TransportProfile
 */
public class TRAMTransportProfile implements TransportProfile, Cloneable, 
        java.io.Serializable {

    /*
     * Constants
     */

    /**
     * constant to indicate the maximum value of the port number.
     */
    private static final int MAX_PORT_NUMBER = 65535;
    
    /**
     * constant to limit congestionWindow as a multiple of ackWindow.
     */
    public static final int MIN_CONG_WINDOW_MULTIPLE = 2;
    public static final int MAX_CONG_WINDOW_MULTIPLE = 5;

    /**
     * Constant to specify the Late Join preference.
     * LATE_JOIN_WITH_LIMITED_RECOVERY specifies the transport, in case
     * of a late join, to recover as much of data as possible.
     */
    public static final int LATE_JOIN_WITH_LIMITED_RECOVERY = 1;

    /**
     * Constant to specify the Late Join preference.
     * LATE_JOIN_WITH_FULL_RECOVERY specifies the transport, in case
     * of a late join, to recover all of prior data. Typically used
     * by applications like file transfer.
     */
    protected static final int LATE_JOIN_WITH_FULL_RECOVERY = 2;

    /**
     * Constant to specify the Late Join preference.
     * LATE_JOIN_WITH_NO_RECOVERY specifies the transport, in case
     * of a late join, to recover none of the earlier data. Typically
     * used by realtime applications like stock quotes.
     */
    public static final int LATE_JOIN_WITH_NO_RECOVERY = 3;

    /**
     * Constant to specify the algorithm to use to build the TRAM repair tree.
     * The constant TREE_FORM_HA specifies the transport to use Head
     * Advertisement(HA) algorithm to build the dynamic repair tree. Refer
     * TRAM protocol information to learn more about HA algorithm.
     */
    public static final int TREE_FORM_HA = 1;

    /**
     * Constant to specify the algorithm to use to build the TRAM repair tree.
     * The constant TREE_FORM_MTHA specifies the transport to use Member
     * Triggered Head Advertisement(HA) algorithm to build the dynamic repair
     * tree. Refer TRAM protocol information to learn more about MTHA
     * algorithm.
     */
    public static final int TREE_FORM_MTHA = 2;

    /**
     * Constant to specify the algorithm to use to build the TRAM repair tree.
     * The constant TREE_FORM_HAMTHA specifies the transport to use the
     * combination of bothe HA and MTHA algorithm to build the dynamic repair
     * tree. Refer TRAM protocol information to learn more about HAMTHA
     * algorithm.
     */
    public static final int TREE_FORM_HAMTHA = 3;
    
    /**
     * Constants to specify the algorithm to use to build the TRAM repair tree.
     * The constants TREE_FORM_HA_S, TREE_FORM_MTHA_S, TREE_FORM_HAMTHA_S
     * specify modified dynamic tree formation algorithms that use a file
     * to store configured, or tree formation information from past sessions.
     * The are the same as the corresponding dynamic ones, except the
     * information read from the file is used to restrict who are eligible
     * to be a head.  The file contains a number of lines.  Each line contains
     * a IP address and a ttl. (e.g. 129.10.20.30 1)  If ttl=0 and the address
     * is the local host address, then you are allowed to be a head.
     * Otherwise, the address is that of an eligible head for you.
     */
    public static final int TREE_FORM_STATIC_R = 32;
    public static final int TREE_FORM_STATIC_RW = 96;
    public static final int TREE_FORM_HA_STATIC_R =
                         TREE_FORM_HA + TREE_FORM_STATIC_R;
    public static final int TREE_FORM_MTHA_STATIC_R =
                         TREE_FORM_MTHA + TREE_FORM_STATIC_R;
    public static final int TREE_FORM_HAMTHA_STATIC_R =
     				TREE_FORM_HAMTHA + TREE_FORM_STATIC_R;
    public static final int TREE_FORM_HA_STATIC_RW =
     				TREE_FORM_HA + TREE_FORM_STATIC_RW;
    public static final int TREE_FORM_MTHA_STATIC_RW =
     				TREE_FORM_MTHA + TREE_FORM_STATIC_RW;
    public static final int TREE_FORM_HAMTHA_STATIC_RW =
     				TREE_FORM_HAMTHA + TREE_FORM_STATIC_RW;

    /*
     * Various Logging levels.
     */
    /**
     * Constant to specify 'Log Everything' option. Choosing this option 
     * will result in logging every logging message in the code path
     * as a result the performance severely degrades. 
     */
    public static final int LOG_VERBOSE = TRAMLogger.LOG_ANY;

    /**
     * Constant to specify 'Log nothing' option. This option causes logging
     * to be turned off. Fatal error conditions will over-ride this logging
     * preference and get logged anyways.
     */
    public static final int LOG_NONE = TRAMLogger.LOG_NONE;

    /**
     * Constant to specify 'Log DIAGNOSTICS' messages. Choosing this option 
     * will result in logging important events in the code path -
     * logs important usual events and all unusual/error events in the code 
     * path. Typically used while debugging an operational code.
     */
    public static final int LOG_DIAGNOSTICS = TRAMLogger.LOG_DIAGNOSTICS;

    /**
     * Constant to specify 'Log INFO' messages. Choosing this option will
     * result in logging very important events or milestones. This is 
     * typically the recommended mode of logging for a code base that is 
     * operational.
     */
    public static final int LOG_INFO = TRAMLogger.LOG_INFO;

    /**
     * Constant to specify 'Logging of Congestion' related messages. Choosing 
     * this option will result in logging all events related to
     * TRAM congestion control. This mode is recommended for functionality 
     * based debugging.
     */
    public static final int LOG_CONGESTION = TRAMLogger.LOG_CONG;
     
    /**
     * Constant to specify 'Logging of TRAM Control' messages. Choosing 
     * this option will result in logging TRAM control message
     * (both send and reception) events. This mode is recommended for 
     * functionality based debugging.
     */
    public static final int LOG_CONTROL_MESG = TRAMLogger.LOG_CNTLMESG;

    /**
     * Constant to specify 'Logging of TRAM Data' messages. Choosing 
     * this option will result in logging TRAM data message
     * (both send and reception) events. This mode is recommended for 
     * functionality based debugging.
     */
    public static final int LOG_DATA_MESG = TRAMLogger.LOG_DATAMESG;

    /**
     * Constant to specify 'Logging of Multicast session related' messages. 
     * Choosing this option will result in logging TRAM messages that
     * session related - like, the sender is not sending data or joined the
     * session late. This mode is recommended for functionality based 
     * debugging.
     */
    public static final int LOG_SESSION_STATUS = TRAMLogger.LOG_SESSION;

    /**
     * Constant to specify 'Logging of Security related' messages. 
     * Choosing this option will result in logging TRAM messages that are
     * related to security. The logging takes place when any one of the 
     * security option (authentication & cipher) is turned on. This mode 
     * is recommended for functionality based debugging.
     */
    public static final int LOG_SECURITY = TRAMLogger.LOG_SECURITY;

    /**
     * Constant to specify 'Logging of Data Cache related' messages. 
     * Choosing this option will result in logging TRAM messages that
     * are related to TRAM data cache. This mode is recommended for 
     * functionality based debugging.
     */
    public static final int LOG_DATA_CACHE = TRAMLogger.LOG_DATACACHE;

    /**
     * Constant to specify 'Logging of Test related' messages. 
     * Choosing this option will result in logging TRAM messages that
     * are not based on any particular functionality but those messages
     * that are interesting for the current testing.
     */
    public static final int LOG_TEST = TRAMLogger.LOG_TEST;

    /**
     * Level for visual performance monitor.
     *
     * Choosing this option will result in a performance monitor 
     * which is started as a separate thread.
     *
     * The monitor will graphically display the current data rate
     * and the average data rate.
     */
    public static final int LOG_PERFORMANCE_MONITOR = TRAMLogger.LOG_PERFMON;

    /*
     * Variable names redefined to minimize SAP packets.
     * Original name			New name
     * name				n
     * address				a
     * port				p
     * transferDataSize			s
     * transferDuration			d
     * multisender			m
     * ordered				o
     * headonly				h
     * sessionId			id
     * logMask 				lm
     * mrole				mr
     * tmode				tm
     * maxMembers			xm
     * helloRate			hr
     * pruningHelloRate			phr
     * beaconRate			br
     * ackWindow			aw
     * congestionWindow			cw
     * nackReportWindow			nw
     * unicastPort			up
     * maxDataRate			xr
     * minDataRate			nr
     * dataSourceAddress		sa
     * haInterval			hi
     * maxBuf				xb
     * useAuthentication		ua
     * ttl				ttl
     * maxNonHeads			xnh
     * msRate				msr
     * allowLanTrees			alt
     * authenticationSpecFileName	afn
     * authenticationSpecPassword	apw
     * treeFormationPreference 		tfp
     * lateJoinPreference		ljp
     * maxHelloMisses			xhm
     * beaconTTLIncrements		bti
     * haTTLIncrements			hti
     * haTTLLimit			htl
     * msTTLIncrements			mti
     * maxHABWWhileDataTransfer		xbd
     * maxHABWWhileTreeForming		xbt
     */

    /*
     * Private variables - Generic to all transports as specified by
     * TransportProfile.
     */
    private String n = "TRAM V"+TRAM_INFO.VERSION;
    private InetAddress a = null;
    private int p = 4567;        // 4567 is the reserved TRAM port number.
    private byte ttl = 1;
    private boolean m = false;
    private boolean o = false;

    /*
     * Private variables specific to TRAM
     */
    private byte mr = MROLE.MEMBER_RELUCTANT_HEAD;
    private byte tm = TMODE.RECEIVE_ONLY; // Transport mode.
    private short xm = 32;     	// maximum number of members in a group.
    private boolean h = false;	 // to be head w/o application

    /*
     * Among the total members that can accepted, a third of members need to
     * be those that can be heads. It is okay to have all members that can
     * play the role of a head but it is not okay to have all members that
     * can ONLY be  members.
     */
    private short xnh = (short) ((xm < 3) ? (xm / 2) 
                                         : ((2 * xm) / 3));
    private long hr = 1000;                  // hello rate in millisecs.
    private long phr = 1000;                 // pruning hello rate in millisecs.
    private long msr = 500;                  // ms rate in millisecs.
    private long br = 1000;                  // beacon rate in millisecs.
    private short nw = 5;                    // nack reporting window size.
    private byte xhm = 5;                    // Maximum hellos to miss
    private short aw = 32;	    	     // ACK window size.
    private short xc = 5;   		     // Maximum congestion window
    					     // as a multiple of ackWindow
    private short cw = (short)(2 * aw);      // congestion window 
					     // starts at twice aw

    private short nackReportWindow = 5;      // nack reporting window size.
    private byte maxHelloMisses = 5;         // Maximum hellos to miss before

    // declaring a member down.

    private byte bti = 2;

    /*
     * TTL increments for beacon message.
     */
    private int up = 0;               // Unicast port to be used.
    private double s = 0;             // Transfer data size in bytes.
    private long d = 0;     	      // transfer duration in minutes.
    private long xr = 64000;          // maximum transmitter rate
    private long nr = 1000;           // Minimum data rate
    private InetAddress sa = null;    // source of the multicast data stream.

    private int lm = LOG_NONE;	      // Log nothing

    private byte hti = 2;
    private byte htl = 1;             // defaults to session TTL
    private byte mti = 1;             // TTL increments for ms message.
    private long hi = 2000;           // HA advt interval in ms
    private long xbd = xr / 20;

    /*
     * Max HA advt bandwidth in kbps
     */
    private long xbt = xr;

    /*
     * Maximum data rate that can used while forming the tree
     */
    private int tfp = TREE_FORM_HAMTHA;
    private int ljp = LATE_JOIN_WITH_NO_RECOVERY;

    private int xb = 1454;        // 1500=1454+ether(14)+ip(20)+udp(12) headers

    // private int xb = 1444;      // 1500=1444+ether(24)+ip(20)+udp(12) headers
    
    private boolean alt = false;
    private boolean ua = false;
    private transient String afn = null;
    private transient String apw = null;
    private int id;
    private boolean sx = false;		// smooth transmission
    private int cs = 1200;		// max size of cache
    private int mtp = 5;		// max time allowed before pruning
					// a slow member
    private boolean rad = true;		// reaffiliate after being disowned

    /* 
     * This is only used for testing.
     */
    private int receiveBufferSize = 0; 

    /**
     * Creates an TRAMTransportProfile using the details specified in a
     * configuration in default TRAM configuration file.
     * 
     * @exception java.io.IOException if an I/O exception occurs
     */

    /*
     * public TRAMTransportProfile() throws IOException {
     * // The file name is not specified. Use the default file name.
     * TRAMConfigurator.LoadConfig(TRAM_INFO.CONFIGFILE,this);
     * }
     */

    /**
     * Creates an TRAMTransportProfile using the details specified in a
     * configuration file.
     * 
     * @param filename the name of an TRAM configuration file
     * @exception java.io.IOException if an I/O exception occurs
     */
    public TRAMTransportProfile(String filename) throws IOException {
        TRAMConfigurator.LoadConfig(filename, this);
    }

    /**
     * Creates an TRAMTransportProfile using the Multicast Address and port
     * specified.
     * 
     * @param ia a multicast InetAddress
     * @param port the multicast port number
     * @exception java.io.IOException if an I/O exception occurs
     * @exception InvalidMulticastAddressException if the multicast address
     * supplied is not a multicast address
     */
    public TRAMTransportProfile(InetAddress ia, int port) 
            throws IOException, InvalidMulticastAddressException {
        setAddress(ia);
        setPort(port);
    }

    /*
     * TransportProfile config tester.
     */

    public static void main(String[] args) {
        TRAMTransportProfile tp = null;

        if (args.length == 0) {
            System.out.println("No config file specified. Loading default");
            System.exit(1);

        /*
         * try {
         * tp = new TRAMTransportProfile();
         * } catch (IOException e) {
         * System.out.println("IOException");
         * System.exit(1);
         * }
         */
        } else {
            System.out.println("Using " + args[0] + " as config file ");

            try {
                tp = new TRAMTransportProfile(args[0]);
            } catch (IOException e) {
                System.out.println("IOException");
                System.exit(1);
            }
        }

        System.out.println("Mrole = " + tp.getMrole());
        System.out.println("Tmode = " + tp.getTmode());
        System.out.println("Address = " + tp.getAddress().getHostName());
        System.out.println("Port = " + tp.getPort());
        System.out.println("TTL = " + tp.getTTL());
        System.out.println("Max Members = " + tp.getMaxMembers());
        System.out.println("Hello Rate = " + tp.getHelloRate());
        System.out.println("Ms Rate= " + tp.getMsRate());
        System.out.println("Beacon Rate = " + tp.getBeaconRate());
        System.out.println("Ack Window = " + tp.getAckWindow());
        System.out.println("Nack Window = " + tp.getNackReportWindow());
        System.out.println("HelloMisses = " + tp.getMaxHelloMisses());
        System.out.println("Ms TTL Incr = " + tp.getMsTTLIncrements());
        System.out.println("Beacon TTL Incr = " 
                           + tp.getBeaconTTLIncrements());
        System.out.println("Unicast Port = " + tp.getUnicastPort());
        System.out.println("Min Rate = " + tp.getMinDataRate());
        System.out.println("Max Rate = " + tp.getMaxDataRate());
        System.out.println("Data Src Address = " 
                           + tp.getDataSourceAddress().getHostName());
        System.out.println("Hello Rate = " + tp.getHelloRate());
        System.out.println("Maximum Buffer Size = " + tp.getMaxBuf());

        if (tp.isValid() != true) {
            System.out.println("The config is Invalid");
        } 
    }

    /**
     * Creates an RMStreamSocket using this TransportProfile.
     * 
     * @param sendReceive indicates whether this socket is being used
     * to send or receive data. The code automatically fills in the
     * tmode and mrole fields based on this parameter.
     * @return a new RMStreamSocket
     * @exception UnsupportedException if the transport does not
     * support a stream interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMStreamSocket createRMStreamSocket(int sendReceive) 
            throws UnsupportedException, InvalidTransportProfileException, 
                   IOException {

	return this.createRMStreamSocket(sendReceive, null);
    }

    /**
     * Creates an RMStreamSocket using this TransportProfile.
     * 
     * @param sendReceive indicates whether this socket is being used
     * to send or receive data. The code automatically fills in the
     * tmode and mrole fields based on this parameter.
     * @param interfaceAddress indicates the IP address of the interface
     * to use for the multicast socket.
     * @return a new RMStreamSocket
     * @exception UnsupportedException if the transport does not
     * support a stream interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMStreamSocket createRMStreamSocket(
	int sendReceive, InetAddress interfaceAddress) 
        throws UnsupportedException, InvalidTransportProfileException, 
        IOException {

        // Make sure the TransportProfile is valid.

        validate();

	/*
	 * Since its a Stream Socket, turn on ordering.
	 */
	setOrdered(true);
        /*
         * Based on the value of the sendReceive parameter, set the
         * transmit mode and member role fields appropriately. Any
         * value currenty in these fields are overridden.
         */
        switch (sendReceive) {

        case TransportProfile.RECEIVER: {
            setTmode(TMODE.RECEIVE_ONLY);

            break;
        }

        case TransportProfile.SENDER: {
            setTmode(TMODE.SEND_ONLY);
            setMrole(MROLE.MEMBER_EAGER_HEAD);

            break;
        }

        default: {
            throw new UnsupportedException("Mode specified is unsupported " 
                                           + sendReceive);
        }
        }

        randomizeTimers();

        TRAMStreamSocket so = new TRAMStreamSocket();

        so.connect(this, interfaceAddress);

        return (RMStreamSocket) so;
    }

    /**
     * Creates an RMPacketSocket using this TransportProfile.
     * 
     * @param sendReceive indicates whether this socket is being used
     * to send or receive data. The code automatically fills in the
     * tmode and mrole fields based on this parameter.
     * @return a new RMPacketSocket
     * @exception UnsupportedException if the transport does not
     * support a packet interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMPacketSocket createRMPacketSocket(int sendReceive) 
            throws UnsupportedException, InvalidTransportProfileException, 
                   IOException {

	return this.createRMPacketSocket(sendReceive, (InetAddress)null);
    }

    /**
     * Creates an RMPacketSocket using this TransportProfile.
     * 
     * @param sendReceive indicates whether this socket is being used
     * to send or receive data. The code automatically fills in the
     * tmode and mrole fields based on this parameter.
     * @param interfaceAddress indicates the IP address of the interface
     * to use for the multicast socket.
     * @return a new RMPacketSocket
     * @exception UnsupportedException if the transport does not
     * support a packet interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMPacketSocket createRMPacketSocket(
	int sendReceive, InetAddress interfaceAddress) 
        throws UnsupportedException, InvalidTransportProfileException, 
        IOException {

        // Make sure the TransportProfile is valid.

        validate();

        /*
         * Based on the value of the sendReceive parameter, set the
         * transmit mode and member role fields appropriately. Any
         * value currenty in these fields are overridden.
         */
        switch (sendReceive) {

        case TransportProfile.RECEIVER: {
            setTmode(TMODE.RECEIVE_ONLY);

            break;
        }

        case TransportProfile.SENDER: {
            setTmode(TMODE.SEND_ONLY);
            setMrole(MROLE.MEMBER_EAGER_HEAD);

            break;
        }

        case TransportProfile.REPAIR_NODE: {

            // setTmode(TMODE.REPAIR_NODE);

            h = true;

            break;
        }

        default: {
            throw new UnsupportedException("Mode specified is unsupported " 
                                           + sendReceive);
        }
        }

        randomizeTimers();

        TRAMPacketSocket rm = new TRAMPacketSocket();

        rm.connect(this, interfaceAddress);

        return (RMPacketSocket) rm;
    }

    /**
     * Creates an RMPacketSocket using this TransportProfile and a
     * TRAMSimulator object. Note: this method is used only for
     * simulation purposes.
     * 
     * @param sendReceive indicates whether this socket is being used
     * to send or receive data. The code automatically fills in the
     * tmode and mrole fields based on this parameter.
     * @param simulator specifies the simulator object being used
     * @return a new RMPacketSocket
     * @exception UnsupportedException if the transport does not
     * support a packet interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMPacketSocket createRMPacketSocket(int sendReceive, 
					       TRAMSimulator simulator) 
            throws UnsupportedException, InvalidTransportProfileException, 
	    IOException {

	return this.createRMPacketSocket(sendReceive, 
	    (InetAddress)null, simulator);
    }

    /**
     * Creates an RMPacketSocket using this TransportProfile and a
     * TRAMSimulator object. Note: this method is used only for
     * simulation purposes.
     * 
     * @param sendReceive indicates whether this socket is being used
     * to send or receive data. The code automatically fills in the
     * tmode and mrole fields based on this parameter.
     * @param interfaceAddress indicates the IP address of the interface
     * to use for the multicast socket.
     * @param simulator specifies the simulator object being used
     * @return a new RMPacketSocket
     * @exception UnsupportedException if the transport does not
     * support a packet interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMPacketSocket createRMPacketSocket(int sendReceive, 
					       InetAddress interfaceAddress,
					       TRAMSimulator simulator) 
            throws UnsupportedException, InvalidTransportProfileException, 
	    IOException {
        // Make sure the TransportProfile is valid.

        validate();

        /*
         * Based on the value of the sendReceive parameter, set the
         * transmit mode and member role fields appropriately. Any
         * value currenty in these fields are overridden.
         */
        switch (sendReceive) {

        case TransportProfile.RECEIVER: {
            setTmode(TMODE.RECEIVE_ONLY);

            break;
        }

        case TransportProfile.SENDER: {
            setTmode(TMODE.SEND_ONLY);
            setMrole(MROLE.MEMBER_EAGER_HEAD);

            break;
        }

        default: {
            throw new UnsupportedException("Mode specified is unsupported " 
                                           + sendReceive);
        }
        }

        randomizeTimers();

        TRAMPacketSocket rm = new TRAMPacketSocket(simulator);

        rm.connect(this, interfaceAddress);

        return (RMPacketSocket) rm;
    }

    /**
     * Returns a copy of this transport profile.
     * @return the clone of the passed transport profile.
     */
    public Object clone() {
        TRAMTransportProfile ntp = null;

        try {
            ntp = (TRAMTransportProfile) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError("TransportProfile Not Cloneable");
        }

        return ntp;
    }

    /**
     * Throws an exception if the TransportProfile is not valid. A 
     * TransportProfile is valid if, as far as can be determined, it could 
     * be used to send or receive data. Possible causes for invalid 
     * TransportProfiles include not setting the multicast address, port, 
     * and TTL (or other transport-specific problems).
     * @exception com.sun.multicast.reliable.channel.
     *   InvalidTransportProfileException if the TransportProfile is not valid
     */
    private void validate() throws InvalidTransportProfileException {

        /*
         * The following fields are validated
         * a - is a class D address
         * p  - port, range check.
         * ttl
         * m - multisender = false
         * mr and tm  - ensure if tm is sender, then the
         * mr should be eager head.
         * xm - maxMembers, range check
         * hello,beacon,wtbm rates - ensure it is a non negative number.
         * aw - ackWindow, range check.
         * cw - congestionWindow, range check.
         * nw - nackReportWindow, range check - ensure this is < than ackWindow.
         * xhm - maxHelloMisses, range check
         * *TTLIncrements - range check.
         * up - unicastPort, range check.
         * Transfer duration/size - non negative number
         * data source address - 0.0.0.0 or a non classD address.
         */
        if (a.isMulticastAddress() == false) {
            throw new InvalidTransportProfileException(
					      "invalid multicast address");
        } 

        // Assuming port # is 16 bits wide. 

        if ((p < 0) || (p > MAX_PORT_NUMBER)) {
            throw new InvalidTransportProfileException(
					       "invalid multicast port");
        } 
        if ((up < 0) || (up > MAX_PORT_NUMBER)) {
            throw new InvalidTransportProfileException("invalid unicast port");
        } 
        if (ttl == 0) {
            throw new InvalidTransportProfileException("invalid TTL");
        } 

        // currently TRAM does not support multisender.

        if (m == true) {
            throw new InvalidTransportProfileException(
		"multisender not supported");
        } 
        if ((mr < MROLE.MEMBER_ONLY) 
                || (mr > MROLE.MEMBER_RELUCTANT_HEAD)) {
            throw new InvalidTransportProfileException("invalid mrole");
        } 
        if ((tm < TMODE.SEND_ONLY) || (tm > TMODE.SEND_RECEIVE)) {
            throw new InvalidTransportProfileException("invalid tmode");
        } 

    /*
     * // check for valid combinations of mr and tm.
     * if( ((mr == MROLE.MEMBER_ONLY) &&
     * (tm != TMODE.RECEIVE_ONLY) ||
     * ((tm != TMODE.RECEIVE_ONLY) &&
     * (mr != MROLE.MEMBER_EAGER_HEAD))))
     * throw new InvalidTransportProfileException(
     *  "invalid combination of mrole and tmode");
     * if( (xm <0) ||
     * (msr < 0) ||
     * (hr < 0) ||
     * (br <0) ||
     * (aw <0) ||
     * (xnh <0) ||
     * (cw <0) ||
     * (nw < 0) ||
     * (xhm <0) ||
     * (nw > aw) ||
     * (mti >ttl) ||
     * (hti > ttl) ||
     * (htl > ttl) ||
     * (hi < 0) ||
     * (xbd<0) ||
     * (xbt<0) ||
     * (bti > ttl) ||
     * (s <0) ||
     * (d <0) ||
     * (xr <0) ||
     * (nr<0) ||
     * (xb<0) ||
     * (sa.isMulticastAddress() == true) )
     * throw new InvalidTransportProfileException("miscellaneous error");
     */
    }

    /**
     * Tests whether this TransportProfile is valid. A TransportProfile
     * is valid if, as far as can be determined, it could be used to send
     * or receive data. Possible causes for invalid TransportProfiles
     * include not setting the multicast address, port, and TTL (or other
     * transport-specific problems).
     * @return <code>true</code> if the TransportProfile is valid;
     * <code>false</code> otherwise
     */
    public boolean isValid() {
        boolean valid = true;

        try {
            validate();
        } catch (InvalidTransportProfileException e) {
            valid = false;
        }

        return (valid);
    }

    /**
     * Returns the name of this transport. The transport sets the name field.
     * Applications cannot modify this field.
     * 
     * @return the name of the transport
     */
    public String getName() {
        return n;
    }

    /**
     * Method to test if REPAIR_NODE mode is turned on. Note if REPAIR_NODE
     * is turned on, the transport will not support local receiver application
     * that is, any received data from the network will not be forward towards
     * the local application. This should be turned on only if the transport is
     * to perform the role of a repair head.
     * @return <code>true </code> if REPAIR_NODE flag is set(turned on).
     * <code> false</code> if REPAIR_NODE flag is turned off.
     */
    public boolean getSAhead() {
        return h;
    }

    /**
     * Returns the multicast address specified in the TransportProfile.
     * 
     * @return the multicast address for this TransportProfile
     */
    public InetAddress getAddress() {
        return a;
    }

    /**
     * Sets the multicast address for this TransportProfile.
     * 
     * @param address the new multicast address.
     * @exception InvalidMulticastAddressException if an
     * the address specified is not a multicast address.
     */
    public void setAddress(InetAddress address) 
            throws InvalidMulticastAddressException {
        if (address.isMulticastAddress()) {
            a = address;
        } else {
            throw new InvalidMulticastAddressException(
		     "The address specified is not a valid multicast address");
        }
    }

    /**
     * Returns the multicast port number specified in the TransportProfile.
     * 
     * @return the multicast port number for this TransportProfile.
     */
    public int getPort() {
        return p;
    }

    /**
     * Sets the multicast port number for this TransportProfile.
     * 
     * @param port the new multicast port number.
     */
    public void setPort(int port) {
        p = port;
    }

    /**
     * Returns the time-to-live for this TransportProfile. The time-to-live
     * value indicates the range of multicast messages.
     * 
     * @return the time-to-live value for this TransportProfile.
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * Sets the value for the Time-to-live. The ttl indicates the range of
     * the multicast messages sent on the multicast address/port. The
     * default value is 1 (local area).
     * 
     * @param ttl the value of the time-to-live parameter.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
        htl = ttl;
    }

    /**
     * Determines if multiple senders are supported with this TransportProfile.
     * 
     * @return true if multiple senders are supported with this 
     * TransportProfile; false otherwise.
     */
    public boolean isMultiSender() {
        return m;
    }

    /**
     * Sets the value of the multisender flag. If the application
     * wishes to support multiple senders with this TransportProfile,
     * this flag must be set to true.
     * 
     * @param multisender true if the application wishes to support
     * multiple senders; false otherwise
     * @exception UnsupportedException if the transport does not
     * support multiple senders.
     */
    public void setMultiSender(boolean multisender) 
            throws UnsupportedException {
        m = multisender;
    }

    /**
     * Test method to check if Packet Ordering option is enabled in the
     * Transport profile. If the application requires that the data is
     * returned to the application in the order which it was sent, the
     * ordered flag needs to be set. If the application doesn't care which
     * order the data arrives in, this flag can be set to false. This flag
     * is only valid for RMPacketSockets.
     * 
     * @return true if ordering was enabled; false if ordering was disabled.
     */
    public boolean isOrdered() {
        return o;
    }

    /**
     * Returns the value of the authentication flag. If the flag is
     * set(true), it indicates that the data packets are authenticated.
     * 
     * @return true indicates that the data packets are authenticated
     * false indicates that the data packets are not
     * authenticated/signed.
     */
    public boolean isUsingAuthentication() {
        return ua;
    }

    /**
     * Sets the Packet Ordering preference in the transport profile. Setting
     * this flag to true indicates that all data is to be forwarded to the
     * application in the order it was sent. Setting this flag to false
     * indicates that the application will get the data in the order that it
     * was received, which may not be the order that it was sent.
     * 
     * @param ordered true to enable; false to disable ordering.
     */
    public void setOrdered(boolean ordered) {
        o = ordered;
    }

    /**
     * Gets the specified Member Role(mrole) value in the Transport profile.
     * The Member Role specifies the role that the Transport has to perform
     * in the TRAM Group management.
     * 
     * @see MROLE
     * @return the value of the mrole (Member Role) field.
     * 
     */
    public byte getMrole() {
        return (mr);
    }

    /**
     * Set the required Member Role(mrole) value in the transport profile.
     * The Member Role specifies the role that the Transport has to perform
     * in the TRAM Group management.
     * If either MEMBER_EAGER_HEAD or MEMBER_RELUCTANT_HEAD is specified,
     * then the transport performs roll of a head which involves data caching
     * and performing repair operations.
     * 
     * @see MROLE
     * @param mrole the mrole value that is to be set. Default is MEMBER_ONLY.
     * 
     * @exception IllegalArgumentException if the mrole value is
     * invalid.
     */
    public void setMrole(byte mrole) throws IllegalArgumentException {
        if (mrole < MROLE.MEMBER_ONLY 
                || mrole > MROLE.MEMBER_RELUCTANT_HEAD) {
            throw new IllegalArgumentException();
        }

        mr = mrole;
    }

    /**
     * Gets the specified Transport Mode(tmode) value in the transport
     * profile. The Transport Mode specifies the role that the Transport
     * has to perform. The value SEND_RECEIVE is for future use.
     * 
     * @see TMODE
     * @return the value of the tmode(Transport Mode) field.
     */
    public byte getTmode() {
        return (tm);
    }

    /**
     * Set the required Transport Mode(tmode) value in the Transport Profile.
     * The Transport Mode specifies the role that the Transport has to perform.
     * 
     * @see TMODE
     * @param tmode the tmode value that is to be set. Default is RECEIVE_ONLY.
     * 
     * @exception IllegalArgumentException if the tmode value is invalid.
     */
    public void setTmode(byte tmode) throws IllegalArgumentException {
        if (tmode < TMODE.SEND_ONLY || tmode > TMODE.REPAIR_NODE) {
            throw new IllegalArgumentException();
        }

        tm = tmode;

        try {
            if ((tm == TMODE.SEND_ONLY) && (sa == null)) {
                sa = InetAddress.getLocalHost();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Gets the limit of the number of members that a group head can
     * accommodate. The maxMembers limits the number of members that a
     * group head can accept. The default limit is 32. The maxMembers
     * field is applicable only when the mrole is set to a non MEMBER_ONLY
     * value.
     * 
     * @return current limit of maximum members a group head can accommodate.
     */
    public short getMaxMembers() {
        return (xm);
    }

    /**
     * Sets the maximum limit of members that a repair head can accommodate.
     * The maxMembers limits the number of members that a group head can
     * accept. The default limit is 32. The maxMembers field is applicable
     * only when the mrole is set to a non MEMBER_ONLY value.
     * 
     * @param maxMembers The maximum count of members that a group head can
     * accommodate. Default is 32.
     * 
     */
    public void setMaxMembers(short maxMembers) {
        xm = maxMembers;

        // recalculate maxNonHeads

        xnh = (short) ((maxMembers < 3) ? (maxMembers / 2) 
                               : ((2 * maxMembers) / 3));
    }

    /**
     * Gets the rate at which the Hello messages are sent.
     * The helloRate specifies the rate at which hello messages are sent
     * by the group heads. The helloRate value is in milli-seconds.
     * 
     * @return The helloRate value in milliseconds.
     */
    public long getHelloRate() {
        return hr;
    }

    /**
     * Sets the required Hello Rate value.
     * The helloRate specifies the rate at which hello messages are sent
     * by the group heads. The helloRate value is in milli-seconds.
     * 
     * @param helloRate The preferred helloRate value(in milliseconds) that is
     * to be set. Default is 1000 ms.
     */
    public void setHelloRate(long helloRate) {
        hr = helloRate;
    }

    /**
     * Gets the rate at which the Hello messages are sent after it has
     * been detected that a member has not sent an ACK.
     * The value is in milli-seconds.
     * 
     * @return the pruneHelloRate value in milliseconds.
     */
    public long getPruneHelloRate() {
        return phr;
    }

    /**
     * Sets the rate at which the Hello messages are sent after it has
     * been detected that a member has not sent an ACK.
     * The value is in milli-seconds.
     * 
     * @param pruneHelloRate The preferred pruneHelloRate value
     * (in milliseconds) that is to be set. Default is 1000 ms.
     */
    public void setPruneHelloRate(long pruneHelloRate) {
        phr = pruneHelloRate;
    }

    /**
     * Gets the rate at which the Member Solicitation(MS) messages
     * are sent. The msRate specifies the rate at which Member-Solicitation
     * messages are sent by the transports seeking group membership.
     * The msRate value is in milli-seconds.
     * 
     * @return The MS rate in milliseconds.
     */
    public long getMsRate() {
        return msr;
    }

    /**
     * Sets the required Member Solicitation(MS) rate value.
     * The msRate specifies the rate at which MS messages are
     * sent by the transports seeking group membership. The msRate value is
     * in milli-seconds.
     * 
     * @param msRate The required MS Rate(in Milliseconds) that is to be
     * set. Default is 500 ms.
     */
    public void setMsRate(long msRate) {
        msr = msRate;
    }

    /**
     * Gets the rate at which the Beacon messages are sent.
     * The beaconRate specifies the rate at which beacon messages are
     * sent by the SENDER transport. This beaconRate is required
     * for transports with tmode of SEND_ONLY. The beaconRate value is in
     * milli-seconds.
     * 
     * @return The current beacon message rate(in milliseconds)
     */
    public long getBeaconRate() {
        return br;
    }

    /**
     * Sets the required rate at which the beacon messages are to be sent.
     * The beaconRate specifies the rate at which beacon messages are
     * sent by the SENDER transport. This beaconRate field is required
     * for transports with tmode of SEND_ONLY. The beaconRate value is in
     * milli-seconds.
     * 
     * @param beaconRate The beacon Rate value(in milliseconds) that
     * is to be set. Default is 1000 ms.
     */
    public void setBeaconRate(long beaconRate) {
        br = beaconRate;
    }

    /**
     * Gets the Acknowledgement window size.
     * The AckWindow specifies window size of the ACK messages. In no packet
     * loss periods, a member sends an ack message for every 'ackWindow' number
     * of messages received.
     * 
     * @return The current size of the acknowledgement.
     */
    public short getAckWindow() {
        return aw;
    }

    /**
     * Sets the size of the acknowledgement window to the required value.
     * The AckWindow specifies window size of the ACK messages. In no packet
     * loss periods, a member sends an ack message for every 'ackWindow' number
     * of messages received.
     * 
     * @param ackWindow The acknowledge window size(in terms of packets)
     * that is to be set. The default value is 32 packets.  At high data rates,
     * it makes sense to increase this value.
     */
    public void setAckWindow(short ackWindow) {
        if (ackWindow < 1)
            aw = 1;
	else
            aw = ackWindow;

        cw = (short)(2 * aw);	// initialize congestion window as well

	if (cs < 3 * aw)
	    cs = Math.max(30, 3 * aw);
    }

    /**
     * Set the value of the congestion window. This value is the
     * number of packets to be received to determine if this member
     * is experiencing congestion.
     * 
     * @param congestionWindow the number of packets in the congestion window. 
     * The default is 32 packets.
     */
    public void setCongestionWindow(int congestionWindow) {
	if (congestionWindow < aw)
	    cw = aw;
	else if (congestionWindow > xc * aw)
	    cw = (short)(xc * aw);
	else
            cw = (short)congestionWindow;
    }

    /**
     * Gets the current congestion window size.
     * @return the value of the congestion window. This value is the
     * number of packets over which congestion is evaluated.
     */
    public int getCongestionWindow() {
        return ((int)cw & 0x0000ffff);
    }

    /**
     * Gets the size of the negative Acknowledgement window.
     * The NackReportWindow specifies window size of the messages that need
     * to be recognized to have been dispatched by the sender to trigger ack
     * message with retransmission request to be generated.
     * 
     * @return The negative Acknowledgement window size.
     */
    public short getNackReportWindow() {
        return nw;
    }

    /**
     * Sets the Negative Acknowledgement window size to the preferred value.
     * The NackReportWindow specifies window size of the messages that need
     * to be recognized to have been dispatched by the sender to trigger ack
     * message with retransmission request to be generated.
     * 
     * @param nackReportWindow The required negative Acknowledgement window
     * size that is to be set. Default is 5.
     */
    public void setNackReportWindow(short nackReportWindow) {
        nw = nackReportWindow;
    }

    /**
     * Gets the count of Hello messages that are to be missed continuously
     * by a member to declare the head inactive and re-affiliate with
     * another head.
     * The maxHelloMisses field is a count of the number of hello messages
     * that need to be missed successively in order to declare a dependent
     * head as inactive.
     * 
     * @return The count of Hello messages that is to be missed to declare
     * a head inactive.
     */
    public byte getMaxHelloMisses() {
        return xhm;
    }

    /**
     * Sets the count of Hello messages that are to be missed, continuously,
     * by a member to disown a head.
     * The maxHelloMisses field is a count of the number of hello messages
     * that need to be missed successively in order to declare a dependent
     * head as down.
     * 
     * @param maxHelloMisses The required count of hello messages to be
     * missed continuously to declare a head inactive. Default value is 4.
     */
    public void setMaxHelloMisses(byte maxHelloMisses) {
        xhm = maxHelloMisses;
    }

    /**
     * Gets the TTL steps by which the multicast scope of the beacon message
     * is incremented.
     * The beaconTTLIncrements field specifies TTL increments between two
     * successive beacon messages. This required as the beacon messages are
     * sent using expanding ring technique. This field is valid only in
     * transports that have tmode of SEND_ONLY.
     * 
     * @return The current value of the beaconTTLIncrements field.
     */
    public byte getBeaconTTLIncrements() {
        return bti;
    }

    /**
     * Sets the TTL steps by which the multicast scope of the beacon message
     * is incremented.
     * The beaconTTLIncrements field specifies TTL increments between two
     * successive beacon messages. This required as the beacon messages are
     * sent using expanding ring technique. This field is valid only in
     * transports that have tmode of SEND_ONLY.
     * 
     * @param beaconTTLIncrements The required TTL steps by which multicast
     * scope of the Beacon message is to be increased. Default is 2.
     */
    public void setBeaconTTLIncrements(byte beaconTTLIncrements) {
        bti = beaconTTLIncrements;
    }

    /**
     * Gets the unicast communication port in use.
     * The unicast port is used for exchange of unicast control
     * messages. This field is optional. If specified and if the specified
     * port is free, the transport uses it. If the port is not free,
     * transport initialization fails. If the unicastPort is not specified,
     * then the transport uses the system allocated one.
     * 
     * @return The unicastPort value..
     */
    public int getUnicastPort() {
        return up;
    }

    /**
     * Sets the unicast port that is to be used for exchange of control
     * messages.
     * This field is optional. If specified, the port, if free, is
     * used. If the port is not free, transport initialization fails. If the
     * unicastPort is unspecified, then the transport uses the system
     * allocated one.
     * 
     * @param unicastPort The unicast port value that is to be set.
     */
    public void setUnicastPort(int unicastPort) {
        up = unicastPort;
    }

    /**
     * Gets the transfer data size that is currently in use.
     * The transferDataSize field is optional and when specified, it indicates
     * total size of the data transfer in bytes for the duration of the
     * multicast session. This can be specified for data transfer
     * applications like FTP.
     * Note: if this field is specified, transferDuration field should also be
     * specified.
     * 
     * @return The current transfer Data Size.
     */
    public double getTransferDataSize() {
        return s;
    }

    /**
     * Sets the size of the data that is to be transferred.
     * The transferDataSize field is optional and when specified, it indicates
     * size of the data transfer in bytes for the duration of the multicast
     * session. This can be specified for data transfer applications like FTP.
     * Note: if this field is specified, transferDuration field should also be
     * specified.
     * 
     * @param transferDataSize The required size of the data that is to be
     * transferred(in bytes). Default is 0.
     */
    public void setTransferDataSize(double transferDataSize) {
        s = transferDataSize;
    }

    /**
     * Gets allowed duration of data transfer.
     * The transferDuration field specifies the allowed duration of the data
     * transfer in the worst case conditions. It is recommended the knowledge/
     * history of similar data transfer(may be unicast) be used as a
     * guideline to choose the appropriate duration. This field is optional and
     * when specified, the transport in its attempt to complete the data
     * transfer in the duration specified may prune members that are unable
     * at a minimal rate determined with the use of transferDataSize field.
     * Note: if this field is specified, transferDataSize field should also be
     * specified.
     * 
     * <P><STRONG>Note:</STRONG> This is currently not supported.
     * 
     * @return The Duration of data transfer value in use.
     */
    public long getTransferDuration() {
        return d;
    }

    /**
     * Sets the allowed duration of Data transfer(in minutes).
     * The transferDuration field specifies the expected duration of the data
     * transfer in the worst case conditions. It is recommended the knowledge/
     * history of similar data transfer(may be unicast) be used as a
     * guideline to choose the appropriate duration. This field is optional and
     * when specified, the transport in its attempt to complete the data
     * transfer in the duration specified may prune members that are unable
     * at a minimal rate determined with the use of transferDataSize field.
     * Note: if this field is specified, transferDataSize field should also be
     * specified.
     * 
     * <P><STRONG>Note:</STRONG> This is currently not supported.
     * 
     * @param transferDuration The required duration of data transfer in
     * minutes. Default is 0.
     */
    public void setTransferDuration(long transferDuration) {
        d = transferDuration;
    }

    /**
     * Gets the minimum rate of data transfer. The value specified is in
     * bytes/second.
     * The transmitter attempts to maintain the minimum data rate under all
     * conditions.
     * 
     * @return the minimum rate of data transmission in use(in bytes/second).
     */
    public long getMinDataRate() {
        return nr;
    }

    /**
     * Sets the minimum rate of data transmission to the specified value.
     * 
     * @param minDataRate the minimum rate of transmission in bytes/second.
     * default is 0.
     */
    public void setMinDataRate(long minDataRate) {
        nr = minDataRate;
    }

    /**
     * Gets the maximum rate data transfer.
     * The transmitter will attempt to the send data up to the maximum
     * data rate specified.
     * 
     * @return the maximum data rate in use(in bytes/second).
     */
    public long getMaxDataRate() {
        return xr;
    }

    /**
     * Sets the maximum rate at which the multicast data messages can be
     * sent.
     * 
     * @param maxDataRate the maximum rate of transmission in bytes/second.
     * Default is 64000.
     */
    public void setMaxDataRate(long maxDataRate) {
        xr = maxDataRate;
    }

    /**
     * Gets the source address of the multicast data stream.
     * 
     * @return the source address of the multicast data session.
     */
    public InetAddress getDataSourceAddress() {
        return sa;
    }

    /**
     * Sets the source address of the multicast data session..
     * 
     * @param dataSourceAddress the source address of the multicast data
     * stream.
     */
    public void setDataSourceAddress(InetAddress dataSourceAddress) {
        sa = dataSourceAddress;
    }

    /**
     * Method to turn on or turn off the logging mechanism. When
     * logging is turned on, the debug messages from the transport
     * appear on the standard output.
     * 
     * @param logMask - the level of logging required to be turned on.
     */
    public void setLogMask(int logMask) {
	lm = logMask;
    }

    /**
     * Method to test the logging status.
     * 
     * @return <code> true </code> if logging is enabled/turned on.
     * <code> false </code> if logging is disabled/turned off.
     * 
     */
    public boolean isLoggingEnabled() {
	if (lm == LOG_NONE)
	    return false;
        return true;
    }

    /*
     * Method to test logging status - Checks to see if logging at a 
     * specified level is turned on.
     * @return <code> true </code> if logging is enabled/turned on.
     * <code> false </code> if logging is disabled/turned off.
     */
    public boolean isLoggingEnabled(int testLogMask) {
	return TRAMLogger.isLoggingEnabled(lm, testLogMask);
    }

    /*
     * Returns the current settings of the logging preference.
     */
    public int getLogMask() {
	return lm;
    }
    
    /**
     * Method to turn on or turn off lan tree formation.
     * 
     * @param b <code> true </code> to enable/turn on.
     * <code> false </code> to disable/turn off.
     * 
     */
    public void setLanTreeFormation(boolean b) {
        alt = b;
    }

    /**
     * Method to test whether or not lan tree formation is allowed.
     * 
     * @return <code> true </code> if  lan tree formation is enabled/turned on.
     * <code> false </code> if  lan tree formation is disabled/turned off.
     * 
     */
    public boolean isLanTreeFormationEnabled() {
        return alt;
    }

    /**
     * Gets the configured limit of the number of non Head members(i.e.,
     * members with MROLE set to MEMBER_ONLY) that can be accepted as
     * members while performing the role of a head.
     * If there are only eager heads in a setup, then the maximum number
     * members accepted is limited by maxMembers. In a setup where
     * there are MEMBER_ONLYs, RELUCTANT_HEADs, EAGER_HEADs, then the
     * distribution will be as follows,
     * A head can have up to maxNonHeads number of MEMBER_ONLY and
     * and can have up to (maxMembers - maxNonHeads) of HEADs.
     * 
     * @return count on the number of non eager members that can be
     * accommodated while performing the role of a head.
     */
    public int getMaxNonHeads() {
        return (int) xnh;
    }

    /**
     * sets the configured limit of the number of non Heads(i.e., MEMBER_ONLY)
     * that can be accepted as members while performing the role of a head.
     * The chosen value should not exceed the maxMembers value. The
     * default value is 2/3 the value of maxMembers.
     * 
     * @param maxNonHeads the new limit of maximum non eager
     * that is to be set.
     */
    public void setMaxNonHeads(int maxNonHeads) {
        xnh = (short) (maxNonHeads & 0xffff);
    }

    /**
     * Gets the TTL steps by which the multicast scope of the Head
     * Advertisement message is to be incremented.
     * The field specifies TTL increments between two
     * successive HA messages. This is required as the HA messages are
     * sent using expanding ring technique.
     * 
     * @return The TTL increments for the HA messages..
     */
    public byte getHaTTLIncrements() {
        return hti;
    }

    /**
     * Sets the TTL steps by which the multicast scope of the HA message
     * is to be incremented.
     * The value specifies TTL increments between two successive HA
     * messages. This required as the HA messages are sent using
     * expanding ring technique.
     * 
     * @param haTTLIncrements The preferred TTL steps by which multicast
     * scope of the HA message is to be increased. Default is 2.
     */
    public void setHaTTLIncrements(byte haTTLIncrements) {
        hti = haTTLIncrements;
    }

    /**
     * Gets the TTL limit of the Head Advertisement message.
     * 
     * @return The TTL increment value for the HA messages.
     */
    public byte getHaTTLLimit() {
        return htl;
    }

    /**
     * Sets the TTL limit of the Head Advertisement message.
     * 
     * @param haTTLLimit The TTL limit of the Head Advertisement message.
     */
    public void setHaTTLLimit(byte haTTLLimit) {
        htl = haTTLLimit;
    }

    /**
     * Gets the TTL steps by which the multicast scope of the Member
     * Solicitation(MS) message is to be incremented.
     * The field specifies TTL increments between two
     * successive MS messages. This required as the MS messages are
     * sent using expanding ring technique.
     * 
     * @return The TTL increments for the MS messages..
     */
    public byte getMsTTLIncrements() {
        return mti;
    }

    /**
     * Sets the TTL steps by which the multicast scope of the Member
     * Solicitation(MS) message is to be incremented.
     * The value specifies TTL increments between two successive MS
     * messages. This required as the MS messages are sent using
     * expanding ring technique.
     * 
     * @param msTTLIncrements The preferred TTL steps by which multicast
     * scope of the HA message is to be increased. Default is 2.
     */
    public void setMsTTLIncrements(byte msTTLIncrements) {
        mti = msTTLIncrements;
    }

    /**
     * Gets the configured limit on the bandwidth usage by HA message during
     * the multicast data transfer.
     * 
     * @return the maximum data rate to be used by HA messages(bytes/sec)
     * during the multicast data transfer.
     */
    public long getMaxHABWWhileDataTransfer() {
        return xbd;
    }

    /**
     * Sets the maximum Bandwidth that can be used by HA messages
     * while the multicast data transfer is in progress. Default is
     * 0 bytes/sec.
     * 
     * @param maxHABWWhileDataTransfer the maximum bandwidth for HA
     * HA messages during data transfer. Default is 0 bytes/sec.
     */
    public void setMaxHABWWhileDataTransfer(long maxHABWWhileDataTransfer) {
        xbd = maxHABWWhileDataTransfer;
    }

    /**
     * Gets the configured limit on the bandwidth usage by HA message during
     * the tree formation phase(before the multicast data starts).
     * 
     * @return the maximum data rate to be used by HA messages(bytes/sec)
     * before the multicast data transfer starts(Tree forming stage).
     */
    public long getMaxHABWWhileTreeForming() {
        return xbt;
    }

    /**
     * Sets the maximum Bandwidth that can be used by HA messages
     * before the multicast data transfer starts. Default is
     * Max Data Rate.
     * 
     * @param maxHABWWhileTreeForming the maximum bandwidth for HA
     * HA messages before data transfer. Default is Max Data
     * rate value.
     */
    public void setMaxHABWWhileTreeForming(long maxHABWWhileTreeForming) {
        xbt = maxHABWWhileTreeForming;
    }

    /**
     * Gets the interval between successive Head Advertisements.
     * The  specifies the rate at which Head Advertisement messages are
     * sent. This Head Advertisement interval is valid for transports
     * with non MEMBER_ONLY MROLE. The HAInterval value is in
     * milli-seconds.
     * 
     * @return The currently chosen rate(in milliseconds) for the HA
     * messages.
     */
    public long getHaInterval() {
        return hi;
    }

    /**
     * Sets the interval between two successive HA messages with the specified
     * value.
     * The haInterval specifies the rate at which HA messages are
     * sent by the transport. This Head Advertisement interval is valid
     * for transports with non MEMBER_ONLY MROLE.The HAInterval value is in
     * milli-seconds.
     * 
     * @param haInterval The preferred HAInterval value(in milliseconds) that
     * is to be set. Default is 1000 ms.
     */
    public void setHaInterval(long haInterval) {
        hi = haInterval;
    }

    /**
     * Gets the current Late join preference. The valid values are
     * LATE_JOIN_WITH_LIMITED_RECOVERY, LATE_JOIN_WITH_FULL_RECOVERY and
     * LATE_JOIN_WITH_NO_RECOVERY.
     * LATE_JOIN_WITH_LIMITED_RECOVERY implies that the lost messages
     * will be repaired to the extent that they are available at the
     * affiliated head. LATE_JOIN_WITH_FULL_RECOVERY option implies
     * that ALL the lost packets are to be repaired. If the packets are
     * unavailable at the head, then the packet retransmissions are to sought
     * from a different head or from the message logger.
     * LATE_JOIN_WITH_NO_RECOVERY indicates earlier data are not to be
     * recovered.
     * 
     * @return The currently chosen preference for late join handling.
     */
    public int getLateJoinPreference() {
        return ljp;
    }

    /**
     * Sets the Late join preference for the session. The valid values are
     * LATE_JOIN_WITH_LIMITED_RECOVERY, LATE_JOIN_WITH_FULL_RECOVERY and
     * LATE_JOIN_WITH_NO_RECOVERY.
     * LATE_JOIN_WITH_LIMITED_RECOVERY implies that the lost messages
     * will be repaired to the extent that they are available at the
     * affiliated head. LATE_JOIN_WITH_FULL_RECOVERY option implies
     * that ALL the lost packets are to be repaired. If the packets are
     * unavailable at the head, then the packet retransmissions are to sought
     * from a different head or from the message logger.
     * LATE_JOIN_WITH_NO_RECOVERY indicates earlier data are not to be
     * recovered.
     * 
     * @param lateJoinPreference The preferred late join preference that
     * is to be set. Default is LATE_JOIN_WITH_LIMITED_RECOVERY.
     */
    public void setLateJoinPreference(int lateJoinPreference) {
        ljp = lateJoinPreference;
    }

    /**
     * Gets the configured tree formation preference. The valid values are
     * TREE_FORM_HA, TREE_FORM_MTHA, and TREE_FORM_HAMTHA,
     * TREE_FORM_HA means that Head Advertisements are used.
     * TREE_FORM_MTHA means that member-triggered Head Advetisements are used.
     * TREE_FORM_HAMTHA means that Head Advertisements are used until
     * data starts flowing, then member-triggered Head Advetisements are used.
     * @return The currently chosen tree formation preference.
     */
    public int getTreeFormationPreference(boolean mask) {
	if (mask == false)
            return tfp;

	return tfp & (TREE_FORM_STATIC_R - 1);
    }

    /**
     * Sets the tree formation preference for the session.
     * 
     * @param treeFormationPreference The preferred late join preference that
     * is to be set. Default is LATE_JOIN_WITH_LIMITED_RECOVERY.
     */
    public void setTreeFormationPreference(int treeFormationPreference) {
        tfp = treeFormationPreference;
    }

    /**
     * This method returns the maximum buffer size used in reception of
     * data packets. Increase the default value if the application sends
     * large packets. Decrease the size to reduce memory consumption. This
     * parameter must be as large as the largest buffer sent.
     * 
     * @return the current maximum buffer size.
     */
    public int getMaxBuf() {
        return xb;
    }

    /**
     * This method sets the maximum buffer size used in reception of
     * data packets. Increase the default value if the application sends
     * large packets. Decrease the size to reduce memory consumption. This
     * parameter must be as large as the largest buffer sent.
     * 
     * @param size the new maximum buffer size.
     */
    public void setMaxBuf(int size) {
        xb = size;
    }

    void randomizeTimers() {

        // first get a random number between 0 and 1

        double random = Math.random();

        // now make it a random number between 2/3 and 4/3

        random += 1;
        random *= 2;
        random /= 3;

        /*
	 * now multiply the random number by the new interval, and put it 
	 * back into an int
	 */

        this.hr = (int) Math.rint(hr * random);
        this.msr = (int) Math.rint(msr * random);
    }

    /**
     * Enables the use of authentication by the transport.
     */
    public void enableAuthentication() {
        ua = true;
    }

    /**
     * Disables the use of Authentication by the transport.
     */
    public void disableAuthentication() {
        ua = false;
    }

    /**
     * Gets the name of the authenticationSpec filename that is to be
     * used for initialization.
     * @return authenticationSpecFileName currently specified.
     */
    public String getAuthenticationSpecFileName() {
        return afn;
    }

    /**
     * Sets the name of the authenticationSpec filename to use for
     * Authentication Module initialization.
     * @param specFileName the name of the authentication Spec file name.
     */
    public void setAuthenticationSpecFileName(String specFileName) {
        afn = specFileName;
    }

    /**
     * Gets the password for the authenticationSpec
     * @return authenticationSpecPassword currently specified.
     */
    public String getAuthenticationSpecPassword() {
        return apw;
    }

    /**
     * Sets the password for the authenticationSpec
     * @param password the password for the authenticationSpec.
     */
    public void setAuthenticationSpecPassword(String password) {
        apw = password;
    }

    /**
     * Gets the session ID associated with this transport profile.
     * @return the session ID associated with this transport profile.
     */
    public int getSessionId() {
        return id;
    }

    /**
     * Sets the session ID associated with this transport profile.
     * @param the session ID associated with this transport profile.
     */
    public void setSessionId(int sessionId) {
        id = sessionId;
    }

    /**
     * Sets the flag to indicate that the sender should try to smooth out
     * the transmission of packets as much as possible over time rather than
     * sending out bursts of packets.
     */
    public void setSmoothTransmission(boolean smoothTransmission) {
	sx = smoothTransmission;
    }

    /**
     * Gets the flag indicating whether or not bursting transmission is allowed.
     */
    public boolean isSmoothTransmission() {
	return sx;
    }

    /**
     * Sets the maximum size of the cache in packets.
     * Default is 1200 packets.
     */
    public void setCacheSize(int cacheSize) {
	if (cacheSize < 30 || cacheSize < 3 * aw)
	    cs = Math.max(30, 3 * aw);
        else
	    cs = cacheSize;
    }

    /**
     * Gets the maximum cache size in packets
     */
    public int getCacheSize() {
	return cs;
    }

    /**
     * Sets the maximum time (in seconds) to wait before pruning a slow member
     * Default is 5 seconds.
     */
    public void setMaxPruneTime(int time) {
	if (time > 0)
	    mtp = time;
    }

    /**
     * Gets the maximum time to wait before pruning a slow member
     */
    public int getMaxPruneTime() {
	return mtp;
    }

    /**
     * Sets the flag indicating whether or not to reaffiliate after
     * being disowned.  This is used to make a member exit quickly after
     * being pruned.
     */
    public void setreaffiliateAfterBeingDisowned(boolean rad) {
	this.rad = rad;
    }

    /**
     * Gets the flag indicating whether or not to reaffiliate after
     * being disowned.
     */
    public boolean reaffiliateAfterBeingDisowned() {
	return rad;
    }

    /**
     * For Debugging...
     * Decentralized pruning lets the repair heads decide from local information
     * which member(s) should be pruned when the sender indicates that there's
     * a need to prune.
     */
    private boolean decentralizedPruning = false;

    public void setDecentralizedPruning(boolean decentralizedPruning) {
	this.decentralizedPruning = decentralizedPruning;
    }

    public boolean decentralizedPruning() {
	return decentralizedPruning;
    }

    /**
     * The pruning window is used with decentralized pruning.
     * The pruning window is the maximum number of packets
     * that a member is allowed to be behind.
     */
    private double pW = 1.5;

    public void setPruningWindow(double pruningWindow) {
	if (pruningWindow < 1.0 || pruningWindow > 5)
	    return;

	pW = pruningWindow;
    }

    public double getPruningWindow() {
	return pW;
    }

    /**
     * The receiverMaxDataRate is used to emulate packet loss.
     * When this value is non-zero, a receiver tries to have the average
     * receive rate approach this value.  When the average receive rate
     * exceeds this value, the receiver randomly drops 30% of the received
     * packets until the average receive rate goes below the
     * desired receive rate.
     */
    private transient long receiverMaxDataRate = 0;

    public void setReceiverMaxDataRate(long receiverMaxDataRate) {
	this.receiverMaxDataRate = receiverMaxDataRate;
    }
    
    public long getReceiverMaxDataRate() {
        return receiverMaxDataRate;
    }

    private transient int maxConsecutiveCongestionCount = 1;

    public void setMaxConsecutiveCongestionCount(
	int maxConsecutiveCongestionCount) {

	this.maxConsecutiveCongestionCount = maxConsecutiveCongestionCount;
    }

    public int getMaxConsecutiveCongestionCount() {
	return maxConsecutiveCongestionCount;
    }

    /**
     * Sets the receive buffer size.  By default, TRAM tries to find the
     * biggest buffer size the system will allow.  On Solaris this is 
     * usually 256k.
     */
    public void setReceiveBufferSize(int rbs) {
	receiveBufferSize = rbs;
    }

    /** 
     * This function returns the value that was asked to be set using 
     * setReceiveBufferSize.  This does not return the value of the 
     * receiver buffer that was "actually" set at the socket. 
     * The actual value set may be different from the one requested.
     */
    public int getReceiveBufferSize() {		
	return receiveBufferSize;
    }

    /**
     * Set/get the rate increase factor.  The rate increment is set to
     * the average data rate multiplied by this factor.  The rate increment
     * is added to the current data rate on an ACK window boundary,
     * when all is well.  Default is .15.
     */
    private double rim = .15;

    public void setRateIncreaseFactor(double rateIncreaseFactor) {
	rim = rateIncreaseFactor;
    }

    public double getRateIncreaseFactor() {
	return rim;
    }
     
    /** 
     * Set/get the time in seconds during which to calculate 
     * the average data rate.  Default is a 5 second moving average.
     */
    private int tfarc = 5;	

    public void setTimeForAvgRateCalc(int timeForAverageRateCalc) {
	tfarc = timeForAverageRateCalc;
    }

    public int getTimeForAvgRateCalc() {
	return tfarc;
    }

    /**
     * Set/get the missing packet threshold.  This is the number
     * of packets which must be missing before a member considers
     * there to be congestion.
     */
    private int mpt = aw/4;

    public void setMissingPacketThreshold(int missingPacketThreshold) {
	mpt = missingPacketThreshold;
    }

    public int getMissingPacketThreshold() {
	return mpt;
    }

    /**
     * Set the maximum congestion window multiple.
     */
    public void setMaxCongestionWindowMultiple(int congestionWindowMultiple) {
	if (congestionWindowMultiple < MIN_CONG_WINDOW_MULTIPLE)
	    this.xc = (short)MIN_CONG_WINDOW_MULTIPLE;
	else if (congestionWindowMultiple > MAX_CONG_WINDOW_MULTIPLE)
	    this.xc = (short)MAX_CONG_WINDOW_MULTIPLE;
	else
	    this.xc = (short)congestionWindowMultiple;
    }

    /**
     *
     */
    public int getMaxCongestionWindowMultiple() {
	return xc;
    }

}       /* End of TRAMTransportProfile Class definition. */
