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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.auth;


/**
 * Represents credentials that can be used for authentication. These
 * credentials may be consumed by any mechanism for authentication.
 * Implementations of <code>IdentityCredentials</code> should not
 * actually contain any authentication logic. This should instead be
 * part of the consuming <code>IdentityAuthenticator</code>.
 */
public interface IdentityCredentials
{

    /**
     * Returns the identifier for the type of credentials. This will be
     * used by the <code>IdentityManager</code> to find applicable
     * <code>IdentityAuthenticator</code>s to consume these credentials.
     *
     * @return an identifier for the type of credentials
     */
    String getCredentialsType();

}
