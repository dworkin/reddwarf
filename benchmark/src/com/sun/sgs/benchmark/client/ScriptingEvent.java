/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

/**
 * This file lists all of the events in the BenchmarkClient for which handlers
 * can be specified via the ON_EVENT command.
 */
public enum ScriptingEvent {
    /** Enum types */
    DISCONNECTED   ("disconnected"),
    JOINED_CHANNEL (new String[] { "joined", "joined_channel" }),
    LEFT_CHANNEL   (new String[] { "left", "left_channel" }),
    LOGGED_IN      ("logged_in"),
    LOGIN_FAILED   (new String[] { "login_failed", "login_fail" }),
    RECONNECTED    ("reconnected"),
    RECONNECTING   ("reconnecting"),
    RECV_CHANNEL   (new String[] { "recv_channel", "channel_msg" }),
    RECV_DIRECT    (new String[] { "recv_direct", "recv_server", "server_msg" });
    
    /** Member variables */
    
    private String[] aliases;
    
    /** Constructors */
    
    ScriptingEvent(String alias) {
        this(new String[] { alias });
    }
    
    ScriptingEvent(String[] aliases) {
        this.aliases = aliases;
    }
    
    /** Public Methods */
    
    public String[] getAliases() {
        return aliases;
    }
    
    public static ScriptingEvent parse(String arg) {
        for (ScriptingEvent event : ScriptingEvent.values()) {
            for (int i=0; i < event.aliases.length; i++) {
                if (event.aliases[i].equals(arg)) {
                    return event;
                }
            }
        }
        
        return null;
    }
}
