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
 * SlingerWithGUI.java
 * 
 * Module Description:
 * 
 * SlingerWithGUI is Slinger with a simple GUI.
 * 
 * Slinger is a sample application that demonstrates the functionality
 * provided by JRMS, a reliable multicast library. It copies
 * a file from one sender to many receivers.
 * 
 * The application is implemented in stages. The first version
 * exercises only the Transport APIs of JRMS.
 * In this version (0.2) of Slinger, the sender creates a channel,
 * and advertises it through the standard SAP address or any other
 * address. Using the channel and application names, receivers hook up
 * to the channel, get the TransportProfile info and start receiving.
 */
package com.sun.multicast.reliable.applications.slinger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Date;
import java.awt.Cursor;
import java.awt.Event;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import com.sun.multicast.reliable.transport.tram.MROLE;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.TMODE;
import com.sun.multicast.reliable.transport.tram.TRAM_INFO;
import com.sun.multicast.reliable.transport.lrmp.LRMP_INFO;
import com.sun.multicast.reliable.transport.um.UMPacketSocket;
import com.sun.multicast.reliable.transport.um.UMTransportProfile;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.ImpossibleException;
import com.sun.multicast.reliable.transport.IrrecoverableDataException;
import com.sun.multicast.reliable.transport.SessionDownException;
import com.sun.multicast.reliable.transport.NoMembersException;
import java.rmi.RemoteException;






/*
 * Package-local class.
 * 
 * @see
 *
 * @author
 */
class SlingerWithGUI extends Thread {

    public SlingerWithGUI(SendDialog pDialog) {
        properties = new Properties();
        progDialog = pDialog;
        bNoGUI = false;
    }

    public SlingerWithGUI() {

        // no user interface, just run!

        properties = new Properties();
        bNoGUI = true;
    }

    /*
     * Method that is called from the GUI to check the arguments and carry out
     * transmission / reception in case of no errors.
     * Return value is 0 if there are no errors.
     * a non-zero integer indicating the incorrect parameter
     * in case of error.
     * 
     * @param sArgs
     *
     * @see
     */
    public void initialize(String[] sArgs) {
        args = sArgs;
    }

    public void run() {
        doOperations();
        cleanup();
    }

    public void cleanup() {

    // ms.close();

    /*
     * if (logFileName.length() != 0) {
     * try {
     * logFile.close();
     * }
     * catch(java.io.IOException e) { }
     * }
     */
    }

    int doOperations() {
        if (!bNoGUI) {
            progDialog.setVisible(true);
            progDialog.startAnimation();
        }

        // Call method to check the args passed from the GUI.

        checkArgs(args);

        // In case of error go back to GUI.

        if (goBack) {
            // to field that is incorrect so that focus can be set there.
            return probArea;        
        }
        if (verbose) {
            System.out.println("Argument check completed...");
        }

        // Create a channel or tune to the right channel
        // depending on whether the application instance is sender or receiver

        if (sender) {
            createChannel();
        } else {
            locateChannel();
        }
        if (goBack) {
            // to field that is incorrect so that focus can be set there.
            return probArea;        
        }
        if (verbose) {
            System.out.println("Created setup for data " 
                               + (sender ? "transmission" : "reception") 
                               + "...\n");
        }

        // Call the SenderPart method or the ReceiverPart method depending on
        // whether this instance of the application is intended to function as
        // sender or receiver.

        if (sender) {
            SenderPart();
        } else {
            ReceiverPart();
        }
        if ((!aborted) && (verbose)) {
            System.out.println("Data " 
                               + (sender ? "transmission" : "reception") 
                               + " completed...\n");
        }

        ms.close();

        // Dispose of the progress dialog.

        if (!bNoGUI) {
            progDialog.stopAnimation();
            progDialog.setVisible(false);
        }

        return 0;
    }

