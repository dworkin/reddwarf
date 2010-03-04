/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
