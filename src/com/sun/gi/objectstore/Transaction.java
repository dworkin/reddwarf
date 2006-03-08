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

package com.sun.gi.objectstore;

import java.io.Serializable;

/**
 * A Transaction is a transactional context through which data stored in
 * the ObejctStore can be manipulated.
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface Transaction {

    /**
     * This initializes the transaction for use and should be called
     * before the first use and before re-use after an abort. It allows
     * the transaction to acquire underlying pooled resources that are
     * returned on an abort
     * 
     */
    public void start();

    /**
     * This method is called to create a new entry in the ObjectStore.
     * 
     * @param object The object who's state should be stored
     * @param name An optional name for the object (null means no-name).
     * @return The Object ID assigned to this data record.
     */
    public long create(Serializable object, String name);

    /**
     * This method removes an entry from the Object Store
     * 
     * @param objectID The ID of the data record to remove
     * @throws NonExistantObjectIDException
     * @throws DeadlockException
     */
    public void destroy(long objectID) throws DeadlockException,
            NonExistantObjectIDException;

    /**
     * <p>
     * This method fetches a local copy of an object stored in the
     * object store. It does not lock the object ion the store.
     * </p>
     * <p>
     * If the object has previously been "peeked" by this transaction,
     * the a referene to the previously created local copy is returned.
     * If the object has previously been "getted" by this transaction
     * then a reference to that write-locked copy is returned.
     * </p>
     * <p>
     * Any object that has only been "peeked' and not "getted" will be
     * thrown away and all state changes lost at the end of the
     * transaction.
     * </p>
     * 
     * @param objectID The ID of the object to return.
     * @return A reference to a local copy of the object referenced by
     * objectID
     * @throws NonExistantObjectIDException
     */
    public Serializable peek(long objectID) throws NonExistantObjectIDException;

    /**
     * <p>
     * This method takes a write-lock on the object referenced by
     * objectID and returns a copy of the object to work with.
     * </p>
     * <p>
     * If the object has been perviously locked then this call will
     * return a refernce to the same object. If the object has been
     * "peeked' but not locked, or if this is the first get operation on
     * the obejct udne this transaction, a new copy will be returned,
     * </p>
     * <p>
     * When the transaction commits, all changes to the state of
     * "getted" objects is captured and written back to the ObjectStore.</p<>
     * 
     * @param objectID The ID of the object to return.
     * @param block If false, this will return NULL if it cannot
     * immediately lock the object
     * @return A reference to a copy of the object referenced by
     * objectID
     * @throws NonExistantObjectIDException
     */
    public Serializable lock(long objectID, boolean block)
            throws DeadlockException, NonExistantObjectIDException;

    public Serializable lock(long objectID) throws DeadlockException,
            NonExistantObjectIDException;

    /**
     * This method returns the object ID for an object that has
     * previously been created with a name.
     * 
     * @param name The name of the object
     * @return The named object's object ID.
     */
    public long lookup(String name); // proxies to Ostore

    /**
     * This method is called to abort this transaction. All changes to
     * "getted" objects get discarded and their locks released.
     * 
     */
    public void abort();

    /**
     * This method is called to commit this transaction. All changes to
     * "getted" objects are written abck to the ObjectStore and their
     * locks are released.
     * 
     */
    public void commit();

    /**
     * Returns the AppID that this transaction is using to access
     * objects in the ObjectStore.
     * 
     * @return The AppID
     */
    public long getCurrentAppID();

    public void clear();

}
