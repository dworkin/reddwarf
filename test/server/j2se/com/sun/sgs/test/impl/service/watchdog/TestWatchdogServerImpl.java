/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.watchdog;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

public class TestWatchdogServerImpl extends TestCase {
    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerClassName =
	WatchdogServerImpl.class.getName();
    
    /** Directory used for database shared across multiple tests. */
    private static final String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
	"TestWatchdogServerImpl.db";

    /** The port for the watchdog server. */
    private static int WATCHDOG_PORT = 0;

    /** Properties for the watchdog server and data service. */
    private static Properties serviceProps = createProperties(
	StandardProperties.APP_NAME, "TestWatchdogServerImpl",
	DataStoreImplClassName + ".directory", DB_DIRECTORY,
	WatchdogServerClassName + ".port", Integer.toString(WATCHDOG_PORT));

    /** Constructs a test instance. */
    public TestWatchdogServerImpl(String name) {
	super(name);
    }

    /** Test setup. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
        setUp(true);
    }

    protected void setUp(boolean clean) throws Exception {
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
	createDirectory(DB_DIRECTORY);
    }

    public void testConstructor() throws Exception {
	WatchdogServerImpl watchdog = new WatchdogServerImpl(serviceProps);
	System.err.println("watchdog server port: " + watchdog.getPort());
    }

    public void testConstructorNullProperties() throws Exception {
	try {
	    new WatchdogServerImpl(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	Properties properties = createProperties(
	    DataStoreImplClassName + ".directory", DB_DIRECTORY,
	    WatchdogServerClassName + ".port", Integer.toString(WATCHDOG_PORT));
	try {
	    new WatchdogServerImpl(properties);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    public void testConstructorNoDirectory() throws Exception {
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServerImpl",
	    WatchdogServerClassName + ".port", Integer.toString(WATCHDOG_PORT));
	try {
	    new WatchdogServerImpl(properties);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
    
    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /** Creates the specified directory, if it does not already exist. */
    private static void createDirectory(String directory) {
	File dir = new File(directory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
    }
    
    /** Deletes the specified directory, if it exists. */
    private static void deleteDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
    }
}
