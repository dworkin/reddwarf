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
 * SlingerResources.java
 */
package com.sun.multicast.reliable.applications.slinger;

import java.util.ListResourceBundle;

/**
 * A ListResourceBundle containing US English resources for the Slinger classes.
 * 
 * This class should not be used by code outside of the 
 * com.sun.multicast package.
 */
public class SlingerResources extends ListResourceBundle {

    /**
     * Undocumented Method Declaration.
     * 
     * 
     * @return
     *
     * @see
     */
    public Object[][] getContents() {
        return contents;
    }

    static final Object[][] contents = {
        {
            "application", 
            "Name of the application to which the channel belongs."
        }, {
            "load", 
            "Loads properties from the specified Configuration file if " +
	    "it exists and is readable. Existing values if any are " +
	    "destroyed. The configuration file contains a list of " +
	    "properties of the form property=value.\n\nThe following " +
	    "properties can be defined in the configuration file: " +
	    "\n\n\tslinger.transport\n\tslinger.config\n\tslinger." +
	    "channel\n\tslinger.application\n\tslinger.address\n\t" +
	    "slinger.port\n\tslinger.uport\n\tslinger.send\n\tslinger." +
	    "ttl\n\tslinger.delay\n\tslinger.linger\n\tslinger.speed\n\t" +
	    "slinger.receive\n\tslinger.wait\n\tslinger.verbose"
        }, {
            "start", "Starts transmission / reception as the case may be."
        }, {
            "reset", "Resets the values in the GUI to default values."
        }, {
            "exit", "Exits the Slinger application."
        }, {
            "config", 
            "Configuration file from where values other than those " +
	    "specified through the GUI are taken. The configuration " +
	    "file is a convenient way to avoid typing the same values " +
	    "again and again. Values specified through the GUI override " +
	    "those in the configuration file. The default configuration " +
	    "file is 'slingerProperties' in the current directory. " +
	    "For more information on the format of the configuration " +
	    "file and the properties that can be specified, please see " +
	    "the help for 'LOAD FROM CONFIGURATION FILE' button."
        }, {
            "channel", 
            "Name of the channel over which data is sent or received."
        }, {
            "address", 
            "Address of the group with which data transmission / reception " +
	    "is desired. Should be a valid class D (Multicast) address. " +
	    "Class D address can be identified by their first 4 bits " +
	    "being 1110."
        }, {
            "port", 
            "Port for multicast data transmission / reception. Should be " +
	    "an integer in the range 1024 - 65535, both inclusive."
        }, {
            "choice", 
            "Control to choose between data transmission / reception."
        }, {
            "send", 
            "File(s) to be transmitted. Successive files should be " +
	    "separated by whitespace characters. Wild cards not supported. " +
	    "Maximum length of a filename (not pathname) is restricted to " +
	    "250 characters."
        }, {
            "browse", 
            "Provides a list and a File dialog to select files by " +
	    "browsing directories."
        }, {
            "ttl", 
            "Time to live value for the data packets transmitted. " +
	    "Should be an integer in the range 1 - 255, both inclusive. " +
	    "The default value is 1."
        }, {
            "delay", 
            "Time in seconds that the sender waits before starting the " +
	    "transmission of each of the files that are sent. Should be " +
	    "an integer in the range 0 - 2147483647, both inclusive. The " +
	    "default value is 60."
        }, {
            "linger", 
            "Time in seconds that the receiver waits between two successive " +
	    "packets before quitting,  concluding that the transmission has " +
	    "been aborted. The default value is 60."
        }, {
            "speed", 
            "Speed in bytes/sec with which the data is desired to be " +
	    "transmitted. Should be a positive integer. Default value is " +
	    "25, 000 bytes/sec. Note that this value is only an advisory. " +
	    "The data rate may be lesser than the value specified."
        }, {
            "receive", "Directory where the files received are stored. "
        }, {
            "wait", 
            "Time in seconds that the receiver waits for the first packet " +
	    "of a file before quitting,  concluding that the transmission " +
	    "has aborted. Should be an integer in the range 0 - 2147483647, " +
	    "both inclusive. The default value is 300."
        }, {
            "verbose", 
            "Turning on the verbose flag dumps progress information onto " +
	    "the standard output."
        }, {
            "help", 
            "Send File(s) Panel \nSlinger will send the named file(s) " +
	    "to a set of receivers. The filenames may be pathnames. " +
	    "The file(s) must already exist. \nMulticast Address: " +
	    "The multicast address for this transmission. \nMulticast Port: " +
	    "The multicast port for this transmission. \nTTL: " +
	    "The network time-to-live value to use. \nDelay Time: " +
	    "Delay this many seconds before sending data. The default is " +
	    "60 seconds. The inter-file " +
	    "delay time is 3 seconds. \nLinger Time: The linger time " +
	    "tells receivers how long they should wait without receiving " +
	    "a packet before assuming the transmission has been terminated. " +
	    "The default is 60 seconds. \nSpeed: The maximum transmission " +
	    "rate.  Note that this value is only advisory. The actual " +
	    "rate of transmission may be less than the value requested. " +
	    "The default is 25000 bytes/sec. \nTransport Protocol: Use " +
	    "the transport with this name. \n\nReceive File(s) Panel\n" +
	    "Slinger will wait to receive file(s) from a sender and place " +
	    "them in the named directory. The directory must already " +
	    "exist. \nWait Time: The receiver waits this number of " +
	    "seconds without receiving a packet before assuming the " +
	    "transmission has been canceled. The default is 300 seconds " +
	    "(five minutes).\n\nMulticast Settings Panel\nMulticast Channel: " +
	    "The channel name used to help receivers identify this " +
	    "transmission.  The default channel name is slingerChannel. " +
	    "\nMulticast Application:  The application name used to help " +
	    "receivers identify this transmission.  The default application " +
	    "name is slingerApplication. \nUnicast Port: An optional " +
	    "parameter indicating a specific unicast port to use for " +
	    "control messages.\nLog to File: Log messages to the file " +
	    "specified by pathname.  This is useful for debugging.\n" +
	    "Verbose: Provide verbose progress and diagnostic messages.\n\n" +
	    "Security Details Panel (optional)\nAuthenticationSpec: " +
	    "Specifies the location of files containing authentication " +
	    "keys.\nAuthentication Password: This is the password which " +
	    "was specified to the AuthenticationSpec class to create the " +
	    "authenticationSpec files.  Only the sender specifies this " +
	    "password since it is used to protect the sender's private key " +
	    "used for signing messages.  The receiver does not need a " +
	    "password because the receiver uses the sender's public key to " +
	    "verify the signature and the public key need not be " +
	    "protected. \nCipherSpec: Specifies the location of files " +
	    "containing cipher keys.  The sender and all receivers must " +
	    "use copies of the same cipherSpec file. \nCipher Password: " +
	    "This is the password  used by the CipherSpec class to create " +
	    "the cipher spec file.  The sender and all receivers must " +
	    "know this password since the password protects the symmetric " +
	    "key used to encrypt the traffic.\n\nConfiguration Window\nThe " +
	    "Slinger configuration files provide an alternative to " +
	    "specifying settings on the command line. This is a convenient " +
	    "way to avoid typing the same command line arguments again and " +
	    "again. Command line arguments always override settings " +
	    "provided in configuration files. \n\nSlinger looks for a " +
	    "configuration file named slingerProperties.cfg in the current " +
	    "directory when it starts up. If it finds such a file, it " +
	    "parses it. Otherwise, Slinger moves directly to parsing the " +
	    "command line. The configuration file can be created and " +
	    "managed with a text editor.  The configuration file can " +
	    "also be managed by Slinger using  a configuration option in " +
	    "the GUI to save the current settings to the configuration " +
	    "file.  The path to the configuration file can also be " +
	    "changed in the GUI. "
        }, {
            "InvalidBevelStyle", "Invalid BevelStyle: "
        }, {
            "InvalidBevelSize", "Invalid bevel size: "
        }, {
            "InvalidFrame", "Invalid Frame: "
        }, {
            "InvalidDirection", "Invalid direction: "
        }, {
            "InvalidArrowIndent", "Invalid arrow indent: "
        }, {
            "ErrorLoadingImage", "Error loading image {0}"
        }, {
            "InvalidImageStyle", "Invalid image style: "
        }, {
            "ErrorLoadingImageForURL", "Unable to load image for URL {0}"
        }, {
            "InvalidAlignStyle", "Invalid AlignStyle: "
        }, {
            "InvalidVerticalAlignStyle", "Invalid VerticalAlignStyle: "
        }, {
            "ElementAlreadyInMatrix", "Element already in Matrix"
        }, {
            "ElementNotInMatrix", "Element row={0} col={1} is not in matrix"
        }, {
            "RowNotInMatrix", "Row {0} is not in the matrix"
        }, {
            "MustBeGreaterThanCurrentRow", 
            "r must be greater than current row: r={0} current row={1}"
        }, {
            "RowTooLarge", "requested row too large: r={0}"
        }, {
            "InvalidRowNumber", "{0} is not a valid row number"
        }, {
            "InvalidColumnIndex", "Column out of range : "
        }, {
            "InvalidAlignment", "{0} must be either {1}, {2}, or {3}"
        }, {
            "InvalidSelectedRadioButtonIndex", 
            "Invalid SelectedRadioButtonIndex: "
        }, {
            "InvalidCurrentValue", "Invalid current value: "
        }, {
            "InvalidMaxValue", "Invalid max value: "
        }, {
            "InvalidMinValue", "Invalid min value: "
        }, {
            "InvalidSplitType", "Invalid SplitType: "
        }, {
            "NodeAlreadyExists", "Node already exists in tree."
        }, {
            "EmptyStrings", "Empty strings in structure."
        }, {
            "NoRootLevelNode", "Indented nodes with no root level node."
        }, {
            "NoParent", 
            "Node with no immediate parent.  Check indentation for: "
        }, {
            "InvalidTextLocation", "Invalid text location: "
        }, {
            "NotCellObject", "Objects to compare must be Cell instances"
        }, 

    // END "LOCALIZE THIS"

    };

}
