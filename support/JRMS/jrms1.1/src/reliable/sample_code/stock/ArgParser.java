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

package com.sun.multicast.reliable.applications.stock;

import java.util.*;

/*
 * All of the options are commented here.  Please update this
 * if you change the options.
 *
 * Options for Data receiver start with "R".
 * Options for Data sender and options common to the data sender
 * and receiver start with "X".
 *
 * Options for the StockViewer start with "V".
 * Options for the stockServer and options common to the server
 * and viewer start with "S".
 *
 * Note that aliases are case sensitivie but the full option names
 * are case insensitive.
 *
 *     File		     Option		Alias 	Default
 *     ----		     ------		-----	-------
 * DataReceiver.java    "-ReceiverLog"	  	 "-Rl"   "Receiver.log"
 * DataReceiver.java    "-RCWinIncr"		 "-RWi"  2
 * DataReceiver.java    "-RLogMask"		 "-Rm"   LOG_INFO
 * DataReceiver.java    "-RMaxReceiveDataRate"	 "-RMR"  0
 * DataReceiver.java    "-RCacheSize"	 	 "-Rc"   TRAM Default
 *
 * DataSender.java      "-XSenderLog"		 "-Xl"   "DataSender.log"
 * DataSender.java      "-XDisablePruning"	 "-Xn"   false
 * DataSender.java      "-XDataPort"		 "-Xp"   6000
 * DataSender.java      "-XSenderAddress"	 "-Xa"   "224.100.100.101"
 * DataSender.java      "-XSenderLogMask"	 "-Xm"   LOG_INFO
 * DataSender.java      "-XMinDataRate"		 "-Xr"   1000
 * DataSender.java      "-XMaxDataRate"		 "-XR"   200000
 * DataSender.java      "-XACKWindow"		 "-Xw"   32
 * DataSender.java      "-XSendDataSize"	 "-Xs"   10000000
 * DataSender.java      "-XSenderDelay"		 "-Xd"   10
 * DataSender.java      "-XStaticTreeFormation"	 "-XST"  false
 * DataSender.java      "-XdecentralizedPruning" "-XDP"  false
 * DataSender.java      "-XNumMembersToWaitFor"	 "-XWM"  0
 * DataSender.java      "-XMaxConsecutiveCongestionCount" "-XMCCC"  1
 * DataSender.java      "-XPasses" 		 "-XP"   0 (means infinity)
 * DataSender.java      "-XCacheSize" 		 "-Xc" 	 TRAM Default
 * DataSender.java      "-XPruningWindow"	 "-XPW"  1.5
 * DataSender.java	"-XSetRateDecreaseFactor "-XRDF" .875
 * DataSender.java	"-XSetRateIncreaseFactor "-XRIF" .15
 * DataSender.java	"-XSetTimeToAverage"	 "-XTTA" 5 seconds
 * DataSender.java	"-XMaxBuf"	 	 "-XB"   1500 bytes
 *
 * StockServer.java     "-StockServerLog"	 "-Sl"   "StockServer.log"
 * StockServer.java     "-STickers"		 none   "SUNW+IBM+AOL"
 * StockServer.java     "-SChannelFile"		 none   null
 * StockServer.java     "-StockServerAddress"	 "-Sa"   "224.100.100.100"
 * StockServer.java     "-SDataPort"		 "-Sp"   4567
 * StockServer.java     "-STTL"		 	 none   1
 * StockServer.java     "-Sin"		 	 none   null
 * StockServer.java     "-Sout"		 	 none   null
 * StockViewer.java     "-SSunTicker"		 none   false
 *
 * StockViewer.java     "-ViewerLog"	 	 "-Vl"   "StockViewer.log"
 * StockViewer.java     "-Vx"		 	 none   80
 * StockViewer.java     "-Vy"		 	 none   0
 * StockViewer.java     "-Vwidth"		 none   25
 * StockViewer.java     "-Vheight"		 none   600
 * StockViewer.java     "-VfontSize"		 none   14
 * StockViewer.java     "-VSAPTimeout"		 none	0
 * StockViewer.java     "-VSUNWDemo"		 none   false
 * StockViewer.java     "-VSunTicker"		 none   false
 * StockViewer.java     "-VDownForRepair" 	 none   null
 * StockViewer.java     "-VLogMask"		 "-Vm"  LOG_INFO
 *
 * StockServer.java     "-VTickers"		 none   "SUNW+IBM+AOL"
 */

public class ArgParser {
    private String args[];

    ArgParser(String args[]) {
	this.args = args;
    }

    /*
     * Look for "arg" or "alias" in the command arguments.
     * Start from the last argument and work toward the first
     * so that if there are duplicate arguments on the command line,
     * the last one overrides the earlier ones.
     */

    public boolean getBoolean(String arg, String alias, boolean defaultValue) {
        String option = "-" + arg;
	String altOption = null;

	if (alias != null)
            altOption = "-" + alias;

        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i].equalsIgnoreCase(option) ||
		(altOption != null && args[i].equals(altOption))) {
                return true;
	    }
        }

        return (defaultValue);
    }

    public int getInteger(String arg, String alias, int defaultValue) {
        String option = "-" + arg;
        String altOption = null;

	if (alias != null)
	    altOption = "-" + alias;

        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i].equalsIgnoreCase(option) ||
		(altOption != null && args[i].equals(altOption))) {
                if (++i < args.length) {
                    return Integer.parseInt(args[i]);
		}

		break;
            }
        }

	return defaultValue;
    }

    public double getDouble(String arg, String alias, double defaultValue) {
        String option = "-" + arg;
        String altOption = null;

	if (alias != null)
	    altOption = "-" + alias;

        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i].equalsIgnoreCase(option) ||
		(altOption != null && args[i].equals(altOption))) {
                if (++i < args.length) {
                    return Double.parseDouble(args[i]);
		}

		break;
            }
        }

	return defaultValue;
    }

    public String getString(String arg, String alias, String defaultValue) {
        String option = "-" + arg;
        String altOption = null;

	if (alias != null)
	    altOption = "-" + alias;

        for (int i = args.length - 1; i >= 0; i--) {
            if (args[i].equalsIgnoreCase(option) ||
		(altOption != null && args[i].equals(altOption))) {
                if (++i < args.length) {
                    return args[i];
		}	

		break;
            }
        }

        return (defaultValue);
    }

}
