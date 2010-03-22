/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 * --
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;

/**
 * A persistent {@link Map} that uses service bindings in the data service
 * to store key/value mappings.  Values stored in this map must implement
 * {@link Serializable}, and may (but are not required to) implement {@link
 * ManagedObject}. This map does not permit {@code null} keys or values.
 *
 * <p>A value is stored in the data service using its associated key (a
 * String) as a suffix to the {@code keyPrefix} specified during construction
 * (see {@link BindingKeyedCollections#newMap}). If a value implements {@code
 * Serializable}, but does not implement {@link ManagedObject}, the value will
 * be wrapped in an instance of {@code ManagedSerializable} when storing it in
 * the data service.  Note: users of this map must use this map's APIs to
 * avoid leaking wrappers for non-managed, serializable objects.
 *
 * <p>The iterators of an {@link #entrySet}, {@link #keySet}, or {@link
 * #values} view of this map implement {@link Serializable}. None of the
 * iterators of this map throw {@link ConcurrentModificationException} for any
 * of their methods.
 *
 * <p>The iterators of an {@link #entrySet} or {@link #values} view of
 * this map throw {@code ObjectNotFoundException} during an attempt to
 * access (via the {@link Iterator#next Iterator.next} method) a {@code
 * ManagedObject} that has been removed from the {@link DataManager}.
 *
 * <p>Note: This map is parameterized by value type only.  A {@code String}
 * is the only valid key type for a {@code BindingKeyedMap}.
 *
 * @param	<V> the type for the map's values
 */
public interface BindingKeyedMap<V>
     extends Map<String, V>
{
    /**
     * Returns the key prefix for this map.
     *
     * @return	the key prefix for this map
     */
    String getKeyPrefix();
    
    /**
     * Associates the specified {@code value} with the specified {@code
     * key} in this map.  If the map previously contained a mapping for the
     * key, the previous value is replaced by the specified value.
     *
     * @param	key a key
     * @param	value a value
     * @return	the previous value associated with {@code key}, or
     *		{@code null} if there was no such mapping
     * @throws	IllegalArgumentException if {@code value} does not implement
     *		{@code Serializable} 
     * @throws	ObjectNotFoundException if {@code value} is a managed
     *		object that has been removed from the {@code DataManager},
     *		or the key was previously mapped to a {@link ManagedObject}
     *		that has been removed
     */
    V put(String key, V value);

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
     * @throws	IllegalArgumentException if {@code value} does not implement
     *		{@code Serializable}
     * @throws	ObjectNotFoundException if {@code value} is a managed
     *		object that has been removed from the {@code DataManager}
     */
    boolean putOverride(String key, V value);
    
    /**
     * Returns the value to which the specified {@code key} is mapped, or
     * {@code null} if this map contains no mapping for this key.
     *
     * @param	key a key
     * @return	the value to which the specified {@code key} is mapped, or
     *		{@code null} if there is no such mapping
     *
     * @throws	ClassCastException if {@code key} is not an instance of
     *		{@code String}
     * @throws	ObjectNotFoundException if the key is mapped to
     *		a {@link ManagedObject} that has been removed
     */
    V get(Object key);
    
    /**
     * Removes the mapping for {@code key} from this map if it is present,
     * and returns the value to which the key was previously mapped, or
     * {@code null} if this map contains no mapping for this key.
     *
     * @param	key a key
     * @return	the value to which the specified {@code key} was previously
     *		mapped, or {@code null} if there is no such mapping
     * @throws	ClassCastException if {@code key} is not an instance of
     *		{@code String}
     * @throws	ObjectNotFoundException if the key was previously mapped to
     *		a {@link ManagedObject} that has been removed
     */
    V remove(Object key);
    
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
     */
    boolean removeOverride(String key);

    /**
     * Returns {@code true} if this map maps one or more keys to the specified
     * value.  Values that have been removed from the data manager will be
     * ignored.
     *
     * @param	value	value whose presence in this map is to be tested
     * @return	{@code true} if this map maps one or more keys to the specified
     *		value
     * @throws	ClassCastException if {@code value} is of an inappropriate
     *		type for this map (optional)
     */
    boolean containsValue(Object value);

    /**
     * Removes all mappings from this map.  This operation may be expensive
     * because all of the map entries need to be traversed.
     */
    void clear();

    /**
     * Returns the number of key-value mappings in this map. This operation
     * may be expensive because all of the map entries may need to be
     * traversed to determine the map's size.
     *
     * @return	the number of key-value mappings in this map
     */
    int size();
    

}
