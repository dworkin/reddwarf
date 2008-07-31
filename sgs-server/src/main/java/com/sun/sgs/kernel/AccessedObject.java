/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.kernel;

import com.sun.sgs.kernel.AccessNotifier.AccessType;


/**
 * An interface that provides access to the detail of a single object access.
 */
public interface AccessedObject {

    /**
     * Returns the accessed object.
     *
     * @return the accessed {@code Object}
     */
    public Object getObject();

    /**
     * Returns the type of access requested.
     *
     * @return the {@code AccessType}
     */
    public AccessType getAccessType();

    /**
     * Returns the annotation, if any, associated with this object.
     *
     * @return the associated annotation, or {@code null}
     */
    public Object getAnnotation();

    /**
     * Returns the name of the source that reported this object access.
     *
     * @return the object's source
     */
    public String getSource();

}
