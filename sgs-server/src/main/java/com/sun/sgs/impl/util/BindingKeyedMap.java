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

package com.sun.sgs.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import java.util.Map;

/**
 * A persistent {@link Map} that uses service bindings in the data service
 * to store key/value pairs.  This map does not permit {@code null} keys or
 * values.
 *
 * <p>Note: This map is parameterized by value type only.  A {@code String}
 * is the only valid key type for a {@code BindingKeyedMap}.
 *
 * @param	V the type for the map's values
 */
public interface BindingKeyedMap<V>
     extends Map<String, V>
{
    /**
     * Returns the key prefix for this collection.
     *
     * @return	the key prefix for this collection
     */
    String getKeyPrefix();

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
    boolean putOverride(String key, V value);
    
    /**
     * Removes the specified {@code key} and associated value from the map,
     * and returns {@code true} if the key was previously mapped.  This method
     * will <i>not</i> throw {@link ObjectNotFoundException} if the key was
     * previously mapped to a {@link ManagedObject} that has been removed from
     * the {@link DataManager}.
     *
     * @param	key a key
     *
     * @return	{@code true} if the key was previously mapped, and {@code
     *		false} otherwise
     *
     * @throws IllegalArgumentException if {@code key} is not {@code null}
     *	       and does not implement {@code Serializable} 
     */
    boolean removeOverride(String key);
}
