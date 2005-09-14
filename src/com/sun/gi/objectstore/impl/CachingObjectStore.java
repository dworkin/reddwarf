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
import com.sun.gi.objectstore.impl.CachingObjectStoreCache.Entry;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author sundt2
 */
public class CachingObjectStore implements ObjectStore {
    
    private ObjectStore store;
    private ObjectIDManager oidManager;
    private long nextID;
    
    // global cache for objects
    CachingObjectStoreCache cache;
    // global set of locks by objectID
    private HashMap<Long, CachingObjectStoreLock> idLockMap;
    // global set of list of locks held by each transaction
    private HashMap<CachingObjectStoreTransaction, ArrayList<CachingObjectStoreLock>> transLockMap;
    
    class CachingObjectStoreLock {
        CachingObjectStoreTransaction trans;
        Long idObj;
        long time;
        CachingObjectStoreLock(CachingObjectStoreTransaction trans, Long idObj) {
            this.trans = trans;
            this.idObj = idObj;
            this.time = System.currentTimeMillis();
        }
    }
    
    class CachingObjectStoreLockException extends Exception {
        CachingObjectStoreLock lock;
        CachingObjectStoreLockException(
                CachingObjectStoreLock lock) {
            super("Lock already acquired");
            this.lock = lock;
        }
    }
    
    /** Creates a new instance of CachingObjectStore */
    public CachingObjectStore(ObjectStore store, int cacheSize) {
        this.store = store;
        this.cache = new CachingObjectStoreCache(128);
        this.idLockMap = new HashMap(16);
        this.transLockMap = new HashMap(16);
        oidManager = new PureOStoreIDManager(store);
        nextID = oidManager.getNextID();
    }
    
    ObjectStore getStore() {
        return store;
    }
    
    public Transaction newTransaction(long appID, ClassLoader loader) {
        Transaction trans = store.newTransaction(appID, loader);
        return new CachingObjectStoreTransaction(this, trans, -1);
    }
    
    public synchronized void clear() {
        cache.clear();
        idLockMap.clear();
        transLockMap.clear();
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
    
    CachingObjectStoreLock lock(CachingObjectStoreTransaction trans, long id)
    throws CachingObjectStoreLockException {
        synchronized (idLockMap) {
            Long idObj = new Long(id);
            CachingObjectStoreLock lock = idLockMap.get(idObj);
            if (lock != null) {
                // if object is already lock, check to see if it is locked by current trans
                if (lock.trans == trans) {
                    return lock; // if so, no op and return
                } else {
                    // otherwise throw exception
                    throw new CachingObjectStoreLockException(lock);
                }
            }
            // if no lock yet, then create it and put it into maps
            lock = new CachingObjectStoreLock(trans, idObj);
            idLockMap.put(idObj, lock);
            ArrayList transLockList = transLockMap.get(trans);
            if (transLockList == null) {
                transLockList = new ArrayList<CachingObjectStoreLock>(8);
                transLockMap.put(trans, transLockList);
            }
            transLockList.add(lock);
            return lock;
        }
    }
    
    CachingObjectStoreLock getLock(CachingObjectStoreTransaction trans, long id) {
        synchronized (idLockMap) {
            Long idObj = new Long(id);
            return idLockMap.get(idObj);
        }
    }
    
    void unlock(CachingObjectStoreTransaction trans, long id) {
        synchronized (idLockMap) {
            Long idObj = new Long(id);
            CachingObjectStoreLock lock = idLockMap.get(idObj);
            if (lock != null) {
                idLockMap.remove(idObj);
                ArrayList<CachingObjectStoreLock> lockList = transLockMap.get(trans);
                // remove the lock entry from the transaction
                if (lockList != null) {
                    lockList.remove(lock);
                }
            }
        }
    }
    
    void unlockAll(CachingObjectStoreTransaction trans) {
        synchronized (idLockMap) {
            // remove all locks
            ArrayList<CachingObjectStoreLock> lockList = transLockMap.get(trans);
            if (lockList != null) {
                for (CachingObjectStoreLock lock : lockList) {
                    idLockMap.remove(lock.idObj);
                }
                transLockMap.remove(trans);
            }
        }
    }
    
    void commit(CachingObjectStoreTransaction trans) {
        synchronized (idLockMap) {
            unlockAll(trans);
            cache.merge(trans.cache);
        }
    }
    
}
