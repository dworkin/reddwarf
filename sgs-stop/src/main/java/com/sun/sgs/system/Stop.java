/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.system;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initiates a shutdown of a running Project Darkstar server.
 */
public final class Stop {
    private static final Logger logger = Logger.getLogger(Stop.class.getName());
    
    /**
     * This class should not be instantiated.
     */
    private Stop() {
        
    }
    
    /**
     * Main-line method that shuts down a running Project Darkstar server.
     * <p>
     * If a single argument is given on the command line, the value of
     * the argument is assumed to be a filename.  This file is used to 
     * specify a set of configuration properties required in order to locate
     * the required components to startup an application in a Project
     * Darkstar container.  If no argument is given on the command line, 
     * the filename is assumed to be at the location specified by the system 
     * resource {@link BootEnvironment.SGS_BOOT}.
     * 
     * @param args optional filename of configuration file
     * @throws Exception if there is a problem
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            logger.log(Level.SEVERE, "Invalid number of arguments");
            throw new IllegalArgumentException("Invalid number of arguments");
        }
        
        //load properties from configuration file
        SubstitutionProperties properties = null;
        if (args.length == 0) {
            properties = BootEnvironment.loadProperties(null);
        } else {
            properties = BootEnvironment.loadProperties(args[0]);
        }
        
        //get shutdown port
        Integer port = Integer.valueOf(
                properties.getProperty(BootEnvironment.SHUTDOWN_PORT));
        
        //connect to shutdown port and initiate shutdown command
        Socket socket = null;
        PrintStream out = null;
        try {
            logger.log(Level.INFO, "Sending " + 
                       BootEnvironment.SHUTDOWN_COMMAND + 
                       " to port " + port + 
                       " on " + InetAddress.getLocalHost());
            socket = new Socket(InetAddress.getLocalHost(), port);
            out = new PrintStream(socket.getOutputStream());
            out.println(BootEnvironment.SHUTDOWN_COMMAND);
            logger.log(Level.INFO, BootEnvironment.SHUTDOWN_COMMAND + 
                       " send successfully");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to initiate shutdown", e);
        } finally {
            if (socket != null) {
                socket.close();
            }
            
            if (out != null) {
                out.close();
            }
        }
    }
}
