/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

/**
 * Provide a simple end-to-end test of the server by calling the Kernel with a
 * simple application class.
 */
public class TestKernelSimpleApp extends KernelSimpleAppTestCase {

    /** Creates the test. */
    public TestKernelSimpleApp(String name) {
	super(name);
    }

    /** Returns the port to use for this application. */
    int getPort() {
	return 33333;
    }

    /** Run a simple application */
    public void testRunSimpleApp() throws Exception {
	new RunProcess(createProcessBuilder(), RUN_PROCESS_MILLIS) {
	    void handleInput(String line) {
		System.out.println("stdout: " + line);
		if (line.equals("count=3")) {
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
