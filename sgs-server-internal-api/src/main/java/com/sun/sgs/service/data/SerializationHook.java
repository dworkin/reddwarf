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

package com.sun.sgs.service.data;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Allows replacing objects during serialization and deserialization. This is
 * done in the {@link ObjectOutputStream#replaceObject(Object)} and {@link
 * ObjectInputStream#resolveObject(Object)} methods.
 */
public interface SerializationHook {

    /**
     * Allows replacing an object with another right before it is serialized.
     * This is done before Darkstar makes its own checks about the serialized
     * object (e.g. do not refer directly to other managed objects). While this
     * method is being called, it is still possible to create managed references
     * to existing and to new managed objects. Other service methods should not
     * be called. Neither should managed references be dereferenced.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this
     * hook to replace {@code object} with some other instance, it should return
     * that other instance.
     *
     * <p>This method is called from the {@link
     * ObjectOutputStream#replaceObject(Object)}
     * method. See its contract for more information on the parameters and
     * return values of this method. The only exception is the {@code
     * topLevelObject} parameter which is Darkstar specific.
     *
     * @param topLevelObject the top level managed object being serialized.
     * @param object         the object to be replaced.
     * @return the alternate object that replaced the specified one.
     */
    Object replaceObject(Object topLevelObject, Object object);

    /**
     * Allows replacing an object with another right after it has been
     * deserialized.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this
     * hook to replace {@code object} with some other instance, it should return
     * that other instance.
     *
     * <p>This method is called from the {@link
     * ObjectInputStream#resolveObject(Object)}
     * method. See its contract for more information on the parameters and
     * return values of this method.
     *
     * @param object the object to be substituted.
     * @return the substituted object.
     */
    Object resolveObject(Object object);
}
