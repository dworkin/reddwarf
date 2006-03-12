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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.DataSpaceTransaction;
import com.sun.gi.utils.classes.CLObjectInputStream;

/**
 * This implements a very simplistic form of Transaction ontop of a 
 * DataSpace.  <b>These transactions are not fully ACID and are intended
 * as a building block towards more complete transactional models.</b>
 * 
 * See the comments on each method for its behavior and limitatons,
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */

public class DataSpaceTransactionImpl implements DataSpaceTransaction {

    private DataSpace dataSpace;
    private ClassLoader loader;
    private Map<Long, Serializable> localObjectCache = new HashMap<Long, Serializable>();
    private Map<Long, byte[]> updateMap = new HashMap<Long, byte[]>();
    private Set<Long> locksHeld = new HashSet<Long>();
    private List<Long> deletedObjects = new ArrayList<Long>();
   

    /**
     * Creates a new DataSpaceTransaction for the passed in data space.
     * All code loading for de-dearilized classes is acomplished through
     * the passed in class loader.
     * 
     * @param loader The class laoder to use when creating classes from the
     * serialized versions stored in the DataSpace
     * @param dataSpace The DataSpace this transaction accesses.
     */
    public DataSpaceTransactionImpl(ClassLoader loader, DataSpace dataSpace) {
        this.dataSpace = dataSpace;
        this.loader = loader;

    }

    /**
     * This method is used to create a new stored object.  Only one
     * objcet can be created with a given name.  Objects are 
     * stored through serialization and thus what can be retrieved
     * is actually a de-serailized copy.
     * 
     * If the name is already in use at the time the creation 
     * attempt is made then this method returns DataSpace.INVALID_ID;
     * 
     * A created object is immediately availabe to this transaction for
     * use but is isolated from all other transactions until a commit.
     * 
     * The ID returned is gauranteed to be unique and not to have been 
     * assigned on any previous create.
     * 
     * @param object  A template object used to create the initial
     * serialized image mapped to the ID returned.
     * @param name Either a name to map to the returned ID, or null
     * @returns the ID of the stored object or INVALID_ID if the
     * name was already in use at the time the store attempt was made.
     * 
     */
    public long create(Serializable object, String name) {
        return dataSpace.create(serialize(object), name);
    }
    
    /**
     * This method is used to destroy an object.  The object
     * disappears from the view of this transaction but remains
     * available to other transactions until this transaction
     * is comitted.
     * 
     * This can cause isolation issues if proper locking
     * is not enforced at a higher level
     * 
     * @param objectID the ID of the object to remove from storage
     *
     */

    public void destroy(long objectID) {      
         deletedObjects.add(objectID); 
    }
    
    /**
     * This method returns a deserialized copy of the object stored under 
     * the given ID in the DataSpace.
     * 
     * The first time it is called with a given ID it will return a new 
     * object.  After that it will return the same object it previously
     * returned unless the forget() call is made that ID.
     * 
     * @param objectID  The ID of the object to fetch from the DataSpace
     * @returns A deserialized copy of the object last written to that ID.
     * @throws NonExistantObjectIDException if the provided ID does not map to
     * any object currently stored in the DataSpace.
     */

    public Serializable read(long objectID) throws NonExistantObjectIDException {
        Long id = new Long(objectID);
        if (deletedObjects.contains(objectID)){ // it gone
        	 throw new NonExistantObjectIDException();
        }
        Serializable obj = localObjectCache.get(id);
        if (obj == null) { 
            byte[] objbytes = dataSpace.getObjBytes(objectID);
            if (objbytes == null) {
                throw new NonExistantObjectIDException();
            }
            //System.out.println("Obejct came from DataSpace");
            obj = deserialize(objbytes);
            localObjectCache.put(new Long(objectID), obj);
        }
        return obj;
    }
    /**
     * This is used to convert the  serialized form of an object back to
     * a live object
     * @param objbytes the serialized form
     * @return a deserialized copy of obejct represented by objbytes
     */

