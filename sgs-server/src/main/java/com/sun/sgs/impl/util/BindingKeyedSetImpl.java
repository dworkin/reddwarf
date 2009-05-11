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

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;

/**
 * An implementation of {@code BindingKeyedSet} backed by a
 * {@link BindingKeyedMap}.
 *
 * @param	<E> the element type
 */
public class BindingKeyedSetImpl<E>
    extends AbstractSet<E>
    implements BindingKeyedSet<E>, Serializable
{

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The reference to the backing map for this set.
     *
     * @serial
     */
    private final BindingKeyedMapImpl<E> map;

    /**
     * Creates an empty set with the specified {@code keyPrefix}.
     *
     * @param	keyPrefix the key prefix for a service binding name
     * @throws	IllegalArgumentException if {@code keyPrefix} is empty
     */
    BindingKeyedSetImpl(String keyPrefix) {
	map = new BindingKeyedMapImpl<E>(keyPrefix);
    }

    /* -- Implement BindingKeyedSet -- */

    /** {@inheritDoc} */
    public String getKeyPrefix() {
	return map.getKeyPrefix();
    }
    
    /* -- Implement AbstractSet overrides -- */
    
    /** {@inheritDoc} */
    @Override
    public boolean add(E e) {
	return !map.putOverride(e.toString(), e);
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
	map.clear();
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(Object o) {
	if (o == null) {
	    throw new NullPointerException("null object");
	}
	return map.containsKey(o.toString());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
	return map.isEmpty();
    }

    /** {@inheritDoc} */
    public Iterator<E> iterator() {
	return map.values().iterator();
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(Object o) {
	return map.removeOverride(o.toString());
    }

    /** {@inheritDoc} */
    public int size() {
	return map.size();
    }

    /** {@inheritDoc} */
    @Override
    public boolean retainAll(Collection<?> c) {
	/*
	 * The AbstractCollection method doesn't currently do this check.
	 * -tjb@sun.com (10/10/2007)
	 */
	if (c == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	return super.retainAll(c);
    }
}
