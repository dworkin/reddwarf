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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Provides information about the data stored by the data service.
 *
 * The serialVersionUID represents the major version number, which must match
 * the value in the current version of the implementation.
 *
 * The minorVersion represents the minor version number, which can vary between
 * a stored instance and the implementation.
 *
 * Version history:
 *
 * Version 1.0: Initial version, 11/3/2006
 */
final class DataServiceHeader implements ManagedObject, Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** 
     * The minor version number.
     *
     * @serial
     */
    final int minorVersion = 0;

    /** 
     * The name of the application that initially created the data service.
     *
     * @serial
     */
    final String appName;

    /**
     * Creates an instance of this class with the specified application
     * name.
     */
    DataServiceHeader(String appName) {
	this.appName = appName;
	validate();
    }

    /** Validates the fields of this instance. */
    private void validate() {
	if (minorVersion < 0) {
	    throw new IllegalArgumentException(
		"The minorVersion field must not be negative");
	}
	if (appName == null) {
	    throw new NullPointerException(
		"The appName field must not be null");
	}
    }

    /** Returns a string representation of this instance. */
    public String toString() {
	return "DataServiceHeader[" +
	    "version:" + serialVersionUID + "." + minorVersion +
	    ", appName:\"" + appName + "\"]";
    }

    /** Validates the fields of this instance. */
    private void readObject(ObjectInputStream in)
	throws ClassNotFoundException, IOException
    {
	in.defaultReadObject();
	try {
	    validate();
	} catch (RuntimeException e) {
	    throw (IOException)
		new InvalidObjectException(e.getMessage()).initCause(e);
	}
    }
}