    /*
     * Method to validate the command-line arguments.
     * The command line syntax is
     * slinger {(-send file(s) [-delay interval] [-linger interval] 
     *     [-speed bytesPerSec] -address mcastAddress -port port -ttl 
     *     ttl -transport transportName) |
     * 
     *     (-receive receiveDirectory [-wait interval])} 
     *     [-config configFileName] [-verbose] [-channel channelName] 
     *     [-application applicationName]
     * 
     * @param args
     *
     * @see
     */
    private void checkArgs(String[] args) {
        String arg;
        int i = 0;

        // Two passes are made over the command-line arguments as the
        // validity of some of the arguments depend upon whether the
        // application is invoked as a sender or receiver. The first
        // pass tries to identify if the application instance is
        // a sender or receiver and checks the validity of the arguments
        // that are independent of the application instance being a
        // sender or receiver.

        while (i < args.length && args[i].startsWith("-")) {
            arg = args[i++];

            // Two successive command-line arguments cannot be flags except when
            // the first argument is -verbose. Again, a flag that is not 
	    // -verbose cannot be the last command-line argument.

            if (!arg.equals("-verbose") &&!arg.equals("-noGUI") 
                    && ((i == args.length) || (args[i].startsWith("-")))) {
                updateUser("No value for the flag " + arg + ".", true);
            }

            int increment = 1;

            // Check the validity of the command-line arguments that are
            // independent of whether the application instance is a sender
            // or a receiver.

            if (arg.equals("-send")) {
                increment = checkSendFiles(args, i);
            } else if (arg.equals("-receive")) {
                checkRecvDirectory(args[i]);
            } else if (arg.equals("-config")) {
                checkConfigFile(args[i]);
            } else if (arg.equals("-channel")) {
                channelName = args[i];
            } else if (arg.equals("-application")) {
                applicationName = args[i];
            } else if (arg.equals("-verbose")) {
                verbose = true;
            } else if (arg.equals("-uport")) {
                checkUPort(args[i]);
            } else if (arg.equals("-authenticationSpec")) {
                checkAuthenticationSpec(args[i]);
            } else if (arg.equals("-authenticationPassword")) {
                checkAuthenticationPassword(args[i]);
            } 


	        else if (arg.equals("-logfile")) {
                checkLogFile(args[i]);
            } else if (arg.equals("-address")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-port")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-transport")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-delay")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-linger")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-ttl")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-wait")) {
                // Valid only for receiver. Check later.
            } else if (arg.equals("-speed")) {
                // Valid only for sender. Check later.
            } else if (arg.equals("-noGUI")) {
                // 
            } else {        // Invalid flag
                updateUser("Invalid command-line argument " + arg + ".", 
                           true);
            }

            // goBack in case of error.

            if (goBack) {
                return;
            } 

            // increment will be 1 for all but the -send and -verbose flags.
            // For -verbose the -1 i should not be increased and for -send
            // flag, the -1 should be increased by the number of files to
            // be sent.

            if (!arg.equals("-verbose")) {
                i += increment;
            } 
        }

        // Invalid argument

        if (i != args.length) {
            updateUser("Invalid command-line argument " + args[i] + ".", 
                       true);

            return;
        }

        // If configuration file is not specified on the command-line, check
        // if the default configuration file DEFAULT_CONFIG_FILE is
        // available and readable. If so, load it.
        // Doing so also means that if DEFAULT_CONFIG_FILE exists, then
        // any other specified arguments will be wiped out; hence commented out.

        /**
         * *****
         * if (configFile == null) {
         * configFile = DEFAULT_CONFIG_FILE;
         * if (new File(configFile).exists()) {
         * checkConfigFile(configFile);
         * if (goBack)
         * return;
         * if (properties.getProperty("slinger.verbose","false").equals("true"))
         * verbose = true;
         * }
         * }
         * *****
         */
        String tmp;

        // If neither sender nor receiver flag has been specified on the
        // command-line check in the configuration file to decide if the
        // application instance is a sender or receiver.

        if (!sender &&!receiver) {
            if (properties.getProperty("slinger.send") != null) {
                sender = true;
            } else if ((recvDirectory = 
                    properties.getProperty("slinger.receive")) != null) {
                receiver = true;
            } 
            if (sender && receiver) {
                updateUser("Both send and receive flags cannot be specified.", 
                           true);

                return;
            }
            if (sender) {
                checkSendFiles(extractSendFiles(), 0);
            } else if (receiver) {
                checkRecvDirectory(recvDirectory);
            } else {
                updateUser(
		    "Send Filename / Receive directory name not specified.", 
                    true);

                probArea = 4;
            }
            if (goBack) {
                return;
            } 
        }

        // Pass-2 of the command-line arguments.
        // Application instance identified to be sender or a receiver
        // at this time. Configuration file, if any, has been loaded.
        // Proceed to check the command-line arguments that depend upon
        // the application instance is a sender or a receiver.
        // (the -delay, -linger, -ttl and -wait flags).

        i = 0;

        while (i < args.length && args[i].startsWith("-")) {
            int increment = 1;

            arg = args[i++];

            if (arg.equals("-send")) {
                increment = checkSendFiles(args, i);
            } else if (arg.equals("-address")) {
                checkAddress(args[i]);
            } else if (arg.equals("-port")) {
                checkPort(args[i]);
            } else if (arg.equals("-transport")) {
                checkTransportName(args[i]);
            } else if (arg.equals("-delay")) {
                checkDelayTime(args[i]);
            } else if (arg.equals("-linger")) {
                checkLingerTime(args[i]);
            } else if (arg.equals("-ttl")) {
                checkTTL(args[i]);
            } else if (arg.equals("-wait")) {
                checkWaitTime(args[i]);
            } else if (arg.equals("-speed")) {
                checkSpeed(args[i]);
            } else if (arg.equals("-headonly")) {
                checkHeadOnly();
            } 
            if (!arg.equals("-verbose") &&!arg.equals("-headonly")) {
                i += increment;
            } 
            if (goBack) {
                return;
            } 
        }

        // Find if channel / application names are specified in the
        // config file. If not, use the default channel and application names.

        if (channelName == null) {
            if ((channelName = properties.getProperty("slinger.channel")) 
                    == null) {
                channelName = DEFAULT_CHANNEL_NAME;
            } 
        }
        if (applicationName == null) {
            if ((applicationName = 
                    properties.getProperty("slinger.application")) == null) {
                applicationName = DEFAULT_APPLICATION_NAME;
            } 
        }
        if (uport == 0) {
            if ((tmp = properties.getProperty("slinger.uport")) == null) {
                uport = 0;
            } else {
                checkUPort(tmp);
            }
        }
        if (authenticationSpecFileName == null) {
            if ((tmp = properties.getProperty("slinger.authenticationSpec")) 
                    != null) {
                if (!isEmpty(tmp)) {
                    checkAuthenticationSpec(tmp);
                } 
            }
        }
        if (authenticationPassword == null) {
            if ((tmp = 
                    properties.getProperty("slinger.authenticationPassword")) 
                    != null) {
                if (!isEmpty(tmp)) {
                    checkAuthenticationPassword(tmp);
                } 
            }
        }



        // Continue processing args.

        if (sender) {
            if (mcastAddress == null) {
                if ((tmp = properties.getProperty("slinger.address")) 
                        == null) {
                    updateUser("Multicast address not specified.", true);

                    probArea = 2;
                } else {
                    checkAddress(tmp);
                }
            }
            if (goBack) {
                return;
            } 
            if (port == 0) {
                if ((tmp = properties.getProperty("slinger.port")) == null) {
                    updateUser("Multicast port not specified.", true);

                    probArea = 3;
                } else {
                    checkPort(tmp);
                }
            }
            if (goBack) {
                return;
            } 
            if (ttl == (byte) 0) {
                if ((tmp = properties.getProperty("slinger.ttl")) == null) {
                    updateUser("TTL value not specified.", true);

                    probArea = 5;
                } else {
                    checkTTL(tmp);
                }
            }
            if (goBack) {
                return;
            } 

            // If delay time not specified on the command-line, load it
            // from the configuration file, if specified. Otherwise the
            // delay time is set to DEFAULT_DELAY_TIME seconds.

            if (delayTime == -1) {
                if ((tmp = properties.getProperty("slinger.delay")) != null) {
                    checkDelayTime(tmp);
                } else {
                    delayTime = DEFAULT_DELAY_TIME;
                }
            }
            if (goBack) {
                return;
            } 

            // If linger time not specified on the command-line, load it
            // from the configuration file, if specified. Otherwise the
            // linger time is set to DEFAULT_LINGER_TIME seconds.

            if (lingerTime == -1) {
                if ((tmp = properties.getProperty("slinger.linger")) 
                        != null) {
                    checkLingerTime(tmp);
                } else {
                    lingerTime = DEFAULT_LINGER_TIME;
                }
            }
            if (goBack) {
                return;
            } 
            if (speed == -1) {
                if ((tmp = properties.getProperty("slinger.speed")) != null) {
                    checkSpeed(tmp);
                } else {
                    speed = DEFAULT_SPEED;
                }
            }
            if (goBack) {
                return;
            } 
        }
        if (receiver) {

            // If wait time not specified on the command-line, load it
            // from the configuration file, if specified. Otherwise the
            // wait time is set to DEFAULT_WAIT_TIME seconds.

            if (waitTime == -1) {
                if ((tmp = properties.getProperty("slinger.wait")) != null) {
                    checkLingerTime(tmp);
                } else {
                    waitTime = DEFAULT_WAIT_TIME;
                }
            }
            if (goBack) {
                return;
            } 

            // check if headonly is specified in the config file, if so
            // set accordingly.

            if (headonly == false) {
                if (properties.getProperty("slinger.headonly", 
                                           "false").equals("true")) {
                    headonly = true;

                    if (verbose) {
                        System.out.println("headonly is " + headonly);
                    }
                }
            }
            if (goBack) {
                return;
            } 
        }
    }

    /*
     * Private method to check if the files to be sent are valid. The files
     * should exist, be readable and normal files; their length should be of
     * a maximum of MAX_FILENAME_LEN characters and the file size should be
     * of a maximum of MAX_FILE_SIZE bytes.
     * 
     * @param sarray
     * @param i
     *
     * @return
     *
     * @see
     */
    private int checkSendFiles(String[] sarray, int i) {
        if (receiver) {

        // updateUser("Both send and receive flags cannot be specified.", true);

        }

        // Extract all the files to be sent and store them in sendFileArray.

        int cnt = 0;

        while (((i + cnt) < sarray.length) 
               && (!(sarray[i + cnt].startsWith("-")))) {
            cnt++;
        }

        sendFileArray = new String[cnt];

        String sfile;
        int j = 0;

        while (cnt-- != 0) {
            File file = new File(sarray[i]);

            sfile = sarray[i];

            if (!file.exists()) {
                updateUser("File to be sent " + sfile + " not available.", 
                           true);

                return 0;
            }
            if (!file.canRead()) {
                updateUser("File to be sent " + sfile + " not readable.", 
                           true);

                return 0;
            }
            if (!file.isFile()) {
                updateUser("File to be sent " + sfile 
                           + " not a normal file.", true);

                return 0;
            }
            if ((file.getName()).length() > MAX_FILENAME_LEN) {
                updateUser(
		    "Maximum length of the filename to be sent can only be " + 
		    MAX_FILENAME_LEN + " characters.", true);

                return 0;
            }
            if (((fileLength = file.length()) > MAX_FILE_SIZE) || 
		(fileLength < 0)) {

                updateUser("Maximum length of the file to be sent can only be " 
                           + MAX_FILE_SIZE + " bytes.", true);

                return 0;
            }

            sendFileArray[j++] = sarray[i++];
        }

        sender = true;

        return sendFileArray.length;
    }

    /*
     * Private method to check the receive directory. It should exist, be
     * a directory and be writable.
     * 
     * @param rdir
     *
     * @see
     */
    private void checkRecvDirectory(String rdir) {
        probArea = 4;

        if (sender) {

            // updateUser("Both send and receive flags cannot be set.", true);

            return;
        }

        File file = new File(rdir);

        if (!file.exists()) {
            updateUser("Receiver directory " + rdir + " not available.", 
                       true);

            return;
        }
        if (!file.isDirectory()) {
            updateUser(rdir + " not a directory.", true);

            return;
        }
        if (!file.canWrite()) {
            updateUser("Receiver directory " + rdir + " not writable.", true);

            return;
        }

        receiver = true;
        recvDirectory = rdir;
    }

    /*
     * Private method to check the Multicast address. It should be a valid
     * class D (multicast) address, when the application instance is sender.
     * 
     * @param address
     *
     * @see
     */
    private void checkAddress(String address) {
        probArea = 2;

        if (receiver) {

            // updateUser(
	    //	"Multicast Address cannot be specified by a receiver.", true);

            return;
        }

        try {
            mcastAddress = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            updateUser("Unknown host " + address + ".", true);

            return;
        }

        if (!mcastAddress.isMulticastAddress()) {
            updateUser(address 
                       + " does not correspond to a Multicast address.", 
                       true);

            return;
        }
    }

    /*
     * Private method to check the Configuration file. It should exist and be
     * readable.
     * 
     * @param cfile
     *
     * @see
     */
    private void checkConfigFile(String cfile) {
        probArea = 1;

        File tmpfile = new File(cfile);

        if (tmpfile.exists() &&!tmpfile.isFile()) {
            updateUser(cfile + " not a normal file.", true);

            return;
        }

        try {
            properties.load(new FileInputStream(cfile));

            configFile = cfile;

            // check and load all the args in the config file

            String tmp;

            if ((tmp = properties.getProperty("slinger.channel")) != null) {
                channelName = tmp;
            } 
            if ((tmp = properties.getProperty("slinger.application")) 
                    != null) {
                applicationName = tmp;
            } 
            if ((tmp = properties.getProperty("slinger.address")) != null) {
                checkAddress(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.port")) != null) {
                checkPort(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.uport")) != null) {
                checkUPort(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.authenticationSpec")) 
                    != null) {
                checkAuthenticationSpec(tmp);
            } 
            if ((tmp = 
                    properties.getProperty("slinger.authenticationPassword")) 
                    != null) {
                checkAuthenticationPassword(tmp);
            } 



            if ((tmp = properties.getProperty("slinger.logfile")) != null) {
                checkLogFile(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.transport")) != null) {
                checkTransportName(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.send")) != null) {
                checkSendFiles(extractSendFiles(), 0);
            } 
            if ((tmp = properties.getProperty("slinger.receive")) != null) {
                checkRecvDirectory(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.ttl")) != null) {
                checkTTL(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.delay")) != null) {
                checkDelayTime(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.linger")) != null) {
                checkLingerTime(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.wait")) != null) {
                checkWaitTime(tmp);
            } 
            if ((tmp = properties.getProperty("slinger.speed")) != null) {
                checkSpeed(tmp);
            } 
            if (!properties.getProperty("slinger.verbose", 
                                        "false").equals("false")) {
                verbose = true;
            } 
        } catch (FileNotFoundException e) {
            updateUser("Configuration file " + cfile 
                       + " not available or readable.", true);

            return;
        } catch (SecurityException e) {
            updateUser("Configuration file " + cfile + " not readable.", 
                       true);

            return;
        } catch (IOException e) {
            updateUser("IO error while trying to read Configuration file " 
                       + cfile + ".", true);

            return;
        }

        updateUser("Configuration file " + cfile 
                   + " has been loaded sucessfully.", false);
    }

    /*
     * Private method to check the Delay time. Valid only when the application
     * instance is a sender. Should be a positive integer.
     * 
     * @param arg
     *
     * @see
     */
    private void checkDelayTime(String arg) {
        probArea = 6;

        if (receiver) {

            // updateUser(
	    //    "Delay time cannot be specified by a receiver.", true);

            return;
        }

        try {
            delayTime = Integer.parseInt(arg);

            if (delayTime < 0) {
                updateUser("Delay time too large.", true);

                return;
            }
        } catch (NumberFormatException e) {
            updateUser("Delay time " + arg + 
		" not a number or is too large.", true);

            return;
        }
    }

    /*
     * Private method to check the Linger time. Valid only when the  application
     * instance is a sender. Since the linger time is sent to the receiver using
     * 2 bytes in the header packet, the maximum value is 65535.
     * 
     * @param arg
     *
     * @see
     */
    private void checkLingerTime(String arg) {
        probArea = 7;

        if (receiver) {

            // updateUser(
	    //    "Linger time cannot be specified by a receiver.", true);

            return;
        }

        try {
            lingerTime = Integer.parseInt(arg);

            if ((lingerTime < 0) || (lingerTime > 65535)) {
                updateUser("Valid range for linger time is 0-65535 seconds.", 
                           true);

                return;
            }
        } catch (NumberFormatException e) {
            updateUser("Linger time " + arg + " invalid.", true);

            return;
        }
    }

    /*
     * Private method to check the port. Should be an integer between 1024 and
     * 65535, both inclusive. Valid only when the application instance is a
     * sender.
     * 
     * @param arg
     *
     * @see
     */
    private void checkPort(String arg) {
        probArea = 3;

        if (receiver) {

            // updateUser(
	    //    "Multicast Port cannot be specified by receiver.", true);

            return;
        }

        try {
            port = Integer.parseInt(arg);

            if ((port < 1024) || (port > 65535)) {
                updateUser("Valid range for port number is 1024-65535.", 
                           true);

                return;
            }
        } catch (NumberFormatException e) {
            updateUser("Port number " + arg + " invalid.", true);

            return;
        }
    }

    /*
     * Private method to check the port. Should be an integer between 1024 and
     * 65535, both inclusive. Valid only when the application instance is a
     * sender.
     * 
     * @param arg
     *
     * @see
     */
    private void checkUPort(String arg) {
        probArea = 11;

        if (arg.length() == 0) {
            return;
        } 

        try {
            uport = Integer.parseInt(arg);

            if ((uport < 1024) || (uport > 65535)) {
                updateUser("Valid range for port number is 1024-65535.", 
                           true);

                return;
            }
        } catch (NumberFormatException e) {
            updateUser("Unicast Port number " + arg + " invalid.", true);

            return;
        }
    }

    /*
     * Private method to check the authenticationSpec. Should be a string.
     * 
     * @param athFileName
     *
     * @see
     */
    private void checkAuthenticationSpec(String athFileName) {
        probArea = 12;

        File tmpfile = new File(athFileName);

        if (tmpfile.exists() &&!tmpfile.isFile()) {
            updateUser(athFileName + " not a normal file.", true);

            return;
        }

        try {
            properties.load(new FileInputStream(athFileName));

        // configFile = athFileName;

        } catch (FileNotFoundException e) {
            updateUser("Authentication file " + athFileName 
                       + " not available or readable.", true);

            return;
        } catch (SecurityException e) {
            updateUser("Authentication file " + athFileName 
                       + " not readable.", true);

            return;
        } catch (IOException e) {
            updateUser("IO error while trying to read Authentication file " 
                       + athFileName + ".", true);

            return;
        }

        if (verbose) {
            System.out.println("Got Spec File name As " + athFileName);
        }

        authenticationSpecFileName = athFileName;
    }




    /*
     * Private method to check the authentication password.
     * 
     * @param arg
     *
     * @see
     */
    private void checkAuthenticationPassword(String arg) {
        probArea = 12;

        if (arg.length() == 0) {
            return;
        } 

        authenticationPassword = arg;
    }




    /*
     * Private method to check the log file. Should be in a valid directory.
     * 
     * @param arg
     *
     * @see
     */
    private void checkLogFile(String arg) {
        if (arg.length() == 0) {
            return;
        } 

        // try {
        // logFile = new FileWriter(arg);
        // }
        // catch(java.io.IOException e) {
        // updateUser("Error: IO Exception while opening log file.", true);
        // return;
        // }

        logFileName = arg;
    }

    /*
     * Private method to check the TTL value. Valid only when the application
     * instance is a sender. Should be between 1 and 255, both inclusive.
     * 
     * @param arg
     *
     * @see
     */
    private void checkTTL(String arg) {
        probArea = 5;

        if (receiver) {

            // updateUser("TTL value cannot be specified by a receiver.", true);

            return;
        }

        try {
            int ttlint = Integer.parseInt(arg);

            if ((ttlint < 1) || (ttlint > 255)) {
                updateUser("Valid range for TTL value is 1-255.", true);

                return;
            } else {
                ttl = (byte) ttlint;
            }
        } catch (NumberFormatException e) {
            updateUser("TTL value " + arg + " invalid.", true);

            return;
        }
    }

    /*
     * Private method to check the Transport name. The only valid transport
     * names for this version of Slinger are "Unreliable Multicast Transport"
     * (DEFAULT_TRANSPORT_NAME), "TRAM" (TRAM_TRANSPORT_NAME), and
     * "LRMP" (LRMP_TRANSPORT_NAME).
     * 
     * @param tname
     *
     * @see
     */
    private void checkTransportName(String tname) {
        if (receiver) {

            // updateUser("Transport cannot be specified by a receiver.", true);

            return;
        }
        if (!tname.equals(DEFAULT_TRANSPORT_NAME) 
                &&!tname.equals(TRAM_TRANSPORT_NAME) 
                &&!tname.equals(LRMP_TRANSPORT_NAME)) {
            updateUser("Invalid transport name.", true);
        } else {
            transportName = tname;
        }
    }

    /*
     * Private method to check the Wait time. Valid only when the  application
     * instance is a receiver. Should be a postive integer.
     * 
     * @param arg
     *
     * @see
     */
    private void checkWaitTime(String arg) {
        probArea = 5;

        if (sender) {

            // updateUser("Wait time cannot be specified by a sender.", true);

            return;
        }

        try {
            waitTime = Integer.parseInt(arg);

            if (waitTime < 0) {
                updateUser("Wait time time too large.", true);

                return;
            }
        } catch (NumberFormatException e) {
            updateUser("Wait time " + arg + " not a number or is too large.", 
                       true);

            return;
        }
    }

    /*
     * Private method to check the validity of the -headonly argument
     * 
     * @see
     */
    private void checkHeadOnly() {
        if (sender) {

            // updateUser("Head only cannot be specified by a sender.", true);

            return;
        }

        headonly = true;
    }

    /*
     * Private method to check the speed value. Valid only when the application
     * instance is a sender. Should be a postive integer. 
     * 
     * @param arg
     *
     * @see
     */
    private void checkSpeed(String arg) {
        probArea = 8;

        if (receiver) {

            // updateUser(
	    //    "Speed value cannot be specified by a receiver.", true);

            return;
        }

        try {
            speed = Integer.parseInt(arg);

            if (speed < 0) {
                updateUser("Speed value too large.", true);

                return;
            }
        } catch (NumberFormatException e) {
            updateUser("Speed value " + arg 
                       + " not a number or is too large.", true);

            return;
        }
    }

    /*
     * Method used by the sender to create a channel and advertise it over SAP
     * 
     * @see
     */
    private void createChannel() {
        try {
            updateUser("Creating Channel " + channelName + 
		" for Application " + applicationName, false);

            // Locate a channel manager
            // When the argument is null, it means get the local channel
            // manager that is part of this library

            pcm = ChannelManagerFinder.getPrimaryChannelManager(null);

            // Create a channel

            channel = pcm.createChannel();
            
            /* if cipher file is specified, Enable CipherMode. */



            // Set channel parameters

            channel.setChannelName(channelName);
            channel.setApplicationName(applicationName);
            createTransportProfile();

            // tp is the transportProfile created through the
            // createTransportProfile method. Use it to set the
            // channel's transport profile.

            channel.setTransportProfile(tp);
            createSocket(tp, TransportProfile.SENDER);
            channel.setAbstract("Slinger channel.");

            // dataStartTime is currentTime + delayTime

            Date dataStartTime = new Date(new Date().getTime() 
                                          + delayTime * 1000);

            channel.setDataStartTime(dataStartTime);

            // channel.setSessionEndTime(
	    //    new Date(dataStartTime.getTime() + 300000));

            // when the following is set true, SAP will used to advertise 
	    // channel

            channel.setAdvertisingRequested(true);

            // Enable the channel

            channel.setEnabled(true);
            
            Thread.sleep(1000);

        } catch (Exception e) {
            probArea = 9;

            System.out.println(e);
            e.printStackTrace();
            updateUser("Error in setting up " + channelName + " for " 
                       + applicationName + ".", true);
        }
    }

    /*
     * Method used by the sender to stop a channel
     * @see
     */
    private void stopChannel() {     
        try {

            // stop advertising

            channel.setEnabled(false);
            channel.setAdvertisingRequested(false);
        } catch (Exception e) {
            updateUser("Error in stopping " + channelName + " for " 
                       + applicationName + ".", true);
        }

        if (verbose) {
            System.out.println("channel " + channelName + " stopped");
        } 
    }

    /*
     * Method used by the receiver to locate a channel
     * If the channel cannot be located within 'waitTime' seconds, the
     * receiver aborts.
     * 
     * @see
     */
    private void locateChannel() {
        try {
            updateUser("Locating " + channelName + "/" + applicationName, 
                       false);

            long timeleft = waitTime * 1000;

            while (timeleft > 0) {
                long start = System.currentTimeMillis();

                // Create a local PCM and locate the channel with the supplied
                // channelName and applicationName. If there is more than one
                // match, pick up the first one.

                pcm = ChannelManagerFinder.getPrimaryChannelManager(null);

                long channelids[] = pcm.getChannelList(channelName, 
                                                       applicationName);

                for (int i = 0; i < channelids.length; i++) {

                    // Look for a good channel

                    boolean goodChannel = true;

                    channel = pcm.getChannel(channelids[i]);

                    // Check data start time. If it's in the past, it's 
		    // not a good channel.  Otherwise, if the data start 
		    // time has been specified, set waitTime accordingly.
                    // If the data start time has not been specified, waitTime 
		    // decreases by timeconsumed.

                    int timeconsumed = (int) ((waitTime * 1000 - timeleft) 
                                              / 1000);
                    Date starttime = channel.getDataStartTime();

                    if (starttime != null) {            // dataStartTime set

			// allow a 4 minute fudge factor to allow for lack 
			// of clock synchronization

                        if (((int)((starttime.getTime() - 
			    new Date().getTime()) / 1000) + 240) > 0) {

			    // wait for 5 mins beyond the set time.
                            waitTime = (int) (((starttime.getTime() - 
				new Date().getTime()) / 1000) + 300);  

                            if (verbose) {
                                System.out.println("channel " + channelName 
                                    + ":" + i + " waittime: " + waitTime);
                            } 
                        } else {
                            goodChannel = false;        // wait some more
                            waitTime -= timeconsumed;

                            if (verbose) {
                                System.out.println(
				    "channel " + channelName + ":" + 
				    i + " sender start time " + 
				    starttime + " is earlier than this " +
				    "machine's current time" + (new Date()));
                            }
                        }
                    } else {
                        waitTime -= timeconsumed;

                        if (waitTime <= 0) {
                            waitTime = DEFAULT_WAIT2_TIME;
                        } 
                        if (verbose) {
                            System.out.println("channel " + channelName + 
			        ":" + i + " start time is not specified");
                        } 
                    }
                    if (goodChannel) {

                        // Extract the transportProfile and check 
			// the transport name.

                        tp = channel.getTransportProfile();

                        if (!(tp.getName().equals(DEFAULT_TRANSPORT_NAME)) && 
			    !(tp.getName().equals(TRAM_TRANSPORT_NAME)) && 
			    !(tp.getName().equals(LRMP_TRANSPORT_NAME))) {

                            updateUser("Invalid transport name.", true);
                            System.out.println("channel " + channelName + ":" +
				i + " Invalid transport name: " + 
				tp.getName());

                            goodChannel = false;
                        }
                    }
                    if (goodChannel) {
                        if ((tp.getName().equals(TRAM_TRANSPORT_NAME)) && 
			    (uport != 0)) {

                            ((TRAMTransportProfile) tp).setUnicastPort(uport);
                        }
                        if (tp.isUsingAuthentication() == true) {
                            tp.setAuthenticationSpecFileName(
				authenticationSpecFileName);
                            tp.setAuthenticationSpecPassword(
				authenticationPassword);

                            if (verbose) {
                                System.out.println("channel " + 
				    channelName + ":" + i + 
				    " Setting FileName as " + 
				    authenticationSpecFileName);
                            }
                        }



                        if (headonly) {
                            createSocket(tp, TransportProfile.REPAIR_NODE);
                        } else {
                            createSocket(tp, TransportProfile.RECEIVER);
                        }
                        if (verbose) {
                            System.out.println("Located " + channelName + "/" 
                                               + applicationName + " in " 
                                               + timeconsumed + " seconds");
                        } 

                        return;
                    }
                }

                Thread.sleep(1000);

                timeleft -= (System.currentTimeMillis() - start);
            }

            probArea = 9;

            updateUser("Timeout locating " + channelName + "/" 
                       + applicationName + ".", true);
        } catch (Exception e) {
            probArea = 9;

            System.out.println(e);
            e.printStackTrace();
            updateUser("Error in locating " + channelName + "/" 
                       + applicationName + ".", true);
        }
    }

    /*
     * Private method to extract a sequence of file names from a string
     * and return them as an array of strings.
     * 
     * @return
     *
     * @see
     */
    private String[] extractSendFiles() {
        String allfiles = properties.getProperty("slinger.send");
        StringTokenizer st = new StringTokenizer(allfiles);
        String[] files = new String[st.countTokens()];

        for (int i = 0; st.hasMoreTokens(); i++) {
            files[i] = st.nextToken();
        }

        return files;
    }

    /*
     * Private method to create the TransportProfile using the supplied
     * Multicast address and port. The TTL value is also set.
     * 
     * @see
     */
    private void createTransportProfile() {
        try {
            if (transportName.equals(DEFAULT_TRANSPORT_NAME)) {
                tp = (TransportProfile) new UMTransportProfile(mcastAddress, 
                        port);

                ((UMTransportProfile) tp).setDataRate((long) speed);
            } else if (transportName.equals(TRAM_TRANSPORT_NAME)) {
                tp = (TransportProfile) new TRAMTransportProfile(mcastAddress, 
                        port);

                ((TRAMTransportProfile) tp).setMaxDataRate((long) speed);
                ((TRAMTransportProfile) tp).setUnicastPort(uport);
            } else if (transportName.equals(LRMP_TRANSPORT_NAME)) {
                tp = (TransportProfile) new LRMPTransportProfile(mcastAddress, 
                        port);

                ((LRMPTransportProfile) tp).setMaxDataRate((long) speed);
            } else {
                throw new InternalError();
            }

            tp.setTTL(ttl);
            tp.setOrdered(true);

            if (authenticationSpecFileName != null) {
                if (verbose) {
                    System.out.println("Setting Authentication fields");
                } 

                tp.enableAuthentication();
                tp.setAuthenticationSpecFileName(authenticationSpecFileName);
                tp.setAuthenticationSpecPassword(authenticationPassword);
            }
        } catch (InvalidMulticastAddressException e) {      // Cannot occur.
            throw new ImpossibleException(e);
        } catch (RMException e) {                         // Cannot occur.
            throw new ImpossibleException(e);
        } catch (IOException e) {                           // Cannot occur.
            throw new ImpossibleException(e);
        }
    }

    /*
     * Private method to create the RMPacketSocket using
     * a Transport profile.
     * 
     * @param tp
     * @param sendReceive
     *
     * @see
     */
    private void createSocket(TransportProfile tp, int sendReceive) {
        try {
            ms = channel.createRMPacketSocket(tp, sendReceive);

            /*
             * Now check if cipher is being used. If so, add the
             * cipher part to the socket.
             */


        } catch (RemoteException re) {

        // can't happen yet...

        }
        catch (Exception se) {
            se.printStackTrace();
            updateUser("Error in setting up the file transfer.", true);
        }
    }

    /*
     * Private method that carries out the duties of a sender.
     * 
     * @see
     */
    private void SenderPart() {

        // Create a Dialog to graphically show the progress
        // 
        // (sender ? "Sending " : "Receiving ")
        // Loop and send all the files to be sent.

        for (int i = 0; i < sendFileArray.length; ++i) {
            File tmpfile = new File(sendFileArray[i]);

            sendFile = tmpfile.getName();
            fileLength = tmpfile.length();

            // update the user

            if (!bNoGUI) {
                progDialog.setLevel(0);
            } 

            // For all but the first file, delayTime is DEFAULT_DELAY2_TIME

            if (i == 1) {
                delayTime = DEFAULT_DELAY2_TIME;
            } 

            // Delay the start of the data transmission by delayTime seconds.

            if (verbose) {
                System.out.println("Delaying sending by " + delayTime 
                                   + " seconds...");
            }

            updateUser("Delaying start by " + delayTime + " seconds.", false);
            waitForDelayTime();

            startTime = System.currentTimeMillis();
            sequenceNumber = 0;

            // Send the header packet. The sender packet contains a host of 
	    // useful information such as the name of the file being sent, 
	    // its size and the maximum time between two packets that a 
	    // receiver should wait.


            updateUser("Sending " + sendFile + "...", false);
            sendHeaderPacket();
            if (!bNoGUI) {
                progDialog.setMode(true, false, false);
            } 

            if (verbose) {
                System.out.println("Header packet sent...");
            }

            // Create a Dialog to graphically show the progress
            // 
            // (sender ? "Sending " : "Receiving ")
            // If the length of the file to be sent is not 0 (0 length may be 
	    // used just to create a file), send all the Datapackets.

            if (fileLength != 0) {
                sendDataPackets(i);
            } 

            endTime = System.currentTimeMillis() 
                      - 1000;       // 1 sec subtracted because

            // the progress dialog sleeps for 1 sec showing the 100% mark;
            // useful when small files are sent across as otherwise the
            // progress dialog is hardly visible

            if (!aborted) {
                updateUser("Sent file " + sendFile + " of size " + fileLength 
                           + " bytes in " + (endTime - startTime) / 1000.0 
                           + " seconds.", false);
                System.out.println("Sent file " + sendFile + " of size " 
                                   + fileLength + " bytes in " 
                                   + (endTime - startTime) / 1000.0 
                                   + " seconds.");
            } else {
                updateUser("Transmission aborted.", true);
            }

            try {
                Thread.sleep(3000);
            } catch (Exception e) {}
        }

        // The last packet informs the receiver that all files to be sent
        // have been sent and the session has ended. The last packet is a
        // header packet with sequenceNumber being 0 and header type being 255.

        sendLastPacket();

        // stop advertising the channel

        stopChannel();
    }

    /*
     * Private method used by the sender to sleep for its delayTime.
     * 
     * @see
     */
    private void waitForDelayTime() {
        try {
            Thread.sleep(delayTime * 1000);
        } catch (InterruptedException e) {
        }
    }

    /*
     * Private method used by the sender to send the Header packet. All
     * packets have a length of PACKET_SIZE (518 for this version)
     * bytes. The header packet does not contain any payload
     * (file to be sent). It contains a HEADERPACKET_HEADER_SIZE (18 for
     * this version) bytes * of header followed by
     * PACKET_SIZE - HEADER_PACKET_HEADER_SIZE (500) bytes * of filename.
     * The format of the header packet is as follows :
     * 
     * length of the packet - 2 bytes
     * sequence number - 4 bytes (will be 0)
     * header type - 1 byte (will be 0 for this version)
     * file size - 8 bytes
     * linger time - 2 bytes
     * filename 0 - 1 byte
     * filename - remaining bytes (500 for this version)
     * 
     * @see
     */
    private void sendHeaderPacket() {
        int namelen = sendFile.length();
        byte[] sarr = new byte[PACKET_SIZE];

        // Construct the header for the header packet. The method
        // constructHeader fills in the header in the first
        // HEADERPACKET_HEADER_SIZE (18) bytes of the array sarr.

        constructHeader(sarr, 2 * namelen);

        // Stuff the filename in the Data part of the header packet.

        char[] name = new char[namelen];

        sendFile.getChars(0, namelen, name, 0);

        for (int i = 0, j = 0; i < 2 * namelen; ) {
            sarr[HEADERPACKET_HEADER_SIZE + i++] = (byte) ((int) name[j] 
                    >>> 8);
            sarr[HEADERPACKET_HEADER_SIZE + i++] = (byte) ((int) name[j++] 
                    & 0xff);
        }

        // Make a datagram out of the array sarr and send it.

        sendPacket = new DatagramPacket(sarr, sarr.length);
	/*
	 * The following while loop is added to take of 
	 * the 'NoMembersException' condition. The current suggested 
	 * policy for the application is to try to send the same 
	 * packet after a while until the packet finally gets sent. 
	 */
	boolean errMesgDisplayed = false;
	while (!aborted) {
	    try { 
		ms.send(sendPacket);
		if (errMesgDisplayed) {
		    // clear the message displayed.
		    updateUser("                ", false);
		}
		break;
	    } catch (NoMembersException nme) {
		/*
		 * Sleep for sometime and try to send the
		 * packet again.
		 */
		try {
		    
		    updateUser("Receiver member Count is 0. " +
			       "Will try to send after 1 Second", false);
		    System.out.println("Receiver member Count is 0. " +
				       "Will try to send after 1 Second");
		    errMesgDisplayed = true;
		    Thread.sleep(1000);
		} catch (InterruptedException ie) { 
		}
		continue;
	    } catch (IOException e) {
		/*
		 * Since this exception is error related, just
		 * print the error condition and fall thru
		 * close() and return from this method.
		 */
		updateUser(
	            "IO error encountered while trying " +
		    " to send packet. Aborting...",
		    true);
		break;
            } catch (RMException e) {
		/*
		 * Since this exception is error related, just
		 * print the error condition and fall thru
		 * close() and return from this method.
		 */
		updateUser(
		  "JRMS exception encountered while trying to send packet. " +
		       "Aborting...", true);
		break;
	    }
	}
    }

    /*
     * Private method to send the Data packets by the sender.
     * The Data packets have the following format :
     * 
     * length of the packet - 2 bytes
     * sequence number - 4 bytes
     * date - remaining bytes (512 in this version)
     * 
     * @param nfile
     *
     * @see
     */
    private void sendDataPackets(int nfile) {

        // Open the file to be sent.

        try {
            in = new FileInputStream(sendFileArray[nfile]);
        } catch (FileNotFoundException e) {
            updateUser(sendFileArray[nfile] + " not found. Aborting...", 
                       true);
        }

        byte[] sarr = new byte[PACKET_SIZE];
        int bytesread = 0;
        long total = 0;

        // Successively read PAYLOAD_SIZE (512) bytes of data from the file
        // and send them as datagrams with suitable headers.

	try { 
	    while (((bytesread = in.read(sarr, DATAPACKET_HEADER_SIZE, 
		PAYLOAD_SIZE)) != -1) && !aborted) {

		constructHeader(sarr, bytesread);
	    
		sendPacket = new DatagramPacket(sarr, sarr.length);
		/*
		 * The following inner while loop is added to take of 
		 * the 'NoMembersException' condition. The current suggested 
		 * policy for the application is to try to send the same 
		 * packet after a while until the packet finally gets sent. 
		 */
		while (!aborted) {
		    try {
			ms.send(sendPacket);
			updateUser("             ", false);
			break;
		    } catch (NoMembersException nme) {
			/*
			 * Sleep for sometime and try to send the
			 * packet again.
			 */
			try {
			    updateUser("Receiver member Count is 0. " +
				     "Will try to send after 1 Second", false);
			    System.out.println("Receiver member Count is 0. " +
					    "Will try to send after 1 Second");
			    Thread.sleep(1000);
			} catch (InterruptedException ie) { 
			    /*
			     * No operation... just try to send again
			     */
			}
			// try again...
			continue;
		    } catch (RMException e) {
			/*
			 * Since this exception is error related, just
			 * print the error condition and fall thru
			 * close() and return from this method.
			 */
			updateUser(
			 "JRMS exception encountered while sending the file " +
				   "across. Aborting...", true);
		    } catch (IOException e) {
			/*
			 * Since this exception is error related, just
			 * print the error condition and fall thru
			 * close() and return from this method.
			 */
			updateUser("IO Error in reading " + sendFile +
			     " or sending the file across. Aborting...", true);
		    }
		    try {
			in.close();
		    } catch (IOException e) {
			updateUser("IO Error while trying to close " + 
				   sendFile + ". Aborting...", true);
		    }
		    return;
		}

		total += bytesread;

		if (!bNoGUI) {
		    progDialog.setLevel((int) (((float) total / fileLength) 
					       * 100));
		} 
		if (verbose) {
		    System.out.println("Data packet " + (sequenceNumber - 1) 
				       + " sent...");
		}
		if (bytesread < PAYLOAD_SIZE) {
		    if (!bNoGUI) {
			progDialog.setLevel(100);
		    } 

		    try {
			Thread.sleep(1000);
		    } catch (InterruptedException e) {
		    }
		    
		    break;
                }
	    }
	    // } catch (RMException e) {
            // updateUser("JRMS exception encountered while sending the file " +
	    // "across. Aborting...", true);
        } catch (IOException e) {
            updateUser("IO Error in reading " + sendFile + 
		" or sending the file across. Aborting...", true);
        }
        try {
            in.close();
        } catch (IOException e) {
            updateUser("IO Error while trying to close " + sendFile + 
		". Aborting...", true);
        }
	
    }

    /*
     * Private method to send the Last packet that indicates end of session
     * to the receiver base.
     * The last packet is a header packet with a sequence number of 0 and
     * header type of 255.
     * 
     * @see
     */
    private void sendLastPacket() {
        byte[] sarr = new byte[PACKET_SIZE];

        Util.writeInt(0, sarr, 2);      // Sequence number is 0.

        sarr[6] = (byte) 255;       // Header type is 255.
        sendPacket = new DatagramPacket(sarr, sarr.length);

	/*
	 * The while loop is added to take of the 'NoMembersException'
	 * condition. The current suggested policy for the application
	 * is to try to send the same packet after a while until 
	 * the packet finally gets sent. 
	 */
	while (!aborted) {
	    try {
		ms.send(sendPacket);
		updateUser("             ", true);
		break;
	    } catch (NoMembersException nme) {
		/*
		 * Sleep for sometime and try to send the
		 * packet again.
		 */
		try {
		    updateUser("Receiver member Count is 0. " +
			       "Will try to send after 1 Second", false);
		    System.out.println("Receiver member Count is 0. " +
			       "Will try to send after 1 Second");
		    Thread.sleep(1000);
		} catch (InterruptedException ie) {
		}
		continue;
	    } catch (RMException e) {
		/*
		 * Since these exception are error related, break out of the
		 * while loop.
		 */
		updateUser("JRMS exception encountered while trying to send " +
			   "packet. Aborting...", true);
		break;
	    } catch (IOException e) {
		/*
		 * Since these exception are error related, break out of the
		 * while loop.
		 */
		updateUser("IO error encountered while trying to send packet.", 
		    true);
		break;
	   }
	}
    }

    /*
     * Private method to construct the header of packets being transmitted.
     * 
     * @param sarr
     * @param payloadlen
     *
     * @see
     */
    private void constructHeader(byte[] sarr, int payloadlen) {
        int pktlen = 0;

        // Compute the packet length based on the payload 0 and depending on
        // whether the packet is a header packet or a data packet.

        if (sequenceNumber == 0) {
            pktlen = payloadlen + HEADERPACKET_HEADER_SIZE;
        } else {
            pktlen = payloadlen + DATAPACKET_HEADER_SIZE;
        }

        // Write out the packet length in bytes 0-1.

        Util.writeShort((short) pktlen, sarr, 0);

        // Write out the Sequence number in bytes 2-5.

        Util.writeInt((int) sequenceNumber, sarr, 2);

        if (sequenceNumber == 0) {   // More info to be written if this were the

            // header packet.
            // Write out the header type in byte 6.

            sarr[6] = (byte) 0;

            // Write out the length of the file in bytes 7-14.

            Util.writeLong(fileLength, sarr, 7);

            // Write out the linger time in bytes 15-16.

            Util.writeShort((short) lingerTime, sarr, 15);

            // Write out the length of the filename in byte 17.

            sarr[17] = (byte) (sendFile.length());
        }

        sequenceNumber++;

        if (sequenceNumber > MAX_SEQUENCE_NUMBER) {
            updateUser("Size of " + sendFile 
                       + " changed during transmission. Aborting...", true);
        }
    }

    /*
     * Private method that carries out the duties of a receiver.
     * 
     * @see
     */
    private void ReceiverPart() {
        int i = 0;

        while (true) {

            // update the user

            if (!bNoGUI) {
                progDialog.setMode(false, true, false);
                progDialog.setLevel(0);
            }

            // For all but the first file, waitTime is DEFAULT_WAIT2_TIME

            if (i == 1) {
                waitTime = DEFAULT_WAIT2_TIME;
            } 

            // recvHeaderPacket returns true if the current packet received
            // is the last packet.

            if (recvHeaderPacket()) {
                break;
            } 

            i++;
            startTime = System.currentTimeMillis();

            if (verbose) {
                System.out.println("Header packet received...");
                System.out.println("Expected file is " + sendFile 
                                   + " with a length of " + fileLength 
                                   + " bytes...");
            }
            if (!bNoGUI) {
                progDialog.setMode(false, false, false);
            } 

            updateUser("Receiving " + sendFile + "...", false);

            if (fileLength != 0) {
                recvDataPackets();
            } 

            endTime = System.currentTimeMillis() 
                      - 1000;       // 1 sec subtracted because

            // the progress dialog sleeps for 1 sec showing the 100% mark.
            // useful when small files are sent across as otherwise the
            // progress dialog is hardly visible

            if (!aborted) {
                updateUser("Received file " + sendFile + " of size " 
                           + fileLength + " bytes in " 
                           + (endTime - startTime) / 1000.0 + " seconds.", 
                           false);
            } else {
                updateUser("Reception aborted.", true);
            }

            try {
                Thread.sleep(3000);
            } catch (Exception e) {}
        }
    }

    /*
     * Private method to receive the header packet.
     * The header packet has to be received within 'waitTime' seconds.
     * 
     * @return
     *
     * @see
     */
    private boolean recvHeaderPacket() {
        sequenceNumber = 0;

        boolean received = false;

        try {
            if (ms instanceof UMPacketSocket) {
                ((UMPacketSocket) ms).setSoTimeout(waitTime * 1000);
            } 
        } catch (SocketException e) {
            updateUser("Internal error. Aborting...", true);
        }

        if (verbose) {
            System.out.println("Started wait time of " + waitTime 
                               + " seconds expecting the header packet.");
        }

        updateUser("Waiting for data. Maximum wait time is " + waitTime 
                   + " seconds.", false);

        try {
            recvPacket = ms.receive();
            received = true;
        } catch (InterruptedIOException e) {
        } catch (RMException e) {
            updateUser("JRMS exception encountered while trying to receive " +
		"packet. Aborting...", true);
        } catch (IOException e) {       // Cannot occur.
            updateUser("Internal error. Aborting...", true);
        }

        boolean last = false;

        // If header packet received, extract the name and size of the file
        // to be sent and the linger time between packets.

        if (received) {
            last = (checkReceivedPacket() == PACKET_SIZE + 1);
        } else {
            updateUser("Wait time (" + waitTime 
                       + " seconds) elapsed. No packets received. Aborting...", 
                       true);
        }
        if (last) {
            return last;
        } 

        // Create an empty file to receive the data.

        try {
            out = new FileOutputStream(new File(recvDirectory, sendFile));
        } catch (IOException e) {
            updateUser("Cannot create output file " + sendFile 
                       + " in directory " + recvDirectory + " Aborting...", 
                       true);
        }

        return last;
    }

    /*
     * Private method to receive the data packets.
     * As the data packets have to be received within 'lingerTime' seconds,
     * an auxillary low-priority thread is spawned and that does the actual
     * wait for the packets. The auxillary thread interrupts the main thread,
     * which sleeps for 'lingerTime' seconds, on the arrival of a packet. If
     * no packet arrives withion 'lingerTime' seconds, the main thread wakes
     * up, kills the auxillary reader thread and aborts reception.
     * 
     * @see
     */
    private void recvDataPackets() {
        int payloadlen;
        boolean eof = false;
        long lengthsofar = 0;
        boolean received;

        try {
            if (ms instanceof UMPacketSocket) {
                ((UMPacketSocket) ms).setSoTimeout(lingerTime * 1000);
            } 
        } catch (SocketException e) {
            updateUser("Internal error. Aborting...", true);
        }

        while (true) {
            received = false;

            try {
                recvPacket = ms.receive();
                received = true;
            } catch (InterruptedIOException e) {        // Packet not received.
            } catch (RMException e) {
                updateUser("JRMS exception encountered while trying to " +
		    "receive packet. Aborting...", true);
            } catch (IOException e) {
                updateUser("Internal error. Aborting...", true);
            }

            // If a packet has been received, check if the sequence Number is
            // correct. If not, delete the file and abort reception. Otherwise
            // append the payload to the file. On receiving the last packet,
            // append the payload, close the file and end reception.

            if (received) {
                payloadlen = checkReceivedPacket() 
                             - ((sequenceNumber == 1) 
                                ? HEADERPACKET_HEADER_SIZE 
                                : DATAPACKET_HEADER_SIZE);

                if (verbose) {
                    System.out.println("Data packet " + (sequenceNumber - 1) 
                                       + " received...");
                }

                lengthsofar += payloadlen;

                if (!bNoGUI) {
                    progDialog.setLevel((int)
			(((float) lengthsofar / fileLength) * 100));
                } 
                if (lengthsofar >= fileLength) {
                    if (lengthsofar != fileLength) {
                        new File(sendFile).delete();
                        updateUser("File received not of the required " +
			    "length. Aborting...", true);
                    } else {        // Last packet.
                        try {
                            out.write(recvPacket.getData(), 
                                      DATAPACKET_HEADER_SIZE, payloadlen);

                            if (!bNoGUI) {
                                progDialog.setLevel(100);
                            } 

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }

                            out.close();
                        } catch (IOException e) {
                            updateUser("IO error while trying to write data " +
				"to or closing file " + sendFile + 
				". Aborting...", true);
                        }

                        break;
                    }
                } else {            // Not the last packet.
                    try {
                        out.write(recvPacket.getData(), 
                                  DATAPACKET_HEADER_SIZE, payloadlen);
                    } catch (IOException e) {
                        updateUser("IO error while trying to write data to " +
			    "or closing file " + sendFile + ". Aborting...", 
			    true);
                    }
                }
            } else {
                updateUser("No packets received in the last " + lingerTime 
                           + " seconds. Aborting...", true);
            }
        }
    }

    /*
     * Private method to check the sequence number of all received packets
     * and extract the filename, size and linger time from the header
     * packet.
     * Returns the number of bytes (including the header) in the packet.
     * 
     * @return
     *
     * @see
     */
    private int checkReceivedPacket() {
        byte[] rarr = recvPacket.getData();

        // Read in the sequence number

        long sno = Util.readUnsignedInt(rarr, 2);

        // If last packet (packet number is 0 and header type is 255), return.

        if ((sno == 0) && ((rarr[6] & 0xff) == 255)) {
            if (verbose) {
                System.out.println(
		    "Received last packet indicating end of session...");
            } 

            return PACKET_SIZE + 1;
        }

        // Read in the packet length.

        int pktlen = Util.readUnsignedShort(rarr, 0);

        // Check for valid sequence number.

        if (sno != sequenceNumber) {
            updateUser("Invalid Sequence number " + sno + ". Aborting...", 
		true);
        }

        sequenceNumber = sno;

        // Extract header packet info.

        if (sno == 0) {

            // Check header type.

            if (rarr[6] != 0) {
                updateUser("Invalid header type " + rarr[6] + ".Aborting...", 
                           true);
            }

            // Read in file size.

            fileLength = Util.readLong(rarr, 7);

            // Read in linger time.

            lingerTime = Util.readUnsignedShort(rarr, 15);

            // Read in filename length.

            int fnamelen = (rarr[17] & 0xff);

            // Read in the filename.

            char[] name = new char[fnamelen];

            for (int i = 0, j = 0; i < 2 * fnamelen; ) {
                name[j++] = (char) 
		    (((rarr[HEADERPACKET_HEADER_SIZE + i++] & 0xff) << 8) + 
		    (rarr[HEADERPACKET_HEADER_SIZE + i++] & 0xff));
            }

            sendFile = new String(name);
        }

        sequenceNumber++;

        return pktlen;
    }

    void updateUser(String sMessage, boolean bCritical) {
        if (bCritical == true) {
            if (!bNoGUI) {
                progDialog.setMessage(sMessage);

                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                }

                progDialog.stopAnimation();
                progDialog.setVisible(false);
            } else {
                System.out.println(sMessage);
            }

            goBack = true;
        } else {
            if (!bNoGUI) {
                progDialog.setMessage(sMessage);
            } else {
                System.out.println(sMessage);
            }
        }

    // if (logFileName.length() != 0) {
    //     try {
    //         logFile.write(sMessage, 0, sMessage.length());
    //         logFile.write(ENTER);
    //     } catch(java.io.IOException e) {
    //     }
    // }

    }

    void setAborted() {
        aborted = true;
    }

    /*
     * Method to check if a string has any char apart from all White space
     * chars. A string with all white space chars (or length 0) is considered
     * an empty string for the purpose of deciding if a TextField is filled
     * or not.
     * 
     * @param s
     *
     * @return
     *
     * @see
     */
    boolean isEmpty(String s) {
        int len1 = 0;

        for (int i = 0; i < s.length(); ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                len1++;
            } 
        }

        if (len1 > 0) {
            return false;
        } 

        return true;
    }

    private SendDialog progDialog;
    private String[] args;
    private boolean goBack;     // a true value indicates that a 
				// usage / input error has been

    // detected and that control has to go back to the GUI.

    private boolean aborted = false;
    private Cursor waitCursor = new Cursor(Cursor.WAIT_CURSOR);
    private Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
    private int probArea = 0;   // 0 indciates no errors. 
				// A non-zero value indicates the field

    // which is erroneous.

    private PrimaryChannelManager pcm;
    private Channel channel;
    private Properties properties;
    private TransportProfile tp;
    private RMPacketSocket ms;
    private DatagramPacket sendPacket;
    private DatagramPacket recvPacket;
    private FileInputStream in;
    private FileOutputStream out;
    private String channelName;
    private String applicationName;
    private boolean sender;
    private boolean receiver;
    private boolean headonly = false;
    private String sendFile;
    private String[] sendFileArray;
    private String recvDirectory;
    private FileWriter logFile;
    private String logFileName;
    private InetAddress mcastAddress;
    private int port;
    private int uport;
    private String authenticationSpecFileName = null;
    private String authenticationPassword = null;
    private String cipherSpecFileName = null;
    private String cipherPassword = null;
    private byte ttl;
    private String configFile;
    private long sequenceNumber;
    private long fileLength;
    private int delayTime = -1;
    private int lingerTime = -1;
    private int waitTime = -1;
    private int speed = -1;
    private String transportName;
    private boolean verbose;
    private long startTime;
    private long endTime;
    private boolean bNoGUI = false;
    private static final int DEFAULT_DELAY_TIME = 60;       // seconds
    private static final int DEFAULT_DELAY2_TIME = 3;       // seconds
    private static final int DEFAULT_LINGER_TIME = 60;      // seconds
    private static final int DEFAULT_WAIT_TIME = 300;       // seconds
    private static final int DEFAULT_WAIT2_TIME = 5;        // seconds

    // Data rate fixed at 10000 bytes/sec as default.

    private static final int DEFAULT_SPEED = 10000;         // bytes/sec
    private static final String DEFAULT_CONFIG_FILE = "slingerProperties";
    private static final String DEFAULT_TRANSPORT_NAME = 
        "Unreliable Multicast Transport";
    private static final String LRMP_TRANSPORT_NAME = 
        "LRMP V"+LRMP_INFO.VERSION;
    private static final String TRAM_TRANSPORT_NAME =
    	"TRAM V"+TRAM_INFO.VERSION;
    private static final String DEFAULT_CHANNEL_NAME = "slingerChannel";
    private static final String DEFAULT_APPLICATION_NAME = 
        "slingerApplication";
    private static final String version = "Slinger Version 8.0";

    // The following sizes are in bytes

    private static final byte DATAPACKET_HEADER_SIZE = 6;
    private static final byte HEADERPACKET_HEADER_SIZE = 18;
    private static final int PAYLOAD_SIZE = 1400;
    private static final int PACKET_SIZE = DATAPACKET_HEADER_SIZE 
                                           + PAYLOAD_SIZE;
    private static final long MAX_SEQUENCE_NUMBER = (long) (Math.pow(2, 32) 
            - 1);
    private static final long MAX_FILE_SIZE = (long) (MAX_SEQUENCE_NUMBER 
            * PAYLOAD_SIZE);
    private static final int MAX_FILENAME_LEN = 
        (PACKET_SIZE - HEADERPACKET_HEADER_SIZE) / 2;

}
