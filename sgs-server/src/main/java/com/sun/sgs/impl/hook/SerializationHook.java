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

package com.sun.sgs.impl.hook;

import com.sun.sgs.impl.service.data.SerialUtil;
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
     * to existing and to new managed objects.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this
     * hook to replace {@code object} with some other instance, it should return
     * that other instance.
     *
     * @param topLevelObject the top level managed object being serialized. See 
     * {@link SerialUtil.CheckReferencesObjectOutputStream#topLevelObject}
     * @param object         the object to be replaced. See {@link
     *                       ObjectOutputStream#replaceObject(Object)}
     * @return the alternate object that replaced the specified one. See {@link
     *         ObjectOutputStream#replaceObject(Object)}
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
     * @param object object to be substituted. See {@link 
     * ObjectInputStream#resolveObject(Object)}
     * @return the substituted object. See {@link
     * ObjectInputStream#resolveObject(Object)}
     */
    Object resolveObject(Object object);
}
