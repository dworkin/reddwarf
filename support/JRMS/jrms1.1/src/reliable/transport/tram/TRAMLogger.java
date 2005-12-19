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
 * TRAMLogger.java
 * 
 * Module Description:
 * 
 * This class implements a logger for TRAM. It is intended to
 * write debugging strings to a file. TRAM classes can call the
 * put packet class to load strings or packets onto the inputList.
 * The input list is serviced in a thread that runs at low priority.
 * When the thread wakes up it removes a pakcet from the list and
 * writes it to the file. Since the thread runs at low priority,
 * the flush routine should be called prior to exiting the
 * application. This clears out any remainting packets from the
 * inputList.
 */
package com.sun.multicast.reliable.transport.tram;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This class implements a simple logger for TRAM. If logging is enabled,
 * calls to the putPacket methods add TRAMLogDatas to the inputList
 * vector. The run thread gets packets off the list at low priority
 * and writes the strings to the log file.
 */
class TRAMLogger {

    /*
     * ====================
     * Basic Logging levels.
     * =====================*/
    /* 
     * LOG_INFO is suitable for Operational code and is intended to
     * result in VERY few logging messages. The messages logged are key
     * milestones/events which provide a sense of normal operation of the
     * code.
     * LOG_INFO is for logging only the very important milstones in the 
     * code path. Any message that occurs on a regular basis or aids 
     * debugging should be avoided at this level.
     */
    public static final int LOG_INFO = (1 << 0);

    /*
     * Just testing purposes... LOG_TEST which is LOG_INFO. This comes
     * handy when we are testing a new functionality within an existing file
     * While testing we can mark them all as TEST and after validating the
     * code, we can go back and assign the appropriate level of logging
     */
    public static final int LOG_TEST = LOG_INFO;

    /*
     * LOG_DIAGNOSTICS is typically suitable for diagnosing a problem and to
     * get a feel for the flow of the various code paths. The messages logged 
     * in this comprise of key events in the code path, error events. In the 
     * LOG_DIAGNOSTICS mode, all LOG_INFO messages will automatically qualify 
     * to be logged.
     */
    public static final int LOG_DIAGNOSTICS = ((1 << 1) | LOG_INFO);

    /* 
     * LOG_VERBOSE level logging is very detailed and gives a detailed
     * account of the code flow (that is almost every step in the
     * code path may be logged). Enabling this level will affect the
     * performance of the program and should be rarely used. In the
     * LOG_VERBOSE mode, all LOG_DIAGNOSTICS and LOG_INFO messages will
     * automatically qualify to be logged.
     */
    public static final int LOG_VERBOSE = ((1 << 2) | LOG_DIAGNOSTICS);


    public static final int LOG_FATAL_ERROR = (1 << 3);

    /*
     * ====================
     * Advanced or functionality based logging levels.
     * =====================
     */
    /*
     * Level LOG_CONG for logging All messages related to congestion.
     * Example : " Rate decreased to...", "Congestion reported by..."
     */
    public static final int LOG_CONG = (1 << 4);  // Congestion based.

    /*
     * Level LOG_CNTLMESG for logging only TRAM control messages both 
     * sent and received.
     */
    public static final int LOG_CNTLMESG = (1 << 5); // Only Control Message

    /*
     * Level LOG_DATAMESG for logging Data messages - both original and 
     * retransmissions.
     */
    public static final int LOG_DATAMESG = (1 << 6); // Only Data message

    /*
     * Level LOG_SESSION for logging session messages. Examples -
     * " Session down detected", "Late join"
     */
    public static final int LOG_SESSION = (1 << 7); // Session based

    /*
     * Level for LOG_DATACACHE logging Data cache related messages. 
     * Examples - " Cache full!", "Lowest message # held for member...."
     */
    public static final int LOG_DATACACHE = (1 << 8); 

    /*
     * Level for LOG_SECURITY logging Security related messages - both 
     * authentication and cipher.
     */
    public static final int LOG_SECURITY = (1 << 9);

    /*
     * Level for visual performance monitor.
     *
     * Choosing this option will result in a performance monitor 
     * which is started as a separate thread.
     *
     * The monitor will graphically display the current data rate
     * and the average data rate.
     */
    public static final int LOG_PERFMON = (1 << 10);

    /*
     * Exit immediately.  This is intended for debugging only!
     */
    public static final int LOG_ABORT_TRAM = (1 << 31);

    /*
     * ========================================
     * Canned combinations Definitions.
     * The following canned combinations of levels predefined for
     * convienience.
     * ======================================
     */
    public static final int LOG_NONE = 0;
    
