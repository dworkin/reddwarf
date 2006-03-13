/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

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

    public static ObjectStore connect(long appId, boolean clear,
	    String type, String traceFile)
    {
	ObjectStore ostore;

	try {
	    if (traceFile == null) {
		ostore = new TSOObjectStore(openDataSpace(appId, type));
	    } else {
		ostore = new TSOObjectStore(openDataSpace(appId,
			type, traceFile));
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
	// return connect(1, clear, "persistant-inmem", "SCRATCH1");
	return connect(1, clear, "persistant-inmem", null);
    }

    /**
     */
    public static DataSpace openDataSpace(long appId, String type) {
	DataSpace dspace;

	if (type == null) {
	    throw new NullPointerException("type is null");
	}

	try {
	    if (type.equals("hadb")) {
		dspace = new HadbDataSpace(appId);
	    } else if (type.equals("inmem")) {
		dspace = new InMemoryDataSpace(appId);
	    } else if (type.equals("persistant-inmem")) {
		dspace = new PersistantInMemoryDataSpace(appId);
	    } else {
		throw new IllegalArgumentException("unknown type: " + type);
	    }
	} catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	    return null;
	}

	return dspace;
    }

    public static DataSpace openDataSpace(long appId,
	    String type, String traceFile)
    {
	DataSpace wrappedDspace = openDataSpace(appId, type);

	try {
	    return new MonitoredDataSpace(wrappedDspace, traceFile);
	} catch (Exception e) {
	    System.out.println("unexpected exception: " + e);
	    return null;
	}
    }

    /**
     * First-order sanity check of the transaction mechanism.  <p>
     *
     * Creates an persistant object (a String) in the database. 
     * References the object by its OID, and then sees whether what
     * was retrieved is what was written.  <p>
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

	    oid = trans1.create(ws, null);
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

    public static void snooze(long millis, String reason) {
	if (reason != null) {
	    System.out.println(reason);
	}

	if (millis < 1) {
	    return;
	} else {
	    try {
		Thread.sleep(millis);
	    } catch (Exception e) {
		System.out.println("unexpected: " + e);
	    }
	}
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

