/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.shared.impl;

/**
 * This class maps between opCodes and method names used by the MethodRequest
 * class.  Note that the "method name" values are not actually names of methods
 * called on the server, but instead of just keys that index into a map of code
 * handlers (which could be named anything).  Thus, the method names listed here
 * do not need to be kept in sync with any server code; just with the properties
 * file that defines the mapping of methods to code modules.
 *
 * @see com.sun.sgs.benchmark.shared.MethodRequest
 */
public enum CodeMethodRequestOp {

    // Note: at most 256 ops are allowed, since the opcode is a byte.

    CPU               ("cpu"),
    CREATE_CHANNEL    ("createChannel"),
    DATASTORE_CREATE  ("datastoreCreate"),
    DATASTORE_GET     ("datastoreAccess"),
    JOIN_CHANNEL      ("joinChannel"),
    LEAVE_CHANNEL     ("leaveChannel"),
    MALLOC            ("malloc"),
    NOOP              ("noop"),
    SEND_CHANNEL      ("sendChannelMessage"),
    SEND_DIRECT       ("sendDirectMessage"),
    START_TASK        ("startTask");
        
    private String methodName;

    CodeMethodRequestOp(String methodName) {
        this.methodName = methodName;
    }
    
    /**
     * Return the Enum for the given ordinal value.
     */
    public static CodeMethodRequestOp fromOpCode(byte opcode) {
        return CodeMethodRequestOp.values()[opcode];
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public byte getOpCode() {        
        return (byte)ordinal();
    }
}
