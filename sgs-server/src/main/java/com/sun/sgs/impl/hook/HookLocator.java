package com.sun.sgs.impl.hook;

public class HookLocator {

    private static volatile ManagedObjectReplacementHook managedObjectReplacementHook;
    private static volatile SerializationHook serializationHook;

    static {
        // initialize with null hooks
        setManagedObjectReplacementHook(null);
        setSerializationHook(null);
    }

    public static ManagedObjectReplacementHook getManagedObjectReplacementHook() {
        return managedObjectReplacementHook;
    }

    public static void setManagedObjectReplacementHook(ManagedObjectReplacementHook managedObjectReplacementHook) {
        if (managedObjectReplacementHook == null) {
            HookLocator.managedObjectReplacementHook = new NullManagedObjectReplacementHook();
        } else {
            HookLocator.managedObjectReplacementHook = managedObjectReplacementHook;
        }
    }

    public static SerializationHook getSerializationHook() {
        return serializationHook;
    }

    public static void setSerializationHook(SerializationHook serializationHook) {
        if (serializationHook == null) {
            HookLocator.serializationHook = new NullSerializationHook();
        } else {
            HookLocator.serializationHook = serializationHook;
        }
    }
}
