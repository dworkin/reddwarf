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
 * TRAMConfigurator.java
 * 
 * Module Description:  TRAMConfigurator class definition.
 */
package com.sun.multicast.reliable.transport.tram;

import java.io.*;
import java.net.*;
import java.util.*;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;

/**
 * TRAMConfigurator is a basic TRAM configuration file parser cum loader.
 * The TRAMConfigurator will have multiple LoadConfig methods and each
 * of the method just initializes the parameters that are relavent to the
 * class that is passed in as formal argument.
 */
final class TRAMConfigurator {

    // TRAMTransportProfile related string tokens.

    static final String str_MROLE = "MROLE";
    static final String str_TMODE = "TMODE";
    static final String str_MAX_MEMBERS = "MAX_MEMBERS";
    static final String str_HELLO_RATE = "HELLO_RATE";
    static final String str_MS_RATE = "MS_RATE";
    static final String str_BEACON_RATE = "BEACON_RATE";
    static final String str_ACK_WINDOW = "ACK_WINDOW";
    static final String str_NACK_REPORT_WINDOW = "NACK_REPORT_WINDOW";
    static final String str_MAX_HELLO_MISSES = "MAX_HELLO_MISSES";
    static final String str_MS_TTL_INCREMENTS = "MS_TTL_INCREMENTS";
    static final String str_BEACON_TTL_INCREMENTS = "BEACON_TTL_INCREMENTS";
    static final String str_UNICAST_PORT = "UNICAST_PORT";
    static final String str_TFR_DATA_SIZE = "TFR_DATA_SIZE";
    static final String str_TFR_DURATION = "TFR_DURATION";
    static final String str_MADDR = "MADDR";
    static final String str_MPORT = "MPORT";
    static final String str_MDATA_SOURCE = "MDATA_SOURCE";
    static final String str_MEMBER_ONLY = "MEMBER_ONLY";
    static final String str_MEMBER_EAGERHEAD = "MEMBER_EAGERHEAD";
    static final String str_MEMBER_RELUCTANTHEAD = "MEMBER_RELUCTANTHEAD";
    static final String str_SEND_ONLY = "SEND_ONLY";
    static final String str_RECEIVE_ONLY = "RECEIVE_ONLY";
    static final String str_SEND_RECEIVE = "SEND_RECEIVE";

    // Other modules String Token definitions.