    public static final int LOG_ANY_BASIC = (LOG_VERBOSE); // note that 
							   // enabling
							   // LOG_VERBOSE will
							   // automatically 
							   // include 
							   // LOG_DIAGNOSTICS
							   // and LOG_INFO.
							   //
    public static final int LOG_ANY_FUNCTIONAL = (LOG_CONG | LOG_CNTLMESG |
						  LOG_DATAMESG | LOG_SESSION |
						  LOG_DATACACHE | 
						  LOG_SECURITY);
    public static final int LOG_ANY = (LOG_ANY_BASIC | LOG_ANY_FUNCTIONAL |
					LOG_FATAL_ERROR);

    private static final String NEWLINE = "\n";
    private static final byte[] NEWLINE_IN_BYTES = NEWLINE.getBytes();

    // FileOutputStream out;
    PrintStream out;
    TRAMControlBlock tramblk;
    long time;

    /**
     * The TRAMLogger implements a simple debug logger. The logger
     * runs in a thread at low priority taking TRAMLogDatas off
     * an input list and writing them to the log file.
     * 
     * @param tramblk the TRAMControlBlock for this session.
     */
    public TRAMLogger(TRAMControlBlock tramblk) {
        this.tramblk = tramblk;
        out = System.err;
        time = System.currentTimeMillis();
    }

    /**
     * Send a packet for logging.
     * 
     * @param pk the TRAMLogData containing the string to log.
     */
    private synchronized void writePacket(TRAMLogData pk, boolean addNewLine) 
    throws IOException {
        String source = pk.getSource().toString();

	if (source.indexOf("Thread") == 0) {
	    source = source.substring(6);  // skip "thread"

	    if (source.indexOf("[TRAM ") == 0)
	        source = source.substring(6);  // skip [TRAM 

            int i;
  
	    // skip ",5,main]"

            if ((i = source.indexOf(",5,main]")) > 0)
                source = source.substring(0, i) + source.substring(i + 8);
	}

        String text = pk.getString();
        int lastDot = source.lastIndexOf(".");
        int asterisk = source.indexOf("@");

        if (lastDot == -1) {
            lastDot = 0;
        } else {
            lastDot++;
        }
        if (asterisk == -1) {
            asterisk = source.length();
        } 

        source = source.substring(lastDot, asterisk).concat(": ");

        Calendar cal = Calendar.getInstance();

	String hour = "" + cal.get(Calendar.HOUR_OF_DAY);
	if (hour.length() == 1)
	    hour = "0" + hour;

        String minute = "" + cal.get(Calendar.MINUTE);
	if (minute.length() == 1)
	    minute = "0" + minute;

        String second = "" + cal.get(Calendar.SECOND);
	if (second.length() == 1)
	    second = "0" + second;

        String ms = "" + cal.get(Calendar.MILLISECOND);
	if (ms.length() == 1)
	    ms = "00" + ms;
	else if (ms.length() == 2)
	    ms = "0" + ms;

	String month = "" + (cal.get(Calendar.MONTH) + 1);
	if (month.length() == 1)
	    month = "0" + month;

	String day = "" + cal.get(Calendar.DATE);
	if (day.length() == 1)
	    day = "0" + day;

        String date = month + "/" + day + " " + 
	    hour + ":" + minute + ":" + second + "." + ms + " ";

	if (addNewLine) {
	    out.println(date + source + text);
	} else {
            out.write(date.getBytes());
            out.write(source.getBytes());
            out.write(text.getBytes());
	}
    }

    /**
     * Log this String.
     */
    public void putPacket(Object o, String s) {
        TRAMLogData pk = new TRAMLogData(o, s);
        try {
            writePacket(pk, false);
        } catch (IOException ioe) {}
    }

    /**
     * An interface method to handover the logging message to the logging
     * module. The provided logging message is first tested to check if
     * the user logging preference requires the logging of this message.
     * XXX Not used any more.
     */
    private void putPacket(Object o, int logMask, String s) {
	if (requiresLogging(logMask)) {
	    /*
	     * Should come here ONLY if the level of logging selected requires
	     * logging.
	     */
	    TRAMLogData pk = new TRAMLogData(o, s);
	    try {
		writePacket(pk, false);
	    } catch (IOException ioe) {}
	}
    }

    /**
     * This routine is same as putPacket() but adds a linefeed to the
     * end of the string.
     */
    public void putPacketln(Object o, String s) {
	TRAMLogData pk = new TRAMLogData(o, s);
	try {
	    writePacket(pk, true);
	} catch (IOException ioe) {}
    }

    /**
     * This routine is same as putPacket() but adds a linefeed to the
     * end of the string.
     * XXX Not used any more.
     */
    private void putPacketln(Object o, int logMask, String s) {

	if (requiresLogging(logMask)) {
	    /*
	     * Should come here ONLY if the level of logging selected requires
	     * logging.
	     */
	    TRAMLogData pk = new TRAMLogData(o, s);
	    try {
		writePacket(pk, true);
	    } catch (IOException ioe) {}
	}


    }

