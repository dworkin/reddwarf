/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

/**
 * This file lists all of the text commands that are supported by the
 * BenchmarkClient.
 */
public enum ScriptingCommandType {
    /** Enum types */
    CPU            (new String[] { "cpu", "do_cpu" }),
    CREATE_CHANNEL (new String[] { "create_channel", "chcreate" }),
    DATASTORE      (new String[] { "datastore", "do_datastore", "get_datastore" }),
    DISCONNECT     ("disconnect"),
    END_BLOCK      ("}"),
    EXIT           (new String[] { "exit", "quit" }),
    HELP           ("help"),
    JOIN_CHANNEL   (new String[] { "join", "join_channel", "chjoin" }),
    LEAVE_CHANNEL  (new String[] { "leave", "leave_channel", "chleave" }),
    LOGIN          ("login"),
    LOGOUT         ("logout"),
    MALLOC         (new String[] { "malloc", "do_malloc", "memory", "do_memory" }),
    ON_EVENT       (new String[] { "on_event", "onevent", "on" }),
    PAUSE          ("pause"),
    PRINT          (new String[] { "debug", "print", "printf" }),
    SEND_CHANNEL   (new String[] { "send_channel", "channel_send", "chsend" }),
    SEND_DIRECT    (new String[] { "send_direct", "send_server", "direct_send" }),
    START_BLOCK    ("{"),
    WAIT_FOR       (new String[] { "wait_for", "wait" });
    
    /** Member variables */
    
    private String[] aliases;
    
    /** Constructors */
    
    ScriptingCommandType(String alias) {
        this(new String[] { alias });
    }
    
    ScriptingCommandType(String[] aliases) {
        if (aliases.length == 0) throw new ArrayIndexOutOfBoundsException(0);
        this.aliases = aliases;
    }
    
    /** Public Methods */
    
    public String[] getAliases() {
        return aliases;
    }
    
    public String getAlias() {
        return aliases[0];
    }
    
    public static ScriptingCommandType parse(String arg)
    {
        for (ScriptingCommandType cmd : ScriptingCommandType.values()) {
            for (int i=0; i < cmd.aliases.length; i++) {
                if (cmd.aliases[i].equals(arg)) {
                    return cmd;
                }
            }
        }
        
        return null;
    }
}
