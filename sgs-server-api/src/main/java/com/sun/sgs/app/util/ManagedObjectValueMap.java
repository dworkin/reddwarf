/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import java.util.Map;

/**
 * Utility methods for a map that may contain {@link ManagedObject}s as
 * values in the map.  The methods of this interface do not throw {@code
 * ObjectNotFoundException} if the previous value associated with the key
 * is a {@code ManagedObject} that has been removed from the {@link
 * DataManager}.  Instead, these methods return a boolean value that
 * indicates whether a previous {@code value} was associated with the
 * specified {@code key} before the operation took place.
 *
 * <p>It is up to the map implementation whether it supports null keys
 * and/or values.
 */
public interface ManagedObjectValueMap<K, V> extends Map<K, V> {

    /**
     * Associates the specified {@code key} with the specified {@code value},
     * and returns {@code true} if the key was previously mapped.  This method
     * will <i>not</i> throw {@link ObjectNotFoundException} if the key was
     * previously mapped to a {@link ManagedObject} that has been removed from
     * the {@link DataManager}.
     *
     * @param	key a key
     * @param	value a value
     *
     * @return	{@code true} if the key was previously mapped, and {@code
     *		false} otherwise
     *
     * @throws IllegalArgumentException if either {@code key} or {@code value}
     *	       is not {@code null} and does not implement {@code Serializable}
     */
    boolean putOverride(K key, V value);
    
    /**
     * Removes the specified {@code key} and associated value from the map,
     * and returns {@code true} if the key was previously mapped.  This method
     * will <i>not</i> throw {@link ObjectNotFoundException} if the key was
     * previously mapped to a {@link ManagedObject} that has been removed from
     * the {@link DataManager}.
     *
     * @param	key a key
     * @param	value a value
     *
     * @return	{@code true} if the key was previously mapped, and {@code
     *		false} otherwise
     *
     * @throws IllegalArgumentException if either {@code key} or {@code value}
     *	       is not {@code null} and does not implement {@code Serializable}
     */
    boolean removeOverride(K key);
}
