package com.sun.sgs.impl.service.data;

public class ReferenceTableUtil {

    public static void flushModifiedObjects() {
        Context context = getActiveContext();
        if (context != null) {
            context.refs.flushModifiedObjects();
        }
    }

    private static Context getActiveContext() {
        try {
            return DataServiceImpl.getContextNoJoin();
        } catch (RuntimeException e) {
            // no active context, possibly because we are in a non-durable task
            return null;
        }
    }
}
