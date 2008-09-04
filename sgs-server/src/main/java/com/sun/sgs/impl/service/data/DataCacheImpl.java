/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;

import com.sun.sgs.app.annotation.ReadOnly;

import java.math.BigInteger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class DataCacheImpl implements DataCache {

    private static final int DEFAULT_CACHE_SIZE = 1000;    

    private final Cache<Long,ManagedObject> cache;

    DataCacheImpl(Properties properties) {

	int cacheSize;

	String specifiedCacheSize = 
	    properties.getProperty(DataCache.CACHE_SIZE_PROPERTY);
	if (specifiedCacheSize == null) {
	    cacheSize = DEFAULT_CACHE_SIZE;
	}
	else {
	    try {
		cacheSize = Integer.parseInt(specifiedCacheSize);
	    } catch (NumberFormatException nfe) {
		throw new IllegalArgumentException("cache size must be a " +
						   "number: " + 
						   specifiedCacheSize);
	    }
	}

	cache = new Cache<Long,ManagedObject>(cacheSize);
    }
    

    public void cacheObject(Long oid, ManagedObject o) {
	if (oid == null || o == null)
	    throw new NullPointerException();
	if (cache.containsKey(oid))
	    throw new IllegalArgumentException("duplicate cache entry for oid: "
					       + oid.longValue());
	checkCacheable(o);
	cache.put(oid, o);
    }

    private void checkCacheable(ManagedObject o) {
	if (o.getClass().getAnnotation(ReadOnly.class) == null)
	    throw new IllegalArgumentException(
		"ManagedObject of type " +  o.getClass().getName() + " cannot "
		+ "be cached because it is not marked with a ReadOnly "
		+ "annotation");
    }

    public boolean contains(Long oid) {
	return cache.containsKey(oid);
    }

    public ManagedObject lookup(Long oid) {
	ManagedObject o = cache.get(oid);
	if (o == null) 
	    throw new ObjectNotFoundException("oid: " + oid.longValue() 
					      + " not found");
	return o;
    }

//     public void remove(Long oid) {
// 	ManagedObject o = cache.remove(oid);
// 	if (o == null) 
// 	    throw new ObjectNotFoundException("oid: " + oid.longValue() 
// 					      + " not found");	
//     }

    private static final class Cache<K,V> extends LinkedHashMap<K,V> {

	private final int cacheSize;

	public Cache(int cacheSize) {
	    super(cacheSize, .75f, true);
	    this.cacheSize = cacheSize;	    
	}

	protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
	    return (size() > cacheSize);
	}

    }
    
}