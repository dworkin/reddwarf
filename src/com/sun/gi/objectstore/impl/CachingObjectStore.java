/*
 * CachingObjectStore.java
 *
 * Created on August 25, 2005, 4:47 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.gi.objectstore.impl;

import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import java.io.Serializable;
import java.util.HashMap;

/**
 *
 * @author sundt2
 */
public class CachingObjectStore implements ObjectStore {

    static final int DEFAULT_CACHE_SIZE = 64;

    static final int UPDATE_NOCHANGE = -1;
    static final int UPDATE_NONE = 0;
    static final int UPDATE_LOCK = 1;
    static final int UPDATE_CREATE = 2;
    static final int UPDATE_DESTROY = 3;

    private Object mapLock = new Object();
    private Object updateLock = new Object();
    private ObjectStore store;
    private ObjectIDManager oidManager;
    private long nextID;
    private HashMap<Long, CacheObject> idMap;
    private HashMap<String, CacheObject> nameMap;
    private HashMap<Serializable, CacheObject> objMap;

    class CacheObject {
        int updateMode = UPDATE_NONE;
        long id;
        String name;
        Serializable sobj;
        private CacheObject(
            int updateMode, long id, String name, Serializable sobj) {
            if (updateMode != UPDATE_NOCHANGE) {
                this.updateMode = updateMode;
            }
            this.id = id;
            this.name = name;
            this.sobj = sobj;
        }
    }

    /** Creates a new instance of CachingObjectStore */
    public CachingObjectStore(ObjectStore store, int cacheSize) {
        this.store = store;
        oidManager = new PureOStoreIDManager(store);
        nextID = oidManager.getNextID();

        int myCacheSize =
            (cacheSize <= 0) ? DEFAULT_CACHE_SIZE : cacheSize;
        idMap = new HashMap<Long, CacheObject>(myCacheSize);
        nameMap = new HashMap<String, CacheObject>(myCacheSize);
        objMap = new HashMap<Serializable, CacheObject>(myCacheSize);
    }

    ObjectStore getStore() {
        return store;
    }

    public Transaction newTransaction(long appID, ClassLoader loader) {
        Transaction trans = store.newTransaction(appID, loader);
        return new CachingObjectStoreTransaction(this, trans);
    }

    public void clear() {
        synchronized (mapLock) {
            store.clear();
            idMap.clear();
            nameMap.clear();
            objMap.clear();
        }
    }

    public long getTimestampTimeout() {
        return store.getTimestampTimeout();
    }

    public OStoreMetaData peekMetaData(Transaction trans) {
        OStoreMetaData metaData = store.peekMetaData(trans);
        return metaData;
    }

    public OStoreMetaData lockMetaData(Transaction trans) {
        OStoreMetaData metaData = store.lockMetaData(trans);
        return metaData;
    }

    synchronized long nextObjectID() {
        return nextID++;
    }

    void remove(long id) {
        synchronized (mapLock) {
            Long idObj = new Long(id);
            CacheObject cacheObj = (CacheObject)idMap.get(idObj);
            if (cacheObj != null) {
                idMap.remove(idObj);
                objMap.remove(cacheObj.sobj);
                nameMap.remove(cacheObj.name);
            }
        }
    }

    CacheObject put(long id, String name, Serializable sobj) {
        return put(UPDATE_NONE, id, name, sobj);
    }

    CacheObject put(int updateMode, long id, String name, Serializable sobj) {
        Long idObj = new Long(id);
        CacheObject cacheObj = null;
        synchronized (mapLock) {
            cacheObj = (CacheObject)idMap.get(name);
            if (cacheObj == null) {
                cacheObj = new CacheObject(updateMode, id, name, sobj);
                idMap.put(idObj, cacheObj);
                objMap.put(sobj, cacheObj);
                nameMap.put(name, cacheObj);
            } else {
                // it's possible that the entry is there with no obj
                if (updateMode != UPDATE_NOCHANGE) {
                    cacheObj.updateMode = updateMode;
                }
                if (name != null) {
                    cacheObj.name = name;
                }
                if (sobj != null) {
                    cacheObj.sobj = sobj;
                }
            }
        }
        return cacheObj;
    }

    CacheObject get(long id) {
        synchronized (mapLock) {
            return idMap.get(new Long(id));
        }
    }

    long getID(String name) {
        synchronized (mapLock) {
            CacheObject cacheObj = nameMap.get(name);
            return (cacheObj != null) ? cacheObj.id : -1;
        }
    }

    long getID(Serializable sobj) {
        synchronized (mapLock) {
            CacheObject cacheObj = objMap.get(sobj);
            return (cacheObj != null) ? cacheObj.id : -1;
        }
    }
}
