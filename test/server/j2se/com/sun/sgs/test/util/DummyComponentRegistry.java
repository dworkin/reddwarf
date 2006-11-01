package com.sun.sgs.test.util;

import com.sun.sgs.kernel.ComponentRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;

public class DummyComponentRegistry implements ComponentRegistry {
    private final Map<Class<?>, Object> components =
	new HashMap<Class<?>, Object>();

    public <T> T getComponent(Class<T> type) {
	Object component = components.get(type);
	if (component == null) {
	    throw new MissingResourceException(
		"Component of type " + type + " was not found",
		type.getName(), "Component");
	}
	return type.cast(component);
    }

    public <T> void setComponent(T component, Class<T> type) {
	components.put(type, component);
    }
}
