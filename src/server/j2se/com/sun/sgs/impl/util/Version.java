/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.impl.util.LoggerWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Utility class for obtaining information about the current version. */
public final class Version {

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(Version.class.getName()));

    /** The resource that contains information about the current version. */
    private static final String resource = "com/sun/sgs/sgs.version";

    /** The version string, or null if not found. */ 
    private static String version = null;

    /** This class cannot be instantiated. */
    private Version() {
	throw new AssertionError();
    }

    /**
     * Returns a string describing the current version.
     *
     * @return	a string describing the current version
     */
    public static synchronized String getVersion() {
	if (version == null) {
	    InputStream in = ClassLoader.getSystemResourceAsStream(resource);
	    if (in == null) {
		logger.log(
		    Level.WARNING, "Version resource not found: " + resource);
	    } else {
		BufferedReader reader =
		    new BufferedReader(new InputStreamReader(in));
		try {
		    version = reader.readLine().trim();
		} catch (Exception e) {
		    logger.logThrow(
			Level.WARNING, e, "Problem getting version");
		} finally {
		    try {
			reader.close();
		    } catch (IOException e) {
		    }
		}
	    }
	}
	return version == null ? "unknown" : version;
    }
}
