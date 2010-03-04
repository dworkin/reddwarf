/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
     * Returns the name associated with this identity.  This name must
     * be unique within the application, and two identities with the same
     * name must be equal.
     *
     * @return the identity's name
     */
    String getName();

    /**
     * Notifies the system that this identity has logged in. Typically this
     * is done shortly after authenticating the identity. Note that it is
     * valid to authenticate an identity that does not log into the system.
     */
    void notifyLoggedIn();

    /**
     * Notifies the system that this identity has logged out. Typically this
     * is done after a client disconnects from the system.
     */
    void notifyLoggedOut();

}
