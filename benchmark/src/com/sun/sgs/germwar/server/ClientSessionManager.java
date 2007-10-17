/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.server;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionId;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.util.ScalableHashMap;

/**
 * Provides a lookup service for client sessions.
 */
public class ClientSessionManager {
    private static final String DATA_BINDING = "ClientSessionManager";

    /**
     * Creates a new {@code ClientSessionManager}.
     */
    private ClientSessionManager() {
        // empty
    }

    /**
     * Gets the map out of the data store.
     */
    @SuppressWarnings("unchecked")
    private static Map<String,ClientSession> getMap() {
        DataManager dm = AppContext.getDataManager();

        try {
            return (Map<String,ClientSession>)dm.getBinding(DATA_BINDING, Map.class);
        } catch (NameNotBoundException nnbe) {
            /** Must not exist yet - create it. */
            ScalableHashMap<String,ClientSession> instance =
                new ScalableHashMap<String,ClientSession>();
            dm.setBinding(DATA_BINDING, instance);
            return instance;
        }
    }

    /**
     * Stores {@code session} under the {@code username} in the map.
     */
    public static ClientSession add(String username, ClientSession session) {
        return getMap().put(username, session);
    }

    /**
     * Removes all entries from the map.
     */
    public static void clear() {
        getMap().clear();
    }

    /**
     * Returns whether the specified session exists in the map.
     */
    public static boolean exists(String username) {
        return getMap().containsKey(username);
    }

    /**
     * Returns the {@link ClientSession} stored under {@code username}, or
     * {@code null} if none currently exists (user is not logged in).
     */
    public static ClientSession get(String username) {
        return getMap().get(username);
    }

    /**
     * If a {@link ClientSession} is currently stored under {@code username}, it
     * is removed from the map and returned.  Otherwise, {@code null} is returned.
     */
    public static ClientSession remove(String username) {
        return getMap().remove(username);
    }

    /**
     * Returns the total number of {@link ClientSession}s stored.  Note that
     * this may be an expensive operation.
     */
    public static int size() {
        return getMap().size();
    }
}
