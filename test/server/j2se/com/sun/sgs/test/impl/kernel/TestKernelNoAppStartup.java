/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.StandardProperties;


/**
 * Tests startup cases for running without an application and running with
 * a sub-set of services.
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
    
    public void testNoListener() throws Exception {
        runApp(true, null, false);
    }

    public void testLimitedServices() throws Exception {
        runApp(true, "WatchdogService", false);
    }

    public void testUnknownLimitedService() throws Exception {
        runApp(true, "FooService", true);
    }

    public void testLimitedServicesWithApp() throws Exception {
        runApp(false, "WatchdogService", true);
    }

    /** Utility that runs the server and looks for specific logging output. */
    private void runApp(boolean noListener, String finalService,
                        final boolean shouldFail) throws Exception {
        config.setProperty(StandardProperties.APP_NAME, APP_NAME);
        if (noListener)
            config.setProperty(StandardProperties.APP_LISTENER,
                               StandardProperties.APP_LISTENER_NONE);
        if (finalService != null)
            config.setProperty(StandardProperties.FINAL_SERVICE, finalService);
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
                    
            }
        }.run();
    }

}
