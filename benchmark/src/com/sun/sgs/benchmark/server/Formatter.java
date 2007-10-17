/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.server;

import com.sun.sgs.app.ClientSession;

/**
 * Provides formatting functions for different objects.
 */
public class Formatter {
    /** Not instantiable */
    private Formatter() { }

    /**
     * Nicely format a {@link ClientSession} for printed display.
     *
     * @param session the {@code ClientSession} to format
     * @return the formatted string
     */
    public static String format(ClientSession session) {
        return String.format("%s [%s]", session.getName(),
            session.getSessionId().toString());
    }
}
