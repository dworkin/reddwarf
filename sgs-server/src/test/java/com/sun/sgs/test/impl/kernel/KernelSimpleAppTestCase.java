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
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import junit.framework.TestCase;
import org.junit.runner.RunWith;

/**
 * Provides utilities for writing simple end-to-end tests of the server by
 * calling the Kernel with a simple application class.  Test classes should
 * extend this class, and use its methods to implement tests that run
 * applications as external processes.
 */
@RunWith(FilteredJUnit3TestRunner.class)
abstract class KernelSimpleAppTestCase extends TestCase {

    /** The number of milliseconds to let the kernel run */
    static final long RUN_PROCESS_MILLIS = 10000;

    /** Application configuration properties */
    Properties config;

    /** Logging configuration properties */
    Properties logging;

    /** System properties */
    Properties system;

    /** Creates the test. */
    KernelSimpleAppTestCase(String name) {
	super(name);
    }

    /** Create the application directory and various properties */
    protected void setUp() throws IOException {
	/* Create the application directory */
	File dir = File.createTempFile("SimpleApp", "dir");
	dir.delete();
	dir.mkdir();
	/* Create the DataStore subdirectory */
	new File(dir, "dsdb").mkdir();
	/* Create application configuration properties */
	config = createProperties(
	    StandardProperties.APP_LISTENER,
                "com.sun.sgs.test.impl.kernel.SimpleApp",
	    StandardProperties.APP_NAME, "SimpleApp",
            com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY,
                String.valueOf(getPort()),
	    "com.sun.sgs.app.root", dir.toURI().toURL().getPath());
	/* Create logging properties to log at WARNING or higher */
	logging = createProperties(
	    ".level", "WARNING",
	    "handlers", "java.util.logging.ConsoleHandler",
	    "java.util.logging.ConsoleHandler.formatter",
	    "java.util.logging.SimpleFormatter",
	    "java.util.logging.ConsoleHandler.level", "WARNING");
	/* Create system properties */
	system = createProperties();
    }

    /** Returns the port to use for this application. */
    abstract int getPort();

    /**
     * Creates a process builder for the application, using the config,
     * logging, and system properties.
     */
    ProcessBuilder createProcessBuilder() throws Exception {
	/* Create the application configuration file */
	File configFile = File.createTempFile("SimpleApp", "config");
	OutputStream configOut = new FileOutputStream(configFile);
	config.store(configOut, null);
	configOut.close();
	/* Create the logging configuration file */
	File loggingFile = File.createTempFile("SimpleApp", "logging");
	OutputStream loggingOut = new FileOutputStream(loggingFile);
	logging.store(loggingOut, null);
	loggingOut.close();
	/* Create the command line for running the Kernel */
	List<String> command = new ArrayList<String>();
	command.add(System.getProperty("java.home") + "/bin/java");
	command.add("-cp");
	command.add(System.getProperty("java.class.path"));
	command.add("-Djava.util.logging.config.file=" + loggingFile);
	command.add("-Djava.library.path=" +
		    System.getProperty("java.library.path"));
	Enumeration<?> names = system.propertyNames();
	while (names.hasMoreElements()) {
	    Object name = names.nextElement();
	    command.add("-D" + name + "=" + system.get(name));
	}
	command.add("com.sun.sgs.impl.kernel.Kernel");
	command.add(configFile.getPath());
	/* Return the process builder */
	return new ProcessBuilder(command);
    }

    /**
     * Runs a process specified by a ProcessBuilder, checking standard input
     * and standard error to determine if the process succeeded.
     */
    static abstract class RunProcess {
	private final ProcessBuilder processBuilder;
	private final long stop;
	private boolean done;
	private Throwable exception;
	/** Processes input from standard input. */
	class InputThread extends Thread {
	    private final BufferedReader in;
	    InputThread(InputStream in) {
		this.in = new BufferedReader(new InputStreamReader(in));
	    }
	    public void run() {
		try {
		    while (true) {
			String line = in.readLine();
			if (line == null) {
			    throw new EOFException();
			} else {
			    handleLine(line);
			}
		    }
		} catch (Throwable t) {
		    failed(t);
		} finally {
		    done();
		}
	    }
	    void handleLine(String line) {
		System.err.println("stdin: " + line);
		handleInput(line);
	    }
	}
	/** Processes input from standard error. */
	class ErrorThread extends InputThread {
	    ErrorThread(InputStream err) {
		super(err);
	    }
	    void handleLine(String line) {
		System.err.println("stderr: " + line);
		handleError(line);
	    }
	}
	/**
	 * Creates an instance for the specified ProcessBuilder that will run
	 * the process no longer than the specified timeout.
	 */
	RunProcess(ProcessBuilder processBuilder, long timeout) {
	    this.processBuilder = processBuilder;
	    this.stop = System.currentTimeMillis() + timeout;
	    System.err.println("Command: " + processBuilder.command());
	}
	/** Notes that the process is done. */
	synchronized void done() {
	    done = true;
	    notifyAll();
	}
	/** Notes that the process failed. */
	synchronized void failed(Throwable t) {
	    if (exception == null) {
		exception = t;
	    }
	}
	/** Runs the process. */
	void run() throws Exception {
	    Process process = processBuilder.start();
	    try {
		new InputThread(process.getInputStream()).start();
		new ErrorThread(process.getErrorStream()).start();
		synchronized (this) {
		    while (!done) {
			long wait = stop - System.currentTimeMillis();
			if (wait <= 0) {
			    break;
			}
			try {
			    wait(wait);
			} catch (InterruptedException e) {
			    break;
			}
		    }
		}
		if (exception instanceof Exception) {
		    throw (Exception) exception;
		} else if (exception instanceof Error) {
		    throw (Error) exception;
		} else if (exception != null) {
		    throw new Exception("Unexpected exception: " + exception);
		} else if (!done) {
		    fail("Process timed out");
		}
	    } finally {
		process.destroy();
		process.waitFor();
	    }
	}
	/** Handles a line of input from standard input. */
	abstract void handleInput(String line);
	/** Handles a line of input from standard error. */
	abstract void handleError(String line);
    }
}
