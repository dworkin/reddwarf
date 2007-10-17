/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

import java.nio.charset.Charset;

/**
 * A collections of constants used by both clients and the server.
 */
public interface GermWarConstants {
    /** The playerId used by any objects that belong to the server. */
    public static final int SERVER_PLAYER_ID = 0;

    /** The name of the global channel. */
    public static final String GLOBAL_CHANNEL_NAME = "GLOBAL";

    /** The {@link Charset} encoding for client/server messages. */
    public static final String MESSAGE_CHARSET = "UTF-8";

    /** The duration of turns (ms). */
    public static final int TURN_DURATION = 10*1000;
}
