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

package com.sun.sgs.kernel;

import com.sun.sgs.kernel.AccessReporter.AccessType;


/**
 * An interface that provides details of a single object access. Two accessed
 * objects are identical if both were reported by an {@link AccessReporter}
 * obtained by registering an access source with a single {@link
 * AccessCoordinator} using the same source name, and the values returned by
 * {@link #getObjectId()} and {@link #getAccessType()} are equal.
 */
public interface AccessedObject {

    /**
     * Returns the identifier for the accessed object.
     *
     * @return the identifier for the accessed object
     */
    Object getObjectId();

    /**
     * Returns the type of access requested.
     *
     * @return the {@code AccessType}
     */
    AccessType getAccessType();

    /**
     * Returns the supplied description of the object, if any.
     *
     * @return the associated description, or {@code null}
     *
     * @see AccessReporter#setObjectDescription(Object,Object)
     */
    Object getDescription();

    /**
     * Returns the name of the source that reported this object access.
     *
     * @return the object's source
     */
    String getSource();

}
