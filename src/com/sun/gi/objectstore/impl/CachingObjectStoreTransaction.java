/*
 * CachingObjectStoreTransaction.java
 *
 * Created on August 25, 2005, 4:51 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.gi.objectstore.impl;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.impl.CachingObjectStore.CacheObject;
import static com.sun.gi.objectstore.impl.CachingObjectStore.*;

/**
 *
 * @author sundt2
 */
public class CachingObjectStoreTransaction implements Transaction {
    
    private CachingObjectStore cstore;
    private ObjectStore store;
    private Transaction trans;
    private HashSet<CacheObject> updateSet;
    
    /**
     * Creates a new instance of CachingObjectStoreTransaction
     */
    CachingObjectStoreTransaction(
            CachingObjectStore cstore, Transaction trans) {
        this.cstore = cstore;
        this.store = cstore.getStore();
        this.trans = trans;
        this.updateSet = new HashSet<CacheObject>(8);
    }
    
    public long create(Serializable object, String name) {
        long id = cstore.nextObjectID();
        CacheObject cacheObj =
                cstore.put(UPDATE_CREATE, id, name, object);
        synchronized (updateSet) {
            updateSet.add(cacheObj);
        }
        return id;
    }
    
    public void destroy(long objectID) {
        CacheObject cacheObj =
                cstore.put(UPDATE_DESTROY, objectID, null, null);
        synchronized (updateSet) {
            updateSet.add(cacheObj);
        }
    }
    
    public Serializable peek(long objectID) {
        CacheObject cacheObj = cstore.get(objectID);
        if (cacheObj != null) {
            return cacheObj.sobj;
        } else {
            Serializable sobj = trans.peek(objectID);
            cstore.put(UPDATE_NONE, objectID, null, sobj);
            return sobj;
        }
    }
    
    public Serializable lock(long objectID) throws DeadlockException {
        Serializable sobj = null;
        boolean locked = false;
        CacheObject cacheObj = cstore.get(objectID);
        if (cacheObj == null) {
            sobj = trans.lock(objectID);
        } else {
            sobj = cacheObj.sobj;
            locked = (cacheObj.updateMode == UPDATE_LOCK);
        }
        // lock it if haven't done so
        if (sobj != null && !locked) {
            trans.lock(cacheObj.id);
            cacheObj = cstore.put(UPDATE_LOCK, objectID, null, sobj);
            synchronized (updateSet) {
                updateSet.add(cacheObj);
            }
        }
        return sobj;
    }
    
    public long lookup(String name) {
        long id = cstore.getID(name);
        if (id != -1) {
            return id;
        } else {
            id = trans.lookup(name);
            if (id >= 0) {
                cstore.put(id, name, null);
            }
            return id;
        }
    }
    
    public void abort() {
        trans.abort();
    }
    
    public void commit() {
        // register all items
        synchronized (updateSet) {
            for (Iterator updates = updateSet.iterator();
            updates.hasNext(); ) {
                CacheObject cacheObj =
                        (CacheObject) updates.next();
                switch (cacheObj.updateMode) {
                    case UPDATE_CREATE:
                        trans.create(cacheObj.sobj, cacheObj.name);
                        break;
                    case UPDATE_DESTROY:
                        trans.destroy(cacheObj.id);
                        break;
                }
                
            }
            updateSet.clear();
        }
        trans.commit();
    }
    
    public long getCurrentAppID() {
        return trans.getCurrentAppID();
    }
    
    public long lookupObject(Serializable sobj) {
        long id = cstore.getID(sobj);
        if (id < 0) {
            id = trans.lookupObject(sobj);
            if (id >= 0) {
                cstore.put(id, null, sobj);
            }
        }
        return id;
    }
}
