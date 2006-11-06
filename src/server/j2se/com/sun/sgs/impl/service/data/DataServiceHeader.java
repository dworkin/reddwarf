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
    private final String appName;

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
