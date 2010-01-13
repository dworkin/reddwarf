/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.impl.sharedutil.logging;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

/**
 * Defines a class that allows the logging configuration to be specified by a
 * URL and periodically rereads the configuration if it has changed. <p>
 *
 * This class recognizes the following {@link LogManager} configuration
 * properties:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>{@value #CONFIG_FILE_PROPERTY}</b> <br>
 *	<i>Default:</i> {@code
 *			file://localhost/${java.home}/lib/logging.properties}
 * <dd style="padding-top: .5em">
 *	Specifies the URL for the logging configuration.  The URL is parsed
 *	using the context {@code file://localhost/}, so the value will be
 *	parsed correctly as a file on the local host if specified with forward
 *	slashes. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #RESAMPLE_INTERVAL_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #RESAMPLE_INTERVAL_DEFAULT}
 * <dd style="padding-top: .5em">
 *	Specifies the interval in milliseconds between times that this class
 *	checks if the contents of the logging configuration URL have
 *	changed. <p>
 *
 * </dl> <p>
 *
 * You can use this class by specifying its class name either as the value of
 * the {@code config} logging property, or as the value of the {@code
 * java.util.logging.config.class} system property, as documented by the {@link
 * LogManager} class.  You need to specify the system property if you want to
 * use a URL for the logging configuration file. <p>
 *
 * For example, to use this class to check for changes to the configuration
 * every 10 seconds, put the following text in the logging configuration file:
 *
 * <pre>
 *   .level=INFO
 *   handlers=java.util.logging.ConsoleHandler
 *   config=com.sun.sgs.impl.sharedutil.logging.ResamplingUrlLogConfiguration
 *   com.sun.sgs.impl.sharedutil.logging.ResamplingUrlLogConfiguration.interval=10000
 * </pre> <p>
 *
 * To specify the location of the logging configuration file using the HTTP URL
 * {@code http://myhost/logging.config}, put the following two system property
 * settings on the command line:
 *
 * <pre>
 *   -Djava.util.logging.config.class=com.sun.sgs.impl.sharedutil.logging.ResamplingUrlLogConfiguration
 *   -Djava.util.logging.config.file=http://myhost/logging.config
 * </pre>
 *
 * This class uses the {@link Logger} named {@code
 * com.sun.sgs.impl.sharedutil.logging.ResamplingUrlLogConfiguration} to log
 * information at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#INFO INFO} - Rereading the configuration file
 * </ul>
 */
public class ResamplingUrlLogConfiguration {

    /** The logger for this class. */
    private static final Logger logger =
	Logger.getLogger(ResamplingUrlLogConfiguration.class.getName());

    /** The property that specifies the configuration file. */
    public static final String CONFIG_FILE_PROPERTY =
	"java.util.logging.config.file";

    /** The default configuration file. */
    private static final String CONFIG_FILE_DEFAULT =
	System.getProperty("java.home") + "/lib/logging.properties";

    /** The property that specifies the resample interval. */
    public static final String RESAMPLE_INTERVAL_PROPERTY =
	"com.sun.sgs.impl.sharedutil.logging.ResamplingUrlLogConfiguration" +
	".interval";

    /** The default resample interval. */
    public static final long RESAMPLE_INTERVAL_DEFAULT = 30000;

    /** The context for parsing the configuration file URL. */
    private static final URL configFileContext;
    static {
	try {
	    configFileContext = new URL("file://localhost/");
	} catch (MalformedURLException e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

    /** Lock for setting the initialized field. */
    private static final Object lock = new Object();

    /**
     * Set to true by the constructor to insure that only one thread is created
     * to resample the configuration.
     */
    private static boolean initialized;

    /** The resample interval. */
    private static long resampleInterval = RESAMPLE_INTERVAL_DEFAULT;

    /**
     * The modification time reported by the URL when it was last read, or 0 if
     * the configuration has not been read.
     */
    private static long lastRead = 0;

    /** The last exception printed, or null. */
    private static String lastException = null;

    /** * Creates an instance of this class. */
    public ResamplingUrlLogConfiguration() {
	init();
    }
    
    /**
     * Initialize this class.
     */
    protected void init() {
        synchronized (lock) {
	    if (initialized) {
		return;
	    }
	    initialized = true;
	    boolean done = false;
	    try {
		readConfiguration();
		resampleLoop();
		done = true;
	    } catch (Exception e) {
		e.printStackTrace();
	    } finally {
		if (!done) {
		    initialized = false;
		}
	    }
	}
    }

    /**
     * Runs a thread that checks the configuration and rereads it if it
     * changes.
     */
    private static void resampleLoop() {
	Thread t = new Thread(
	    new Runnable() {
		public void run() {
		    while (true) {
			long last = System.currentTimeMillis();
			long when = last + resampleInterval;
			long wait;
			while ((wait = when - System.currentTimeMillis()) > 0)
			{
			    try {
				Thread.sleep(wait);
			    } catch (InterruptedException e) {
			    }
			}
			readConfiguration();
		    }
		}
	    },
	    "ResamplingUrlLogConfiguration.resampleLoop");
	t.setDaemon(true);
	t.start();
    }

    /**
     * Rereads the configuration if it has changed since the last time it was
     * read.
     */
    private static void readConfiguration() {
	try {
	    String configFile = System.getProperty(
		CONFIG_FILE_PROPERTY, CONFIG_FILE_DEFAULT);
	    URL url = new URL(configFileContext, configFile);
	    URLConnection connection = url.openConnection();
	    connection.setUseCaches(false);
	    connection.connect();
	    long lastModified = connection.getLastModified();
	    if (lastModified > lastRead) {
		lastRead = lastModified;
		InputStream in = null;
		try {
		    in = new BufferedInputStream(connection.getInputStream());
		    LogManager.getLogManager().readConfiguration(in);
		    updateResampleInterval();
		    lastException = null;
		    logger.log(
			Level.INFO, "Read configuration {0}", configFile);
		} finally {
		    if (in != null) {
			try {
			    in.close();
			} catch (IOException e) {
			}
		    }
		}
	    }
	} catch (Exception e) {
	    printException("Error reading logging configuration file: " + e);
	}
    }

    /** Updates the resampleInterval field from the log manager property. */
    private static void updateResampleInterval() {
	String resampleIntervalProperty =
	    LogManager.getLogManager().getProperty(RESAMPLE_INTERVAL_PROPERTY);
	if (resampleIntervalProperty != null) {
	    try {
		resampleInterval = Long.parseLong(resampleIntervalProperty);
	    } catch (NumberFormatException e) {
		printException(
		    "Problem parsing the " + RESAMPLE_INTERVAL_PROPERTY +
		    " logging configuration property: " + e);
	    }
	}
    }

    /** Prints the exception message if different from the previous message. */
    private static void printException(String message) {
	if (!message.equals(lastException)) {
	    System.err.println(message);
	    lastException = message;
	}
    }
}
