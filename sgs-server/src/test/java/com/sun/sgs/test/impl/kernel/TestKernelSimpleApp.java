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
