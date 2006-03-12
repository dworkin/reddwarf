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
    private boolean clear = false;

    public DataSpaceTransactionImpl(ClassLoader loader, DataSpace dataSpace) {
        this.dataSpace = dataSpace;
        this.loader = loader;

    }

    public long create(Serializable object, String name) {
        return dataSpace.create(serialize(object), name);
    }

    public void destroy(long objectID) {      
         deletedObjects.add(objectID); 
    }

    public Serializable read(long objectID) throws NonExistantObjectIDException {
        Long id = new Long(objectID);
        if (deletedObjects.contains(objectID)){ // it gone
        	 throw new NonExistantObjectIDException();
        }
        Serializable obj = localObjectCache.get(id);
        if ((obj == null) && (!clear)) { // if clear, pretend nothing
                                            // in the data space
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

    public void lock(long objectID) throws NonExistantObjectIDException {
        dataSpace.lock(objectID);
        locksHeld.add(objectID);
    }

    public void release(long objectID) {

        //System.out.println("DataSpaceTransactionImpl.release");

        try {
            dataSpace.release(objectID);
        } catch (NonExistantObjectIDException e) {
            // XXX: should note the error.
        }

        locksHeld.remove(objectID);
    }

    public void write(long objectID, Serializable obj) {
        updateMap.put(objectID, serialize(obj));
    }

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

    public void clear() {
        clear = true;
        resetTransaction();
    }

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

    public void commit() {
        try {
            dataSpace.atomicUpdate(clear, updateMap, deletedObjects);
        } catch (DataSpaceClosedException e) {
            e.printStackTrace();
        }
        resetTransaction();
    }

    public void abort() {
        resetTransaction();

    }

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

    public void close() {
        abort();
    }

}
