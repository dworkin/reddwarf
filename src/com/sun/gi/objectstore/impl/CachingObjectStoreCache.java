/*
 * CachingObjectStoreCache.java
 *
 * Created on September 14, 2005, 1:41 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.gi.objectstore.impl;

import java.io.Serializable;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * @author cc144453
 */
class CachingObjectStoreCache {
    
    static final int DEFAULT_CACHE_SIZE = 32;
    static final int UPDATE_NOCHANGE = -1;
    static final int UPDATE_NONE = 0;
    static final int UPDATE_CREATE = 2;
    static final int UPDATE_DESTROY = 3;
    
    class Entry {
        int updateMode = UPDATE_NONE;
        Long idObj;
        String name;
        Serializable sobj;
        private Entry(
                int updateMode, Long idObj, String name, Serializable sobj) {
            if (updateMode != UPDATE_NOCHANGE) {
                this.updateMode = updateMode;
            }
            this.idObj = idObj;
            this.name = name;
            this.sobj = sobj;
        }
    }
    
    private HashMap<Long, Entry> idMap;
    private HashMap<String, Entry> nameMap;
    private HashMap<Serializable, Entry> objMap;
    
    /**
     * Creates a new instance of CachingObjectStoreCache
     */
    CachingObjectStoreCache(int cacheSize) {
        int myCacheSize =
                (cacheSize <= 0) ? DEFAULT_CACHE_SIZE : cacheSize;
        idMap = new HashMap<Long, Entry>(myCacheSize);
        nameMap = new HashMap<String, Entry>(myCacheSize);
        objMap = new HashMap<Serializable, Entry>(myCacheSize);
    }
    
    void clear() {
        synchronized (this) {
            idMap.clear();
            nameMap.clear();
            objMap.clear();
        }
    }
    
    void merge(CachingObjectStoreCache source) {
        synchronized (this) {
            // currently this copies all entries from source
            // we may or may not want to do this;
            // the only goal is to make sure the 2 copies are consistent
            for (Long idObj : source.idMap.keySet()) {
                Entry entry = idMap.get(idObj);
                Entry sourceEntry = source.idMap.get(idObj);
                // take care not to merge destroyed objects
                if (sourceEntry.updateMode == UPDATE_DESTROY) {
                    if (entry != null) {
                        remove(idObj.longValue());
                    }
                } else {
                    if (entry != null) {
                        // if in existing cache, reconcile, cheaper
                        entry.updateMode = UPDATE_NONE;
                        entry.name = sourceEntry.name;
                        entry.sobj = sourceEntry.sobj;
                    } else {
                        // if not, add the new entry
                        sourceEntry.updateMode = UPDATE_NONE;
                        put(sourceEntry);
                    }
                }
            }
        }
    }
    
    void remove(long id) {
        synchronized (this) {
            Long idObj = new Long(id);
            Entry cacheObj = (Entry)idMap.get(idObj);
            if (cacheObj != null) {
                idMap.remove(idObj);
                objMap.remove(cacheObj.sobj);
                nameMap.remove(cacheObj.name);
            }
        }
    }
    
    Entry put(Entry entry) {
        synchronized (this) {
            idMap.put(entry.idObj, entry);
            objMap.put(entry.sobj, entry);
            nameMap.put(entry.name, entry);
        }
        return entry;
    }
    
    Entry put(long id, String name, Serializable sobj) {
        return put(UPDATE_NONE, id, name, sobj);
    }
    
    Entry put(int updateMode, long id, String name, Serializable sobj) {
        Long idObj = new Long(id);
        Entry cacheObj = null;
        synchronized (this) {
            cacheObj = (Entry)idMap.get(name);
            if (cacheObj == null) {
                cacheObj = put(new Entry(updateMode, id, name, sobj));
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
    
    Entry get(long id) {
        synchronized (this) {
            return idMap.get(new Long(id));
        }
    }
    
    long getID(String name) {
        synchronized (this) {
            Entry cacheObj = nameMap.get(name);
            return (cacheObj != null) ? cacheObj.idObj.longValue() : -1;
        }
    }
    
    long getID(Serializable sobj) {
        synchronized (this) {
            Entry cacheObj = objMap.get(sobj);
            return (cacheObj != null) ? cacheObj.idObj.longValue() : -1;
        }
    }
}
