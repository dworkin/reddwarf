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

package com.sun.sgs.test.util;

import java.util.Enumeration;
import java.util.Properties;

/** Provides properties utilities for writing tests. */
public class UtilProperties {

    /**
     * Creates a property list with the specified keys and values, and
     * including all current system properties that start with "com.sun.sgs.",
     * "java.rmi." or "je.".  Including those system properties allows
     * supplying server properties from the command line.  It is necessary to
     * not include all properties because some of them interfere if used for
     * starting up another VM.
     *
     * @param	args an alternating list of property names and values
     * @return	the properties
     * @throws	IllegalArgumentException if the argument has an odd number of
     *		elements
     */
    public static Properties createProperties(String... args) {
	if (args.length % 2 != 0) {
	    throw new IllegalArgumentException("Odd number of arguments");
	}
	Properties props = new Properties();
	for (Enumeration<?> names = System.getProperties().propertyNames();
	     names.hasMoreElements(); )
	{
	    Object name = names.nextElement();
	    if (name instanceof String) {
		String property = (String) name;
		if (property.startsWith("com.sun.sgs.") ||
		    property.startsWith("java.rmi.") ||
		    property.startsWith("je."))
		{
		    props.setProperty(property, System.getProperty(property));
		}
	    }
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }
}    