    private Serializable deserialize(byte[] objbytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(objbytes);
        try {
            ObjectInputStream ois = new CLObjectInputStream(bais, loader);
            Serializable obj = (Serializable) ois.readObject();
            ois.close();
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This call takes a very simple Mutex lock on an ID in use
     * in the DataSpace. If the ID does not corrospond to an object
     * currently stroed in the DataSpace this method will through
     * a NonExistantObjectIDException.
     * 
     * This is a *very* simple and brain-dead locking mechanism provided
     * as a building block with which to build more complex behaviors.
     * It is a simple Mutex which has no idea of who holds the lock. Once
     * a lock has been taken, any transaction that attempts to take
     * the lock again will block until the lock is freed.
     * 
     * Similarly, any call to any transaction to release the lock 
     * will result in its release regardless of what transaction took it.
     *
     *@params objectID  the ID to lock
     *@throws NonExistantObjectIDException if the ID does not corrospond to 
     *an object currently stored in the DataSpace.
     *  
     */
    public void lock(long objectID) throws NonExistantObjectIDException {
        dataSpace.lock(objectID);
        locksHeld.add(objectID);
    }

    	/**
    	
    	 * 
    	 * This method releases a currently an objectID previously locekd with
    	 * lock(). Its is a *very* brain dead locking mechanism and provided\
    	 * as a building block for more sophisticated behaviors. 
    	 * 
    	 * This method  makes no checks as to who took the lock before releasing 
    	 * it.  Any transaction may release any lock.
    	 * 
    	 * Existance of the obejctID or the state of the lock are also not 
    	 * checked.  If the objectID does not corrpospond to a currently 
    	 * stored object then the release operation is ignored and returns
    	 * as if successful. If the ID is not locked then again, it simply
    	 * NOPs and returns as if successful.
    	 * 
    	 * @param objectID  the ID to unlock
    	 */
    public void release(long objectID) {

        //System.out.println("DataSpaceTransactionImpl.release");

        try {
            dataSpace.release(objectID);
        } catch (NonExistantObjectIDException e) {
            // XXX: should note the error.
        }

        locksHeld.remove(objectID);
    }

    /**
     * This method writes a serialized copy of the passed in object
     * as the new value of the passeed in objectID.
     * 
     * This update is immediately visible to thsi transaction but
     * isolated from other transactions until commit time.
     * 
     * @param objectID the ID of the object
     * @param obj the new value of the ID
     */
    public void write(long objectID, Serializable obj) {
        updateMap.put(objectID, serialize(obj));
    }
    
    /**
     * This is used to convert the object to its serialized form 
     * @param obj obejct to serialize
     * @return byte array containing the serialized form
     */

    private byte[] serialize(Serializable obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = null;
        try {
            ObjectOutputStream oas = new ObjectOutputStream(baos);
            oas.writeObject(obj);
            oas.flush();
            oas.close();
            buf = baos.toByteArray();
            baos.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buf;
    }
    

    public void forget(long objectID) {
        localObjectCache.remove(objectID);
    }

    /**
     * This method is used by both commit and abort to
     * reset the transaction to a pristine state.
     * 
     * It resets all caches and lists and releases all
     * locks that might still be held.
     *
     */

    private void resetTransaction() {
        updateMap.clear();
        localObjectCache.clear();
        deletedObjects.clear();
        // release left over locks
        try {
            dataSpace.release(locksHeld);
        } catch (NonExistantObjectIDException e) {
            // ignore
        }

        /*
         * for(Long id : locksHeld){ try { dataSpace.release(id); }
         * catch (NonExistantObjectIDException e) { // XXX: note the
         * excecption. } }
         */

        locksHeld.clear();
    }

    /**
     * This commist the transaction.  It makes all chnages to
     * the DataSpace visible to otehr transactions, releases all
     * locks still held, and cleans all internal caches and lists.
     */
    public void commit() {
        try {
        	// the false below used to be the clear flag, which is
        	// not implemented in the current DataSpace
            dataSpace.atomicUpdate(false, updateMap, deletedObjects);
        } catch (DataSpaceClosedException e) {
            e.printStackTrace();
        }
        resetTransaction();
    }

    /**
     * This aborts the transaction.  It abandons all chnages to
     * the DataSpace, releases all
     * locks still held, and cleans all internal caches and lists.
     */
    public void abort() {
        resetTransaction();

    }

    /**
     * This method finds the obejct ID associated with a name
     * by an earlier create() call.
     * 
     * If the name does not currently map to an existing
     * object in the DataSpace, it returns DataSpace.INVALID_ID
     * 
     * @param name The name for which to find the associated ID
     * @returns the associated object's ID or INVALID_ID if there
     * is no such current object.
     */
    public long lookupName(String name) {
        Long l = dataSpace.lookup(name);
        if (l == null) {
            return DataSpace.INVALID_ID;
        }
        if (deletedObjects.contains(l)){
        	return DataSpace.INVALID_ID;
        }
        return l.longValue();
    }

    /**
     * Does nothing.  Formerly a synonym for abort().
     */
    public void close() {
        // resetTransaction();
    }

}
