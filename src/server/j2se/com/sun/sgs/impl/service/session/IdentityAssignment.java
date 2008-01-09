/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.session;

import com.sun.sgs.auth.Identity;

/**
 * Contains the {@code Identity} assigned to a {@code ManagedObject}.
 *
 * TBD: should this be combined with NodeAssignment?  Also, should this
 * class be moved to the com.sun.sgs.service package?
 */
public interface IdentityAssignment {

    /**
     * Returns the identity of this instance.
     *
     * @return	the identity of this instance
     */
    Identity getIdentity();
}
