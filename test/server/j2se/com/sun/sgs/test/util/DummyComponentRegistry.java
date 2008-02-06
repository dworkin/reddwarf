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

package com.sun.sgs.test.util;

import com.sun.sgs.kernel.ComponentRegistry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Provides a simple implementation of ComponentRegistry, for testing.  This
 * version requires registering and looking up components by exact type
 * matches.
 */
public class DummyComponentRegistry implements ComponentRegistry {

    /** Mapping from type to component. */
    private final Map<Class<?>, Object> components =
	new HashMap<Class<?>, Object>();

    /** Creates an instance of this class. */
    public DummyComponentRegistry() { }

    /** {@inheritDoc} */
    public <T> T getComponent(Class<T> type) {
	Object component = components.get(type);
	if (component == null) {
	    throw new MissingResourceException(
		"Component of type " + type + " was not found",
		type.getName(), "Component");
	}
	return type.cast(component);
    }

    /**
     * Specifies the component that should be returned for an exact match for
     * the specified type.
     */
    public <T> void setComponent(Class<T> type, T component) {
	if (type == null || component == null) {
	    throw new NullPointerException("Arguments must not be null");
	}
	components.put(type, component);
    }

    /** Returns an iterator over the components */
    public Iterator<Object> iterator() {
        return components.values().iterator();
    }

    /**
     * Clears all components from the registry.
     */
    public void clearComponents() {
	components.clear();
    }
}
