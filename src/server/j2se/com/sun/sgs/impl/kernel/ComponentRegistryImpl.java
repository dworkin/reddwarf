/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.kernel.ComponentRegistry;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.MissingResourceException;
import java.util.Set;


/**
 * This is a simple implementation of <code>ComponentRegistry</code> used
 * to startup and configure the system and individual applications. It
 * can have any objects added to it, but components cannot be removed. This
 * implementation is not thread-safe.
 */
class ComponentRegistryImpl implements ComponentRegistry {

    // the set of components
    private LinkedHashSet<Object> componentSet;

    /**
     * Creates an empty instance of <code>ComponentRegistryImpl</code>.
     */
    ComponentRegistryImpl() {
        componentSet = new LinkedHashSet<Object>();
    }

    /**
     * Creates an instance of <code>ComponentRegistryImpl</code> with the
     * given components.
     *
     * @param components an initial <code>Set</code> of components
     */
    ComponentRegistryImpl(Set<Object> components) {
        componentSet = new LinkedHashSet<Object>(components);
    }

    /**
     * Returns a matching component if there is exactly one, otherwise
     * throws an exception.
     *
     * @param <T> the type of the component
     * @param type a <code>Class</code> representing the type of the component
     *
     * @return a single component, if there is exactly one match
     *
     * @throws MissingResourceException if there isn't exactly one match
     */
    public <T> T getComponent(Class<T> type) {
        Object matchingComponent = null;

        // iterate through the available components
        for (Object component : componentSet) {
            // see if provided type matches the component
            if (type.isAssignableFrom(component.getClass())) {
                // if this isn't the first match, it's an error
                if (matchingComponent != null)
                    throw new MissingResourceException("More than one " +
                                                       "matching component",
                                                       type.getName(), null);
                matchingComponent = component;
            }
        }

        // if no matches were found, it's an error
        if (matchingComponent == null)
            throw new MissingResourceException("No matching components",
                                               type.getName(), null);

        return type.cast(matchingComponent);
    }

    /**
     * Adds a component to the set of available components.
     *
     * @param component the component to add to the registry
     */
    void addComponent(Object component) {
        componentSet.add(component);
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<Object> iterator() {
        return Collections.unmodifiableSet(componentSet).iterator();
    }

    /**
     * Clears all components from the registry.
     */
    void clearComponents() {
        componentSet.clear();
    }

}
