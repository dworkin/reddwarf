/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.system.stop;

import com.sun.sgs.management.KernelMXBean;
import com.sun.sgs.system.BootEnvironment;
import com.sun.sgs.system.SubstitutionProperties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

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
     * specify a set of configuration properties that were used to startup
     * the running Project Darkstar Server.  If no argument is given on the 
     * command line, the filename is assumed to be at the location specified 
     * by the system resource {@link BootEnvironment#SGS_BOOT}.
     * <p>
     * A shutdown command is sent to the running Project Darkstar Server
     * by connecting to the JMX server on the localhost at the port specified
     * by the {@link BootEnvironment#JMX_PORT} property in the configuration,
     * or the value of {@link BootEnvironment#DEFAULT_JMX_PORT} if no value
     * is given for the property. The {@code KernelMXBean.requestShutdown()}
     * method is invoked to start shutdown. Note that shutdown may not
     * complete until after {@code main()} has already returned.
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

        // get the JMX port and make the connection
        String port = properties.getProperty(BootEnvironment.JMX_PORT,
                                             BootEnvironment.DEFAULT_JMX_PORT);
        JMXServiceURL url =
            new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" +
                              port + "/jmxrmi");
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

        // call the requestShutdown() interface on the server
        ObjectName name = new ObjectName(KernelMXBean.MXBEAN_NAME);
        KernelMXBean proxy = JMX.newMXBeanProxy(mbsc, name, KernelMXBean.class);
        proxy.requestShutdown();
    }

}
