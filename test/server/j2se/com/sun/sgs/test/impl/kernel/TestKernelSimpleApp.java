/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

import java.io.BufferedReader;
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
		  "com.sun.sgs.app.root = " + dir);
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
	    props.getAbsolutePath());
	Process process = pb.start();
	/* Copy process output to System.err */
	new Copy(process.getInputStream(), "stdout: ");
	Copy err = new Copy(process.getErrorStream(), "stderr: ");
	/* Let the process run */
	Thread.sleep(RUN_PROCESS_MILLIS);
	process.destroy();
	process.waitFor();
	/* Check for error output */
	assertEquals("Error output", null, err.lastLine());
    }

    /**
     * Copies input from an input stream to System.err, applying the specified
     * prefix to each line, and keeping track of the last line output.
     */
    private static final class Copy extends Thread {
	private final InputStream in;
	private final String prefix;
	private String lastLine;
	Copy(InputStream in, String prefix) {
	    this.in = in;
	    this.prefix = prefix;
	    start();
	}
	synchronized String lastLine() {
	    return lastLine;
	}
	public void run() {
	    try {
		BufferedReader reader =
		    new BufferedReader(new InputStreamReader(in));
		while (true) {
		    String line = reader.readLine();
		    if (line == null) {
			break;
		    }
		    synchronized (this) {
			lastLine = line;
		    }
		    System.err.println(prefix + line);
		}
	    } catch (Throwable t) {
		System.err.println(t);
	    }
	}
    }
}
