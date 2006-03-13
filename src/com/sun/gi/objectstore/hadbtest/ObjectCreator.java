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

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;

/**
 * An object to help populate the database with FillerObjects. <p>
 *
 * @author Daniel Ellard
 */

public class ObjectCreator {
    private final IdGenerator id;
    private long localVal = -1000000000000L;
    private ClassLoader classLoader;
    private final ObjectStore os;
    private String populatorName = "default";

    /**
     * Create an object that helps populate the database by creating
     * new objects and adding them to the database.  <p>
     *
     * @param os the {@link ObjectStore ObjectStore} to which to add
     * the new objects.
     *
     * @param start the "start" of the per-object Id numbers to embed
     * in each object.  These numbers increase sequentially.  These
     * numbers are <em>not</em> related to the OIDs.
     *
     * @param populatorName a string representing the "name" of this
     * populator.  May be null.
     */

    public ObjectCreator(ObjectStore os, int start, String populatorName) {
	this.classLoader = this.getClass().getClassLoader();
	this.id = new IdGenerator(start);
	this.os = os;
	this.populatorName = populatorName;
    }

    /*
     * A simple wrapper, in case we need to gather more stats here.
     */

    private Transaction beginTransaction(long appId) {

	Transaction trans = os.newTransaction(classLoader);
	return trans;
    }

    /*
     * A simple wrapper, in case we need to gather more stats here.
     */

    private void commitTransaction(Transaction trans) {
	trans.commit();
    }

    /**
     * Create and add a bunch of new objects.  <p>
     *
     * The objects are all added within a single transaction.  <p>
     *
     * @param count the number of new objects to create.
     *
     * @param appId the Id of the owning app.
     *
     * @param selfId whether or not the objects should be backpatched
     * so that the oid field contains a copy of the OID value.
     *
     * @return an array of the OIDs for the new object.
     */

    public long[] createNewBunch(int count, int objSize,
	    long appId, boolean selfId)
    {

	if (count <= 0) {
	    return null;	// &&& Sloppy.
	}

	long[] newOIDs = new long[count];

	/*
	 * There's something wrong with adding more than a certain
	 * number of new objects in a single transaction.  So they are
	 * chunked, and we never create more than chunkSize without
	 * committing them.
	 *
	 * Even this may fail, if the individual objects are large
	 * enough.  For example, there is some magic number (around
	 * 5MB?) that Derby transactions must not exceed.  Other
	 * systems may have similar limitations.
	 */

	int chunkSize = 100;
	for (int base = 0; base < count; base += chunkSize) {
	    System.out.println("chunk base: " + base);
	    Transaction trans = beginTransaction(appId);

	    trans.start();
	    for (int i = 0; i + base < count && i < chunkSize; i++) {
		newOIDs[i + base] = addFillerObj(trans, appId, objSize, base + i);
	    }

	    commitTransaction(trans);

	    if (selfId) {
		trans.start();
		FillerObject fo;

		for (int i = 0; i + base < count && i < chunkSize; i++) {
		    try {
			fo = (FillerObject) trans.lock(newOIDs[i + base]);
			fo.setOID(newOIDs[i + base]);
		    }
		    catch (Exception e) {
			trans.abort();
			// &&& fix this.
			System.out.println("FAILED TO INITIALIZE");
			System.exit(1);
		    }
		}
		commitTransaction(trans);
	    }
	}
	return newOIDs;
    }

    /**
     * Create and add a single new object.  <p>
     *
     * The object is added within a single transaction.
     *
     * @param appId the Id of the owning app
     *
     * @param selfId whether or not the objects should be backpatched
     * so that the oid field contains a copy of the OID value
     *
     * @param objSize the size of the payload of the object to create
     *
     * @return the OID of the new object.
     */

    public long createNew(long appId, boolean selfId, int objSize) {

	long myVal;
	synchronized (this) {
	    myVal = localVal++;
	}

	Transaction trans = beginTransaction(appId);

	trans.start();
	long newOID = addFillerObj(trans, appId, objSize, myVal);

	commitTransaction(trans);

	if (selfId) {
	    selfIdentify(appId, newOID);
	}

	return newOID;
    }

    /**
     * Update the "oid" field with the value of the OID that was
     * assigned to the object.  This self-identifies the object
     * (useful for debugging).
     *
     * @param appId the application ID of the object.
     *
     * @param oid The OID
     *
     * @return <code>true</code> if successful, <code>false</code>
     * otherwise.
     */

    public boolean selfIdentify(long appId, long oid) {
	Transaction trans = beginTransaction(appId);

	trans.start();
	try {
	    FillerObject fo = (FillerObject) trans.lock(oid);
	    fo.setOID(oid);
	    commitTransaction(trans);
	    return true;
	}
	catch (Exception e) {
	    trans.abort();
	    // &&& fix this.
	    return false;
	}
    }

    /**
     * Create a new object and add it, within the context of a
     * specific transaction.
     *
     * @param trans the {@link Transaction Transaction}.
     *
     * @param appId the Id of the owning application.
     *
     * @return the OID of the new object.
     */

    protected long addFillerObj(Transaction trans, long appId, int objSize, long val) {

	FillerObject obj = new FillerObject(id, objSize);

	String name = idString(appId, val);
	long oid = trans.create(obj, name);

	/*
	 * &&& should check whether oid is OK.
	 */

	// System.out.println("\tNew OID: " + oid);
	return oid;
    }

    public static String idString(long appID, long val) {
	return "appID " + appID + " val " + val;
    }
}

