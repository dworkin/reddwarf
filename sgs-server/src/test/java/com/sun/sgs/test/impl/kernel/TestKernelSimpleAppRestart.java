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
