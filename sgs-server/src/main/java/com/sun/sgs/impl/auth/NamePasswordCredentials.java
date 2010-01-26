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
 * --
 */

package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.IdentityCredentials;


/**
 * This simple implementation of <code>IdentityCredentials</code> is used to
 * represent a name and password pair.
 */
public class NamePasswordCredentials implements IdentityCredentials
{

    /**
     * The identifier for this type of credentials.
     */
    public static final String TYPE_IDENTIFIER = "NameAndPasswordCredentials";

    // the name and password
    private final String name;
    private final char [] password;

    /**
     * Creates an instance of <code>NamePasswordCredentials</code>.
     *
     * @param name the name
     * @param password the password
     */
    public NamePasswordCredentials(String name, char [] password) {
        this.name = name;
        this.password = password.clone();
    }

    /**
     * {@inheritDoc}
     */
    public String getCredentialsType() {
        return TYPE_IDENTIFIER;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the password.
     *
     * @return the password
     */
    public char [] getPassword() {
        return password.clone();
    }

}
