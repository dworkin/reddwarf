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
 * This interface is used to define modules that know how to authenticate
 * an identity based on provided credentials. The credentials must be of
 * a form recognizable to the implementation. Note that each application
 * context has its own instances of <code>IdentityAuthenticator</code>s.
 * Typically implementations of <code>IdentityAuthenticator</code> are
 * invoked by a containing <code>IdentityManager</code>.
 * <p>
 * All implementations of <code>IdentityAuthenticator</code> must have a
 * constructor that accepts an instance of <code>java.util.Properties</code>.
 * The provided properties are part of the application's configuration.
 * <p>
 * FIXME: When the IO interfaces are ready, these should also be provided
 * to the constructor.
 */
public interface IdentityAuthenticator
{

    /**
     * Returns the identifiers for this <code>IdentityAuthenticator</code>'s
     * supported credential types. This may contain any number of
     * identifiers, which are matched against the identifier of
     * <code>IdentityCredential</code>s to determine if this
     * <code>IdentityAuthenticator</code> can consume those credentials.
     *
     * @return the identifiers for the supported credential types
     */
    String [] getSupportedCredentialTypes();

    /**
     * Authenticates the given credentials. The returned <code>Identity</code>
     * is valid, but has not yet been notified as logged in.
     *
     * @param credentials the <code>IdentityCredentials</code> to authenticate
     *
     * @return an authenticated <code>Identity</code>
     *
     * @throws LoginException if authentication fails
     */
    Identity authenticateIdentity(IdentityCredentials credentials)
        throws LoginException;

}
