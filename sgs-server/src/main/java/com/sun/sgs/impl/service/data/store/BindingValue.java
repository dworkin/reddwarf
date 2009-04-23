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
