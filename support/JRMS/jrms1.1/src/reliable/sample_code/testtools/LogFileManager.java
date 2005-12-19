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
 * LogFileManager.java
 */
package com.sun.multicast.reliable.applications.testtools;

import java.io.*;

/** 
 * Parses through all log and logbak files checking for succeeded and 
 * passed. If it finds a failed, or cannot find the term succeded.  It 
 * will let you know by printing to System.out
 */

public class LogFileManager {
    private static int counter = 0;
    private static boolean failure = false;
    private static boolean succeeded = false;
    private static String s = "";
    private static File logfile = null;
    private static File logfilebak = null;
    static void parseFile(String filename) {
	try {
	    failure = false;
	    succeeded = false;
	    logfile = new File(filename);
	    logfilebak = new File(filename + "bak");
	    BufferedReader log =
	        new BufferedReader(
		    new FileReader(filename));

	    while ((s = log.readLine()) != null) {
		counter++;
		if (s.indexOf("failed") != -1) {
		    System.out.println("Test failed in " + 
			filename + " at line " + 
			Integer.toString(counter) + ".");
		    System.out.println(s);
	 	    failure = true;
	        }
		if (s.indexOf("succeeded") != -1) {
		succeeded = true;
		}
	    }

	    if (logfile.length() == 0) {
		succeeded = false;
		failure = true;
		System.out.println(filename + " file is zero bytes.");
	    }
	    if (!failure) {
		System.out.println("All tests in the " + 
		    filename + " file passed.");
	    }

	    counter = 0;

	    if (logfilebak.exists()) {
		BufferedReader logbak =
		    new BufferedReader(
			new FileReader(logfilebak));
	        while ((s = logbak.readLine()) != null) {
		    counter++;
		    if (s.indexOf("failed") != -1) {
		        System.out.println("Test failed in " + 
			    filename + " at line " + 
			    Integer.toString(counter) + ".");
		        System.out.println(s);
	 	        failure = true;
	            }
		    if (s.indexOf("succeeded") != -1) {
		    succeeded = true;
		    }
	        }
	    }
	    if (!failure) {
		System.out.println("All tests in the " + 
		    filename + "bak file passed.");
	    }
	    if (succeeded) {
		System.out.println(filename + 
		    " and " + filename + "bak" + " files succeeded.");
	    } else {
		System.out.println(filename + 
		    " and " + filename + "bak" + " files did not succeed.");
	    }
	    log.close();
	} catch (FileNotFoundException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	} catch (IOException e) {
	    System.out.println(e);
	    e.printStackTrace(System.out);
	}
    }
}
