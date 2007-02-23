/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.util;

import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.kernel.ComponentRegistry;
import java.util.HashMap;
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

    /**
     * Registers this component registry as the source of components supplied
     * by the AppContext for the current thread.
     */
    public void registerAppContext() {
	new DummyAbstractKernelAppContext(this);
    }
}
