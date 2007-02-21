package com.sun.sgs.example.chat.app;

/**
 * Defines the commands understood by the {@link ChatApp} server.
 */
public enum ChatCommand {

    /**
     * Joins this session to the named channel.
     * <pre>
     *    /join channelName
     * </pre>
     */
    JOIN,

    /**
     * Removes this session from the named channel.
     * <pre>
     *    /leave channelName
     * </pre>
     */
    LEAVE,

    /**
     * Echos the given message back to the sender.
     * <pre>
     *    /ping message
     * </pre>
     */
    PING,

    /**
     * Forcibly disconnects this session to the named channel.
     * <pre>
     *    /disconnect
     * </pre>
     */
    DISCONNECT,

    /**
     * Join this session to the named channel.
     * <pre>
     *    /shutdown
     * </pre>
     */
    SHUTDOWN;
}
