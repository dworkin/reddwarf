/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.DataSpace;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.HadbDataSpace;
import com.sun.gi.objectstore.tso.dataspace.MonitoredDataSpace;
import java.io.Serializable;

/**
 * @author Daniel Ellard
 */

public class TestUtil {

    /**
     * Boot/connect/initialize the database.  <p>
     *
     * @param clear If true, drop the current contents of the
     * database.  If false, just boot/connect but don't blow it away.
     *
     * @param type the type of the DataSpace to use
     *
     * @return an {@link ObjectStore ObjectStore}.
     */

    public static ObjectStore connect(boolean clear,
	    String type, String traceFile)
    {
	ObjectStore ostore;

	try {
	    if (traceFile != null) {
		ostore = new TSOObjectStore(openDataSpace(type));
	    } else {
		ostore = new TSOObjectStore(openDataSpace(type, traceFile));
	    }
	} catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	    return null;
	}

	if (clear) {
	    System.out.println("Clearing object store");
	    ostore.clear();
	}

	return ostore;
    }

    public static ObjectStore connect(boolean clear) {
	return connect(clear, "persistant-inmem", null);
    }

    public static DataSpace openDataSpace(String type) {
	DataSpace dspace;

	if (type == null) {
	    throw new NullPointerException("type is null");
	}

	try {
	    if (type.equals("hadb")) {
		dspace = new HadbDataSpace(1);
	    } else if (type.equals("inmem")) {
		dspace = new InMemoryDataSpace(1);
	    } else if (type.equals("persistant-inmem")) {
		dspace = new PersistantInMemoryDataSpace(1);
	    } else {
		throw new IllegalArgumentException("unknown type: " + type);
	    }
	} catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	    return null;
	}

	return dspace;
    }

    public static DataSpace openDataSpace(String type, String traceFile) {
	DataSpace wrappedDspace = openDataSpace(type);

	try {
	    return new MonitoredDataSpace(wrappedDspace, traceFile);
	} catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	    return null;
	}
    }

    /**
     * First-order sanity check of the transaction mechanism. <p>
     *
     * Creates an persistant object (a String) in the database.
     * References the object by its OID, and then sees whether
     * what was retrieved is what was written. <p>
     *
     * @param os the ObjectStore to use.
     *
     * @param text the test String.
     *
     * @param verbose whether or not to print diagnostics.
     *
     * @return true if successful, false othewise.
     */

    public static boolean sanityCheck(ObjectStore os, String text,
    	    boolean verbose) {

	String newSuffix = " (appended garbage)";
	long oid;

	{
	    StringRef ws = new StringRef(text);

	    Transaction trans1 = os.newTransaction(null);
	    trans1.start();

	    oid = trans1.create(ws, "foo");
	    if (verbose) {
		System.out.println("\tOID = " + oid);
	    }
	    trans1.commit();
	}

	{
	    Transaction trans2 = os.newTransaction(null);
	    StringRef nws;

	    trans2.start();
	    try {
		nws = (StringRef) trans2.peek(oid);
	    } catch (NonExistantObjectIDException e) {
		System.out.println("unexpected: " + e);
		return false;
	    }

	    boolean success = nws.str.equals(text);
	    trans2.abort();

	    if (verbose) {
		System.out.println("\ttrans2 (" + text + ") -> (" + nws.str + ") " +
			(success ? "success" : "failure"));
	    }
	    if (! success) {
		return false;
	    }
	}

	{
	    Transaction trans3 = os.newTransaction(null);
	    StringRef nws;

	    trans3.start();

	    try {
		nws = (StringRef) trans3.lock(oid);
	    } catch (NonExistantObjectIDException e) {
		System.out.println("unexpected: " + e);
		return false;
	    }

	    boolean success = nws.str.equals(text);

	    if (verbose) {
		System.out.println("\ttrans3 (" + text + ") -> (" + nws.str + ") " +
			(success ? "success" : "failure"));
	    }
	    if (! success) {
		return false;
	    }

	    nws.str += newSuffix;
	    trans3.commit();
	}

	{
	    Transaction trans4 = os.newTransaction(null);
	    StringRef nws;
	    String ntext = text + newSuffix;

	    trans4.start();

	    try {
		nws = (StringRef) trans4.peek(oid);
	    } catch (NonExistantObjectIDException e) {
		System.out.println("unexpected: " + e);
		return false;
	    }

	    boolean success = nws.str.equals(ntext);

	    if (verbose) {
		System.out.println("\ttrans4 (" + ntext + ") -> (" + nws.str + ") " +
			(success ? "success" : "failure"));
	    }
	    if (! success) {
		return false;
	    }

	    trans4.abort();
	}

	return true;
    }
}

/*
 * I need something mutable so that I can mutate it...  Strings
 * aren't mutable, but a reference to a string is.
 */

class StringRef implements Serializable {
    public String str;

    public StringRef(String str) {
	this.str = str;
    }
}

