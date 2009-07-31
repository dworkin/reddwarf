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
abstract class BindingKey implements Comparable<BindingKey> {

    /** A key before all possible names. */
    static final BindingKey FIRST = new BindingKey() {
	String getNameAllowFirst() {
	    return null;
	}
	public int compareTo(BindingKey other) {
	    return other == FIRST ? 0 : -1;
	}
	public boolean equals(Object other) {
	    return this == other;
	}
	public int hashCode() {
	    return 0x1234567;
	}
	public String toString() {
	    return "BindingKey[FIRST]";
	}
    };

    /** A key after all possible names. */
    static final BindingKey LAST = new BindingKey() {
	String getNameAllowLast() {
	    return null;
	}
	public int compareTo(BindingKey other) {
	    return other == LAST ? 0 : 1;
	}
	public boolean equals(Object other) {
	    return this == other;
	}
	public int hashCode() {
	    return 0x7654321;
	}
	public String toString() {
	    return "BindingKey[LAST]";
	}
    };

    /** Creates an instance of this class. */
    private BindingKey() { }

    /**
     * Gets a binding key for the specified name, which should not be
     * {@code null}.
     *
     * @param	name the name of the binding
     * @return	the key to represent the binding
     */
    static BindingKey get(String name) {
	return new BindingKeyName(name);
    }

    /**
     * Gets a binding key for the specified name, treating {@code null} as
     * representing the first key.
     *
     * @param	name the name of the binding or {@code null} for the first key
     * @return	the key
     */
    static BindingKey getAllowFirst(String name) {
	return (name == null) ? FIRST : new BindingKeyName(name);
    }

    /**
     * Gets a binding key for the specified name, treating {@code null} as
     * representing the last key.
     *
     * @param	name the name of the binding or {@code null} for the last key
     * @return	the key
     */
    static BindingKey getAllowLast(String name) {
	return (name == null) ? LAST : new BindingKeyName(name);
    }

    /**
     * Returns the name associated with this key.
     *
     * @return	the name
     * @throws	UnsupportedOperationException if this is the first or last key
     */
    String getName() {
	throw new UnsupportedOperationException("getName is not supported");
    }

    /**
     * Returns the name associated with this key, or {@code null} if this is
     * the first key.
     *
     * @return	the name or {@code null}
     * @throws	UnsupportedOperationException if this is the last key
     */
    String getNameAllowFirst() {
	throw new UnsupportedOperationException(
	    "getNameAllowFirst is not supported");
    }

    /**
     * Returns the name associated with this key, or {@code null} if this is
     * the last key.
     *
     * @return	the name or {@code null}
     * @throws	UnsupportedOperationException if this is the first key
     */
    String getNameAllowLast() {
	throw new UnsupportedOperationException(
	    "getNameAllowLast is not supported");
    }

    /** A binding key for a particular name. */
    private static class BindingKeyName extends BindingKey {
	private final String name;
	BindingKeyName(String name) {
	    checkNull("name", name);
	    this.name = name;
	}
	String getName() {
	    return name;
	}
	String getNameAllowFirst() {
	    return name;
	}
	String getNameAllowLast() {
	    return name;
	}
	public int compareTo(BindingKey bindingKey) {
	    if (bindingKey == FIRST) {
		return 1;
	    } else if (bindingKey == LAST) {
		return -1;
	    } else {
		/*
		 * FIXME: Result is wrong if either name contains a NUL because
		 * we're using modified UTF-8 encoding.  Should check for NUL
		 * and compare real encodings in that case.
		 * -tjb@sun.com (06/22/2009)
		 */
		return name.compareTo(((BindingKeyName) bindingKey).name);
	    }
	}
	public boolean equals(Object object) {
	    if (this == object) {
		return true;
	    } else if (object instanceof BindingKeyName) {
		return name.equals(((BindingKeyName) object).name);
	    } else {
		return false;
	    }
	}
	public int hashCode() {
	    return name.hashCode();
	}
	public String toString() {
	    return "BindingKey[name:" + name + "]";
	}
    }
}
