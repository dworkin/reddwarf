/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.auth;


/**
 * This interface represents a single identity in a form used only by
 * <code>Service</code>s, the kernel, and other system-level components.
 * Implementations must also implement <code>Serializable</code>,
 * <code>equals</code>, and <code>hashCode</code>.
 * <p>
 * While instances of <code>Identity</code> may be used by
 * <code>Service</code>s and other components to manage users or task
 * ownership (including serializing and persisting <code>Identity</code>s),
 * this interface is really a means for communicating with the accounting
 * and management system. As such, any combinations of calls to
 * <code>notifyLoggedIn</code> and <code>notifyLoggedOut</code> are
 * valid. Note that an application may still enforce that its users are not
 * allowed to login multiple times, or may only logout if they are logged in.
 */
public interface Identity {

    /**
     * Returns the name associated with this identity.
     *
     * @return the identity's name
     */
    public String getName();

    /**
     * Notifies the system that this identity has logged in. Typically this
     * is done shortly after authenticating the identity. Note that it is
     * valid to authenticate an identity that does not log into the system.
     */
    public void notifyLoggedIn();

    /**
     * Notifies the system that this identity has logged out. Typically this
     * is done after a client disconnects from the system.
     */
    public void notifyLoggedOut();

}
