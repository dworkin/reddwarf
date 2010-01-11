/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.auth;

import javax.security.auth.login.LoginException;


/**
 * A system component that is used to authenticate identities. This interface
 * is provided to <code>Service</code>s and other system components, and is
 * used to authenticate identities within a specific context. Implementations
 * of this interface use <code>IdentityAuthenticator</code>s to actually
 * perform authentication.
 * <p>
 * Note that the <code>IdentityCoordinator</code> provided to
 * <code>Service</code>s via the <code>ComponentRegistry</code> field of
 * their constructor will only be able to authenticate identities within
 * that <code>Service</code>'s context. It is safe, however, to use
 * that <code>IdentityCoordinator</code> in any context and outside of a
 * running transaction. <code>Service</code>s must not, however, use
 * their <code>IdentityCoordinator</code> until <code>ready</code> is
 * called, because before this point the underlying context is not
 * valid and available to the <code>IdentityCoordinator</code>.
 */
public interface IdentityCoordinator
{

    /**
     * Authenticates the given credentials.
     *
     * @param credentials the <code>IdentityCredentials</code> to authenticate
     *
     * @return an authenticated <code>Identity</code> that has not been
     *         notified of login
     *
     * @throws LoginException if authentication fails
     */
    Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException;

}
