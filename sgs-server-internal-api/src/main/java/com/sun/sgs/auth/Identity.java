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
