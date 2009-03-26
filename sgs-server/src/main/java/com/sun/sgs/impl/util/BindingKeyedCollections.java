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

import com.sun.sgs.kernel.ComponentRegistry;

/**
 * A factory for creating collections whose key/value pairs are stored as
 * service bindings.  This factory may be obtained from the service {@link
 * ComponentRegistry}.
 */
public interface BindingKeyedCollections {

    /**
     * Constructs an instance of a {@link BindingKeyedMap}
     * with the specified {@code keyPrefix}.
     *
     * @param	<V> the type of the map's values
     * @param	keyPrefix a key prefix
     * @return	a {@code BindingKeyedMap} with the specified
     *		{@code keyPrefix}
     * @throws	IllegalArgumentException if {@code keyPrefix} is empty
     */
    <V> BindingKeyedMap<V> newMap(String keyPrefix);

    /**
     * Constructs an instance of a {@link BindingKeyedSet}
     * with the specified {@code keyPrefix}.
     *
     * @param	<V> the type of the set's values
     * @param	keyPrefix a key prefix
     * @return	a {@code BindingKeyedSet} with the specified
     *		{@code keyPrefix} 
     * @throws	IllegalArgumentException if {@code keyPrefix} is empty
     */
    <V> BindingKeyedSet<V> newSet(String keyPrefix);
}

