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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.auth.IdentityImpl;

/**
 * The system identity is pinned to the node it was created on and
 * is not used for load balancing decisions.
 */
public class SystemIdentity extends IdentityImpl {

    private static final long serialVersionUID = 1L;
    
    /**
     * Creates an instance of {@code SystemIdentity} associated with the
     * given name.
     *
     * @param name the name of this identity
     */
    public SystemIdentity(String name) {
        super(name);
    }
}
