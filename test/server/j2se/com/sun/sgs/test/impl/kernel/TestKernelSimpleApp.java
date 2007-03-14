/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import junit.framework.TestCase;

/**
 * Provide a simple end-to-end test of the server by calling the Kernel with a
 * simple application class.
 */
public class TestKernelSimpleApp extends TestCase {

    /** The number of milliseconds to let the kernel run */
    private static final long RUN_PROCESS_MILLIS = 10000;

    /** Run a simple application */
    public void testRunSimpleApp() throws Exception {
	/* Create the application directory */
	File dir = File.createTempFile("SimpleApp", "dir");
	dir.delete();
	dir.mkdir();
	/* Create the DataStore subdirectory */
	new File(dir, "dsdb").mkdir();
	/* Create the application properties file */
	File props = File.createTempFile("SimpleApp", "properties");
	Writer out = new FileWriter(props);
	out.write("com.sun.sgs.app.listener =" +
		  " com.sun.sgs.test.impl.kernel.SimpleApp\n" +
		  "com.sun.sgs.app.name = SimpleApp\n" +
		  "com.sun.sgs.app.port = 33333\n" +
		  "com.sun.sgs.app.root = " + dir.toURI().toURL().getPath());
	out.close();
	/* Create a logging file at WARNING or higher */
	File logging = File.createTempFile("SimpleApp", "logging");
	out = new FileWriter(logging);
	out.write(".level = WARNING\n" +
		  "handlers = java.util.logging.ConsoleHandler\n" +
		  "java.util.logging.ConsoleHandler.formatter =" +
		  " java.util.logging.SimpleFormatter\n" +
		  "java.util.logging.ConsoleHandler.level = WARNING");
	out.close();
	/* Run the Kernel */
	ProcessBuilder pb = new ProcessBuilder(
	    "java",
	    "-cp", System.getProperty("java.class.path"),
	    "-Djava.util.logging.config.file=" + logging,
	    "-Djava.library.path=" + System.getProperty("java.library.path"),
	    "com.sun.sgs.impl.kernel.Kernel",
	    props.toURI().toURL().getPath());
	new RunProcess(pb, RUN_PROCESS_MILLIS) {
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

    /**
     * Runs a process specified by a ProcessBuilder, checking standard input
     * and standard error to determine if the process succeeded.
     */
    private static abstract class RunProcess {
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
		handleInput(line);
	    }
	}
	/** Processes input from standard error. */
	class ErrorThread extends InputThread {
	    ErrorThread(InputStream err) {
		super(err);
	    }
	    void handleLine(String line) {
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