    /**
     * LoadConfig method to read the TRAMTransportProfile configuration
     * information. Other LoadConfig routines to support other modules
     * in TRAM can be modeled using this routine.
     * 
     * @param	Configuration file name.
     * @param 	reference to TRAMTransportProfile which is to be initialized
     * with configuration information.
     * 
     * @Exception Throws IOEception if the config file cannot be opened.
     */
    public static final void LoadConfig(String filename, 
			  TRAMTransportProfile tp) throws IOException {

        /*
         * ------------------------------------------------------------------
         * TRAMTransportProfile Configuration file format specification
         * <Param Token> = <Param value>
         * Examples
         * MROLE = MEMBER_ONLY
         * MROLE = MEMBER_EAGERHEAD
         * MROLE = MEMBER_RELUCTANTHEAD
         * TMODE =	RECEIVE_ONLY
         * TMODE =	SEND_ONLY
         * MAX_MEMBERS = 32
         * HELLO_RATE = 100
         * MS_RATE =	100
         * BEACON_RATE = 100
         * ACK_WINDOW = 32
         * NACK_REPORT_WINDOW = 5
         * MAX_HELLO_MISSES = 5
         * MS_TTL_INCREMENTS = 	5
         * BEACON_TTL_INCREMENTS = 5
         * UNICAST_PORT = 3500
         * TFR_DATA_SIZE =	1000000   // in bytes
         * TFR_DURATION =	60		// in minutes
         * ------------------------------------------------------------------
         */
        FileInputStream inputfile = new FileInputStream(filename);
        Properties prop = new Properties();

        prop.load(inputfile);

        /*
         * Basically from this point on just check is a config item is
         * listed in the config file. If so load the specified value and go
         * to the next config item.
         */
        String val = null;

        // Look for MROLE entry. If present load the specified config value.

        if ((val = prop.getProperty(str_MROLE)) != null) {
            byte mrole = 0;

            if (str_MEMBER_ONLY.equals(val)) {
                mrole = MROLE.MEMBER_ONLY;

                System.out.println("Mrole = MemberOnly");
            } else {
                if (str_MEMBER_EAGERHEAD.equals(val)) {
                    System.out.println("Mrole = MemberEagerHead");

                    mrole = MROLE.MEMBER_EAGER_HEAD;
                } else {
                    if (str_MEMBER_RELUCTANTHEAD.equals(val)) {
                        System.out.println("Mrole = MemberReluctant HEad");

                        mrole = MROLE.MEMBER_RELUCTANT_HEAD;
                    }
                }
            }
            if (mrole != 0) {
                tp.setMrole(mrole);
            } 
        }

        // Look for TMODE entry. If present load the specified config value.

        if ((val = prop.getProperty(str_TMODE)) != null) {
            byte tmode = 0;

            if (str_SEND_ONLY.equals(val)) {
                tmode = TMODE.SEND_ONLY;
            } else {
                if (str_RECEIVE_ONLY.equals(val)) {
                    tmode = TMODE.RECEIVE_ONLY;
                } 
            }

            tp.setTmode(tmode);
        }

        /*
         * Look for MAX_MEMBERS entry. If present load the specified
         * config value.
         */
        if ((val = prop.getProperty(str_MAX_MEMBERS)) != null) {
            Integer I = new Integer(val);

            tp.setMaxMembers((short) (I.intValue()));
        }
        if ((val = prop.getProperty(str_HELLO_RATE)) != null) {
            Long L = new Long(val);

            tp.setHelloRate(L.longValue());
        }
        if ((val = prop.getProperty(str_MS_RATE)) != null) {
            Long L = new Long(val);

            tp.setMsRate(L.longValue());
        }
        if ((val = prop.getProperty(str_BEACON_RATE)) != null) {
            Long L = new Long(val);

            tp.setBeaconRate(L.longValue());
        }
        if ((val = prop.getProperty(str_ACK_WINDOW)) != null) {
            Integer I = new Integer(val);

            tp.setAckWindow((short) I.intValue());
        }
        if ((val = prop.getProperty(str_NACK_REPORT_WINDOW)) != null) {
            Integer I = new Integer(val);

            tp.setNackReportWindow((short) (I.intValue()));
        }
        if ((val = prop.getProperty(str_MAX_HELLO_MISSES)) != null) {
            Integer I = new Integer(val);

            tp.setMaxHelloMisses((byte) (I.intValue()));
        }
        if ((val = prop.getProperty(str_MS_TTL_INCREMENTS)) != null) {
            Integer I = new Integer(val);

            tp.setMsTTLIncrements((byte) (I.intValue()));
        }
        if ((val = prop.getProperty(str_BEACON_TTL_INCREMENTS)) != null) {
            Integer I = new Integer(val);

            tp.setBeaconTTLIncrements((byte) (I.intValue()));
        }
        if ((val = prop.getProperty(str_UNICAST_PORT)) != null) {
            Integer I = new Integer(val);

            tp.setUnicastPort(I.intValue());
        }
        if ((val = prop.getProperty(str_TFR_DATA_SIZE)) != null) {
            Long L = new Long(val);

            tp.setTransferDataSize(L.longValue());
        }
        if ((val = prop.getProperty(str_TFR_DURATION)) != null) {
            Long L = new Long(val);

            tp.setTransferDuration(L.longValue());
        }
        if ((val = prop.getProperty(str_MADDR)) != null) {
            InetAddress ia = null;

            try {
                ia = InetAddress.getByName(val);
            } catch (UnknownHostException e) {
                System.out.println("Invalid MAddress");
            }
            try {
                tp.setAddress(ia);
            } catch (InvalidMulticastAddressException e) {
                System.out.println("Invalid Mcast address ");
            }
        }
        if ((val = prop.getProperty(str_MDATA_SOURCE)) != null) {
            try {
                tp.setDataSourceAddress(InetAddress.getByName(val));
            } catch (UnknownHostException e) {
                System.out.println("Invalid Data SourceAddress");
            }
        }
        if ((val = prop.getProperty(str_MPORT)) != null) {
            Integer I = new Integer(val);

            tp.setPort((I.intValue()));
        }
    }

}

