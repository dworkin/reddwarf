/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

/**
 * TODO doc
 */
public class SessionDispatcher
    implements ClientSessionListener, ManagedObject, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1L;

    /** The dispatch map. */
    private final ManagedReference dispatchMapRef;

    /**
     * TODO doc
     */
    protected SessionDispatcher(Map<Byte, Object> dispatchMap) {
        dispatchMapRef =
            AppContext.getDataManager()
                .createReference((ManagedObject) dispatchMap);
    }

    /**
     * TODO doc
     */
    @SuppressWarnings("unchecked")
    protected Map<Byte, Object> dispatchMap() {
        return dispatchMapRef.get(Map.class);
    }

    // implement ClientSessionListener

    /**
     * TODO doc
     */
    public void receivedMessage(byte[] message) {
        // TODO
    }

    /**
     * TODO doc
     */
    public void disconnected(boolean graceful) {
        for (Object listener : dispatchMap().values()) {
            // TODO
            //listener.disconnected(graceful);
        }

        AppContext.getDataManager().removeObject(this);
    }

}
