/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;


/**
 * This is a simple implementation of <code>TaskOwner</code> that is nothing
 * more than a container for a name and a context.
 */
public class TaskOwnerImpl implements TaskOwner {

    // the identity of the owner
    private final Identity identity;

    // the context of the owner
    private final KernelAppContext context;

    // a cache for the hash code
    private final int hash;

    /**
     * Creates an instance of <code>SimpleTaskOwner</code>.
     *
     * @param identity the <code>Identity</code> of the owner
     * @param context the context in which this owner runs tasks
     */
    public TaskOwnerImpl(Identity identity, KernelAppContext context) {
        this.identity = identity;
        this.context = context;

        // cache the hash code as the hash of the identity and the context
        hash = identity.hashCode() ^ context.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    public KernelAppContext getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Provides some diagnostic detail about this owner.
     *
     * @return a <code>String</code> representation of the owner.
     */
    @Override
    public String toString() {
        return "[ id=\"" + identity.getName() + "\" context=" +
            context.toString() + " ]";
    }

    /**
     * Returns <code>true</code> if the object is an instance of
     * <code>TaskOwnerImpl</code> and represents the same owner.
     *
     * @param o the other owner
     *
     * @return <code>true</code> if the given owner is the same as this
     *         owner, <code>false</code> otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (! (o instanceof TaskOwnerImpl))
            return false;

        TaskOwnerImpl other = (TaskOwnerImpl)o;

        return ((other.identity.equals(identity)) &&
                (other.context.equals(context)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return hash;
    }
}