    /**
     * Flush all remaining packets on the input list to the
     * log file. This method is called just prior to shutting down
     * the application.
     * 
     * @exception IOException if there is an error writing to the file.
     */
    public synchronized void flush() throws IOException {
        if (tramblk.getTransportProfile().isLoggingEnabled()) {
            out.flush();
        }
    }
    /*
     * private method to test if logging is enabled.
     * The logMask and the user specified preference in the Transport
     * profile is used to carry out the test. If the test results indicate
     * that no logging is required, the control is returned back to the
     * calling routine.
     */

    public boolean requiresLogging(int logMask) {

	/*
	 * Fatal errors get logged irrespective of the logging preference
	 * chosen. Though there is no need to 'OR' along with LOG_FATAL_ERROR
	 * mask, the following is just a safe guard to catch and log even
	 * if somebody found it necessary to 'OR' it with someother logging 
	 * option.
	 */
	if ((logMask & LOG_FATAL_ERROR) != 0)
	    return true;

	/*
	 * we could have defined a local varible for this module and
	 * initialized it with the transportprofile and used it over and
	 * over again without having to fetching TransportProfile everytime!.
	 * But we cannot do it because in the event the transportProfile value
	 * is changed, a new transportProfile will be used with the help of the
	 * clone operating hence, a change in the logging via a packet could
	 * go unnoticed!. Therefore we have to bear the hit on the performance.
	 */
	int enabledLevel = tramblk.getTransportProfile().getLogMask();

	switch (enabledLevel) {

	  case LOG_NONE:
	    return false;

	  case LOG_ANY:
	    break;

	  default:
	    /*
	     * As indicated in the definition section, the BASIC logging
	     * levels consist of LOG_VERBOSE, LOG_DIAGNOSTICS & LOG_INFO.
	     * The FUNCTIONALity based logging level includes LOG_CONG, 
	     * LOG_CNTLMESG, LOG_DATAMESG, LOG_SESSION, LOG_DATACACHE, 
	     * LOG_SECURITY.
	     * Since enabling LOG_VERBOSE will automatically qualify those
	     * messages with LOG_DIAGNOSTICS and LOG_INFO levels, we need to
	     * perform special tests to accuratly determine which messages
	     * need to be logged and which ones don't qualify.
	     * 
	     * A brief gist of whats happening in the following test to
	     * identify which messages are to be logged and which ones don't.
	     * First we mask off the Functional parts of the logging levels
	     * in the 'user preferred logging level'(i.e. enabledLevel in the
	     * evaluation below) and the 'message logging level'(i.e logMask
	     * in the evaluation below). Then we check if the BASIC bits of
	     * logMask is non zero and then also check if the integer value
	     * of the BASIC bits of enabledLevel is greater than the integer
	     * value of the BASIC bits of the logMask value. If TRUE, the
	     * message qualifies to be printed. (Note that the bit value of 
	     * LOG_VERBOSE is 0x00000007 and that of LOG_INFO is 0x00000001 &
	     * that of LOG_DIAGNOSTICS is 0x00000003. So if enabledLevel is
	     * LOG_VERBOSE, all messages with logMask of LOG_DIAGNOSTICS or 
	     * LOG_INFO will be less than the integer value of LOG_VERBOSE and 
	     * as a result qualify to be printed.
	     *
	     * Evaluation of FUNCTIONAL part is simply a bitwise logical
	     * 'AND'ing of the functional bits of enabledLevel and the
	     * logMask. If the result yields a non zero value, the message 
	     * qualifies to be logged.
	     */
	    int result1 = logMask & LOG_ANY_BASIC;

	    if ((result1 != 0 && (enabledLevel & LOG_ANY_BASIC) >=  result1) ||
		((enabledLevel & logMask) & LOG_ANY_FUNCTIONAL) != 0) {
		break;
	    }
	    return false;
	}
	return true;
    }


    /* Quick debugging output for use with xgraph.... */

    public void timeStampPacket(String id, int value) {
        long currentTime = System.currentTimeMillis();

        out.println(id + " " + (currentTime - time) + " " + value);

        time = currentTime;
    }

    public void timeStampPacket(String id, long value) {
        long currentTime = System.currentTimeMillis();

        out.println(id + " " + (currentTime - time) + " " + value);

        time = currentTime;
    }

    /*
     * A simple test to check if ANY logging is enabled.
     */
    public static final boolean isLoggingEnabled(int inUseMask, int testMask) {

	if ((inUseMask & testMask) == LOG_NONE)
	    return false;

	return true;
    }

}

