/*
 * Copyright 2005, Sun Microsystems, Inc. All Rights Reserved. 
 */

package com.sun.gi.objectstore.hadbtest;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.impl.DerbyObjectStore;
import com.sun.gi.objectstore.Transaction;
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
     * @return an {@link ObjectStore ObjectStore}.
     */

    public static ObjectStore connect(boolean clear) {

	// &&& DJE: The (10, 20) are magic.  Do they mean anything?.

	ObjectStore ostore = new DerbyObjectStore(10, 20);

	if (clear) {
	    System.out.println("Clearing object store");
	    ostore.clear();
	}

	return ostore;
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

	    Transaction trans1 = os.newTransaction(101, null);

	    oid = trans1.create(ws, null);
	    if (verbose) {
		System.out.println("\tOID = " + oid);
	    }
	    trans1.commit();
	}

	{
	    Transaction trans2 = os.newTransaction(101, null);
	    StringRef nws = (StringRef) trans2.peek(oid);

	    boolean success = nws.str.equals(text);
	    trans2.abort();

	    if (verbose) {
		System.out.println("\t(" + text + ") -> (" + nws.str + ") " +
			(success ? "success" : "failure"));
	    }
	    if (! success) {
		return false;
	    }
	}

	{
	    Transaction trans3 = os.newTransaction(101, null);
	    StringRef nws = (StringRef) trans3.lock(oid);

	    boolean success = nws.str.equals(text);

	    if (verbose) {
		System.out.println("\t(" + text + ") -> (" + nws.str + ") " +
			(success ? "success" : "failure"));
	    }
	    if (! success) {
		return false;
	    }

	    nws.str += newSuffix;
	    trans3.commit();
	}

	{
	    Transaction trans4 = os.newTransaction(101, null);
	    StringRef nws = (StringRef) trans4.peek(oid);
	    String ntext = text + newSuffix;

	    boolean success = nws.str.equals(ntext);

	    if (verbose) {
		System.out.println("\t(" + ntext + ") -> (" + nws.str + ") " +
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

