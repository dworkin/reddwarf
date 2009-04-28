/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.io.BufferedReader;
import java.io.EOFException;
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
		    String line = reader.readLine();
		    if (line == null) {
			throw new EOFException("Unexpected end of file");
		    }
		    version = line.trim();
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
