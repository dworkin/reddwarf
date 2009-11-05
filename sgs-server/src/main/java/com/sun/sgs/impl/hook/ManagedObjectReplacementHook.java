package com.sun.sgs.impl.hook;

/**
 * Allows hooking into Darkstar's managed object API.
 *
 * <p>Can be used for example to implement transparent references, where the application code may use managed objects
 * that are wrapped in proxies, but those proxies must be unwrapped before they are passed as a parameter to Darkstar.
 */
public interface ManagedObjectReplacementHook {

    /**
     * Allows replacing an object with another right before it is processed by a method in {@link com.sun.sgs.app.DataManager}
     * that takes a managed object as a parameter. For example, if {@code object} is a proxy for a managed object,
     * then this method should return the actual managed object instead of the proxy.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this hook to replace
     * {@code object} with some other instance, it should return that other instance.
     *
     * @param object the object given as a parameter to a method of the public API which expects a managed object.
     * @return the object that is passed to the implementation of the public API.
     */
    <T> T replaceManagedObject(T object);
}
