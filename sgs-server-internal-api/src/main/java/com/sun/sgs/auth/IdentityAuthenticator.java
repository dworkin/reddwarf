/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
