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
