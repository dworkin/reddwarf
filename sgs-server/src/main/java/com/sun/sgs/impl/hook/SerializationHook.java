package com.sun.sgs.impl.hook;

import java.io.*;

/**
 * Allows replacing objects during serialization and deserialization. This is done in the
 * {@link ObjectOutputStream#replaceObject(Object)} and {@link ObjectInputStream#resolveObject(Object)} methods.
 */
public interface SerializationHook {

    /**
     * Allows replacing an object with another right before it is serialized. This is done before Darkstar
     * makes its own checks about the serialized object (e.g. do not refer directly to other managed objects).
     * While this method is being called, it is still possible to create managed references to existing and
     * to new managed objects.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this hook to replace
     * {@code object} with some other instance, it should return that other instance.
     *
     * @param topLevelObject the top level managed object being serialized. See
     *                       {@link com.sun.sgs.impl.service.data.SerialUtil.CheckReferencesObjectOutputStream#topLevelObject}
     * @param object         the object to be replaced. See {@link ObjectOutputStream#replaceObject(Object)}
     * @return the alternate object that replaced the specified one. See {@link ObjectOutputStream#replaceObject(Object)}
     */
    Object replaceObject(Object topLevelObject, Object object);

    /**
     * Allows replacing an object with another right after it has been deserialized.
     *
     * <p>For this hook to do nothing, it should return {@code object}. For this hook to replace
     * {@code object} with some other instance, it should return that other instance.
     *
     * @param object object to be substituted. See {@link ObjectInputStream#resolveObject(Object)}
     * @return the substituted object. See {@link ObjectInputStream#resolveObject(Object)}
     */
    Object resolveObject(Object object);
}
