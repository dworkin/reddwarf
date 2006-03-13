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

package com.sun.gi.objectstore.tso.dataspace;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;

/**
 * <p>Title: DataSpace.java</p>
 * <p>Description: </p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface DataSpace {

    static final long INVALID_ID = Long.MIN_VALUE;

    /**
     * Returns a copy of the object as a <code>byte</code> array
     * (which typically represents a serialized object) bound to the
     * given objectID.  <p>
     *
     * @param objectID the identifier of the object to get
     *
     * @return a <code>byte</code> array representing the object, or
     * <code>null</code> if no such object exists
     *
     * (seems inconsistent to return null here and throw
     * NonExistantObjectIDException in other situations -DJE) (agreed. 
     * There are also some functions that return Long and others that
     * return long.  The ones that return Long return null for error
     * and the ones that return long return DataSpace.INVALID ID. 
     * This should probably all get sorted out post GDC...  JK)
     */
    byte[] getObjBytes(long objectID);

    /**
     * Blocks until the object with the given <em>objectID</em> is
     * available, locks the object, and returns.
     * 
     * Note that this is not a counting lock, calling lock twice on
     * the same ID is a deadlock situation as you will sit and wait
     * for yourself to free the lock.
     * 
     * Also note that there is no notion of a lock owner.  Anyone who
     * knows the number can free a lock by calling release on it. 
     *
     * @param objectID the identifier of the object to lock
     *
     * @throws NonExistantObjectIDException if no object with the
     * given <em>objectID</em> exists
     */
    void lock(long objectID) throws NonExistantObjectIDException;

    /**
     * Releases the lock on the object with the given
     * <em>objectID</em>.  <p>
     *
     * Note that any thread may release any lock.  (see above.)<p>
     *
     * Also note that it is not an error to release the lock on an
     * object that is not currently locked, or to attempt to release
     * the lock on an object that does not exist.  In either of these
     * cases, releasing the lock has no effect on the state of the
     * DataSpace.
     *
     * @param objectID the object whose lock to release
     *
     * @throws NonExistantObjectIDException if no object with the
     * given <em>objectID</em> exists
     */
    void release(long objectID) throws NonExistantObjectIDException;

    /**
     * Releases all of the locks in the given set of object IDs.  <p>
     *
     * Note that any thread may release any lock.  (see above.)<p>
     *
     * Also note that it is not an error to release the lock on an
     * object that is not currently locked, or to attempt to release
     * the lock on an object that does not exist.  In either of these
     * cases, releasing the lock has no effect on the state of the
     * DataSpace.  A {@link NonExistantObjectIDException} is thrown if
     * any of the objects do not exist, but this does not prevent all
     * of the other objects from being released.
     *
     * @param objectIDs a set of IDs for objects whose locks to
     * release
     *
     * @throws NonExistantObjectIDException if one or more of the
     * object IDs does not exist
     */
    void release(Set<Long> objectIDs) throws NonExistantObjectIDException;

    /**
     * Atomically updates the DataSpace.  <p>
     *
     * The <code>updateMap</code> contains new bindings between object
     * identifiers and the byte arrays that represent their values. 
     *
     * @param clear <b>NOT USED IN CURRENT IMPL</b>
     *
     * @param updateMap new bindings between object identifiers and
     * byte arrays
     *
     * @throws DataSpaceClosedException
     *
     * (What is <code>clear</code> supposed to do?  Or is this now
     * unused and should be removed?  -DJE)
     */
    void atomicUpdate(boolean clear, Map<Long, byte[]> updateMap,
	    List<Long> deleted)
	    throws DataSpaceClosedException;

    /**
     * Returns the object identifier of the object with a given name
     * (assigned via a {@link #create create}).  <p>
     *
     * If more than one object identifier is bound to the same name,
     * the identifier returned may be chosen arbitrarily from the set
     * of such identifiers.
     *
     * @param name the name of the object
     *
     * @return the object identifier of the object with the given name
     */
    Long lookup(String name);

    /**
     * Return the application identifier for the application that owns
     * this dataspace.
     *
     * @return the application identifier for the application that
     * owns this dataspace
     */
    long getAppID();

    /**
     * Deletes the contents of the DataSpace and releases any locks
     * held on those contents.
     * 
     * Clear is an immediate (non-transactional) chnage to the
     * DataSpace
     */
    void clear();
    
    /**
     * Creates a new element in the DataSpace.  <p>
     *
     * If <code>name</code> is non-<code>null</code> and
     * <code>name</code> is already in the DataSpace then this method
     * will fail.  <p>
     * 
     * Create is an immediate (non-transactional) change to the
     * DataSpace.
     * 
     * @return a new objectID if successful, or
     * <code>DataSpace.INVALID_ID</code> if it fails
     */
    public long create(byte[] data, String name);

    /**
     * Closes the DataSpace, preventing further updates.  <p>
     *
     * All other operations are permitted when the DataSpace is
     * closed.  <p>
     */
    void close();
}
