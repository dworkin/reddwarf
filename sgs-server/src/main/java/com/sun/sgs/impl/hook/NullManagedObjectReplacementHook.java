package com.sun.sgs.impl.hook;

public class NullManagedObjectReplacementHook implements ManagedObjectReplacementHook {

    public <T> T replaceManagedObject(T object) {
        return object;
    }
}
