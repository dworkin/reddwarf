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
 */

package com.sun.sgs.impl.protocol.ipwrapper;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.IdentityImpl;
import java.net.InetAddress;

/**
 * Identity associated with a client connected via IP.
 */
public class IPIdentity extends IdentityImpl {

    private final InetAddress address;

    IPIdentity(Identity identity, InetAddress address) {
        super(identity.getName());
        this.address = address;
    }

    /**
     * Return the IP address of the client associated with this identity.
     *
     * @return the IP address of the client associated with this identity
     */
    InetAddress getInetAddress() {
        return address;
    }
}
