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

import com.sun.sgs.service.data.ManagedObjectReplacementHook;
import com.sun.sgs.service.data.SerializationHook;

/**
 * Locates the hooks.
 */
public final class HookLocator {

    private static volatile ManagedObjectReplacementHook
            managedObjectReplacementHook;
    private static volatile SerializationHook serializationHook;

    static {
        // initialize with null hooks
        setManagedObjectReplacementHook(null);
        setSerializationHook(null);
    }

    private HookLocator() {
    }

    /**
     * Returns the ManagedObjectReplacementHook.
     *
     * @return the hook.
     */
    public static ManagedObjectReplacementHook getManagedObjectReplacementHook(
    ) {
        return managedObjectReplacementHook;
    }

    /**
     * Sets the ManagedObjectReplacementHook.
     *
     * @param managedObjectReplacementHook the hook.
     */
    public static void setManagedObjectReplacementHook(
            ManagedObjectReplacementHook managedObjectReplacementHook) {
        if (managedObjectReplacementHook == null) {
            HookLocator.managedObjectReplacementHook =
                    new NullManagedObjectReplacementHook();
        } else {
            HookLocator.managedObjectReplacementHook =
                    managedObjectReplacementHook;
        }
    }

    /**
     * Returns the SerializationHook.
     *
     * @return the hook.
     */
    public static SerializationHook getSerializationHook() {
        return serializationHook;
    }

    /**
     * Sets the SerializationHook.
     *
     * @param serializationHook the hook.
     */
    public static void setSerializationHook(
            SerializationHook serializationHook) {
        if (serializationHook == null) {
            HookLocator.serializationHook = new NullSerializationHook();
        } else {
            HookLocator.serializationHook = serializationHook;
        }
    }
}
