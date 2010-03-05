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
