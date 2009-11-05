package com.sun.sgs.impl.hook;

public class NullSerializationHook implements SerializationHook {

    public Object replaceObject(Object topLevelObject, Object object) {
        return object;
    }

    public Object resolveObject(Object object) {
        return object;
    }
}
