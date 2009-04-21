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

package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.Identity;

import java.io.Serializable;


/**
 * This is a basic implementation of <code>Identity</code> that maps a name
 * to the identity.
 */
public class IdentityImpl implements Identity, Serializable
{
    private static final long serialVersionUID = 1L;

    // the name of this identity
    private final String name;

    /**
     * Creates an instance of <code>Identity</code> associated with the
     * given name.
     *
     * @param name the name of this identity
     */
    public IdentityImpl(String name) {
        if (name == null) {
            throw new NullPointerException("Null names are not allowed");
        }

        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public void notifyLoggedIn() {

    }

    /**
     * {@inheritDoc}
     */
    public void notifyLoggedOut() {

    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if ((o == null) || (!(o instanceof IdentityImpl))) {
            return false;
        }
        return ((IdentityImpl) o).name.equals(name);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
	return getClass().getName() + "[" + name + "]";
    }
}
