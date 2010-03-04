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
		if (line.equals("count=" + stopCount)) {
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
