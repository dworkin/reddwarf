/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
		if (line.equals("count=3")) {
		    done();
		}
	    }
	    void handleError(String line) {
		failed(
		    new RuntimeException(
			"Unexpected error input: " + line));
	    }
	}.run();
    }
}
