/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

/** Test restarting a simple application. */
public class TestKernelSimpleAppRestart extends KernelSimpleAppTestCase {

    /** Creates the test. */
    public TestKernelSimpleAppRestart(String name) {
	super(name);
    }

    /** Returns the port to use for this application. */
    int getPort() {
	return 33335;
    }

    /** Run a simple application */
    public void testRunSimpleApp() throws Exception {
        logging.setProperty(
	    "com.sun.sgs.impl.service.watchdog.server.level", "SEVERE");
	runApp(3);
	runApp(6);
    }

    private void runApp(final int stopCount) throws Exception {
	new RunProcess(createProcessBuilder(), RUN_PROCESS_MILLIS) {
	    void handleInput(String line) {
		System.out.println("stdout: " + line);
		if (line.equals("count=" + stopCount)) {
		    done();
		}
	    }
	    void handleError(String line) {
		System.err.println("stderr: " + line);
		failed(
		    new RuntimeException(
			"Unexpected error input: " + line));
	    }
	}.run();
    }
}
