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

/**
 * Allows hooking into Darkstar's managed object API.
 *
 * <p>Can be used for example to implement transparent references, where the
 * application code may use managed objects that are wrapped in proxies, but
 * those proxies must be unwrapped before they are passed as a parameter to
 * Darkstar.
 */
public interface ManagedObjectReplacementHook {

    /**
     * Allows replacing an object with another right before it is processed by a
     * method in {@link com.sun.sgs.app.DataManager} that takes a managed object
     * as a parameter. For example, if {@code object} is a proxy for a managed
     * object, then this method should return the actual managed object instead
     * of the proxy.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this
     * hook to replace {@code object} with some other instance, it should return
     * that other instance.
     *
     * @param object the object given as a parameter to a method of the public
     *               API which expects a managed object.
     * @param <T>    the type of object.
     * @return the object that is passed to the implementation of the public
     *         API.
     */
    <T> T replaceManagedObject(T object);
}
