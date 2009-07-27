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

package com.sun.sgs.impl.service.data.store.cache;

import static com.sun.sgs.impl.sharedutil.Objects.checkNull;

/**
 * A key to represent binding names that is {@link Comparable} and provides a
 * non-{@code null} value to represent the a value beyond any possible name.
 */
final class BindingKey implements Comparable<BindingKey> {

    /** A key after all possible names. */
    static final BindingKey LAST = new BindingKey(null);

    /** The associated name or {@code null} for the last key. */
    private final String name;

    /**
     * Creates an instance of this class.
     *
     * @param	name the name or {@code null}
     */
    private BindingKey(String name) {
	this.name = name;
    }

    /**
     * Gets a binding key for the specified name, which should not be
     * {@code null}.
     *
     * @param	name the name of the binding
     * @return	the key to represent the binding
     */
    static BindingKey get(String name) {
	checkNull("name", name);
	return new BindingKey(name);
    }

    /**
     * Gets a binding key for the specified name, treating {@code null} as
     * representing the last key.
     *
     * @param	name the name of the binding or {@code null} for the last key
     * @return	the key
     */
    static BindingKey getAllowLast(String name) {
	return new BindingKey(name);
    }

    /**
     * Returns the name associated with this key.
     *
     * @return	the name
     * @throws	UnsupportedOperationException if this is the last key
     */
    String getName() {
	if (name == null) {
	    throw new UnsupportedOperationException(
		"The last key does not have a name");
	}
	return name;
    }

    /**
     * Returns the name associated with this key, or {@code null} if this is
     * the last key.
     *
     * @return	the name or {@code null}
     */
    String getNameAllowLast() {
	return name;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(BindingKey bindingKey) {
	if (name == null) {
	    return bindingKey.name == null ? 0 : 1;
	} else if (bindingKey.name == null) {
	    return -1;
	} else {
	    /*
	     * FIXME: Result is wrong if either name contains a NUL because
	     * we're using modified UTF-8 encoding.  Should check for NUL
	     * and compare real encodings in that case.
	     * -tjb@sun.com (06/22/2009)
	     */
	    return name.compareTo(bindingKey.name);
	}
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object object) {
	if (this == object) {
	    return true;
	} else if (object instanceof BindingKey) {
	    BindingKey bindingKey = (BindingKey) object;
	    return name == null ? bindingKey.name == null
		: name.equals(bindingKey.name);
	} else {
	    return false;
	}
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
	return (name == null) ? 0x7654321 : name.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
	return "BindingKey[" + (name == null ? "LAST" : "name:" + name) + "]";
    }
}
