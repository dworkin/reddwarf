/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.data.store;

import java.io.Serializable;

/** Provides information about the value bound to a name. */
public final class BindingValue implements Serializable {

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /**
     * The value of the requested name, or {@code -1} if the name was not
     * bound.
     */
    private final long oid;

    /**
     * The next name or {@code null} if the next name was not determined.  The
     * value will also be {@code null} if there was no bound name after the
     * requested name.
     */
    private final String nextName;

    /**
     * Creates an instance of this class.
     *
     * @param	oid the value of the requested name or {@code -1}
     * @param	nextName the next name or {@code null}
     * @throws	IllegalArgumentException if {@code oid} is less than {@code -1}
     */
    public BindingValue(long oid, String nextName) {
	if (oid < -1) {
	    throw new IllegalArgumentException(
		"The oid must not be less than -1");
	}
	this.oid = oid;
	this.nextName = nextName;
    }

    /**
     * Returns whether the name was bound.
     *
     * @return	{@code true} if the name was bound, otherwise {@code false}
     */
    public boolean isNameBound() {
	return oid != -1;
    }

    /**
     * Returns the object ID that the requested name was bound to, or {@code
     * -1} if the name was not bound.
     *
     * @return	the object ID or {@code -1}
     */
    public long getObjectId() {
	return oid;
    }

    /**
     * Returns the next name, if the requested name was not found, otherwise
     * returns {@code null}.  The value will also be {@code null} if there was
     * no bound name after the requested name.
     *
     * @return	the next name or {@code null}
     */
    public String getNextName() {
	return nextName;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    @Override
    public String toString() {
	return "BindingValue[oid=" + oid + ", nextName=" + nextName + "]";
    }
}
