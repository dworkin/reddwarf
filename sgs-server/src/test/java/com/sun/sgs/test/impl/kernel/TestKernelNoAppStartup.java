/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.StandardProperties;


/**
 * Tests startup cases for running without an application.
 */
public class TestKernelNoAppStartup extends KernelSimpleAppTestCase {

    // the name of the test application
    private static final String APP_NAME = "TestApp";

    /** Creates the test. */
    public TestKernelNoAppStartup(String name) {
        super(name);
    }

    /** Returns the port to use for this application. */
    int getPort() {
        return 33336;
    }
    
    // We must have a listener in single node
    public void testNoListenerSingleNode() throws Exception {
        runApp(true, "singleNode", true);
    }
    
    // Core server node will succeed whether or not there is a listener
    public void testNoListenerCoreServerNode() throws Exception {
        runApp(true, "coreServerNode", false);
    }
    
    public void testCoreServerNode() throws Exception {
        runApp(false, "coreServerNode", false);
    }
    

    /** Utility that runs the server and looks for specific logging output. */
    private void runApp(boolean noListener, String nodeType,
                        final boolean shouldFail) throws Exception {
        config.setProperty(StandardProperties.APP_NAME, APP_NAME);
        if (noListener) {
            config.remove(StandardProperties.APP_LISTENER);
        }
        config.setProperty(StandardProperties.NODE_TYPE, nodeType);
        
        logging.setProperty(".level", "INFO");
        logging.setProperty("java.util.logging.ConsoleHandler.level", "INFO");
        new RunProcess(createProcessBuilder(), RUN_PROCESS_MILLIS) {
            void handleInput(String line) {}
            void handleError(String line) {
                if (line.equals("INFO: " + APP_NAME + ": non-application " +
                                "context is ready"))
                    done();
                if (line.equals("SEVERE: " + APP_NAME +
				": failed to create services")) {
                    if (shouldFail)
                        done();
                    else
                        failed(new RuntimeException("App failed to start"));
                }
                if (line.endsWith("SEVERE: Missing required property " + "" +
                                  "com.sun.sgs.app.listener for application: " +
                                  APP_NAME)) 
                {
                    if (shouldFail)
                        done();
                    else
                        failed(new RuntimeException("App failed to start"));
                }
            }
        }.run();
    }

}
