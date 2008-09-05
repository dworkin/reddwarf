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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import java.math.BigInteger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * A facility for caching {@link ManagedObject} instances with the {@code
 * ReadOnly} annotation.  This allows static data to avoid the serialization
 * costs assocatiated with persistant storage access.
 *
 * <p>
 *
 * This class sets the default value of the {@value
 * DataCache#CACHE_SIZE_PROPERTY} to be {@code 1000}.  This class also defines
 * a separate property for enabling modification checking for the read-only
 * elements contained within.
 *
 * <dl style="margin-left: 1em">
 *
 * <dt><i>Property:</i> <code><b>{@value #ENABLE_MODIFICATION_CHECKING_PROPERTY}
 *     </b></code> <br>
 *     <i>Default:</i> {@code false}
 * </dl> 
 *
 * If enabled, modifications will be reported using the {@code
 * com.sun.sgs.impl.service.data.ReadOnlyDataCache.object.modifications}
 * logger.  Note that detecting modification will not do anything to the
 * objects themselves, and is intended solely for error reporting purposes.
 *
 * <p>
 *
 * @see DataServiceImpl
 * @see ReadOnly
 * @see ReadOnlyManagedReference
 */
public class ReadOnlyDataCache implements DataCache {

    private static final String ENABLE_MODIFICATION_CHECKING_PROPERTY = 
	ReadOnlyDataCache.class.getName() + ".enable.modification.checking";

    /**
     * The default cache size if {@value DataCache#CACHE_SIZE_PROPERTY} is not
     * specified.
     */
    private static final int DEFAULT_CACHE_SIZE = 1000;    

    /**
     * A mapping from object id to object.
     */
    private final Cache<Long,ManagedObject> cache;

    /**
     * Constructs an instance of the {@code ReadOnlyDataCache} using the
     * specified properties.
     */
    ReadOnlyDataCache(Properties properties) {
	if (properties == null)
	    throw new NullPointerException("properties cannot be null");

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
		throw new IllegalArgumentException(
		    "cache size must be a number: " + specifiedCacheSize);
	    }
	}
	
	String enableModificationChecking = properties.
	    getProperty(ENABLE_MODIFICATION_CHECKING_PROPERTY);
	boolean isEnabled = false;
	if (enableModificationChecking != null) {
	    try {
		isEnabled = 
		    Boolean.parseBoolean(enableModificationChecking);
		// NOTE: it is unspecified in the Boolean API what exception
		// might thrown if parseBoolean fails, so we catch
		// RuntimeException here instead.
	    } catch (RuntimeException e) {
		throw new IllegalArgumentException(
		    ENABLE_MODIFICATION_CHECKING_PROPERTY + " must be set " +
		    "to a boolean value: " + enableModificationChecking);
	    }
	}
	
	// Update the ReadOnlyReferenceTable class so that all new reference
	// tables created will have the correct checking behavior.  Note that
	// we set this as a static property to minimize the amount of
	// implementation-specific state pass around amongst the data service
	// package classes.
	ReadOnlyReferenceTable.modificationCheckingEnabled = isEnabled;
	
	cache = new Cache<Long,ManagedObject>(cacheSize);
    }
    
    /**
     * {@inheritDoc}
     */
    public void cacheObject(Long oid, ManagedObject o) {
	if (oid == null || o == null)
	    throw new NullPointerException();
	if (cache.containsKey(oid))
	    throw new IllegalArgumentException("duplicate cache entry for oid: "
					       + oid.longValue());
	checkCacheable(o);
	cache.put(oid, o);
    }

    /**
     * Checks that the {@code Class} of the provided {@code ManagedObject} has
     * a {@link ReadOnly} annotation.
     *
     * @param o an object to be cached
     *
     * @throws IllegalArgumentException if the {@code Class} of {@code o} does
     *         not have a {@code ReadOnly} annotation
     */
    private void checkCacheable(ManagedObject o) {
	if (o.getClass().getAnnotation(ReadOnly.class) == null)
	    throw new IllegalArgumentException(
		"ManagedObject of type " +  o.getClass().getName() + " cannot "
		+ "be cached because it is not marked with a ReadOnly "
		+ "annotation");
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(Long oid) {
	return cache.containsKey(oid);
    }

    /**
     * {@inheritDoc}
     */
    public ManagedObject lookup(Long oid) {
	return cache.get(oid);
    }

    /**
     * A bounded-size LRU cache.
     */
    private static final class Cache<K,V> extends LinkedHashMap<K,V> {

	/**
	 * The maximum size of the cache
	 */
	private final int cacheSize;

	/**
	 * Constructs a cache with the provided maximum size
	 *
	 * @param cacheSize the maximum size of the cache
	 *
	 * @throws IllegalArgumentException if {@code cacheSize} is
	 *         non-positive
	 */
	public Cache(int cacheSize) {
	    super(checkSize(cacheSize), .75f, true);
	    this.cacheSize = cacheSize;	    
	}

	private static int checkSize(int cacheSize) {
	    if (cacheSize <= 0)
		throw new IllegalArgumentException("cache size be positive: " +
						   cacheSize);
	    return cacheSize;
	}

	/**
	 * Returns true if adding the newest mapping would cause the size to
	 * exceed to the provided bound.
	 *
	 * @param {@inheritDoc}
	 *
	 * @return {@inheritDoc}
	 */
	protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
	    return (size() > cacheSize);
	}
    }    
}