/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.service.DataService;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A utility class for obtaining an iterator for a set of bound names
 * (matching a prefix) in a data service.
 */
public final class BoundNamesUtil {

    /** Prevents instantiation. */
    private BoundNamesUtil() { }

    /**
     * Returns an {@code Iterable} that can be used to obtain an
     * {@code Iterator} for the set of service bound names matching
     * the specified {@code prefix} in the specified {@code
     * dataService}.  Only names starting with the specified prefix and
     * containing additional characters after the prefix will be
     * returned. The returned {@code Iterable} is not serializable.
     *
     * <p>The {@code iterator} method of the returned {@code Iterable}
     * returns the result of invoking {@link
     * #getServiceBoundNamesIterator getServiceBoundNamesIterator}
     * with the specified {@code dataService} and {@code prefix}.
     *
     * @param 	dataService a data service
     * @param	prefix the prefix of service bound names
     * @return 	an {@code Iterable} for the set of service bound names
     * 		matching the {@code prefix}
     * @see	#getServiceBoundNamesIterator
     */
    public static Iterable<String> getServiceBoundNamesIterable(
	DataService dataService, String prefix)
    {
	if (dataService == null || prefix == null) {
	    throw new NullPointerException("null argument");
	}
	return new BoundNamesIterable(dataService, prefix);
    }

    /**
     * Returns an {@code Iterator} for the set of service bound names
     * matching the specified {@code prefix} in the specified {@code
     * dataService}.   Only names starting with the specified prefix and
     * containing additional characters after the prefix will be
     * returned.  The returned {@code Iterator} is not serializable.
     *
     * <p>The {@code remove} method of the returned iterator removes
     * the binding of the last name returned by {@code next} by
     * invoking {@link DataService#removeServiceBinding
     * removeServiceBinding} on the given {@code dataService} passing
     * the name.
     *
     * <p>Note: the {@code remove} method does not remove from the
     * data store the {@link ManagedObject} bound to the name.  If the
     * {@code ManagedObject} needs to be removed, that object should
     * be removed by invoking the {@code dataService}'s {@link
     * DataManager#removeObject removeObject} method passing the
     * {@code ManagedObject} bound to the name.
     *
     * @param 	dataService a data service
     * @param	prefix the prefix of service bound names
     * @return	an {@code Iterator} for the set of service bound names
     * 		matching the {@code prefix}
     */
    public static Iterator<String> getServiceBoundNamesIterator(
	DataService dataService, String prefix)
    {
	if (dataService == null || prefix == null) {
	    throw new NullPointerException("null argument");
	}
	return new BoundNamesIterator(dataService, prefix);
    }

    /* -- other classes -- */

    /**
     * An {@code Iterable} that is a container for a {@code
     * BoundNamesIterator}.
     */
    private static class BoundNamesIterable implements Iterable<String> {

	/** The data service. */
	private final DataService dataService;
	/** The prefix for service bound names. */
	private final String prefix;

	BoundNamesIterable(DataService dataService, String prefix) {
	    this.dataService = dataService;
	    this.prefix = prefix;
	}

	/** {@inheritDoc} */
	public Iterator<String> iterator() {
	    return new BoundNamesIterator(dataService, prefix);
	}
    }

    /**
     * An {@code Iterator} for the set of service bound names matching
     * the {@code prefix} of the {@code dataService} both specified
     * during construction.
     */
    private static class BoundNamesIterator implements Iterator<String> {

	/** The data service. */
	private final DataService dataService;
	/** The prefix for service bound names. */
	private final String prefix;
	/** The key used to look up next service bound name, or null. */
	private String key;
	/** The key returned by {@code next}, or null. */
	private String keyReturnedByNext;
	/** The name fetched in the {@code hasNext} method, which
	 * is only valid if {@code hasNext} returns {@code true}. */
	private String nextName;
	
	BoundNamesIterator(DataService dataService, String prefix) {
	    this.dataService = dataService;
	    this.prefix = prefix;
	    this.key = prefix;
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    if (key == null) {
		return false;
	    }
	    if (nextName != null) {
		return true;
	    }
	    String name = dataService.nextServiceBoundName(key);
	    if (name != null && name.startsWith(prefix)) {
		nextName = name;
		return true;
	    } else {
		key = null;
		return false;
	    }
	}

	/** {@inheritDoc} */
	public String next() {
	    try {
		if (!hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = nextName;
		key = nextName;
		return keyReturnedByNext;
	    } finally {
		nextName = null;
	    }
	}

	/** {@inheritDoc} */
	public void remove() {
	    if (keyReturnedByNext == null) {
		throw new IllegalStateException();
	    }

	    dataService.removeServiceBinding(keyReturnedByNext);
	    keyReturnedByNext = null;
	}
    }
}
